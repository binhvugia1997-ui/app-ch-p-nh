# Composition Engine — specification

Module: `:core:photography` (pure Kotlin, no Android).
Inputs: `FrameAnalysis` (`specs/schemas/frame-analysis.schema.json`).
Outputs: `List<RuleResult>` + `CompositionSummary`.
Never produces user-visible strings. Never touches the camera or UI.

---

## 1. Responsibilities

1. Estimate the **shot type** actually present (headshot → full body) and whether it matches intent.
2. Run the framing/composition rules from `photography-rules.md` §5–§7.
3. Emit `RuleResult`s with measurement, target range, severity, confidence and a **semantic** suggested
   action.
4. Abstain loudly: every `RuleResult` carries `abstained: true|false` and an `abstainReason`.
5. Provide a compact `CompositionSummary` for the UI (progress indicators), the readiness machine, and the
   developer screen.

What it does **not** do: choose wording, arbitrate against other engines, decide readiness,
draw anything.

---

## 2. Data contracts

### 2.1 `RuleResult`

Authoritative schema: `specs/schemas/rule-result.schema.json`.

```kotlin
data class RuleResult(
    val ruleId: RuleId,                    // e.g. FRAME_HEADROOM_EXCESSIVE
    val category: RuleCategory,            // FRAMING / COMPOSITION / POSE / LIGHTING / READINESS / INFO
    val evaluated: Boolean,                // false when the rule abstained
    val abstainReason: AbstainReason?,     // e.g. INSUFFICIENT_VISIBILITY, NOT_APPLICABLE_SHOT_TYPE
    val severity: Severity,                // INFO / NUDGE / IMPORTANT / BLOCKING
    val confidence: Float,                 // 0..1 after class capping
    val measurement: Measurement,          // value + unit + how it was computed
    val targetRange: ClosedFloatingPointRange<Float>?,
    val suggestedAction: SemanticAction?,  // e.g. INCREASE_HEADROOM / MOVE_FARTHER / LEVEL_CAMERA
    val actor: Actor,                      // PHOTOGRAPHER / SUBJECT / BOTH / NONE
    val sceneContext: SceneContextTag?,    // optional; e.g. WALL_BEHIND, WINDOW_BEHIND (Phase 7)
    val thresholdStatus: ThresholdStatus,  // SETTLED / CALIBRATION_REQUIRED / DEVICE_TUNED
    val persistenceFrames: Int             // how long the condition has held (stateful wrapper)
)
```

Notes:

* `Measurement` carries `kind` (`RATIO_OF_FRAME_HEIGHT`, `DEGREES`, `FRACTION_OF_PIXELS`, …) and
  the numeric value, so the developer screen and the readiness explanation can show real numbers and
  localization can interpolate them where useful.
* `severity` is a **policy output**, not a rule constant: the same measurement can be `NUDGE` when the
  user is exploring and `IMPORTANT` when a template/shot type is locked in.
* `RuleResult`s are immutable; the stateful part (`persistenceFrames`, hysteresis latches) lives in a
  `CompositionEngineState` that is threaded through calls.

### 2.2 `CompositionSummary`

```kotlin
data class CompositionSummary(
    val shotType: ShotType,                 // detected
    val intendedShotType: ShotType?,        // from the user
    val subjectHeightFraction: Float,
    val headroomRatio: Float?,
    val eyeLineFromTop: Float?,
    val minEdgeMargin: Float?,
    val subjectCentreOffset: Float,
    val cameraRollDegrees: Float?,          // from IMU
    val overallBand: Band                   // GOOD / ACCEPTABLE / NEEDS_WORK  (display only)
)
```

`overallBand` is **display only** and must never be presented as a "score out of 10". A single-word band
plus the one active instruction is the whole composition UI in the MVP.

---

## 3. Pipeline

```
FrameAnalysis
     │
     ├─► ShotTypeEstimator
     │       uses: subject bbox height fraction, face bbox height fraction (when available),
     │             landmark coverage, feet visible?
     │       outputs: ShotType + confidence
     │       policy: if user intent exists and detected type disagrees by one step → treat as intent
     │               (people frame slightly looser than they say); two steps → surface intent mismatch
     │
     ├─► FramingRules            (FRAME_*)
     │       gate: subjects present, visibleFraction ≥ 0.5, staleness < 200 ms
     │
     ├─► CompositionRules        (COMP_*) — advisory only
     │
     ├─► LightingRules           (LIGHT_*) — see lighting-engine.md (separate module, same contract)
     │
     └─► RulePostProcessor
             • applies hysteresis (enter/exit) and persistence counters
             • applies severity policy (shot type, mode, user intent)
             • applies confidence caps by rule class
             • sorts by (severity, confidence × persistence)
```

### 3.1 Rule evaluation order (deterministic, documented for testability)

1. `FRAME_SUBJECT_PRESENT` (gate — if it fails, everything else abstains except the light rules)
2. `ShotTypeEstimator`
3. `FRAME_SUBJECT_TOO_SMALL` / `TOO_LARGE`
4. `FRAME_HEADROOM_*`
5. `FRAME_EDGE_MARGIN`
6. `FRAME_JOINT_CROP`
7. `FRAME_SUBJECT_OFF_CENTER_EXTREME`
8. `FRAME_CAMERA_TILT` (IMU; independent of framing)
9. `COMP_EYE_LINE_PLACEMENT`, `COMP_LEAD_ROOM`
10. remaining `COMP_*` (as they become available)

Ordering matters only for the *dev screen* and for deterministic unit tests; the arbiter re-sorts.

---

## 4. Shot-type estimation (shared by framing, pose and guidance)

```
h_face = faceBBox.height               (when a face is detected)
h_body = bbox.height                   (subject bbox in normalized frame units)
feetVisible = usable(left_foot_index) && usable(right_foot_index)
hipsVisible = usable(left_hip) && usable(right_hip)

if  h_face ≥ T_headshot              → HEADSHOT
elif h_face ≥ T_close                → CLOSE_PORTRAIT
elif h_body ≥ T_full  && feetVisible → FULL_BODY
elif h_body ≥ T_threequarter         → THREE_QUARTER
elif h_body ≥ T_half                 → HALF_BODY
else                                 → UNKNOWN
```

Proposed thresholds (all `CALIBRATION_REQUIRED`, to be fitted in Phase 2 from labelled captures):
`T_headshot = 0.30`, `T_close = 0.20`, `T_full = 0.72`, `T_threequarter = 0.50`, `T_half = 0.28`.

Shot type is used to:
* select the applicable band in `FRAME_SUBJECT_TOO_SMALL/LARGE` and `FRAME_HEADROOM_*`,
* decide which pose templates are offered,
* decide which readiness criteria are required (`guidance-engine.md` §7),
* suppress irrelevant rules (no "feet cut" warning in a headshot).

**Confidence policy:** the estimator's confidence = f(bbox stability, face presence, feet visibility);
rules that depend on a low-confidence shot type abstain rather than guess.

---

## 5. Threshold policy

* User-visible text never appears here: rules emit instruction ids and the UI resolves them
  through `specs/i18n/messages.json` (vi primary, en later).
* Every **rule** numeric constant lives in a single `Thresholds` object (generated from
  `specs/rules/mvp-rules.json`), never inline in rule code. Non-rule engine constants — shot-type estimator
  cut-offs (`T_*`), guidance dwell/cooldown, readiness holds, cadences — are specified in their engine docs
  and are added to the same object as they are implemented; they carry the same `ThresholdStatus` marks.
* Each constant carries its `ThresholdStatus`. `CALIBRATION_REQUIRED` constants:
  * may be used in developer builds,
  * must be excluded from user-visible hard errors,
  * must be logged (id + value) whenever they fire, so calibration sessions can produce data.
* Hysteresis is mandatory for every threshold: `enter`, `exit = enter × (1 ± margin)`.
* Per-tier overrides (`LOW/MEDIUM/HIGH`) are allowed only to change *cadence*, not *semantics*
  (a low-end device must not give different advice — it gives less frequent advice).

---

## 6. Example rule evaluation (worked, for the implementer)

Input: full-body intent, back camera, subject fills 0.62 of the frame height, head top at y = 0.03,
eyes at y = 0.135, feet visible, camera roll 0.4°.

```
FRAME_SUBJECT_PRESENT     → evaluated, severity INFO,            "subject usable (0.91 visible)"
ShotTypeEstimator         → FULL_BODY is 0.62 < 0.72 → THREE_QUARTER (0.78 conf) ; intent FULL_BODY
                            → one-step disagreement → treat as THREE_QUARTER for bands, note intent
FRAME_SUBJECT_TOO_SMALL   → h_body 0.62 vs band [0.70, 0.95] → NUDGE "slightly small", action MOVE_CLOSER
FRAME_HEADROOM_INSUFFICIENT → headroom 0.03 vs band [0.05, 0.25] → IMPORTANT, action DECREASE_HEADROOM…
                            (i.e. increase the space above the head by pressing the camera down / stepping back)
FRAME_EDGE_MARGIN         → min margin 0.11 → PASS
FRAME_JOINT_CROP          → feet at y=0.97, no frame crossing → PASS (feet visible: no ankle cut)
FRAME_CAMERA_TILT         → 0.4° → PASS (below 1.5°)
COMP_EYE_LINE_PLACEMENT   → eye line at 0.135 (above the upper third at 0.333) → NUDGE (advisory), action ADJUST_EYE_LINE
Result: two candidate instructions (headroom IMPORTANT, subject size NUDGE, eye line NUDGE)
        → arbiter picks headroom (highest severity × confidence)  [guidance-engine.md §5]
```

Note the deliberate conflict inside this example: "slightly small" wants the camera closer, "insufficient
headroom" wants more space above the head. Both are satisfied by *stepping back slightly and pressing the
camera down*, which is exactly what a human photographer would do. This is why the engine emits
**semantic actions**, and the guidance layer is responsible for finding the compatible mapping
(`guidance-engine.md` §5.4).

---

## 7. Statefulness and determinism rules

* Engines are **pure functions** `(FrameAnalysis, EngineState) → (Results, EngineState)`.
* No clock reads inside rules — time comes from `FrameAnalysis.timestampMs`.
* No random, no I/O, no logging side effects with user data.
* Unit tests construct `FrameAnalysis` fixtures from JSON and assert exact `RuleResult` sets and exact
  hysteresis transitions (enter/exit/persistence) across frame sequences.

---

## 8. Composition features deliberately NOT in the engine

| Feature | Reason |
| --- | --- |
| Rule-of-thirds compliance score | Unsupported by evidence (`photography-errors.md` §4) |
| "Balance" / visual weight score | Requires saliency modelling; subjective |
| Aesthetic score | No licence-clean on-device model; unexplained numbers harm trust |
| Leading lines, symmetry, frame-within-frame detection | Needs scene segmentation; Phase 7+ suggestions only |
| Golden spiral overlay | No measurable target for a human subject |

---

## 9. Acceptance criteria (Phase 5)

1. Given synthetic fixtures, each rule produces the documented `measurement`, `severity`, `actor` and
   `suggestedAction`.
2. Every threshold is sourced from `Thresholds`, and a test asserts that no `CALIBRATION_REQUIRED`
   threshold is used with severity above `NUDGE` in a release configuration.
3. Rule evaluation of 10 000 synthetic frames takes < 2 ms on the JVM (proving the engine is not the
   bottleneck) and allocates no more than a documented budget per frame.
4. Hysteresis tests: a measurement oscillating around a threshold produces at most one severity
   transition per `minDwellMs`.
5. Abstention tests: rules abstain (never guess) when required landmarks are below the usability floor.
