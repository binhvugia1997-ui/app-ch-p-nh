# Photography Rules — database and measurement specification

This document defines **every rule the app is allowed to reason about**, how it is measured, and how
confident we are allowed to be. It is the single source of truth for the rule engine
(`:core:photography`) and for the machine-readable rule set in `specs/rules/`.

Two principles govern this file:

1. **A rule is a measurement plus a policy, not an opinion.** If we cannot measure it, we do not score it.
2. **No invented numbers.** Every threshold is tagged with its evidence class (§3). Any number that has
   not been validated on real data is `CALIBRATION_REQUIRED` and must not be presented in the UI as a
   hard error until it is calibrated.

---

## 1. Rule schema (mandatory fields)

| Field | Meaning |
| --- | --- |
| `Rule ID` | Stable, uppercase, prefixed by category (`FRAME_`, `COMP_`, `POSE_`, `LIGHT_`, `READY_`) |
| `Rule Name` | Human-readable name |
| `Category` | Framing / Composition / Pose / Lighting / Readiness / Information |
| `Purpose` | Why the rule exists; what it protects |
| `Applicable Scenarios` | Shot types (`HEADSHOT`, `CLOSE_PORTRAIT`, `HALF_BODY`, `THREE_QUARTER`, `FULL_BODY`, `GROUP_LATER`), modes, subject counts |
| `Required Inputs` | Exact fields of `FrameAnalysis` used |
| `Detection Method` | Algorithmic steps (deterministic list, not prose) |
| `Mathematical Representation` | Formula(e) with symbols defined |
| `Threshold Strategy` | Enter/exit values, evidence class, per-tier overrides |
| `Possible Corrections` | Guidance instruction IDs (`docs/guidance-engine.md`) |
| `Conflicting Rules` | Rule IDs that can demand the opposite action |
| `Failure Cases` | Conditions under which the measurement is untrustworthy → rule must abstain |
| `Confidence Strategy` | How confidence is computed and capped |
| `Implementation Complexity` | S / M / L / XL |
| `Implementation Priority` | `MVP` / `NEXT` / `LATER` / `EXPERIMENTAL` / `REJECT` |
| `Class` | `DETERMINISTIC` / `ML_ASSISTED` / `HYBRID` / `DIFFICULT` / `SUBJECTIVE` |

Rules **abstain** (emit nothing) more often than they fire. Abstention reasons are always recorded so the
developer screen can explain *why* the assistant is silent.

---

## 2. Rule categories and what each may say

| Prefix | Category | May produce blocking errors? | Notes |
| --- | --- | --- | --- |
| `READY_` | Readiness | Yes (gates capture) | Binary/derived; drives auto-capture |
| `FRAME_` | Framing | Yes (framing mistakes are objective: subject cut off, tiny subject) | The "safe" objective layer |
| `POSE_` | Pose geometry | Only for high-confidence geometric issues | Craft conventions must never be "errors" |
| `LIGHT_` | Lighting | Only for measurable exposure failures (e.g. blown highlights, severe face underexposure) | Advisory wording elsewhere |
| `COMP_` | Composition | Never blocking; advisory | Rule-of-thirds style advice is context-dependent |
| `INFO_` | Information | No | Emitted to the UI/dev screen only |

**Hard product rule:** only `FRAME_`, `LIGHT_` (measurable subset) and `READY_` rules may block readiness.
`POSE_` and `COMP_` rules can only *suggest*.

---

## 3. Threshold evidence classes

Every threshold in this document carries one tag:

| Tag | Meaning | May be shown to the user as… |
| --- | --- | --- |
| `GEOMETRIC` | Follows from geometry/optics, not taste (e.g. "subject bbox exceeds frame") | Fact |
| `LITERATURE` | Traced to a cited study or official documentation (see `research-sources.md`) | Fact with tolerance |
| `PRACTITIONER` | Consistent craft consensus across credible photography educators, but not an empirical law | Strong suggestion |
| `CALIBRATION_REQUIRED` | Proposed value with **no** evidence yet; must be measured on real captures before it becomes user-visible | Nothing yet — dev builds only |
| `DEVICE_TUNED` | Set per device tier by measurement in Phase 10 | Internal |

Additionally, measurement-noise reasoning is used to size tolerances:

* Single-camera pose estimation against multi-camera motion capture shows **joint-angle RMSE of roughly
  8–11°** for knee/hip flexion and ~6° for trunk inclination (see `research-sources.md` §Pose accuracy).
  **Consequence:** any angle-based rule must use tolerances of at least ~2× the expected measurement
  error (≈15–20°) before it flags anything. A rule that fires at ±5° would be firing on noise.

---

## 4. Confidence model (shared by all rules)

```
usable(l)         = sigmoid(visibility_l) * sigmoid(presence_l)          # per landmark
quality(rule)     = f( min/mean usable over required landmarks,
                       staleness of the source (< 200 ms full credit),
                       subject stability,
                       shot-type confidence, scene/lighting confidence )
confidence(rule)  = min( quality(rule), classCap(rule.class) )
```

| `class` | `classCap` | Rationale |
| --- | --- | --- |
| `DETERMINISTIC` | 0.95 | Pure geometry on well-localized inputs |
| `ML_ASSISTED` | 0.80 | Depends on a learned model's accuracy |
| `HYBRID` | 0.85 | Geometry over an ML detection with a validated fallback |
| `DIFFICULT` | 0.60 | Known low precision; advisory only |
| `SUBJECTIVE` | 0.40 | Never phrased as a correction; at most a display hint |

Rules with `confidence < 0.5` do not generate guidance. Rules with `confidence < 0.7` cannot block readiness.

---

# 5. Framing rules (`FRAME_`)

> Framing rules are the highest-value, lowest-risk part of the product: they are geometry over a
> localized body, so they are `DETERMINISTIC` or `HYBRID` and they genuinely help novice photographers.
> Guide reference: `pose-system.md` §Shot-type estimation.

### FRAME_SUBJECT_PRESENT

| Field | Value |
| --- | --- |
| Rule Name | Subject present and usable |
| Category | Framing / gate |
| Purpose | Every other rule depends on a usable subject; this rule gates the pipeline and drives the "no subject" UI state |
| Applicable Scenarios | All |
| Required Inputs | `subjects[]`, `visibleFraction` per subject |
| Detection Method | 1) take subject with highest `visibleFraction * bboxArea`. 2) if `visibleFraction < V_min` → state `SUBJECT_PARTIAL`. 3) if no subject for `N_absent` consecutive frames → state `NO_SUBJECT` |
| Math | `prominence = visibleFraction * sqrt(bboxArea)`, `V_min = 0.55` `CALIBRATION_REQUIRED` |
| Threshold Strategy | Hysteresis: enter NO_SUBJECT after 15 consecutive frames without a subject (~0.5 s); exit on first frame with prominence above threshold. `N_absent` is `CALIBRATION_REQUIRED` |
| Possible Corrections | `SHOW_MESSAGE(no_subject, "point the camera at a person")` |
| Conflicting Rules | none |
| Failure Cases | Partially occluded subject, subject too far (BlazePose model card lists > ~4 m as out of scope), severe motion blur |
| Confidence Strategy | `DETERMINISTIC`, cap 0.95, scaled by frame quality |
| Complexity / Priority / Class | S / **MVP** / DETERMINISTIC |

### FRAME_SUBJECT_TOO_SMALL / FRAME_SUBJECT_TOO_LARGE

| Field | Value |
| --- | --- |
| Purpose | A subject that is too small cannot be judged (pose is unreadable and detection quality collapses); a subject that is too large is cropped awkwardly |
| Required Inputs | `subject.bbox` (normalized), `frame.aspectRatio`, active `ShotType` intent |
| Detection Method | Compute subject height fraction `h = bbox.height` (and for headshots `face bbox height`). Compare against the *intended* shot type band |
| Math | `h_body = bboxBottom - bboxTop` in normalized y; `h_face = faceBox.height`. Intended bands (proposal, all `CALIBRATION_REQUIRED`): `FULL_BODY 0.75–0.98`, `THREE_QUARTER 0.55–0.85`, `HALF_BODY 0.35–0.60`, `CLOSE_PORTRAIT 0.18–0.35` (face height), `HEADSHOT 0.28–0.45` (face height) |
| Threshold Strategy | Enter/exit bands separated by 10 % of the band width; behaviour differs per shot type; if the user has not selected a shot type, this rule only warns on extreme values (`h < 0.25` or `h > 1.0` clipped) |
| Possible Corrections | `MOVE_CLOSER`, `MOVE_FARTHER`, `ZOOM_IN`, `ZOOM_OUT` (zoom is advisory in MVP) |
| Conflicting Rules | `FRAME_HEADROOM_*` (moving closer changes headroom), `FRAME_EDGE_MARGIN` |
| Failure Cases | Subject partially out of frame (bbox clipped by frame → `h` underestimates), loose clothing, group of overlapping people |
| Confidence Strategy | `DETERMINISTIC` when `visibleFraction ≥ 0.55`; abstain otherwise |
| Complexity / Priority / Class | S / **MVP** / DETERMINISTIC |

### FRAME_HEADROOM_EXCESSIVE / FRAME_HEADROOM_INSUFFICIENT

| Field | Value |
| --- | --- |
| Purpose | Headroom is the most consistently cited, most fixable portrait framing error; it is trivially measurable |
| Applicable Scenarios | `HEADSHOT`, `CLOSE_PORTRAIT`, `HALF_BODY`, `THREE_QUARTER`, `FULL_BODY` |
| Required Inputs | head landmarks (`nose`, `left_eye`, `right_eye`, `ears`), `bbox.top`, frame height; face bbox when available |
| Detection Method | 1) `topOfHead ≈ min( y(ears), y(nose) − 0.5 * earToNose … )` — prefer a *head-top estimate* rather than raw landmark min, because BlazePose has no "top of skull" landmark at index 0..32. Recommended estimator: fit a circle through `left_eye`, `right_eye`, `left_ear`, `right_ear`, take `headRadius`; then `headTopY = centerY − k * headRadius` with `k ≈ 1.45` (head is taller than the eye–ear circle). `CALIBRATION_REQUIRED` for `k`. 2) `headroom = headTopY` (normalized distance from frame top to head top). |
| Math | `headroomRatio = headTopY`; `eyeLine = mean(y(left_eye), y(right_eye))` |
| Threshold Strategy | *Practitioner consensus:* eyes on or near the upper third line; headshots commonly keep a small even margin (roughly 1/8–1/4 of frame height for head-and-shoulders). Proposed: `HEADSHOT` target `0.04 ≤ headroomRatio ≤ 0.14`, `CLOSE_PORTRAIT` `0.04–0.18`, `HALF_BODY` `0.05–0.25`, `FULL_BODY 0.03–0.20`; excessive when `headroomRatio > bandMax`, insufficient when `< bandMin` — **all `CALIBRATION_REQUIRED`**, all advisory in MVP |
| Possible Corrections | `MOVE_CAMERA_UP` (insufficient → camera up? see note), `MOVE_CAMERA_DOWN`… **Direction logic:** pressing the camera down *increases* space above the head; raising the camera *decreases* it. The rule emits a *semantic* correction (`INCREASE_HEADROOM` / `DECREASE_HEADROOM`) and the guidance layer maps it to camera or subject motion according to the active mode (see `guidance-engine.md` §10) |
| Conflicting Rules | `FRAME_SUBJECT_TOO_SMALL` (framing tighter reduces headroom), `COMP_EYE_LINE_PLACEMENT` |
| Failure Cases | Head partially out of frame (rule must abstain, not report "insufficient"), hats/hair volume, tilted camera (roll > 10°), non-upright head |
| Confidence Strategy | `HYBRID` (ML localization + geometric estimate), cap 0.85, reduced when the head-top estimator is uncertain (eyes/ears visibility low) |
| Complexity / Priority / Class | M / **MVP** / HYBRID |

### FRAME_EDGE_MARGIN (subject touching or crossing the frame edge)

| Field | Value |
| --- | --- |
| Purpose | Subjects pinned to (or cut by) the frame edge read as an accident unless deliberate |
| Required Inputs | `subject.bbox`, all landmark positions, frame bounds |
| Detection Method | Compute margins `m_left = bbox.left`, etc. Flag if `min(margins) < m_min` **and** the subject is not intentionally cropped by a *limb-safe* crop (§`FRAME_JOINT_CROP`) |
| Math | `m_min = 0.02` `CALIBRATION_REQUIRED`; margins normalized |
| Threshold Strategy | Enter/exit hysteresis at `0.02 / 0.03`; suppressed when the user has chosen a tight-crop shot type (`HEADSHOT`) where edge contact is normal |
| Possible Corrections | `MOVE_CAMERA_AWAY` (i.e. `ZOOM_OUT`), `SUBJECT_MOVE_INWARD`, `REFRAME` |
| Conflicting Rules | `FRAME_SUBJECT_TOO_SMALL` (moving back makes the subject smaller) |
| Failure Cases | Deliberate tight crops, subject entering/leaving the frame during walking poses |
| Confidence Strategy | `DETERMINISTIC` |
| Complexity / Priority / Class | S / **MVP** / DETERMINISTIC |

### FRAME_JOINT_CROP

| Field | Value |
| --- | --- |
| Purpose | Cropping *through* a joint (wrist, elbow, knee, ankle, neck) looks unintentional; cropping through a long limb segment mid-length reads as intentional |
| Required Inputs | landmark positions + visibility, frame rectangle |
| Detection Method | 1) Build the skeleton graph, 2) for each segment that intersects a frame edge, compute the intersection parameter `t ∈ [0,1]` along the segment, 3) flag when `t < t_joint` or `t > 1 − t_joint` (i.e. the cut is near an endpoint joint) while the joint itself is inside the frame by less than `d_joint` |
| Math | `t_joint = 0.15`, `d_joint = 0.03 × frameHeight` `CALIBRATION_REQUIRED` |
| Threshold Strategy | Flag only high-confidence cases (both conditions) to avoid annoying false positives |
| Possible Corrections | `MOVE_CAMERA_AWAY`, `REFRAME`, `SUBJECT_MOVE_INWARD` |
| Conflicting Rules | `FRAME_SUBJECT_TOO_SMALL`, `FRAME_FILL_FRAME_*` |
| Failure Cases | Occluded landmarks producing a wrong segment geometry; crouching/sitting poses where legs are legitimately cut |
| Confidence Strategy | `DETERMINISTIC` with visibility gating |
| Complexity / Priority / Class | M / **MVP** (as advisory only) / DETERMINISTIC |

### FRAME_SUBJECT_OFF_CENTER_EXTREME

| Field | Value |
| --- | --- |
| Purpose | Detect *extreme* imbalance (subject squeezed into a corner), not "not following rule of thirds" |
| Required Inputs | subject bbox centre, frame centre, subject facing/gaze when available |
| Detection Method | `offset = |centre − frameCentre|` normalized by half-frame; flag only if `offset > 0.55` and the subject is not deliberately looking into negative space |
| Threshold Strategy | `0.55` `CALIBRATION_REQUIRED`; **never** flag plain centering (research shows centering is frequently preferred — see §9) |
| Possible Corrections | `REFRAME`, `SUBJECT_MOVE_INWARD` |
| Conflicting Rules | `COMP_*` rules that may ask to move the subject off-center |
| Confidence Strategy | `DETERMINISTIC`, cap 0.9 |
| Complexity / Priority / Class | S / **MVP** (advisory) / DETERMINISTIC |

### FRAME_CAMERA_TILT (roll)

| Field | Value |
| --- | --- |
| Purpose | An unintended horizon tilt is objectively measurable from the IMU, and is one of the most common "looks wrong but hard to name" errors |
| Required Inputs | `device.gravity` / rotation vector (IMU), not the image |
| Detection Method | Compute camera roll from the gravity vector (`roll = atan2(gx, gy)` in the device frame, adjusted for display rotation); smooth with EMA; flag |roll| in a band around level |
| Math | threshold proposal `1.5°` enter / `1.0°` exit; `CALIBRATION_REQUIRED`. Note: intentional "Dutch tilt" is a deliberate style — never blocking |
| Possible Corrections | `LEVEL_CAMERA` |
| Conflicting Rules | none (this one is never in conflict) |
| Failure Cases | IMU unavailable (rare), intentional tilt, tilt during deliberate diagonal composition |
| Confidence Strategy | `DETERMINISTIC` when an IMU source exists, otherwise abstain (never infer tilt from image content in MVP) |
| Complexity / Priority / Class | S / **MVP** / DETERMINISTIC |

### FRAME_HORIZON_THROUGH_HEAD, FRAME_BACKGROUND_MERGER, FRAME_BACKGROUND_CLUTTER

*Purpose:* avoid the classic "pole growing out of the head" and cluttered-background errors.
*Method:* requires either (a) a semantic/edge analysis of the region directly above the head, or (b) a
person segmentation mask to know which edges belong to the background.
*Verdict:* `DIFFICULT` / `ML_ASSISTED`; **`LATER`**. In MVP we ship only the *cheap* variant:

* `FRAME_BRIGHT_BLOB_BEHIND_HEAD`: high-luma connected region overlapping the head bbox perimeter
  (deterministic on the luma grid, cap confidence 0.6) — advisory only.
* Anything requiring line/edge reasoning waits for Phase 7+ and a segmentation model.

---

# 6. Pose rules (`POSE_`)

> Reminder: craft conventions are **not** errors. Every rule here is *context-dependent* unless it is pure
> geometry, and even then it may be intentional. Pose rules generate "corrections" only when
> (a) the user has selected a template that disagrees with the current pose **and**
> (b) the deviation exceeds a tolerance band derived from measurement noise.

### POSE_MATCH_SCORE

Defined fully in `pose-system.md`. Not a "rule" in the error sense: it is the primary quality signal for
the pose dimension and the main driver of the dashed guide. Complexity L, priority **MVP**.

### POSE_ARM_TORSO_GAP_TOO_SMALL

| Field | Value |
| --- | --- |
| Purpose | The single most frequently cited posing issue in credible practitioner sources: arms pressed against the torso flatten and widen the arm silhouette and read as tense |
| Required Inputs | `left/right_shoulder`, `elbow`, `wrist`, `hip` landmarks + visibility |
| Detection Method | 1) Build the torso axis (mid-shoulder → mid-hip). 2) For each side, compute the minimum distance from the *upper arm segment* (shoulder→elbow) and *forearm* (elbow→wrist) to the torso axis line, then normalize by torso length. 3) Also compute the **arm–torso angle**: the angle between the upper-arm vector and the torso axis in the frontal plane |
| Math | `gapRatio = minDistance / torsoLength`; `armTorsoAngle = angle(shoulder→elbow, hip→shoulder)` |
| Threshold Strategy | Proposal: flag when `gapRatio < 0.10` **or** `armTorsoAngle < 12°`, with exit at `1.25×` those values. `CALIBRATION_REQUIRED` (both numbers), tolerance floor justified by pose measurement error (~10°) |
| Possible Corrections | `RAISE_LEFT_ARM` / `RAISE_RIGHT_ARM`, `MOVE_ARM_AWAY_FROM_TORSO(side, amount)`, `HAND_ON_HIP(side)` |
| Conflicting Rules | Templates that *intend* arms close to the torso (e.g. a formal A-pose, military stance) — the active template's expected value takes precedence |
| Failure Cases | Camera angle along the body axis (foreshortening makes a real gap invisible), loose sleeves, subject wearing a coat, arms behind the back |
| Confidence Strategy | `DETERMINISTIC` geometry over ML landmarks; cap 0.8; requires shoulder/elbow/wrist visibility ≥ 0.5 for that side (else abstain for that side only) |
| Complexity / Priority / Class | M / **MVP** / DETERMINISTIC (over ML perception) |

### POSE_LIMB_FORESHORTENED (limb pointing at the camera)

| Field | Value |
| --- | --- |
| Purpose | A limb (or foot) aimed at the lens reads as a stump and flattens the pose; photographers call this "don't point it at the camera" |
| Required Inputs | 2D landmarks + world landmarks |
| Detection Method | Compare the **2D projected length** of a limb segment with its **3D length** from world landmarks: `foreshortening = length2D / (length3D * projectionScale)`. A ratio near 1 = limb lies in the image plane; a ratio near 0 = limb points at the camera. `projectionScale` is estimated from a reference segment known to be roughly in-plane (e.g. shoulder width or torso height) to absorb distance/zoom |
| Math | `r_seg = ‖p_a − p_b‖₂D / (‖P_a − P_b‖₃D × s)`, with `s = median` over reference segments |
| Threshold Strategy | flag `r < 0.45` for arms/hands and `r < 0.35` for feet/legs; `CALIBRATION_REQUIRED`; note world-landmark z is synthetic (GHUM) and **not metric-accurate** → treat as a heuristic |
| Possible Corrections | `TURN_BODY(side/angle)`, `MOVE_LIMB_OUT_OF_CAMERA_AXIS(limb)` |
| Conflicting Rules | Templates that intentionally point a hand at the camera (fashion/editorial) |
| Failure Cases | World-landmark noise, subject at an angle where the reference segment is itself foreshortened, extremely close crops |
| Confidence Strategy | `HYBRID`, cap 0.7, abstain when reference segments are uncertain |
| Complexity / Priority / Class | L / **NEXT** / HYBRID |

### POSE_ELBOW_LOCKED (one rule, elbows and knees)

*Statement:* fully straight joints ("locked") look stiff; practitioners repeat "if it bends, bend it".
Elbows and knees share the same measurement and the same fix, so they are **one rule** (`POSE_ELBOW_LOCKED`);
`POSE_KNEE_LOCKED` is not a separate rule ID.
*Measurement:* joint angle `θ = angle(proximal→joint, joint→distal)`; locked when `θ > 170°`.
*Classification:* **context-dependent**. Fully extended limbs are *correct* in elongation/elongation-style
poses, in mid-stride walking, and in formal portraits. Therefore this rule is only ever active
**relative to the active template's expected joint angle**, never as a global error.
*Priority:* `NEXT` (depends on templates).

### POSE_WRIST_ANGLE_EXTREME

*Statement:* a broken/limp wrist reads as an accident. *Measurement:* interior angle at the wrist between
the forearm axis and the hand direction (hand direction estimated from wrist → index/middle MCP; BlazePose
gives `index`/`pinky`/`thumb` but not MCP indices, so use wrist → midpoint(index, pinky) as a proxy).
*Threshold:* comfortable range proposal `150°–200°`; `CALIBRATION_REQUIRED`.
*Classification:* **context-dependent**; **`NEXT`**. Failure case: hand partially out of frame.

### POSE_SHOULDERS_SQUARE / POSE_HIPS_LEVEL / POSE_EXCESSIVE_SYMMETRY

*Statement:* perfectly square shoulders / perfectly level hips / full left-right symmetry read as "ID photo".
*Measurement:*
* shoulder line angle vs image x-axis: `α_s = atan2(Δy, Δx)` over the shoulder segment.
* torso orientation (facing angle): estimated from `cos(φ) ≈ width2D(shoulders) / width3D(shoulders)`
  (world landmarks), giving a coarse `0° (facing) … 90° (profile)` reading.
* symmetry: mirror-difference of the normalized skeleton (see `pose-system.md` §mirror metric).
*Threshold:* `|α_s| < 3°` and `|φ| < 10°` → "very square"; `CALIBRATION_REQUIRED`.
*Classification:* **context-dependent** (square-on is correct for many corporate headshots).
*Priority:* `NEXT`. **Never blocks readiness. Never phrased as "wrong" — at most "try turning slightly".**

### POSE_FEET_MERGING / POSE_STANCE_TOO_NARROW

*Statement:* feet visually merging into one shape reads as a mistake, especially in wide/full-body shots.
*Measurement:* `footSeparation = ‖p(left_foot_index) − p(right_foot_index)‖₂D / torsoLength`; also
check vertical overlap of the two ankle–foot segments.
*Threshold:* proposal `footSeparation < 0.25 × shoulderWidth`; `CALIBRATION_REQUIRED`.
*Priority:* `NEXT`; context-dependent (a deliberate narrow stance is a real posing choice).

### POSE_HANDS_HIDDEN_UNINTENTIONALLY

*Statement:* hands that vanish behind the body or out of frame without intent look like a mistake.
*Measurement:* wrist `visibility < 0.35` for ≥ `N` consecutive frames **and** the wrist is outside the
frame or behind the torso polygon (wrist inside the torso region while invisible ⇒ occlusion).
*Classification:* `DETERMINISTIC` (visibility-based) but **context-dependent** (hands behind the back is a
valid pose). *Priority:* **MVP** as an *information/dev* signal, `NEXT` as guidance (needs face/hands
context to phrase well).

### POSE_HEAD_TILT / POSE_CHIN_TUCK / POSE_GAZE_DIRECTION

*Measurement:*
* head roll from the eye line: `atan2(Δy_eyes, Δx_eyes)` — deterministic, ±3° noise.
* head pitch/yaw: from MediaPipe's **facial transformation matrix** (available with Face Landmarker) —
  this is the correct source, not body landmarks. (Alternatively the older approach: compare eye/nose
  geometry.)
* gaze: iris landmarks (indices 468–477) relative to eye corners.
*Classification:* all **informational / context-dependent**; none is an error. These feed
`COMP_LEAD_ROOM` (gaze-aware) and pose templates that specify gaze.
*Priority:* head roll **MVP** (cheap, needed by templates); pitch/yaw **NEXT**; iris gaze `LATER`.

### POSE_WEIGHT_SHIFT_AMBIGUOUS / POSE_CONTRAVERSION_DETECTED

*Statement:* to *detect* rather than to judge: photographers read a pose from where the weight sits.
*Measurement:* `weightX = hipMidX − supportMidX` (support = ankles/feet mid), normalized by stance width;
sign indicates weight on the left/right leg. `contrapposto = |shoulderLineAngle − hipLineAngle|`
(shoulder and hip lines diverge → classic contrapposto).
*Use:* these are **features for the matcher**, not errors. They let the app tell whether a subject has
achieved a requested weight shift.
*Priority:* **MVP** (needed for the most common template family).

---

# 7. Composition rules (`COMP_`) — advisory only, context-dependent by default

### COMP_EYE_LINE_PLACEMENT

*Purpose:* deliver the "eyes in the upper third" convention as an *option*, not a law.
*Measurement:* `eyeLine = mean(y(eyes))`, compared with the upper third line `y = 0.333`.
*Threshold:* within `0.08` → "on the third"; `CALIBRATION_REQUIRED`.
*Evidence:* practitioner consensus is strong (headshot/portrait craft), **but** research on the rule of
thirds shows weak/no correlation between ROT compliance and aesthetic preference in large photo sets
(see §9 and `research-sources.md`). Therefore: **advisory, never a score, never blocking.**
*Corrections:* `ADJUST_EYE_LINE` (mapped to camera up/down or subject movement).

### COMP_LEAD_ROOM

*Purpose:* subjects looking/moving toward the frame edge feel cramped.
*Measurement:* gaze/head yaw direction; measured as space in the gaze direction
`leadSpace = distance from bbox edge to frame edge in the look direction`, normalized by frame width.
*Threshold:* flag when `leadSpace < 0.5 × trailingSpace` while subjects look sideways;
`CALIBRATION_REQUIRED`. Direct-to-camera gaze exempt.
*Class:* `HYBRID` (needs reliable gaze/head-yaw). *Priority:* `NEXT`.

### COMP_HORIZON_PLACEMENT

*Purpose:* horizon on a third is the classic landscape convention. *Measurement:* requires horizon
detection (`DIFFICULT` in MVP). *Priority:* `LATER`.
*Note:* measuring "tilted horizon" is already covered objectively by `FRAME_CAMERA_TILT` via the IMU.

### COMP_RULE_OF_THIRDS_SUBJECT_PLACEMENT

*Status:* **REJECT as a rule.** Evidence (see §9) does not support scoring subject placement against the
thirds grid. Kept in this document as an explicit non-rule so no future contributor re-adds it.
*What we do instead:* `FRAME_SUBJECT_OFF_CENTER_EXTREME` (only extreme cases) and the golden-ratio — no —
the *user-selected* composition intent (e.g. "centered" vs "off-center" in the composition profile).

### COMP_NEGATIVE_SPACE_BALANCE / COMP_VISUAL_WEIGHT

*Status:* `SUBJECTIVE` / `DIFFICULT` → **REJECT for scoring**, `LATER` as an *optional, clearly labelled*
"balance hint" backed by a saliency model. Requires saliency/attention prediction, which we do not have a
license-clean, on-device model for in Phase 0.

### COMP_FOREGROUND_LAYERING / COMP_DEPTH / COMP_LEADING_LINES / COMP_SYMMETRY / COMP_FRAME_IN_FRAME

*Status:* genuinely useful photographic ideas, but detecting them requires scene segmentation, line
detection and an aesthetic model. **`LATER`/`EXPERIMENTAL`**, and only ever as *suggestions from a
scene classifier*, never as measurements.

---

# 8. Lighting rules (`LIGHT_`) — see `lighting-engine.md` for the math

| Rule ID | Measurement | Class | Priority | Blocking? |
| --- | --- | --- | --- | --- |
| `LIGHT_FACE_UNDEREXPOSED` | median luma of face region vs frame median, plus absolute floor | DETERMINISTIC (relative) | **MVP** | advisory, may block in extreme cases |
| `LIGHT_FACE_OVEREXPOSED` | share of face pixels ≥ `CLIP_HI` | DETERMINISTIC | **MVP** | advisory |
| `LIGHT_HIGHLIGHT_CLIPPING` | share of all pixels ≥ `CLIP_HI` (250/255) | DETERMINISTIC | **MVP** | advisory |
| `LIGHT_SHADOW_CRUSHING` | share of all pixels ≤ `CLIP_LO` (5/255) | DETERMINISTIC | **MVP** | advisory |
| `LIGHT_LOW_CONTRAST` | `p95 − p05` of luma grid | DETERMINISTIC | **MVP** | no |
| `LIGHT_BACKLIT_SUBJECT` | `median(background ring) − median(subject region) > T` | HYBRID (needs a subject mask) | **MVP** | advisory |
| `LIGHT_UNEVEN_FACE` | left/right face-region luma ratio | DETERMINISTIC | `NEXT` | no |
| `LIGHT_HARD_LIGHT` | shadow edge gradient width (umbra/penumbra ratio) | LITERATURE but lab-constrained | `LATER` | no |
| `LIGHT_COLOR_CAST` | grey-world deviation | DIFFICULT (WB already applied by ISP) | `EXPERIMENTAL` | no |
| `LIGHT_FLAT_FACIAL_LIGHT` | absence of any directional gradient across the face | DIFFICULT | `LATER` | no |

All lighting thresholds are `CALIBRATION_REQUIRED`. The engine must state, in the developer screen and in
`lighting-engine.md`, that **RGB camera frames are not a light meter** and that absolute
exposure/contrast values are relative to the camera's own AE/WB.

---

# 9. Rules we deliberately do not implement (and why)

| Idea | Verdict | Reason |
| --- | --- | --- |
| "Rule of thirds compliance" score | REJECT | Empirical studies find weak/no correlation between computed ROT measures and aesthetic ratings, and a preference study found participants *preferred* centered single objects (see `research-sources.md` §Composition research) |
| "Golden ratio / golden spiral" scoring | REJECT | Not distinguishable from thirds at mobile framing precision; no credible evidence of benefit over thirds, and no way to measure "compliance" meaningfully for a human subject |
| "Center composition is wrong" | REJECT | Contradicted by evidence; symmetry and centering are legitimate and often preferred |
| "Low angle is always better for full body" | REJECT | Camera angle effects are small, context-dependent, and body-type dependent (see `research-sources.md` §Camera angle). Angle becomes a *user choice* of intent, not an error |
| Aesthetic score ("photo beauty 7.4/10") | REJECT for MVP | License-clean models unavailable on-device (see `model-licenses.md`), and an unexplained number is worse than no number. Possible `EXPERIMENTAL` feature with a NIMA-family model later, clearly labelled |
| "Subject looks fat/thin" style judgements | REJECT | Body-shaming risk; not measurable objectively; must never exist |
| Face identity, age, gender, emotion scoring | REJECT | Privacy + model-card scope + potential harm |
| Colour-grading advice | REJECT | Requires display-referred evaluation we cannot do reliably from a preview stream |

---

# 10. Rule interactions and conflicts (must be handled by the arbiter)

| A | B | Nature of conflict | Proposed resolution |
| --- | --- | --- | --- |
| `FRAME_SUBJECT_TOO_SMALL` (move closer) | `FRAME_HEADROOM_EXCESSIVE` (less headroom) | Both solved by the same camera move — *synergy*, must be merged into one instruction | Merge into `MOVE_CLOSER_AND_DOWN` composite or pick the dominant one |
| `FRAME_SUBJECT_TOO_LARGE` (move back) | `FRAME_JOINT_CROP` (move back) | Synergy | Merge |
| `FRAME_SUBJECT_TOO_SMALL` | `FRAME_JOINT_CROP` (move back) | Direct opposition | Score by severity × confidence; subject-too-small usually wins for pose readability; arbitrate with hysteresis so it does not oscillate |
| `COMP_EYE_LINE_PLACEMENT` (camera down) | `FRAME_HEADROOM_INSUFFICIENT` (camera down) | Synergy | Merge |
| `COMP_EYE_LINE_PLACEMENT` (camera down) | `FRAME_SUBJECT_TOO_SMALL` (move closer) | Independent axes | Emit the higher-priority one; they can be sequenced over time |
| `POSE_ARM_TORSO_GAP_TOO_SMALL` (subject moves arm) | `FRAME_SUBJECT_OFF_CENTER_EXTREME` (camera moves) | Different actors (subject vs photographer) | Different actors may each receive one instruction — but only if `mode` allows two actors (see `guidance-engine.md`) |
| `POSE_*` (subject action) | `COMP_*` (photographer action) | Actor conflict | Pose instruction wins while the subject is "in progress"; composition wins when pose score is above the template's good band |
| Any `LIGHT_*` | Any framing rule | Almost never opposed; lighting failures suppress *all* other guidance when severe (a blown-out or black frame cannot be judged) | Lighting pre-empts (§`guidance-engine.md` precedence table) |

**Global rule:** never display two instructions that require the *same actor* to do conflicting things;
never display more than one instruction per actor; never display more than two instructions total.

---

# 11. Implementation priority summary (Phase-mapped)

| Priority | Rules | Phase |
| --- | --- | --- |
Phase 5 is the last MVP phase (`roadmap.md`). Each rule also carries `implementationPriority` and `phase` in
`specs/rules/mvp-rules.json`; the machine-readable file is the source of truth for those two fields.

| Priority | Rules (rule IDs are canonical) | Phase |
| --- | --- | --- |
| **MVP** — in `specs/rules/mvp-rules.json` | `READY_SUBJECT_STABLE`, `READY_CAMERA_STABLE`, `FRAME_SUBJECT_PRESENT`, `FRAME_SUBJECT_TOO_SMALL/LARGE`, `FRAME_HEADROOM_EXCESSIVE/INSUFFICIENT`, `FRAME_EDGE_MARGIN`, `FRAME_CAMERA_TILT`, `FRAME_SUBJECT_OFF_CENTER_EXTREME`, `FRAME_JOINT_CROP`, `FRAME_BRIGHT_BLOB_BEHIND_HEAD` (cheap luma variant), `POSE_MATCH_SCORE`, `POSE_ARM_TORSO_GAP_TOO_SMALL`, `POSE_WEIGHT_SHIFT_AMBIGUOUS`, the six basic `LIGHT_*` (`LIGHT_FACE_UNDEREXPOSED`, `LIGHT_FACE_OVEREXPOSED`, `LIGHT_HIGHLIGHT_CLIPPING`, `LIGHT_SHADOW_CRUSHING`, `LIGHT_LOW_CONTRAST`, `LIGHT_BACKLIT_SUBJECT`) | 2–5 |
| **NEXT** — in `specs/rules/mvp-rules.json` | `POSE_ELBOW_LOCKED`, `POSE_WRIST_ANGLE_EXTREME`, `POSE_LIMB_FORESHORTENED`, `POSE_FEET_MERGING`, `POSE_SHOULDERS_SQUARE`, `POSE_EXCESSIVE_SYMMETRY`, `POSE_HANDS_HIDDEN_UNINTENTIONALLY`, `COMP_EYE_LINE_PLACEMENT`, `COMP_LEAD_ROOM` | 6 |
| **NEXT** — specified in prose (`§5`–`§8`), entry added to the rule file when its phase starts | `POSE_HIPS_LEVEL`, `POSE_STANCE_TOO_NARROW`, `POSE_HEAD_TILT`, `POSE_CHIN_TUCK`, `POSE_GAZE_DIRECTION`, `POSE_CONTRAVERSION_DETECTED`, `LIGHT_UNEVEN_FACE`, `READY_FACE_ACCEPTABLE`, `FRAME_HORIZON_THROUGH_HEAD` | 6–7 |
| **LATER** | `FRAME_BACKGROUND_MERGER`, `FRAME_BACKGROUND_CLUTTER`, `FRAME_BACKGROUND_SEPARATION`, `FRAME_EDGE_DISTRACTION`, `COMP_HORIZON_PLACEMENT`, `COMP_*` scene-dependent rules, `LIGHT_HARD_LIGHT`, group rules | 7–8 |
| **EXPERIMENTAL** | `LIGHT_COLOR_CAST`, aesthetic scoring, VLM scene reasoning, LLM phrasing | 9+ |
| **REJECT** | see §9 | — |

---

## 12. Machine-readable form

`specs/rules/mvp-rules.json` contains the rules specified to implementation depth (Phases 4–6), validated
against `specs/schemas/rule-set.schema.json` (whose entries use `specs/schemas/rule.schema.json`), so that
Codex Local can load the rule table at build time (or generate Kotlin data classes) instead of hand-porting
constants. A rule that exists only as prose in §5–§8 is **not** in the JSON yet; it is added when its phase
begins and its entry is complete. **The Markdown remains normative for prose; the JSON is normative for IDs,
classes, severities, implementation priority/phase and threshold status. If they disagree, fix both in the
same commit.**
