# Test Plan — criteria, fixtures and acceptance gates

The product's risk lives in **algorithms** (does the pose matcher agree with a human? does the arbiter
avoid flicker?), not in the camera plumbing. Therefore the test strategy is inverted relative to a typical
Android app: **most tests are JVM unit tests with synthetic fixtures**, and device tests are reserved for
things that genuinely require hardware.

---

## 1. Test layers

| Layer | Where it runs | Share of tests | What it proves |
| --- | --- | --- | --- |
| **L1 — Pure logic (JVM)** | `./gradlew test` | ~70 % | geometry, rules, pose matching, guidance arbitration, temporal filters, readiness |
| **L2 — Contract / schema (JVM)** | same | ~5 % | JSON schemas, template validation, rule-table integrity, message-ID completeness |
| **L3 — Instrumented (device/emulator, no camera)** | `connectedAndroidTest` | ~10 % | Compose overlay mapping, Canvas rendering, repository/state wiring, MediaPipe tasks can initialise |
| **L4 — Camera pipeline (device only)** | manual + targeted instrumentation | ~5 % | CameraX binding, 3-use-case session, rotation/mirroring correctness on real hardware |
| **L5 — Performance (device only)** | Perfetto + Macrobenchmark | ~5 % | latency, FPS, memory, thermal, degradation ladder |
| **L6 — Human evaluation (device only)** | guided sessions | ~5 % | does the guidance actually help? does the overlay feel right? false-positive audit |

**Rule:** the emulator may be used for L3, never for L4/L5 claims. MediaPipe's GPU delegate is disabled on
the emulator, so emulator performance numbers are meaningless.

---

## 2. Fixtures

### 2.1 Synthetic landmark fixtures (the workhorse)

JSON files under `specs/fixtures/` (dev-only) with:

```jsonc
{
  "name": "pose_weight_shift_standing__subject_175cm__camera_3m",
  "frame": { "width": 480, "height": 640, "rotationDegrees": 0, "mirrored": false },
  "landmarks": [ { "id": "nose", "x": 0.51, "y": 0.18, "visibility": 0.99, "presence": 0.99 }, … ],
  "worldLandmarks": [ … ],
  "expected": {
    "shotType": "THREE_QUARTER",
    "rules": {
      "FRAME_HEADROOM_EXCESSIVE": { "evaluated": false },
      "FRAME_SUBJECT_TOO_SMALL":  { "evaluated": false },
      "POSE_ARM_TORSO_GAP_TOO_SMALL": { "evaluated": true, "side": "left" }
    },
    "guidancePrimary": "MOVE_ARM_AWAY_FROM_TORSO"
  }
}
```

Generation rules: fixtures are **synthetic geometry first** (exact expected values, including degenerate
cases: invisible limbs, out-of-frame subject, 90°-rotated body, mirrored template, oscillation sequences),
and only then real captures. Synthetic fixtures make the tests deterministic and reviewable in a diff.

### 2.2 Recorded sequences (for temporal tests)

A sequence fixture is a list of `FrameAnalysis` snapshots (or a compact landmark trace) with a scripted
story: *"subject approaches the pose → overshoots → settles → blinks → camera shakes"*. These are the tests
that catch flicker, dwell violations and premature readiness. Recorded from real devices as **derived
data only** (landmarks, statistics) — never as images, because the repository must not contain people's
photographs (privacy + size).

### 2.3 Camera-independent replay harness

A dev-only tool (`:tools:pose-authoring` or a `debug` entry point) that replays a recorded landmark trace
through the real engines and prints the guidance timeline. This is how tuning happens without a phone in
hand — and it is also how reviewers can inspect a guidance decision that a tester complained about.

---

## 3. L1 test specifications (must exist before the corresponding phase closes)

### 3.1 `:core:geometry`

| Test | Criterion |
| --- | --- |
| angle between vectors | exact values for 0°/45°/90°/180°, wrap-around at ±180° |
| interior joint angle | correct for mirrored/reflected inputs; never returns the reflex angle |
| segment-to-line distance | correct for parallel, crossing and degenerate (zero-length) segments |
| Procrustes/Kabsch 2D | recovers a known rotation within 1e-6; handles degenerate point sets by returning identity + a flag |
| normalization to SUBJECT space | round-trip: normalize(denormalize(x)) == x |
| One Euro filter | converges to a constant input; response to a unit step is monotone; no overshoot beyond spec |
| EMA + hysteresis | enter/exit latches exactly once for a slow ramp; no oscillation for a noisy signal at the boundary |
| angle wrapping in smoothing | 179° → −179° produces no jump |

### 3.2 `:core:photography`

* Every MVP rule has at least 4 fixtures: clearly-inside, clearly-outside, boundary-enter, boundary-exit.
* Rule abstention is tested explicitly (missing/short-visible landmarks → `evaluated == false` with a reason).
* `ShotTypeEstimator` boundary table.
* Threshold-source test: no `CALIBRATION_REQUIRED` threshold may be used with severity > `NUDGE` in a
  release build variant.
* Determinism: identical input → identical output (no clock, no random, no ordering nondeterminism).

### 3.3 `:core:pose`

| Test | Criterion |
| --- | --- |
| exact template match | score ≥ 0.99 and band `MATCHED` |
| translated subject | score unchanged (translation invariance) |
| scaled subject (1.4× taller) | score unchanged within 0.02 (torso-normalization invariance) |
| **body proportion change** (arms 30 % longer, legs 20 % shorter) | score drops < 0.05 (this is the key robustness test) |
| mirrored pose, `mirrorAllowed: true` | score ≥ 0.95 with the mirrored handedness chosen |
| mirrored pose, `mirrorAllowed: false` | score penalized as specified, handedness not flipped |
| global rotation +20°/+40° | 20° tolerated (score drop < 0.1); 40° penalized (clamp works, score < 0.6) |
| invisible limb | component excluded, `notEvaluated` reported, other components unaffected, coverage reported in the result |
| 60 % of the body invisible | hard gate → `POSE_UNVERIFIABLE`, no score |
| noise ±2° on all joints | score drop < 0.05 |
| noise ±10° on all joints | score drop documented (calibration target) |
| degenerate template (collinear landmarks) | template validator rejects it at load time |
| wrong pose (different family) | score < 0.45 |

### 3.4 `:core:guidance`

* One-primary invariant (property test with randomized candidates).
* Conflict table: every row of `guidance-engine.md` §5.4 is a test case.
* Dwell/cooldown/flip-guard timeline tests over scripted sequences.
* Mode test: `SELF_MODE` never emits an unactionable camera instruction.
* Localization: every emitted `messageId` exists in `values/strings.xml` **and** `values-vi/strings.xml`
  (a generated inventory test; missing keys fail the build).
* Readiness state machine: scripted stories (`settles`, `blink during countdown`, `camera shake`,
  `subject steps out of frame`) reach exactly the expected states.

### 3.5 `:core:light`

* Synthetic luma grids with known clipping fractions → exact rule outcomes.
* Region-size abstention, AE-instability abstention.
* No absolute-exposure wording anywhere (string audit test over `messageId` inventory).

---

## 4. L3/L4 device tests

| Test | Type | Criterion |
| --- | --- | --- |
| ANALYSIS→PREVIEW transform | instrumented, synthetic view sizes | a landmark drawn at a known analysis coordinate lands within 1 px of the expected preview coordinate, for: portrait/landscape, front/back camera, 4:3 and 16:9 preview |
| Front-camera mirroring | instrumented + manual | guidance "raise your left arm" highlights the arm that appears on the correct side for the user |
| Overlay rendering performance | macrobenchmark/Perfetto | 60 FPS overlay with 15 FPS perception; no jank > 16 ms in the frame timeline |
| CameraX 3-use-case session | manual on device matrix | preview + analysis + capture bind successfully; document devices that require a fallback |
| Rotation/orientation handling | manual | rotating the device does not scramble landmarks, templates or guidance direction |
| Permission flow | manual | denial/partial grants leave a usable app with a clear explanation |
| MediaPipe init failure path | instrumented (forced failure) | app degrades to a clear "not supported" state, never crashes |

---

## 5. L5 performance protocol

Per `performance-strategy.md` §7. Acceptance gates G-P1…G-P6 apply. Every measurement goes into
`docs/phase-status.md` with the device table.

**Explicitly required:** the Phase-2 experiment comparing 480p vs 720p analysis input for a full-body
subject at ~3–4 m, measuring both detection success rate and pose accuracy (against human judgement on a
locally captured set). The outcome decides the default resolution per tier.

---

## 6. L6 human evaluation (the one that matters most)

Run with 3–5 people on 2–3 real devices, scripted scenarios:

| Scenario | What we observe | Failure signal |
| --- | --- | --- |
| Novice asks a friend to take a full-body photo | do they follow the instruction? does the photo improve? | instruction ignored/confusing; photo no better |
| Subject is told to match a pose template | does the guide communicate the pose? | subject cannot tell what to change; overlay misleads |
| Deliberate rule-of-thirds/centered/creative composition | does the app nag? | false positive on a good photo → **highest-severity bug** |
| Backlit scene | is the lighting advice correct and actionable? | advice contradicts what the user sees |
| Auto-capture armed | does the shutter fire at a good moment? | early capture, missed capture, repeated capture |

Metrics recorded: instruction comprehension (yes/no), number of instructions shown before a good photo,
false-positive count (app complained about a photo a photographer called good), and subjective trust (1–5).
**A false positive that annoys a skilled user is treated as a P1 bug**, because trust is the product.

---

## 7. Device matrix

| Class | Examples (or equivalent available locally) | Purpose |
| --- | --- | --- |
| LOW | 4 GB, Snapdragon 6xx-class | minimum viable experience, degradation ladder |
| MEDIUM | 6–8 GB, Snapdragon 7-series / Dimensity 7xxx | primary target |
| HIGH | 8 GB+, flagship | best experience, headroom features |
| Odd but important | a device with a poor MediaPipe GPU path (falls back to CPU) | verify delegate handling |
| Android versions | 7.0 (minSdk 24), 10, 13, 14+ | platform API behaviour |
| Camera quirks | a device where 3-use-case sessions are limited | fallback path |

Devices used must be recorded with exact model + Android build in each phase report.

---

## 8. Definition of Done for a phase (test perspective)

A phase is accepted only if:

1. All L1/L2 tests for its modules pass, with the new fixtures committed.
2. The instrumented tests it touches pass on at least one LOW/MEDIUM/HIGH device.
3. Its acceptance gates (`performance-strategy.md` §9 and the phase section of `roadmap.md`) are measured
   and recorded with device details.
4. No new false positive was introduced in the L6 scenarios for the features it added (or the false
   positive is documented as a known limitation and threshold-tagged `CALIBRATION_REQUIRED`).

---

## 9. Bug severity policy

| Severity | Examples | Response |
| --- | --- | --- |
| P0 | crash, data loss, privacy leak, camera frame uploaded anywhere | immediate fix, block phase |
| P1 | false-positive guidance on a good photo; guidance in the wrong direction; overlay mirrored wrongly; readiness fires early | block phase |
| P2 | jitter/flicker; wrong measurement without user-visible harm; degraded performance by > 30 % | fix within phase |
| P3 | cosmetic overlay issues; wording awkwardness; dev-screen issues | fix before release |

---

## 10. What must never be claimed without evidence

* "Works on all Android phones" — we claim a tested device list and a tier policy.
* "Real-time" — we claim measured FPS per tier.
* "Accurate pose detection" — we claim a measured match-score agreement with human judgement on our fixture
  set, plus the model card's published accuracy for the model itself.
* "Detects posing mistakes" — we claim specific, enumerated, calibrated rules with known false-positive
  behaviour; anything uncalibrated stays in the developer build.
