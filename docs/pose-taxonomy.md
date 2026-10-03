# Pose Taxonomy — solo, two-person and group posing knowledge base

This is the **content** document: what the poses are, how their geometry is described, and how each will
become a template (`pose-system.md` §3) and a guidance target.

> **Epistemic status.** Posing is a craft, not a science. The material below is **practitioner consensus**
> from credible photography education (see `research-sources.md` §Posing), cross-checked against what is
> geometrically measurable. Where a convention is culturally or aesthetically loaded, it is marked
> `PREFERENCE`. Nothing here is ever surfaced to the user as an objective error unless
> `photography-errors.md` classifies it `HIGH_CONFIDENCE_ERROR`.

Every pose entry uses the same structured geometry description, because that structure maps 1:1 onto the
template schema:

```
Body / Hips / Legs / Shoulders / Arms / Hands / Head / Feet / Camera / Shot types /
Detectable properties / Typical mistakes
```

Coordinates refer to the template space of `pose-system.md` §3.1 (hip origin, y down, torso length = 1.0).

---

## 1. Shared craft foundations (apply across many poses)

These recur in essentially every credible posing source. They are **starting points for templates**, not
global laws — each is overridden by deliberate intent.

| # | Foundation | Geometric statement | Template/normalization impact |
| --- | --- | --- | --- |
| G1 | Turn the body away from straight-on, typically ~30–45° | shoulder-line projection width < shoulder width (world) | `bodyOrientation.facingDeg ≈ 30–45` |
| G2 | Shift the weight onto one leg rather than standing evenly | hip midpoint x-offset from support midpoint ≠ 0 | `POSE_WEIGHT_SHIFT_AMBIGUOUS` sign/magnitude |
| G3 | Create a small gap between arm and torso | minimum arm-segment-to-torso-axis distance / torso length ≥ ~0.10 | `POSE_ARM_TORSO_GAP_TOO_SMALL` threshold |
| G4 | Bend joints slightly ("if it bends, bend it") | elbow/knee interior angle ≈ 150–170° rather than 180° | `jointAngles[].tolerance` |
| G5 | Avoid pointing limbs at the lens | 2D/3D segment length ratio low | `POSE_LIMB_FORESHORTENED` |
| G6 | Keep the silhouette line (S-curve / C-curve) | curvature through hip → ribcage → shoulder → head | chain of `relativeVectors`, not a single landmark |
| G7 | Head slightly toward the camera / chin forward and down a touch | head yaw toward lens; pitch slightly down | head-orientation component |
| G8 | Keep the frame's crop off joints | edge crossing near a joint vs mid-segment | `FRAME_JOINT_CROP` |
| G9 | Hands should have a purpose, and relaxed (not splayed or clenched) | hand landmark spread above 0 and below max; wrist angle within comfortable band | `POSE_WRIST_ANGLE_EXTREME`, hand proxy from wrist/index/pinky |
| G10 | Feet usually turn ~45° from the camera, one foot forward | foot-index vector vs frame x | stance features |

**Cultural/preference caveats (never enforced):** "slimming" angles, arms crossed vs open, hands on hips
(reads as confident in one context, aggressive in another), which side of the face to show, and whether
weight goes on the front or back leg all differ by culture, gender presentation and intent.

---

## 2. Solo poses

For each: geometry, camera, shot types, detectable properties, typical mistakes.
`diff` = expected difficulty for a non-model subject.

### 2.1 Weight-Shift Standing (S-curve) — `diff: EASY`

```
Body       slight lateral curve from ankle to head; torso rotated ~25-35° from camera
Hips       asymmetric; hip on the weighted side raised; hip line tilted 5-12° from horizontal
Legs       weight on rear leg; front leg relaxed, knee slightly bent (~165°), foot forward
Shoulders  rotated with the torso, upper body ~10-20° off the hip line
Arms       one arm relaxed with a small gap (≥0.1 torso); other arm free (pocket, hip, thigh)
Hands      relaxed; if in pocket, wrist visible, thumb out
Head       small tilt (0-8°) matching the body line; gaze to camera or just off-camera
Feet       front foot forward by ~0.25-0.4 torso; feet at ~45° to the lens
Camera     waist-to-chest height; 3-4 m for full body; slightly above subject's waist
Shot types FULL_BODY, THREE_QUARTER
Detectable weight side (sign of hip offset), weight magnitude, arm gap, hip-line tilt,
           shoulder-vs-hip divergence (contrapposto magnitude), knee bend, feet separation
Mistakes   both legs perpendicular and locked; hips level; arms pressed to the body;
           leaning backward instead of a subtle shift
```

### 2.2 A-Pose Relaxed Standing (natural, "nothing happening") — `diff: TRIVIAL`

```
Body       facing camera ±15°; upright, weight even or slightly shifted; silhouette open
Hips       near level (0-4°)
Legs       both straight but not locked (~170-175°); feet ~shoulder-width or slightly narrower
Shoulders  near level, relaxed and dropped, not raised
Arms       hanging with a visible gap; hands relaxed, slight curve in the fingers
Head       level or 0-5° tilt; gaze to camera
Camera     chest to eye height; 2.5-4 m
Shot types HALF_BODY, THREE_QUARTER, FULL_BODY
Detectable arm gap, elbow bend, foot separation, symmetry, shoulder vs head alignment
Mistakes   "toy soldier" (feet together, arms clamped); shoulders raised; weight on both legs
           with locked knees
```

### 2.3 Hands in Pockets — `diff: EASY`

```
Body       torso rotated 20-40°; slight lean back or forward depending on mood
Hips       tilted as in 2.1
Legs       one leg bearing weight; the other relaxed, often angled outward
Shoulders  dropped, one shoulder slightly forward
Arms       upper arms close to the torso is ACCEPTABLE here; elbows bent backward/outward,
           wrist visible at the pocket line
Hands      hidden by design (this is the point) — but a partially visible wrist reads better
Head       level, gaze to camera or off
Camera     waist to chest height; 3-4.5 m for full body
Shot types THREE_QUARTER, FULL_BODY
Detectable whether hands are actually in pockets (wrist visibility high yet hand landmarks occluded),
           elbow direction, weight shift
Mistakes   both hands fully hidden (reads as arm-less silhouette); elbow tucked to the front,
           creating a flat torso; standing perfectly square
```

### 2.4 Hands on Hips / Triangle Pose — `diff: EASY`

```
Body       facing 0-30°; open, confident silhouette; triangles formed by arms + torso
Hips       level or subtly tilted; one hip slightly forward
Legs       weight on one leg, other knee relaxed, feet apart ~shoulder width
Shoulders  level or one slightly forward; not raised
Arms       elbows out ~30-55° from torso, wrists at or slightly above the hip line
Hands      palm on hip with fingers forward; wrist angle ~150-170° (avoid a snap at the wrist)
Head       level or slight tilt; gaze to camera
Camera     waist to chest; 3-4 m
Shot types HALF_BODY, THREE_QUARTER, FULL_BODY
Detectable elbow-out angle, wrist height relative to hip line, wrist angle, stance width
Mistakes   elbows too far forward (in silhouette against torso); hands too high on the waist;
           both elbows square to the camera on a square body
```

### 2.5 Arms Crossed (loose, not clenched) — `diff: MEDIUM`

```
Body       facing 10-35°; upright
Hips       level or slightly asymmetric
Legs       weight on one leg; feet apart
Shoulders  relaxed, not hunched
Arms       forearms crossed above the waist (not at the chest); hands tucked under the opposite arm
           or resting on the opposite elbow; fingertips visible
Hands      relaxed fingers; no fist
Head       slight tilt 0-6°; gaze to camera
Camera     chest height; 2-3.5 m; slightly to the side
Shot types HALF_BODY, THREE_QUARTER
Detectable crossing geometry (wrist near the opposite elbow), hand visibility, hunch (shoulder height
           vs ear distance), elbow height
Mistakes   clenched hands; arms too high (covering the chin); hunched shoulders; both wrists hidden
```

### 2.6 Leaning Against a Wall / Structure — `diff: MEDIUM`

```
Body       contact with the wall along one shoulder/upper arm or the lower back;
           torso rotated 30-60°; the wall is behind, not beside, when possible
Hips       pushed toward the wall; hip line tilted 8-15°
Legs       one leg straight bearing weight, the other crossed in front or bent with the foot on the wall
Shoulders  contacting side dropped, other raised slightly
Arms       one arm along the wall (or hand in pocket), other relaxed
Hands      relaxed, or flat on the wall with fingers slightly spread
Head       level or slightly turned toward or away from the wall
Camera     chest to eye height; 3-5 m; camera parallel to the wall (avoid shooting along it)
Shot types THREE_QUARTER, FULL_BODY, ENVIRONMENTAL
Detectable contact side (landmark proximity to the wall plane is NOT directly available → infer from
           body lean and asymmetry), lean angle, cross-leg geometry
Mistakes   floating (no visible contact point); leaning backward away from the wall;
           standing parallel to the wall with a square body
```

### 2.7 Sitting — Edge of a Chair/Stool (forward lean) — `diff: EASY`

```
Body       seated near the front edge; torso leaning forward 10-20°; rotated 20-40°
Hips       knees below or level with hips (avoids the "collapsed" look)
Legs       one knee at ~90°, the other angled outward or crossed; ankles stacked
Shoulders  relaxed forward, not slumped
Arms       forearms resting on thighs or one hand on the knee; elbows bent ~100-120°
Hands      relaxed on the thigh/knee; fingers staggered, not parallel
Head       level or slightly toward the camera; chin slightly forward
Camera     eye height (or slightly above); 1.5-3 m; avoid shooting down at a seated subject
Shot types HALF_BODY, THREE_QUARTER, SEATED_PORTRAIT
Detectable hip-height vs knee-height ratio (in world units, approximate), knee angle, torso lean
Mistakes   sitting fully back (slumped, short torso); knees square to the camera;
           photographing from standing height
```

### 2.8 Sitting on the Ground / Steps — `diff: MEDIUM`

```
Body       side-sit or cross-legged; torso rotated 30-60°; one hand supporting behind
Hips       one hip grounded; hip line tilted
Legs       crossed or extended sideways; the near knee is normally raised
Shoulders  organic, one dropped
Arms       one support arm angled back (elbow slightly bent, not hyper-extended);
           other arm resting on the knee
Hands      long fingers on the knee, wrist relaxed
Head       turned toward the camera; slight tilt
Camera     low (0.5-1.2 m); 1.5-3 m; pay attention to the ground line
Shot types ENVIRONMENTAL, FULL_BODY, THREE_QUARTER
Detectable hip/knee/ankle geometry, support-arm hyperextension, ground contact plausibility from
           ankle/heel positions
Mistakes   legs pointed straight at the lens (foreshortened); both hands hidden;
           camera too high (compresses the subject)
```

### 2.9 Walking / Mid-Stride (candid) — `diff: MEDIUM`

```
Body       captured mid-step, torso rotated 20-40°; motion implied by limb positions
Hips       asymmetric, one hip forward
Legs       one leg forward with heel contact, other pushing off; knees bent 150-170°
Shoulders  counter-rotated slightly to the hips
Arms       opposite arm forward; natural swing, elbows softly bent
Hands      relaxed, often partly curled
Head       level, gaze in the walking direction or toward the camera if it is a "look-at-me" walk
Camera     waist to chest height; 3-6 m; camera parallel to the walk direction when possible
Shot types FULL_BODY, CANDID, ENVIRONMENTAL
Detectable step phase, knee angles, arm-swing opposition, overall lean
Mistakes   both legs together (frozen "walking" look); too much lean forward;
           shot from the front while walking (heavy foreshortening)
```

### 2.10 Turning / Look-Back-Over-Shoulder — `diff: MEDIUM`

```
Body       torso rotated 60-100° away from the camera; head rotated back toward the lens
Hips       facing away; hip line nearly perpendicular to the shoulder line
Legs       weight on the far leg; near leg relaxed
Shoulders  strong rotation; shoulders nearly in profile
Arms       often one arm across the body or hands in pockets; the far arm partly hidden
Head       rotated 45-90° back; chin slightly toward the shoulder; watch the neck for strain
Camera     chest height; 2-4 m; slightly behind the subject's original facing
Shot types THREE_QUARTER, FULL_BODY, EDITORIAL
Detectable torso–head rotation mismatch, neck strain (angle between head and shoulder lines),
           arm visibility
Mistakes   over-rotation (neck looks twisted); the whole body turned instead of just the head;
           the far shoulder cut awkwardly
```

### 2.11 Crouching / Squatting — `diff: MEDIUM`

```
Body       crouched with an upright-ish torso; one knee more bent than the other; weight on the balls
           of the feet
Hips       low, tilted; one hip forward
Legs       knees bent 60-110°; heels may lift
Shoulders  relaxed, leaning slightly forward
Arms       forearms on the knees, or one hand on the ground, one on the knee
Hands      relaxed; avoid flat palms pressed hard
Head       level; gaze to camera
Camera     low (0.4-1.0 m); 1.5-3 m
Shot types FULL_BODY, ENVIRONMENTAL, STREET
Detectable knee/hip angles, symmetry of leg bend, torso lean
Mistakes   both knees exactly even (squat = symmetric); torso folded forward; camera too high
```

### 2.12 Hands Near Face / Framing the Face — `diff: EASY`

```
Body       torso rotated 20-45°; close crop means the body contributes little
Hips       not visible in the crop
Legs       not visible
Shoulders  one shoulder forward; both relaxed and dropped
Arms       one or both arms raised; elbow bent 60-110°; the hand approaches the face but does not
           cover features
Hands      relaxed, staggered fingers, small gaps; back of the hand angled away from the lens
Head       slight tilt toward the hand; chin slightly forward/down
Camera     eye height or slightly above; 1-2 m (head-and-shoulders)
Shot types HEADSHOT, CLOSE_PORTRAIT, HALF_BODY
Detectable hand-to-face distance, hand-face overlap fraction, wrist angle, elbow elevation
Mistakes   palm pressed to the cheek (flattens); fingers covering the mouth/eye;
           hand too far from the face (looks like a decision not made)
```

### 2.13 Environmental Portrait (subject in a place) — `diff: HARD`

```
Body       body turned to interact with the environment; posture relaxed
Hips       often on the edge of the frame's structure
Legs       planted; a stable base matters more than the S-curve
Shoulders  aligned to the interaction (leaning on a railing, holding a tool)
Arms       doing something: holding, touching, resting, gesturing
Hands      engaged with an object (if the object is relevant)
Head       gaze at the object, or turned to the camera to include the viewer
Camera     distance chosen to include context; height often chest or above; a wider framing
Shot types ENVIRONMENTAL, FULL_BODY, THREE_QUARTER
Detectable body-to-object interaction is NOT measurable without object detection;
           what IS measurable: subject size, headroom, framing, subject-vs-background separation proxy
Mistakes   subject too small to read; cluttered background; the subject looks "placed" rather than
           interacting
```

### 2.14 Close-Up / Headshot Variants — `diff: TRIVIAL`

```
Variants   (a) straight to camera, shoulders 30-45° turned;
           (b) three-quarter head turn;
           (c) chin slightly down and forward (jawline definition);
           (d) head tilted 3-8° toward the far shoulder;
           (e) gazing off-camera (lead room required on the gaze side)
Body       only shoulders/upper chest visible
Shoulders  one shoulder forward; the far shoulder slightly lower
Arms       mostly out of frame; hands only if they frame the face (2.12)
Head       as above; keep the neck long, avoid pressing the head back into the shoulders
Camera     eye height; 1.2-2.5 m; slight above-eye at most (avoid looking down the nose)
Shot types HEADSHOT, CLOSE_PORTRAIT
Detectable head roll/pitch/yaw (face transformation matrix), eye-line placement, headroom,
           shoulder angle, whether gaze has lead room
Mistakes   eyes too low in frame; excessive headroom; camera too high (big forehead);
           camera too low (wide jaw/nostrils); chin pushed back into the neck
```

### 2.15 Two-Hand Gesture / Pointing Forward — `diff: MEDIUM`

```
Body       torso square or slightly turned; the gesture carries the meaning
Arms       one or both hands raised toward the camera
Hands      palm or index toward the lens (this is exactly the case where foreshortening is INTENDED)
Head       follows the gesture
Camera     chest height; 1.5-3 m
Shot types HALF_BODY, CANDID
Detectable hand position; foreshortening is expected → POSE_LIMB_FORESHORTENED must be suppressed by
           the template's intent flag
Mistakes   the hand is out of frame; the gesture looks like an accident because the arm is cut
```

### 2.16 Lying / Reclining — `diff: MEDIUM` (post-MVP)

```
Body       horizontal axis in frame; torso supported by elbows or ground
Hips       rotated; hip line not parallel to the frame edges (avoid perfectly horizontal bodies)
Legs       knees bent, ankles crossed at different depths
Shoulders  one shoulder raised; the head is supported or turned
Arms       one forearm support, one arm relaxed along the body
Hands      relaxed, fingers staggered
Head       turned toward the camera; slight tilt
Camera     above and slightly to the side (for a 3/4 view), or at eye level for a flat lay
Shot types FULL_BODY, EDITORIAL
Detectable roll/orientation of the body axis, limb separation, head placement relative to the body axis
Mistakes   perfectly straight body along the frame edge (flat, dead); feet at the frame edge;
           limbs merging into the torso line
```

> Post-MVP: the matcher's rotation clamp (±25°) deliberately refuses to align a lying body to a standing
> template. Lying poses need their own templates and, probably, a rotation-tolerant variant of the matcher
> (Phase 6+ decision).

---

## 3. Two-person poses

Two subjects introduce **relations**, not just individual geometry. The measurable relational features are
the valuable part:

| Feature | Measurement | Why it matters |
| --- | --- | --- |
| spacing | distance between hip midpoints / mean torso length | couples read closer than friends |
| overlap | bbox intersection over union, plus depth order proxy (scale ratio, occlusion) | a slight overlap reads as a couple; a gap reads as two solo portraits |
| head stagger | `Δy` of head tops / frame height | equal head heights create a "row" feel; staggering reads naturally |
| height difference | head-top Δy / mean subject height | inform stagger amount, avoid the shorter person hiding |
| depth offset | ratio of subject torso lengths (larger = closer) | forward/back placement creates layering |
| interaction anchors | wrist/hand proximity to the other subject's shoulder/waist/hip | indicates physical connection |
| mutual orientation | angle between each subject's shoulder line and the axis between them | "turning toward each other" |
| synchronicity | correlation of body orientation | mirrored vs matched vs opposite; avoid "both identical" |

Detectability notes:

* Everything above is `DETERMINISTIC` **given two reliable subjects** — but the v1 perception stack detects
  one person (`ai-models.md`), so in MVP these are **specified, not implemented**.
* Assigning landmarks to "person A/B" across frames (tracking) is a Phase 8 requirement.

### 3.1 Couples

```
Structure    two subjects, one slightly in front (depth offset 10-25% torso length),
             heads staggered (10-25% of frame height), bodies angled toward each other (30-60°)
Spacing      shoulder-to-shoulder distance ≈ 0.3-0.8 torso lengths (close, often touching)
Overlap      slight (5-20% of the nearer subject's bbox) reads as intimacy; avoid full overlap
Heads        often tilted toward each other (a small "V"); avoid identical head tilts
Hands        at least one visible connection or purposeful placement (waist, shoulder, hand-in-hand)
Heights      if heights differ strongly, taller subject slightly behind lowers the apparent gap
Camera       chest to eye height; 2.5-4 m; avoid shooting from a distance that flattens them into a row
Mistakes     gap between them (two solo portraits); identical poses (twins); the shorter subject
             hidden; both facing the camera squarely
```

### 3.2 Friends / Siblings

```
Structure    can be looser than couples; two head heights clearly staggered (12-30%),
             bodies angled 20-45° toward or away from each other
Spacing      closer than strangers, farther than couples; 0.5-1.2 torso lengths
Overlap      minimal to slight; avoid limbs crossing confusingly
Heads        one slightly higher; different tilts (asymmetric)
Interaction  shoulder-to-shoulder, arm over shoulder, or a shared prop/activity
Camera       chest height; 3-4.5 m
Mistakes     both at exactly the same height and angle; a void in the middle of the frame;
             one subject much larger (too far in front)
```

### 3.3 Parent + Child

```
Structure    heights differ a lot: place the taller generally BEHIND, or both seated/lowered
Spacing      very close; the adult's arm often wraps or rests
Heads        stagger amounts should be large (≥ 20% of frame height) to avoid the child being lost;
             cameras/reference height should sit between the two (chest of the adult, head of the child)
Interaction  physical connection is the point; the child is not a prop: keep the child's face clear
Camera       lower than adult eye height; 2-4 m
Mistakes     shooting from adult eye height (child's face looks displaced or the adult's chin dominates);
             child fully hidden by the adult's body; adult's arm crossing the child's face
```

### 3.4 Two subjects of very different heights (general)

* Put the taller subject further from the camera and/or lower (sitting, leaning, crouching) — the scale
  ratio is measurable as a torso-length ratio between subjects, so the guidance can be phrased as
  "move the taller subject slightly back" or "have the shorter subject stand closer".
* Avoid the "hanging height gap": do not stack heads at exactly the same y unless both faces are clear.
* Real camera advice: lower the camera toward the *shorter* subject's eye height (the frame is shared).

---

## 4. Group poses (3, 4, 5, 6+)

Group posing is a **structure** problem. Detectable properties per structure:

| Structure | Description | Measurable properties | Failure modes |
| --- | --- | --- | --- |
| Triangle | heads at three different heights, spacing uneven | head `y` variance, pairwise spacing variance, coverage of the bbox by subjects | perfect equilateral (stiff), heads on one line |
| Staggered / zigzag | alternating depth and height | depth proxies (torso length ratio), alternating head `y` | ambiguous alternation (reads as a mistake) |
| Layered (sitting + standing) | front row lower, back row higher, clear depth | torso-length ratio between rows, bbox overlap | front row blocking back row faces |
| V arrangement | centre subject forward, others receding to both sides | signed depth vs lateral offset correlation | symmetric V used with an even number (a "driver" in the middle) |
| Rows (least preferred) | everyone in one line | head `y` variance ≈ 0 | overlapping shoulders, chopped arms |
| Height-balanced | tallest outside or background, shortest downstage | head-top y variance after sorting | the "hanging height" of one head between two others |
| Asymmetric group | intentional imbalance, one anchor subject | not measurable (aesthetic) | — |

Group rules that *are* mathematically checkable later (Phase 8):

* **head-line variance**: `var(headTopY) ≥ T` (avoid a ruler-straight row of heads) — `CALIBRATION_REQUIRED`
* **equal spacing check**: pairwise hip-distance standard deviation above a floor
* **visibility check**: no subject's face occluded by another subject's body (requires a per-subject mask
  and depth ordering)
* **frame coverage**: subjects should occupy a target fraction of the frame; count > 6 requires rows
* **no perfect symmetry**: mirror-difference metric across the group

Group sizes:

```
3        triangle or staggered; one subject clearly the anchor
4        staggered pairs or a 2x2 with depth; avoid a 4-across line
5        layered: 3 standing staggered + 2 seated, or a V
6+       two rows with the back row slightly higher / narrower; odd counts favour one centre anchor
```

---

## 5. What the app can actually measure (summary for implementers)

| Property | Source | Deterministic? |
| --- | --- | --- |
| weight side & magnitude | hip vs support midpoint | yes |
| contrapposto magnitude | shoulder line angle − hip line angle | yes |
| arm–torso gap | segment-to-axis distance | yes |
| joint angles | landmark triplets | yes (±8–11° noise) |
| stance width, feet separation | ankles/foot landmarks | yes |
| body facing angle | world vs 2D shoulder width ratio | approximately (world z is synthetic) |
| foreshortening | 2D/3D segment ratio | approximately |
| head roll | eye line | yes |
| head pitch/yaw | face transformation matrix | yes (ML) |
| gaze | iris landmarks | yes (ML, needs face model) |
| hand gesture / relaxed fingers | hand landmarks | `LATER` (needs Hand Landmarker) |
| "leaning on a wall" | body lean + contact inference | no (needs scene geometry) |
| "interacting with an object" | — | no |
| expression / emotion | — | no (rejected) |

---

## 6. Template authoring backlog (owner decision 2026-10-03)

**Target library: 20 high-quality SOLO poses**, distributed exactly as the owner specified. Every template
must ship as normalized geometry (`pose-system.md` §3), be calibrated against real captures, and reach
`provenance.status = VALIDATED` before it is offered to users.

| Group (owner target) | Count | Families to use (`§2`) | Template ids |
| --- | --- | --- | --- |
| Standing, full body | 8 | 2.1 weight shift, 2.2 relaxed A-pose, 2.3 hands in pockets, 2.4 hands on hips, 2.5 arms crossed, 2.6 leaning on a wall, 2.9 walking mid-stride *(standing variant)*, 2.10 look back over shoulder | `solo_weight_shift_standing_v1` ✅, `solo_a_pose_relaxed_v1` ✅, `solo_hands_in_pockets_v1` ✅, `solo_hands_on_hips_v1`, `solo_arms_crossed_v1`, `solo_lean_wall_v1`, `solo_walking_mid_stride_v1`, `solo_look_back_shoulder_v1` |
| Three-quarter (body turned, waist-up framing) | 4 | 2.11 three-quarter body, 2.13 hands near hair, 2.6 lean, 2.1 weight shift *(three-quarter crop)* | `solo_three_quarter_hands_hair_v1`, `solo_three_quarter_weight_shift_v1`, `solo_three_quarter_lean_v1`, `solo_three_quarter_look_down_v1` |
| Half-body / portrait | 4 | 2.14(a) headshot square, 2.14(b) three-quarter headshot, 2.12 hand near face (close crop), 2.15 environmental half-body | `solo_headshot_three_quarter_v1` ✅, `solo_headshot_square_v1`, `solo_portrait_hand_near_face_v1`, `solo_half_body_environmental_v1` |
| Sitting | 2 | 2.7 seated on a chair edge, 2.8 sitting on the ground/steps | `solo_sitting_edge_forward_lean_v1`, `solo_sitting_ground_crossed_v1` |
| Walking / leaning | 2 | 2.9 mid-stride, 2.6 lean on wall/railing | `solo_walking_toward_camera_v1`, `solo_lean_railing_relaxed_v1` |

✅ = authored as a Phase 0 seed (4 templates + 1 mirrored twin in `specs/poses/`); it still has to be
calibrated and promoted to `VALIDATED` in Phase 6 like every other template.

Two-person templates are **not** part of this target: they belong to Phase 8 (`pair_couple_close_staggered_v1`
is listed there), consistent with the single-person MVP.

Handed poses (weight shift, lean, look-back) must follow the mirror contract: `mirrorAllowed: false` plus an
explicit `_m1` twin (`pose-system.md` §4.4). `solo_weight_shift_standing_v1_m1` is the worked example.
