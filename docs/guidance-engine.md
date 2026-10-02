# Guidance Engine — instruction model, arbitration, temporal stability, readiness

Module: `:core:guidance` (pure Kotlin).
Upstream: `:core:photography` (composition/lighting `RuleResult`s) and `:core:pose`
(`PoseMatchReport`, `PoseCorrection`s).
Downstream: UI (message IDs + overlay cues), capture controller (readiness).

This is the module that decides **what the app says, to whom, and when** — the difference between a helpful
assistant and an annoying one.

---

## 1. Instruction model

Authoritative schema: `specs/schemas/guidance-instruction.schema.json`.

```kotlin
data class GuidanceInstruction(
    val id: InstructionId,               // stable id, e.g. MOVE_CAMERA_LEFT (semantic action)
    val messageId: MessageId,            // localization key resolved by the UI layer
    val params: Map<String, Float>,      // numbers interpolated into the message (magnitude, side…)
    val actor: Actor,                    // PHOTOGRAPHER / SUBJECT / BOTH
    val direction: Direction?,           // LEFT/RIGHT/UP/DOWN/CLOSER/FARTHER/CW/CCW/…
    val magnitude: Float,                // 0..1 normalized amount (not pixels!)
    val priority: Int,                   // static base priority by category
    val severity: Severity,
    val confidence: Float,
    val source: InstructionSource,       // COMPOSITION / POSE / LIGHTING / READINESS / SYSTEM
    val ruleIds: List<RuleId>,           // provenance for the dev screen
    val persistenceFrames: Int,
    val firstSeenMs: Long
)
```

Semantic action IDs (internal, stable, no text):

```
MOVE_CAMERA_LEFT / RIGHT / UP / DOWN
MOVE_CLOSER / MOVE_FARTHER
ZOOM_IN / ZOOM_OUT                     (advisory in MVP, no programmatic zoom)
ROTATE_CAMERA_CW / CCW                 (device rotation in hand)
LEVEL_CAMERA
REFRAME                                (generic: recompose, used when the fix is not a single axis)
INCREASE_HEADROOM / DECREASE_HEADROOM
ADJUST_EYE_LINE
SUBJECT_MOVE_LEFT / RIGHT / INWARD
SUBJECT_STEP_BACK / STEP_FORWARD
SUBJECT_TURN_LEFT / RIGHT
SUBJECT_SHIFT_WEIGHT_LEFT / RIGHT
SUBJECT_WIDEN_STANCE / NARROW_STANCE
RAISE_LEFT_ARM / RAISE_RIGHT_ARM / LOWER_LEFT_ARM / LOWER_RIGHT_ARM
MOVE_ARM_AWAY_FROM_TORSO (side param)
HAND_ON_HIP / HAND_IN_POCKET / HAND_AWAY_FROM_FACE
TURN_HEAD_LEFT / RIGHT / TILT_HEAD_LEFT / RIGHT / CHIN_DOWN / CHIN_UP
LOOK_AT_CAMERA / LOOK_AWAY
TURN_ON_LIGHT / MOVE_TO_BETTER_LIGHT / MOVE_OUT_OF_BACKLIGHT
HOLD_STILL / WAIT
```

The engine emits `InstructionId` + `messageId` + `params`; `:app` maps `messageId` to
`strings.xml`/`values-vi/strings.xml`. **No literal text anywhere in `:core:guidance`.**

---

## 2. Actors and modes

| Mode | Who holds the phone | Who can act on camera instructions | Who can act on subject instructions |
| --- | --- | --- | --- |
| `PHOTOGRAPHER_MODE` | another person | that person | the subject |
| `SELF_MODE` | nobody (tripod/stand) or the subject | the subject (they move the phone/prop) | the subject |
| `HYBRID_UNKNOWN` | not yet known | — | — |

Product decision (Phase 0 recommendation, needs human confirmation): on first launch ask once
("Ai đang cầm máy ảnh?"), remember it, allow switching.

Consequences for the engine:

* In `PHOTOGRAPHER_MODE`, both actors are available → at most **one instruction per actor**, max two total.
* In `SELF_MODE`, camera instructions are meaningless unless the subject physically moves the phone; the
  engine therefore **re-expresses** camera actions as subject actions where possible:
  `MOVE_CLOSER` → `SUBJECT_STEP_FORWARD`; `MOVE_CAMERA_DOWN` → `SUBJECT_STAND_TALLER`/`SUBJECT_STEP_BACK`
  (whichever is compatible), and suppresses the rest with a `modeNotActionable` reason.
* In `SELF_MODE` with a tripod, the app may also use **audio cues** (`LATER`) and a countdown
  (§8) rather than text the subject cannot read.

---

## 3. Candidate generation

Each engine emits `GuidanceCandidate`s (not final instructions). A candidate is an instruction plus the
evidence that produced it. Generation rules:

* composition candidates come from `RuleResult.suggestedAction` where `confidence ≥ 0.5`
* pose candidates come from the top-k `PoseCorrection`s (k=2)
* lighting candidates come from `LightReport` (highest severity first)
* readiness candidates (e.g. `HOLD_STILL`) come from the readiness machine

Every candidate must carry `ruleIds`, so the UI/dev screen can always answer "why am I being told this?".

---

## 4. Scoring

```
baseScore(c)      = priorityBase(c.source)            // see table
severityFactor    = { BLOCKING: 1.6, IMPORTANT: 1.3, NUDGE: 1.0, INFO: 0.4 }
confidenceFactor  = c.confidence                       // 0..1
persistenceBonus  = min(1.35, 1 + 0.05 · c.persistenceFrames)
modeFactor        = actionableInMode(c) ? 1.0 : 0.0     // hard gate for SELF_MODE camera actions
conflictPenalty   = c flipped against the currently displayed instruction within cooldown → ×0.5

score(c) = baseScore · severityFactor · confidenceFactor · persistenceBonus
           · modeFactor · conflictPenalty
```

| Source | `priorityBase` | Rationale |
| --- | --- | --- |
| `READINESS` | 100 | "hold still" or "almost there" is contextual and must win while counting down |
| `LIGHTING` | 80 | lighting failures invalidate everything else (a black or blown frame cannot be judged) |
| `COMPOSITION` (framing subset: size, headroom, crop, margin) | 70 | high value, objective |
| `POSE` | 60 | high value, but the subject needs time to move and repeated nagging is counter-productive |
| `COMPOSITION` (`COMP_*` advisory) | 40 | never pre-empts anything |
| `SYSTEM` (permissions, capability warnings) | 90 | rare, must be seen |

---

## 5. Arbitration

### 5.1 The rule of one

Only **one primary instruction** is shown at a time. Optionally one **secondary** instruction if (and only
if) it targets a *different actor* and its score is within 20 % of the primary. Never three.

### 5.2 Selection algorithm

```
1. drop candidates with score < MIN_SCORE (0.25) or confidence < 0.5
2. group candidates by "resolution compatibility" (see 5.4) and merge compatible ones
3. keep the current primary if it is still valid and its dwell time has not elapsed
   (unless a candidate with severity BLOCKING appears or the primary's cause vanished)
4. otherwise pick argmax(score)
5. attach a secondary only if actor differs and score ≥ 0.8 × primary score
```

### 5.3 Dwell, cooldown and anti-flicker

| Mechanism | Default | Purpose |
| --- | --- | --- |
| `minDwellMs` | 900 ms (`CALIBRATION_REQUIRED`) | an instruction is not replaced before the user can read it |
| `cooldownMs` after a resolved instruction | 2500 ms | prevents flip-flop between two near-equal candidates |
| `repeatCooldownMs` | 4000 ms | the same instruction is not repeated immediately after it is resolved |
| flip guard | an instruction that reverses the previous one within 1.5 s is suppressed unless `BLOCKING` | prevents "move left / move right" oscillation |
| pending-praise window | 600 ms | after the criterion is satisfied, show "good" before switching topics |

### 5.4 Resolution compatibility (how conflicts are actually solved)

Two candidates are **compatible** when a single physical action satisfies both. The engine knows a small
curated table (this is product knowledge, not ML):

| A | B | Merged instruction |
| --- | --- | --- |
| `MOVE_CLOSER` | `INCREASE_HEADROOM` | `STEP_BACK_AND_TILT_DOWN` → rendered as "move closer, camera slightly lower"? → **prefer** the action that solves both: `MOVE_CAMERA_DOWN` + slight step in — implemented as `COMPOSITE(move closer; lower camera)` with one message id |
| `MOVE_CLOSER` | `DECREASE_HEADROOM` | `MOVE_CLOSER` (framing tighter also reduces headroom) |
| `MOVE_FARTHER` | `INCREASE_HEADROOM` | `MOVE_FARTHER` |
| `MOVE_FARTHER` | `DECREASE_HEADROOM` | `MOVE_CAMERA_DOWN` (stepping back increases headroom, so prefer the vertical fix) |
| `ADJUST_EYE_LINE` (down) | `INCREASE_HEADROOM` | same action → merge |
| `SUBJECT_TURN_LEFT` | `TURN_HEAD_RIGHT` | they are different components; allowed together (torso vs head) only if template intent says so, otherwise the higher-scoring one wins |
| two `POSE` candidates, same limb | — | keep the larger magnitude; drop the other (never ask two things of one limb) |

If no compatible mapping exists and both are `IMPORTANT`, the arbiter picks the one with the higher score
and **suppresses the other for `cooldownMs`** — it will be offered again after that, once the first is
resolved. Sequencing, not simultaneity, is how real photographers teach.

### 5.5 Explanation and trust

Every instruction retains `ruleIds`, `measurement`s and `confidence`. The UI may expose a small "?" that
shows a plain-language reason (localized). This is cheap and dramatically increases trust in a
directive app.

---

## 6. Temporal stability

Applied in this order; each layer has unit tests.

### 6.1 Layer 1 — measurement smoothing

* **Landmarks:** One Euro filter per coordinate (`x`, `y`, `z`, `visibility`, `presence`), parameters
  `minCutoff ≈ 1.0 Hz`, `beta ≈ 0.007` initially, tuned per tier (`CALIBRATION_REQUIRED`).
  Rationale from the literature: fixed low-pass filters trade jitter against lag; the One Euro filter
  adapts its cutoff to speed, which is exactly right for a subject who is momentarily still.
* **Scalars** (scores, ratios, roll, luma): EMA with per-metric alpha at 30 fps:
  `poseScore α = 0.25`, `headroom α = 0.2`, `luma α = 0.3` (fast enough for lighting changes),
  all `CALIBRATION_REQUIRED`.
* **Never smooth across a scene cut or a subject-identity change** — reset the filter on
  `trackId` change / `subjectCount` change / > 500 ms gap.

### 6.2 Layer 2 — decision hysteresis

* Every threshold has `enter` / `exit` values (exit is inside the good band).
* Discrete states use N-of-M voting: `N = 3` of `M = 5` frames (`CALIBRATION_REQUIRED`).
* Handedness / mirroring decisions latch with a 250 ms lock to avoid flip-flopping on symmetric poses.
* Readiness uses a *duration gate*, not a frame count: satisfied criteria must hold continuously for
  `readyHoldMs` (§7).

### 6.3 Layer 3 — guidance dwell

As in §5.3.

### 6.4 What is smoothed, and what is not

| Signal | Smoothed? | Why |
| --- | --- | --- |
| landmarks | yes (One Euro) | jitter directly distorts the overlay |
| bounding boxes | yes (derived from landmarks) | prevents the frame from pulsing |
| pose score | yes (EMA) | prevents score flicker at the band boundary |
| composition measurements | yes (EMA) | same |
| luma statistics | lightly (EMA 0.3) | must still react within ~200 ms when light changes |
| head orientation | yes (EMA + wrap-aware) | angle wrapping bugs are a classic; test 179°→−179° |
| capture readiness | **no smoothing of the decision** — only duration gating | a readiness decision must never be "partially true" |
| guidance choice | dwell + hysteresis | see above |

---

## 7. Readiness state machine

```
        ┌────────────┐   criteria partially met      ┌─────────────┐
        │ NOT_READY  │ ────────────────────────────► │ NEAR_READY  │
        └────────────┘ ◄──────────────────────────── └─────────────┘
              ▲              criteria lost                 │
              │                                              │ all required criteria met
              │                                              ▼  and stable for readyHoldMs
              │                                        ┌───────────┐
              └───────────── cancel ────────────────  │  READY    │
                                                      └───────────┘
                                                            │ auto-capture enabled
                                                            ▼
                                                      ┌───────────┐   countdown 3-2-1
                                                      │ COUNTDOWN │ ──── capture ───► CAPTURED
                                                      └───────────┘
```

### 7.1 Criteria classification

| Criterion | Class | Notes |
| --- | --- | --- |
| subject present & inside margin | **required** | always |
| pose score ≥ band (or no template active) | **required** when a template is active | `MATCHED` → `readyHoldMs = 400`; `CLOSE` → `readyHoldMs = 1200` (`CALIBRATION_REQUIRED`) |
| subject stable (landmark velocity below threshold) | **required** | + IMU camera stability |
| camera stable (IMU variance + landmark motion) | **required** | for auto-capture; manual capture only warns |
| framing: subject size within band, edge margins satisfied | **required** | shot-type aware |
| headroom within band | **optional** (recommended) | never blocks a manual capture; may block auto-capture in strict mode |
| face visible & unobstructed (portrait shot types) | **required** when the shot type implies a visible face | unobstructed = no hand over the face, no subject occlusion |
| lighting: severe face underexposure / blown highlights | **blocking** if extreme | thresholds `CALIBRATION_REQUIRED`; "extreme" is deliberately conservative |
| composition advisory rules (`COMP_*`) | **never gate** | advisory by policy |
| aesthetic score | does not exist | — |

### 7.2 Duration and stability

* Required criteria must hold **continuously** for `readyHoldMs`. Any violation resets the timer
  (with a small grace: a single-frame dropout of one criterion does not reset if it recovers within 100 ms).
* `READY` is announced (haptic + short label) but does **not** capture by itself.
* Auto-capture (if enabled): countdown 3 s, visible and cancellable; each countdown second re-validates the
  hard criteria; a violation cancels the countdown and returns to `NEAR_READY`.
* Cooldown after capture: 2 s of `NOT_READY` suppression so the assistant does not immediately nag the
  user to take another shot.

### 7.3 Why not "capture when one frame is good"

Because single frames lie: a blink, a half-step, a jitter spike, or a transient AE/exposure adjustment can
make a frame look perfect and the next unusable. The duration gate plus stability criteria is the whole
point of the readiness machine.

---

## 8. Outputs to the UI

```kotlin
data class GuidanceState(
    val mode: GuidanceMode,                 // PHOTOGRAPHER / SELF
    val primary: GuidanceInstruction?,
    val secondary: GuidanceInstruction?,
    val readiness: ReadinessState,          // NOT_READY / NEAR_READY / READY / COUNTDOWN(n) / CAPTURED
    val readinessReasons: List<ReadinessReason>,  // for the dev screen and the "why?" sheet
    val poseScore: Float?,                  // smoothed
    val poseBands: Map<PoseComponent, Band>,// for per-component guide emphasis
    val composition: CompositionSummary?,
    val light: LightReportSummary?,
    val quality: AnalysisQuality            // gives the UI the right to show a "low confidence" hint
)
```

UI contract rules:

* The UI renders **exactly** `primary` (and `secondary` if present). It never composes its own advice.
* The dashed guide uses `poseBands` to decide which components are drawn solid vs dashed.
* When `quality` is poor, the UI shows a neutral state ("đang nhận diện…" / "detecting…") rather than
  stale or false guidance.
* Vietnamese strings live in `values-vi/`; `guidance.*` keys are stable and versioned with this document.

---

## 9. Acceptance criteria (Phase 4–5)

1. **No flicker test:** feeding a synthetic sequence that oscillates a measurement across a threshold at
   30 Hz produces at most one instruction change per `minDwellMs`.
2. **Actor test:** in `SELF_MODE`, no camera-movement instruction is ever emitted without a compatible
   subject-action mapping.
3. **One-primary invariant:** a property test over randomized candidate sets asserts
   `primary != null → secondary.actor != primary.actor`.
4. **Conflict table test:** each row of §5.4 is a test case asserting the merged/preferred outcome.
5. **Determinism test:** the same `(snapshot, state)` input yields byte-identical outputs (no hidden time
   or randomness).
6. **Readiness test:** a scripted scenario (subject poses → stabilizes → blink) only reaches `READY` after
   the required hold, and cancels correctly during countdown.
7. **Latency test:** arbitration of a realistic candidate set (≤ 12 candidates) < 0.5 ms on the JVM.
