# Photography Errors — taxonomy, detectability and severity policy

Companion to `photography-rules.md` (which defines measurement). This file answers a different question:
**when is something actually an error, and how sure are we allowed to be?**

The app lives or dies on one thing: if it tells a user "that's wrong" and it is not wrong — or it is a
matter of taste — the user stops trusting it. So this document is deliberately conservative.

---

## 1. The four classification axes

Every candidate error is classified on four independent axes. All four are stored per rule
(see `specs/rules/mvp-rules.json`) and are used by the arbiter to decide wording and blocking power.

### Axis 1 — Error class (how "wrong" is it?)

| Class | Definition | UI wording | May block readiness? |
| --- | --- | --- | --- |
| `HIGH_CONFIDENCE_ERROR` | Universally recognized as a defect by credible practitioners **and** consistent with perception research; the *"my photo looks like a mistake"* category | "Fix: …" (imperative) | Yes, if it also breaks pose readability/framing |
| `CONTEXT_DEPENDENT_ISSUE` | A convention that is correct in some intents and wrong in others (turn, crop, angle, symmetry) | "Try: …" / "Consider …" | No |
| `SUBJECTIVE_PREFERENCE` | Fashion/style/aesthetic taste | Not shown as an error at all; only as an optional, clearly-labelled suggestion in a dedicated "ideas" surface | No |
| `NOT_AN_ERROR` | Frequently listed as mistakes in SEO content but unsupported (e.g. centering, rule-of-thirds violation) | Never surfaced | No |

### Axis 2 — Detectability (can we measure it?)

| Class | Meaning | Implementation status |
| --- | --- | --- |
| `DETERMINISTIC` | Closed-form geometry/statistics on reliable inputs | Buildable now |
| `ML_ASSISTED` | A learned model's output is required (segmentation, saliency, line detection) | Needs a licence-cleared on-device model |
| `HYBRID` | Geometry over an ML detection, with a validated fallback | Buildable now with confidence caps |
| `DIFFICULT` | Measurement is noisy, ambiguous, or needs scene semantics | Advisory only, if at all |
| `SUBJECTIVE` | No ground truth exists | Do not score |

### Axis 3 — Subject vs photographer

Who has to act? This decides which *actor* gets the instruction and prevents contradictory asks.

`ACTOR_PHOTOGRAPHER` (camera moves/changes) · `ACTOR_SUBJECT` (person moves) · `ACTOR_BOTH` ·
`ACTOR_NONE` (informational)

### Axis 4 — Severity / urgency

| Severity | Meaning | Behaviour |
| --- | --- | --- |
| `BLOCKING` | The photograph cannot be judged or will certainly be bad (subject absent, frame black, subject cut in half, motion blur) | Suppresses all other guidance; blocks readiness |
| `IMPORTANT` | Visible defect that a competent photographer would fix (headroom excessive for the chosen shot type, blown highlights on the face, arm pressed to torso in a template-driven pose) | Pre-empts lower severities |
| `NUDGE` | Small improvement, cheap to fix | Shown only when nothing more important is pending |
| `INFO` | Telemetry/annotation for the developer screen or an optional "why this shot" view | Never in the main banner |

---

## 2. Master error table

Legend: **Class** = error class; **Detect** = detectability; **Actor**; **Sev**; **Phase** (implementation
priority). Rule IDs map to `photography-rules.md`.

### 2.1 Framing and cropping

| # | Problem | Rule ID | Class | Detect | Actor | Sev | Phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F1 | Subject absent / unreadable | `FRAME_SUBJECT_PRESENT` | HIGH_CONFIDENCE | DETERMINISTIC | PHOTOGRAPHER | BLOCKING | MVP |
| F2 | Subject too small (pose unreadable) | `FRAME_SUBJECT_TOO_SMALL` | HIGH_CONFIDENCE | DETERMINISTIC | PHOTOGRAPHER | IMPORTANT | MVP |
| F3 | Subject too large / cropped by frame | `FRAME_SUBJECT_TOO_LARGE` | HIGH_CONFIDENCE | DETERMINISTIC | PHOTOGRAPHER | IMPORTANT | MVP |
| F4 | Excessive headroom | `FRAME_HEADROOM_EXCESSIVE` | HIGH_CONFIDENCE (within a chosen shot type) | HYBRID | PHOTOGRAPHER | IMPORTANT | MVP |
| F5 | Insufficient headroom / head touching top edge | `FRAME_HEADROOM_INSUFFICIENT` | HIGH_CONFIDENCE | HYBRID | PHOTOGRAPHER | IMPORTANT | MVP |
| F6 | Subject touching / crossing the frame edge unintentionally | `FRAME_EDGE_MARGIN` | CONTEXT_DEPENDENT | DETERMINISTIC | PHOTOGRAPHER | NUDGE | MVP |
| F7 | Accidental crop at a joint (ankle, knee, wrist, elbow, neck) | `FRAME_JOINT_CROP` | CONTEXT_DEPENDENT | DETERMINISTIC | PHOTOGRAPHER | NUDGE | MVP |
| F8 | Feet cut off in a full-body shot | `FRAME_JOINT_CROP` (foot variant) | HIGH_CONFIDENCE | DETERMINISTIC | PHOTOGRAPHER | IMPORTANT | MVP |
| F9 | Subject squeezed into a corner / extreme imbalance | `FRAME_SUBJECT_OFF_CENTER_EXTREME` | HIGH_CONFIDENCE | DETERMINISTIC | PHOTOGRAPHER | NUDGE | MVP |
| F10 | Unintended camera roll / tilted horizon | `FRAME_CAMERA_TILT` | CONTEXT_DEPENDENT (Dutch tilt exists) | DETERMINISTIC (IMU) | PHOTOGRAPHER | NUDGE | MVP |
| F11 | Subject centered (alone) | — | NOT_AN_ERROR | — | — | — | — |
| F12 | Rule-of-thirds not followed | — | NOT_AN_ERROR | — | — | — | — |

### 2.2 Background and scene

| # | Problem | Rule ID | Class | Detect | Actor | Sev | Phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| B1 | Object appears to grow from the head (pole/branch tangent) | `FRAME_BACKGROUND_MERGER` | HIGH_CONFIDENCE | DIFFICULT (needs segmentation/edges) | PHOTOGRAPHER | NUDGE | LATER |
| B2 | Horizon or strong line crossing the head/neck | `FRAME_HORIZON_THROUGH_HEAD` | CONTEXT_DEPENDENT | DIFFICULT | PHOTOGRAPHER | NUDGE | LATER |
| B3 | Background clutter / busy area behind the subject | `FRAME_BACKGROUND_CLUTTER` | CONTEXT_DEPENDENT | ML_ASSISTED | PHOTOGRAPHER | NUDGE | LATER |
| B4 | Poor subject/background separation (no tonal or focus separation) | `FRAME_BACKGROUND_SEPARATION` | CONTEXT_DEPENDENT | ML_ASSISTED (segmentation + contrast) | PHOTOGRAPHER | NUDGE | LATER |
| B5 | Very bright region behind the head drawing the eye | `FRAME_BRIGHT_BLOB_BEHIND_HEAD` | CONTEXT_DEPENDENT | DETERMINISTIC (luma grid) | PHOTOGRAPHER | NUDGE | MVP (cheap variant) |
| B6 | Distracting element at the frame edge | `FRAME_EDGE_DISTRACTION` | SUBJECTIVE | ML_ASSISTED | PHOTOGRAPHER | INFO | LATER |
| B7 | Two competing subjects ("double subject") | — | SUBJECTIVE | DIFFICULT | — | — | REJECT (see §4) |

### 2.3 Pose and body

| # | Problem | Rule ID | Class | Detect | Actor | Sev | Phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P1 | Arms pressed against the torso (flattening/widening) | `POSE_ARM_TORSO_GAP_TOO_SMALL` | HIGH_CONFIDENCE (practitioner consensus) | DETERMINISTIC over ML landmarks | SUBJECT | IMPORTANT | MVP |
| P2 | Fully locked (straight) elbows/knees | `POSE_ELBOW_LOCKED` (elbows and knees are one rule) | CONTEXT_DEPENDENT | DETERMINISTIC | SUBJECT | NUDGE | NEXT |
| P3 | Awkward wrist angle | `POSE_WRIST_ANGLE_EXTREME` | CONTEXT_DEPENDENT | HYBRID | SUBJECT | NUDGE | NEXT |
| P4 | Limb pointed straight at the camera (foreshortened into a stump) | `POSE_LIMB_FORESHORTENED` | CONTEXT_DEPENDENT | HYBRID | SUBJECT | NUDGE | NEXT |
| P5 | Feet visually merging / stance too narrow | `POSE_FEET_MERGING` | CONTEXT_DEPENDENT | DETERMINISTIC | SUBJECT | NUDGE | NEXT |
| P6 | Shoulders unnaturally square to camera / hips perfectly level | `POSE_SHOULDERS_SQUARE`, `POSE_HIPS_LEVEL` | CONTEXT_DEPENDENT | DETERMINISTIC | SUBJECT | NUDGE | NEXT |
| P7 | Excessive unintended left-right symmetry | `POSE_EXCESSIVE_SYMMETRY` | CONTEXT_DEPENDENT | DETERMINISTIC | SUBJECT | NUDGE | NEXT |
| P8 | Hands hidden unintentionally / outside frame | `POSE_HANDS_HIDDEN_UNINTENTIONALLY` | CONTEXT_DEPENDENT | DETERMINISTIC (visibility) | SUBJECT | NUDGE | NEXT |
| P9 | Hand overlapping the face in a distracting way | `POSE_HAND_OVER_FACE` | CONTEXT_DEPENDENT | DETERMINISTIC (overlap geometry) | SUBJECT | INFO | NEXT |
| P10 | Poor weight distribution ("toy soldier" stance) | `POSE_WEIGHT_SHIFT_AMBIGUOUS` | CONTEXT_DEPENDENT | DETERMINISTIC | SUBJECT | NUDGE | MVP (as feature) |
| P11 | Overlapping limbs that merge into one shape | `POSE_LIMB_MERGE` | CONTEXT_DEPENDENT | DIFFICULT (needs z) | SUBJECT | NUDGE | LATER |
| P12 | Chin tucked creating a double chin | `POSE_CHIN_TUCK` | CONTEXT_DEPENDENT (culture-dependent, sensitive) | HYBRID (head pitch) | SUBJECT | NUDGE (careful wording) | NEXT |
| P13 | Body language "closed off" | — | SUBJECTIVE | DIFFICULT | — | — | REJECT (§4) |

### 2.4 Lighting and exposure

| # | Problem | Rule ID | Class | Detect | Actor | Sev | Phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| L1 | Face underexposed / in shadow | `LIGHT_FACE_UNDEREXPOSED` | HIGH_CONFIDENCE | DETERMINISTIC (relative) | PHOTOGRAPHER | IMPORTANT | MVP |
| L2 | Blown highlights on the face | `LIGHT_FACE_OVEREXPOSED` | HIGH_CONFIDENCE | DETERMINISTIC | PHOTOGRAPHER | IMPORTANT | MVP |
| L3 | Large highlight clipping anywhere | `LIGHT_HIGHLIGHT_CLIPPING` | HIGH_CONFIDENCE | DETERMINISTIC | PHOTOGRAPHER | IMPORTANT | MVP |
| L4 | Crushed shadows with detail loss | `LIGHT_SHADOW_CRUSHING` | CONTEXT_DEPENDENT (silhouettes are a style) | DETERMINISTIC | PHOTOGRAPHER | NUDGE | MVP |
| L5 | Backlit subject (background much brighter than the subject) | `LIGHT_BACKLIT_SUBJECT` | HIGH_CONFIDENCE | HYBRID | PHOTOGRAPHER | IMPORTANT | MVP |
| L6 | Flat, low-contrast light | `LIGHT_LOW_CONTRAST` | CONTEXT_DEPENDENT | DETERMINISTIC | PHOTOGRAPHER | NUDGE | MVP |
| L7 | Harsh midday light / hard shadow edges | `LIGHT_HARD_LIGHT` | CONTEXT_DEPENDENT | DIFFICULT in the wild | PHOTOGRAPHER | NUDGE | LATER |
| L8 | Uneven left/right facial lighting | `LIGHT_UNEVEN_FACE` | SUBJECTIVE | DETERMINISTIC | PHOTOGRAPHER | INFO | NEXT |
| L9 | Colour cast | `LIGHT_COLOR_CAST` | SUBJECTIVE | DIFFICULT (ISP WB) | PHOTOGRAPHER | INFO | EXPERIMENTAL |

### 2.5 Timing and motion

| # | Problem | Rule ID | Class | Detect | Actor | Sev | Phase |
| --- | --- | --- | --- | --- | --- | --- | --- |
| T1 | Camera shake at the moment of capture | `READY_CAMERA_STABLE` | HIGH_CONFIDENCE | DETERMINISTIC (IMU + landmark velocity) | PHOTOGRAPHER | BLOCKING for auto-capture | MVP |
| T2 | Subject mid-blink / odd expression at capture | `READY_FACE_ACCEPTABLE` (blink) | CONTEXT_DEPENDENT | ML_ASSISTED (blendshapes would be needed) | SUBJECT | NUDGE | NEXT |
| T3 | Subject in transit between poses | `READY_SUBJECT_STABLE` | HIGH_CONFIDENCE | DETERMINISTIC | SUBJECT | BLOCKING for auto-capture | MVP |
| T4 | Frame captured before the pose settles | readiness duration gate | HIGH_CONFIDENCE | DETERMINISTIC | — | BLOCKING | MVP |

---

## 3. Severity policy (how errors become behaviour)

```
BLOCKING  → suppress all other guidance, block readiness, show as the single banner message
IMPORTANT → eligible to be the primary instruction; blocks readiness only if it also breaks
            the pose/composition criteria required by the active shot type
NUDGE     → eligible to be primary only if nothing IMPORTANT/BLOCKING is active for ≥ dwellMs;
            never blocks readiness
INFO      → developer/analytics screen only (or an opt-in "why?" sheet)
```

Additional global policies:

* **Never show more than one error at a time** in the main banner (see `guidance-engine.md`).
* **Never show the same error again** within `cooldownMs` (default 4 s, `CALIBRATION_REQUIRED`) unless the
  user has acted and it persists.
* **Silence when unsure**: if the best candidate has `confidence < 0.5` or the frame is degraded
  (motion blur, severe underexposure), the assistant shows a neutral state, not a guess.
* **Praise is part of the design.** When the frame is in the good band and stable, the UI says so
  ("ready") rather than staying silent — silence is ambiguous.

---

## 4. Explicitly rejected "errors" (with reasons)

| Popular claim | Verdict | Why |
| --- | --- | --- |
| "Centering the subject is bad, use rule of thirds" | NOT_AN_ERROR | A peer-reviewed preference study on single-object composition found participants overwhelmingly preferred the centered object; large photo/painting analyses found computed rule-of-thirds measures uncorrelated with aesthetic ratings |
| "Golden ratio composition is better than thirds" | NOT_AN_ERROR | No evidence distinguishable at mobile precision; the "compliance" measure for a human subject is ill-defined |
| "Never shoot from below / low angle always better for full body" | NOT_AN_ERROR | Angle effects on perception are small and context-dependent; the useful framing advice is about *proportion* and *background visibility*, not about a universally better angle |
| "Always shoot with an 85 mm lens for portraits" | NOT_AN_ERROR (but the underlying fact matters) | Perspective is determined by camera–subject *distance*, not focal length; on a phone the useful guidance is about distance and apparent-head-size ratio, not "use 85 mm" |
| "Wide-angle lenses distort faces" | CONTEXT_DEPENDENT | True *at a given distance*; a wide lens used from the same distance as an 85 mm gives the same facial proportions (cropping aside) |
| "Crossed arms look defensive" | SUBJECTIVE | Body-language interpretation is culturally loaded and contested |
| "Don't show the back of the hand / hands too big" | SUBJECTIVE | Editorial preference |
| "Subject should look thinner" / "slimming pose" | REJECT (harmful) | Body-image harm; not an objective defect; forbidden as app guidance |
| "Smile for a good photo" (expression scoring) | REJECT | Culturally and personally variable; expression is not a defect |
| "The subject should be off-centre with space to look into" (absolute) | CONTEXT_DEPENDENT | Lead room is a real convention; direct-to-camera gaze with centering is equally valid |

> These rejections are part of the specification. A future agent that re-introduces one of them is
> violating the product's honesty contract, not adding a feature.

---

## 5. Detectability summary (the answer to Q1–Q3 of Phase 0)

**Deterministically detectable today (no model risk):**
framing size, headroom (with a hybrid head-top estimator), edge margins, joint crops, extreme off-centering,
camera roll (IMU), exposure/clipping/contrast statistics, subject presence and shot type,
arm–torso gap, stance width, symmetry, weight distribution, landmark stability.

**Needs ML assistance (with licence-clean models):**
background merges/tangents, background clutter, subject/background separation, hard-light detection,
face-region statistics (needs the face/pose mask), scene context, aesthetic assessment (not licensed
today — see `model-licenses.md`).

**Too subjective / not automatable responsibly:**
visual balance, "closed" body language, expression quality, attractiveness, fashion appropriateness,
story/narrative strength, colour grading quality, "which of these two photos is better".

---

## 6. Error → instruction mapping preview

Errors are never displayed as diagnostics: the primary banner shows an **action** and the "why?" sheet
shows an **explanation**. Both are message keys from the catalog (`specs/i18n/messages.json`), which the
spec validator checks against the rule database — an unregistered key fails validation.

| Error | Rule | Primary action(s) | Explanation key ("why?") |
| --- | --- | --- | --- |
| F2 | `FRAME_SUBJECT_TOO_SMALL` | `MOVE_CLOSER`, `ZOOM_IN` | `guidance.why.frame_subject_too_small` |
| F4 | `FRAME_HEADROOM_EXCESSIVE` | `DECREASE_HEADROOM` | `guidance.why.frame_headroom_excessive` |
| F5 | `FRAME_HEADROOM_INSUFFICIENT` | `INCREASE_HEADROOM` | `guidance.why.frame_headroom_insufficient` |
| F7 | `FRAME_JOINT_CROP` | `REFRAME`, `SUBJECT_MOVE_INWARD`, `ZOOM_OUT` | `guidance.why.frame_joint_crop` |
| F10 | `FRAME_CAMERA_TILT` | `LEVEL_CAMERA` | `guidance.why.frame_camera_tilt` |
| P1 | `POSE_ARM_TORSO_GAP_TOO_SMALL` | `MOVE_ARM_AWAY_FROM_TORSO` (side param) | `guidance.why.pose_arm_torso_gap_too_small` |
| P5 | `POSE_FEET_MERGING` | `SUBJECT_WIDEN_STANCE` | `guidance.why.pose_feet_merging` |
| P10 | `POSE_WEIGHT_SHIFT_AMBIGUOUS` | `SUBJECT_SHIFT_WEIGHT_LEFT/RIGHT` | `guidance.why.pose_weight_shift_ambiguous` |
| L1 | `LIGHT_FACE_UNDEREXPOSED` | `MOVE_TO_BETTER_LIGHT`, `TURN_ON_LIGHT`, `MOVE_OUT_OF_BACKLIGHT` | `guidance.why.light_face_underexposed` |
| L5 | `LIGHT_BACKLIT_SUBJECT` | `MOVE_OUT_OF_BACKLIGHT` (message `guidance.light.change_camera_position`) | `guidance.why.light_backlit_subject` |
| T1 | `READY_CAMERA_STABLE` | `HOLD_STILL` | `guidance.why.ready_camera_stable` |

Every rule in `specs/rules/mvp-rules.json` has exactly one explanation key (the `explanations` map in the
catalog), so the dev screen and the "why?" sheet can always answer the question with a registered key.
