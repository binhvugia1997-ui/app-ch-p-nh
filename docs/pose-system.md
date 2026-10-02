# Pose System — representation, matching, corrections and the dashed guide

Covers Phase 3 (dashed pose guide) and Phase 4 (pose matching + corrections). Numeric thresholds are
tagged with the evidence classes from `photography-rules.md` §3.

---

## 1. Why geometry instead of images

A PNG/SVG pose library fails the product requirements: it cannot be matched against a live body, cannot be
mirrored, scaled, or rotated robustly, cannot express "this part matters more", and cannot be localized or
measured. Therefore:

* **Templates are normalized landmark geometry** (JSON, one file per pose), with optional drawing hints.
* Images may later exist as *thumbnails* generated from the geometry — never as the source of truth.
* Every template carries the same anatomical landmark ids as the runtime detector, so matching is a pure
  comparison of like-for-like structures.

---

## 2. Canonical skeleton and landmark set

Based on MediaPipe Pose Landmarker's 33 landmarks (`ai-models.md`). Semantics that matter:

* ids are **anatomical** (`left_shoulder` = the subject's left), never spatial.
* each landmark has `visibility` (in frame and not occluded) and `presence` (in frame).
* world landmarks add a third dimension but **z is synthetic (GHUM) and not metric-accurate** — usable for
  relative comparisons only.

Landmark roles used by the pose system:

| Role | Landmarks | Used for |
| --- | --- | --- |
| `HEAD_CORE` | `nose`, `left_eye`, `right_eye`, `left_ear`, `right_ear` | head position, roll, head-top estimate |
| `SHOULDER_LINE` | `left_shoulder`, `right_shoulder` | torso orientation, shoulder angle |
| `HIP_LINE` | `left_hip`, `right_hip` | torso orientation, weight shift |
| `ARM_L` / `ARM_R` | shoulder, elbow, wrist (+ pinky/index/thumb as hand proxy) | arm geometry, gap, foreshortening |
| `LEG_L` / `LEG_R` | hip, knee, ankle, heel, foot_index | stance, weight, leg geometry |
| `TORSO` | shoulder line, hip line, mid-points | normalization reference frame |

Landmark weights for matching (proposal, `CALIBRATION_REQUIRED`): shoulders/hips/nose `1.0`,
knees/elbows `0.9`, wrists/ankles `0.8`, feet/hands `0.5`, eyes/ears `0.6`.

---

## 3. Template data model

Authoritative schema: `specs/schemas/pose-template.schema.json`.

```jsonc
{
  "id": "solo_weight_shift_standing_v1",
  "name": { "vi": "Đứng chuyển trọng tâm", "en": "Weight-shift standing" },
  "peopleCount": 1,
  "shotTypes": ["THREE_QUARTER", "FULL_BODY"],
  "contextTags": ["outdoor", "casual", "street", "wall"],
  "difficulty": "EASY",
  "mirrorAllowed": true,
  "orientationExpectation": { "torsoYawDeg": 30, "toleranceDeg": 20 },

  "frame": {                       // how to frame this pose (guidance, not matching)
    "cameraHeight": "WAIST",       // GROUND | WAIST | CHEST | EYE | ABOVE_EYE
    "cameraDistanceHint": "3-4 m for full body",
    "headroomBand": [0.05, 0.18],
    "subjectHeightFraction": [0.7, 0.95]
  },

  "landmarks": [                   // SUBJECT space: hip-mid origin, y down, torso length = 1.0
    { "id": "nose",           "x":  0.02, "y": -1.32, "weight": 1.0, "required": true  },
    { "id": "left_shoulder",  "x":  0.28, "y": -0.62, "weight": 1.0, "required": true  }
  ],

  "jointAngles": [                 // expected interior angles, degrees
    { "joint": "left_elbow", "value": 150, "tolerance": 25, "weight": 0.8 },
    { "joint": "right_knee", "value": 172, "tolerance": 20, "weight": 0.6 }
  ],

  "relativeVectors": [             // unit vectors between landmarks, direction matters, length does not
    { "from": "left_shoulder", "to": "left_elbow", "dir": [0.35, 0.94], "weight": 0.8 }
  ],

  "bodyOrientation": {
    "shoulderAngleDeg": -12,       // shoulder line vs image x-axis, template space
    "hipAngleDeg": 8,
    "facingDeg": 30                // 0 = facing camera, 90 = profile
  },

  "cameraRecommendations": ["WIDE_SHOULDERS_ANGLE", "SLIGHTLY_ABOVE_EYE"],
  "compositionRecommendations": ["EYES_UPPER_THIRD", "LEAD_ROOM", "FULL_BODY_GROUND_LINE"],

  "provenance": {
    "source": "craft consensus (see research-sources.md §Posing)",
    "status": "SEED_UNVALIDATED",
    "calibration": "requires capture validation in Phase 6"
  }
}
```

Field notes:

* `frame` and `compositionRecommendations` drive **guidance for the photographer**, not the match.
* `provenance.status` must be `SEED_UNVALIDATED` for every template authored in Phase 0 — the coordinates
  are engineering seeds, not validated art direction.
* Every template must state `mirrorAllowed` explicitly; asymmetric templates (e.g. leaning against a wall
  with the right shoulder) set `false` or provide a mirrored variant id.

### 3.1 Template space and normalization

```
SUBJECT space:
  origin        = midpoint(left_hip, right_hip)
  +x            = image right
  +y            = image down
  unit          = torsoLength = ‖mid_shoulder − mid_hip‖₂D     (in the template: 1.0 at the hips-to-shoulder span)
  z             = optional, torso units, positive away from camera (not used for matching in MVP)
```

Normalizing by **torso length** (not body height, not shoulder width) is deliberate:

* torso length is the most stable, most frequently visible, and least foreshortening-prone span available
  (hips and shoulders are almost always in frame for a photo subject);
* it removes height and limb-length differences between people;
* it fails when the torso is itself foreshortened (subject leaning far forward/backward) — mitigated by
  using *directions and angles* as the primary terms and positions as secondary (§4.2).

---

## 4. Pose matching

### 4.1 Pipeline

```
detected landmarks (ANALYSIS space)
   → usability mask            (visibility/presence, out-of-frame, implausible jumps)
   → normalization             (SUBJECT space: hip origin, torso unit, mirrored to template handedness)
   → rigid alignment           (rotation + translation only, no scale change after normalization)
   → component scoring         (Head, Torso, LeftArm, RightArm, LeftLeg, RightLeg)
   → weighted aggregation      (per-template component weights × per-landmark weights)
   → score + per-component report + reasons
```

**Never** use raw pixel distance as the score. Raw distance is proportional to subject size, penalizes
taller people, and rewards standing still close to the camera.

### 4.2 Terms (in order of robustness)

1. **Joint-angle similarity** (most robust to body proportions, moderately robust to roll)

```
sAngle(j) = max(0, 1 − |θ_detected(j) − θ_template(j)| / tol_max(j))
θ = interior angle at a joint between the two adjacent segments
tol_max(j) = template tolerance, floored at 20° for large joints and 25° for wrists/ankles
             (floor justified by single-camera pose angle RMSE ≈ 8–11°, see photography-rules.md §3)
```

2. **Limb-vector direction similarity** (robust to limb length and to overall scale)

```
sDir(seg) = (cos(angle between unit(v_detected) and unit(v_template)) + 1) / 2      ∈ [0,1]
```
Foreshortened segments have unreliable direction → their weight is multiplied by a
*foreshortening factor* (see `POSE_LIMB_FORESHORTENED`) when world landmarks are available.

3. **Body orientation similarity** (torso facing angle, shoulder and hip line angles)

```
sOrient = max(0, 1 − Δfacing / 35°) · w_shoulder + … (component-wise)
```

4. **Normalized position similarity** (secondary term, after the alignment below)

```
sPos(l) = max(0, 1 − ‖p_detected(l) − p_aligned_template(l)‖ / pos_tol)
pos_tol ≈ 0.35 torso units for extremities, 0.15 for hips/shoulders     [CALIBRATION_REQUIRED]
```

5. **Symmetry/mirror term** — see §4.4.

### 4.3 Rigid alignment (how to compare two skeletons fairly)

Given detected landmarks in SUBJECT space and the template (already in SUBJECT space):

1. **Handedness:** if the template allows mirroring, compute the score for both handedness assignments and
   take the better one. Mirroring is a discrete flip of `x` on all landmarks — cheap and exact.
2. **Rotation:** solve a 2D orthogonal Procrustes problem (Kabsch in 2D) over `required` landmarks only,
   **without scaling** (scaling is already handled by the torso normalization), and **clamp the rotation to
   ±25°** so that a wildly wrong body rotation cannot be "aligned away" into a false match.
3. **Translation:** after normalization, the hip midpoint is already the origin for both — no additional
   translation is needed except for partial templates (below).
4. **Partial templates (head-and-shoulders only):** align on the available `required` landmarks and record
   `coverage` (see §4.5). Never align on a single landmark (degenerate rotation).

> Rationale for the rotation clamp: without it, a subject lying at 90° could be mathematically rotated into
> a standing template and score 1.0. The clamp encodes "the photographer is not going to rotate the whole
> world for you" while still tolerating a slightly tilted camera.

### 4.4 Mirroring

* Templates declare `mirrorAllowed`. When true, the matcher evaluates both handedness assignments and keeps
  the better score, and the *guide* is drawn in the winning handedness.
* When false, mirroring is only considered with a score penalty (`× 0.85`, `CALIBRATION_REQUIRED`).
* Guidance wording must respect the winning handedness: "raise your left arm" must refer to the subject's
  anatomical left, and the overlay must show the correct side even for a mirrored front-camera preview
  (see `architecture.md` §5 rule 3–4).

### 4.5 Component scoring and partial visibility

Components: `Head`, `Torso`, `LeftArm`, `RightArm`, `LeftLeg`, `RightLeg`, each with a template-provided
weight (defaults: Head 0.15, Torso 0.25, Arms 0.2 each, Legs 0.1 each; `CALIBRATION_REQUIRED`).

```
componentScore(c) = Σ_l w(l) · usable(l) · termScore(l)
                    ─────────────────────────────────────
                    Σ_l w(l) · usable(l)
```
where `termScore(l)` blends the applicable terms for that landmark (angle for joints, direction for limb
vectors, position for endpoints).

```
coverage(c) = (Σ_l w(l) · usable(l)) / (Σ_l w(l))          # how much of the component we can see
```

Aggregation:

```
score = Σ_c  W(c) · coverage(c)^α · componentScore(c)
        ────────────────────────────────────────────────
        Σ_c  W(c) · coverage(c)^α
```
with `α = 1` for MVP (linear weighting). A component with `coverage < 0.35` is **excluded** from the
numerator and denominator and reported as `notEvaluated`. This is the mechanism that prevents one invisible
ankle from destroying the score — and equally prevents a pose being declared "matched" while 60 % of the
body is invisible.

Hard gates before a score is published:

* `visibleFraction(subject) ≥ 0.5`
* at least `Torso` + one other component evaluated
* all template `required` landmarks that lie inside the frame must be `usable` (otherwise report
  `POSE_UNVERIFIABLE`, not a low score)

### 4.6 Score bands (for UI and readiness)

| Band | Range (proposal) | Meaning | Readiness role |
| --- | --- | --- | --- |
| `MATCHED` | ≥ 0.85 | Very close to the template | satisfies the pose criterion (with stability) |
| `CLOSE` | 0.70 – 0.85 | Recognizably the intended pose | satisfies with longer stability window |
| `DIFFERENT` | 0.45 – 0.70 | Wrong or partially achieved | not ready |
| `FAR` | < 0.45 | Different pose entirely | not ready; suggestions reset |

All band values `CALIBRATION_REQUIRED` — they must be calibrated against real subjects photographed in both
the template pose and plausible wrong poses, so that `MATCHED` really means "a human would say yes".

### 4.7 Anti-degenerate-template guard (authoring-time, not runtime)

A template that is easy to *reach* but hard to *judge* (e.g. only symmetric shoulders specified) makes the
score meaningless. The authoring tool (`:tools:pose-authoring`) must reject templates where:

* fewer than 8 landmarks are `required`, or
* all required landmarks lie on a single line (rank < 2), or
* the template's own expected joint angles are mutually inconsistent (> 25° vs the landmark geometry), or
* `mirrorAllowed: true` but the template is asymmetric *and* is used in guidance context that forbids it.

---

## 5. Semantic pose corrections

Matching produces a score; corrections produce *actions*. Corrections are generated by comparing the
per-component reports against template targets **and** the camera/lighting context, then emitting internal
events (never user strings):

```kotlin
data class PoseCorrection(
    val code: PoseCorrectionCode,   // enum, e.g. LEFT_ARM_TOO_CLOSE_TO_TORSO
    val component: PoseComponent,   // HEAD / TORSO / LEFT_ARM / RIGHT_ARM / LEFT_LEG / RIGHT_LEG
    val direction: Direction?,      // LEFT/RIGHT/UP/DOWN/TOWARD_CAMERA/AWAY_FROM_CAMERA/CCW/CW
    val magnitude: Float,           // normalized deviation, 0..1
    val confidence: Float,          // 0..1, from landmark usability
    val ruleId: String,             // provenance
    val persistentFrames: Int
)
```

Example codes (internal identifiers only):

```
HEAD_TILT_RIGHT / HEAD_TILT_LEFT
CHIN_DOWN / CHIN_UP
TORSO_ROTATE_CW / TORSO_ROTATE_CCW          (body yaw)
SHOULDER_ANGLE_TOO_SQUARE
HIP_ANGLE_TOO_LEVEL
LEFT_ARM_TOO_CLOSE_TO_TORSO / RIGHT_ARM_…
LEFT_ELBOW_TOO_LOW / RIGHT_ELBOW_…
LEFT_WRIST_ANGLE_EXTREME / …
LEFT_LEG_TOO_CLOSE_TO_RIGHT / STANCE_TOO_NARROW
WEIGHT_ON_WRONG_LEG (relative to template)
LIMB_POINTING_AT_CAMERA (with limb id)
HAND_HIDDEN (with side)
```

Generation policy (prevents nagging and jitter):

1. A correction is emitted only if its magnitude exceeds `enterThreshold` for `N_enter` consecutive
   frames (default 3, `CALIBRATION_REQUIRED`) and it maps to the *active* template's components.
2. It stays latched until magnitude drops below `exitThreshold = 0.75 × enterThreshold` for 3 frames.
3. Only the top `k` corrections (by `magnitude × confidence × templateWeight`) enter the guidance engine;
   `k = 2` in MVP.
4. Corrections are suppressed while the subject is moving fast (landmark velocity above a threshold) —
   chasing a moving body is useless.

Mapping to UI text happens in the resource layer, e.g.
`PoseCorrectionCode.LEFT_ARM_TOO_CLOSE_TO_TORSO` → `guidance.pose.arm_far_from_torso.left` with a
`{side}` parameter. **No Vietnamese (or any) literal appears in `core-pose`.**

---

## 6. The dashed pose guide

### 6.1 What it is

A vector overlay that shows the *target* pose as a ghost skeleton drawn with dashed strokes, anchored to
the detected subject, so the user can see the delta between "where you are" and "where the template wants
you" without reading text.

### 6.2 Geometry generation

```
template (SUBJECT space)
   → mirror if the winning handedness requires it
   → scale by the detected subject's torsoLength (per-subject calibration)
   → translate to the detected hip midpoint
   → (optional) rotate by the aligned rotation? NO — see below
   → emit primitives: joints (circles/points), bones (dashed segments), hints (arrows, angle arcs)
   → map ANALYSIS → PREVIEW (single tested transform, architecture.md §5)
```

Deliberate design decision: **the guide is anchored, not rotated.** The ghost uses the subject's hip
position and torso scale but *keeps the template's own rotation* (i.e. it shows where the body should be
oriented, rather than rotating itself to hide the orientation error). Rotating the guide to match the
current body would erase exactly the information the user needs (the body rotation error). If the camera
is rolled, the whole overlay is rotated with the preview, not the guide relative to the body.

### 6.3 Visual language (to be finalized in Phase 11, specified now to keep the engine independent)

| Element | Meaning | Style |
| --- | --- | --- |
| Dashed bone (ghost) | target direction | dashed stroke, 2–3 px, ~60 % alpha, dash 8/6 |
| Solid thin bone | detected body | 2 px, low alpha, behind the ghost |
| Joint dot | target joint position | 6 px ring, dashed |
| Arrow | required movement direction (translation or rotation) | short arrow with magnitude-scaled length, fading after `dwellMs` |
| Arc arrow | required joint rotation (elbow, knee, torso yaw) | arc between current and target angle |
| Match glow | component matched within tolerance | the ghost's component is drawn brighter/solid instead of dashed |
| Occlusion handling | component not evaluable | drawn at 25 % alpha with a dotted texture |

Because matched components become "solid", the user gets progressive feedback: the figure fills in as they
approach the pose. This is the single most important UX idea in the pose feature and it is purely
geometric.

### 6.4 Required capabilities

The guide must support (and the geometry layer must be unit-tested for):

* **translation** — anchor to hip midpoint (or face centre for headshot templates)
* **uniform scale** — from the subject's torso length, clamped to `[0.4×, 2.5×]` of the design scale
* **mirroring** — handedness selection, with anatomical correctness
* **rotation** — device/frame rotation handled by the ANALYSIS→PREVIEW transform; template rotation is a
  *displayed deviation*, not a transform
* **partial-body templates** — head-and-shoulders templates draw only the components they define and hide
  the rest (they do not fabricate legs)
* **full-body templates** — include a subtle ground line (from foot landmarks) to make stance readable
* **occlusion** — components with `coverage < 0.35` are de-emphasized, never hidden silently
* **temporal stability** — the guide must not jitter: anchor position/scale are EMA-smoothed
  (α ≈ 0.2 at 30 fps, `CALIBRATION_REQUIRED`), and handedness switching is hysteresis-gated to avoid
  flip-flopping when the subject is symmetric.

### 6.5 Rendering performance rules

* One `Canvas` composable; bones emitted as a pre-built `Path` per style (dashed via
  `PathEffect.dashPathEffect`), rebuilt only when the geometry changes materially (delta threshold).
* No allocation of `Path`/`Offset` collections per frame; reuse builders (measured in Phase 10).
* Overlay updates at 30–60 fps even if perception runs at 15 fps: animate between the last two known
  states (interpolate geometry) so the guide feels alive while the model is slower.
* The overlay must never block the preview: use a separate Compose layer, no recomposition of the entire
  screen per frame (drive the canvas state through a `State` holder consumed only by the draw lambda).

### 6.6 Alignment modes

| Mode | Anchor | When |
| --- | --- | --- |
| `ANCHOR_TO_SUBJECT` (default) | hip midpoint + torso scale | Subject stands roughly in place; guide shows the delta |
| `ANCHOR_TO_FRAME` | frame centre + intended subject size | Used when no subject is detected yet (onboarding: "stand here") |
| `ANCHOR_TO_FACE` | face centre + eye distance | Headshot/half-body templates |

---

## 7. Template authoring and calibration plan (Phase 6)

1. Author templates as JSON by hand (seed values) — Phase 0/6.
2. Validate mechanically: schema, topology sanity, self-consistency of angles vs landmarks,
   handedness/mirroring, `required` landmark coverage (the authoring tool).
3. Render a preview sheet (skeleton drawings) for human review — no device needed.
4. Capture real subjects locally (consented, on-device, never uploaded) performing each pose; run the
   matcher; verify `MATCHED` for the intended pose and `DIFFERENT`/`FAR` for plausible wrong poses.
5. Calibrate band thresholds and per-joint tolerances from these captures; store per-template overrides.
6. Only templates with `provenance.status = VALIDATED` may appear in the user-facing library.

---

## 8. Open questions

1. Minimum viable template set for Phase 6 — proposal: 2 standing, 1 sitting, 1 walking/candid,
   1 leaning/environmental, 1 headshot, 1 half-body with hand near face, 2 two-person (reference only).
2. Should the guide auto-select a template by scene/shot type, or must the user always choose?
   Proposal: MVP = user chooses; Phase 7 = ranked suggestions, never automatic.
3. Do we need a "pose difficulty" progression (teach easy poses first)? Proposal: `LATER`, nice for UX.
