# Architecture — AI Photographer

Status: **Phases 0 and 1 accepted; Phase 2 authorized** (owner authorization 2026-10-06).
Audience: Codex Local (implementer), future reviewers.

---

## 1. Goals and constraints

### Functional goal

Given a live camera feed containing one or more people, continuously answer four questions and act on them:

1. **What is in frame?** (how many people, where, at what scale, how visible)
2. **What is the pose?** (body geometry, head orientation, gaze, stability)
3. **How good is the photograph right now?** (framing, composition, lighting, in relation to a chosen pose template)
4. **What single change improves it most?** (one primary instruction, plus at most one secondary)

### Hard constraints

| Constraint | Consequence for architecture |
| --- | --- |
| Local-first, offline-first | No network layer in the core. No `INTERNET` permission in MVP. |
| $0 cost | Only Apache-2.0 / MIT / permissive or explicitly redistribution-safe models. |
| Privacy | Analysis frames are transient; never persisted unless the user presses capture. |
| Real-time feel | Guidance must update within a human-perceptible "instant"; no flicker (see §9). |
| Low-end devices must work | Tiered/downgradable pipeline (§8). Never assume 30 FPS inference. |
| Future multi-person | Data model carries a *list* of subjects from day one; only inference is single-person in v1. |
| Localizable | All user-facing text via message IDs; `vi` default, `en` fallback. |

### Non-goals (MVP)

Scene understanding, aesthetic scoring, VLM/LLM, group posing, cloud sync, face recognition,
automatic lens/exposure control, video recording, editing.

---

## 2. Module graph

Single Gradle project, multi-module, clean boundaries so that **pure logic is testable on the JVM**
(no emulator required for the majority of the algorithm suite).

```
:app                         Android application
  └─ Compose UI, navigation, DI wiring, permission flow, resource strings (vi/en)

:core:model                  Pure Kotlin data types (no Android deps)
  └─ Landmark, Subject, FrameAnalysis, PoseTemplate, RuleResult, GuidanceInstruction,
     ShotType, Severity, CapabilityReport, enums + value classes

:core:geometry               Pure Kotlin math
  └─ vectors, angles, line/segment distance, polygon ops, Procrustes alignment,
     normalization, coordinate-space transforms, smoothing filters (One Euro, EMA, hysteresis)

:core:photography            Deterministic photography rule engine (pure)
  └─ framing/framing rules, composition rules, shot-type estimation, rule registry, scoring

:core:pose                    Pose domain (pure)
  └─ template loading/validation, matching (per-segment scoring), correction events, guide rendering geometry

:core:guidance                Guidance domain (pure)
  └─ candidate generation, arbitration, dwell/hysteresis, readiness state machine, message-ID emission

:core:light                  Lighting analysis (pure, operates on a luma buffer + subject mask)
  └─ exposure stats, clipping, backlight detection, face-vs-background balance

:perception:api              Perception interfaces + confidence semantics (pure)
  └─ interface PoseSource, FaceSource, HandSource, SceneSource, SubjectTracker, ImuSource

:perception:mediapipe        MediaPipe Tasks implementation (Android)
  └─ PoseLandmarker, FaceLandmarker, optional HandLandmarker wrappers, delegate selection

:perception:image            Image statistics producer (Android)
  └─ Y-plane extraction from ImageProxy, downscale, mask generation, luma buffer

:feature:camera              CameraX: Preview + ImageAnalysis + ImageCapture, frame router
:feature:guide               Compose overlay: dashed pose guide, skeleton, skeletons-to-view mapping
:feature:poselib             Pose template browsing/selection (Phase 6)
:feature:settings            Settings, capability report, developer/perf screen
:benchmark                   Macrobenchmark + on-device stage-latency harness (Phase 10)
:tools:pose-authoring        JVM CLI: validate/preview/normalize pose template JSON (dev-only)
```

Rules of the graph (enforced by module dependencies):

* `core:*` never depends on `perception:*`, `feature:*`, or the Android SDK.
* `perception:*` never depends on `feature:*`.
* `app` is the only module that knows about all of them.
* Anything that needs a `Context` lives in `perception:*`, `feature:*`, or `app`.

### 2.1 Phasing: which modules exist when (owner review, 2026-10-03)

Creating fifteen Gradle modules on day one buys nothing and costs build complexity, review noise and
dependency-graph churn. The graph above is the **target**, not the Phase 1 checklist. Modules are created
when their first real dependency exists:

| Phase | Modules created | Why this is the minimum |
| --- | --- | --- |
| **1** | `:app`, `:core:model`, `:core:geometry`, `:feature:camera` | Camera session, pure snapshot types, independently tested geometry, and camera-owned Y-plane producer. The brief §2.1 allows folding image production into the camera module; ADR-014 records this resolution of the inconsistent image-module names. |
| **2** | `:perception:api`, `:perception:mediapipe`, `:core:photography` | The interfaces and the MediaPipe implementation appear together with the first landmark source; the framing rules (subject presence/size) land with them. Splitting `api` from `mediapipe` exists to keep the Android dependency out of the interfaces. |
| **3** | `:core:pose`, `:feature:guide`, `:tools:pose-authoring` | The template loader/matcher domain and the overlay UI appear with the dashed guide. The authoring tool ships with the first templates that need validating. |
| **4–5** | `:core:guidance`, `:core:light`, `:feature:settings`*, `:feature:poselib` | Guidance/readiness and the lighting engine are their own pure domains; settings hosts the mode switch and the capability report UI; the pose library UI arrives with the library. |
| **6+** | `:feature:poselib` (if not earlier), `:benchmark` | Macrobenchmark only becomes meaningful when there is enough pipeline to measure (Phase 6+ / Phase 10). |
| **never in MVP** | `:perception:image` as a *separate* module (fold the Y-plane producer into `:perception:api`'s implementation until it needs to be swapped), `:tools:*` beyond the authoring tool | Avoid a module whose only consumer is itself. |

Two guards replace "create everything now":

1. **A dependency-rule test from Phase 1** (a JVM test enumerating module dependencies) so the graph cannot
   drift while it is still small — this is what actually protects the architecture.
2. **A written rule in `AGENTS.md`:** a new module is created when a new boundary or a new dependency
   direction is needed, and the phase brief must say which module it adds; "we will need it later" is not a
   reason (the ETA of a folder is zero).

Consequence for Phase 1: the brief's module list is the table above, not the full graph.

> Rationale: pose matching, composition rules, guidance arbitration and readiness logic are where the
> product risk lives. They must be unit-testable as pure functions with synthetic skeletons — no camera,
> no emulator, no flakiness.

---

## 3. Runtime data flow

```
 CameraX ImageAnalysis (YUV_420_888, 640x480 default; 1280x720 in "full-body" profile)
        │  (rotationDegrees, timestamp, Y/U/V planes, sensor orientation, mirror flag)
        ▼
 FrameRouter (single background executor, backpressure = drop oldest, never queue more than 2)
        ├──────────────► LumaBuffer producer  (Y plane → 64x64 luma grid; ~0.2 ms)
        │                        │
        │                        ▼
        │                 :core:light  → LightReport        (2 Hz)
        │
        ├──────────────► RgbFrame producer (packed ARGB, rotated) → owned RGB byte buffer → MediaPipe
        │                        │
        │                        ├── PoseLandmarker  (LIVE_STREAM, numPoses=1)  → 15-30 Hz
        │                        ├── FaceLandmarker  (LIVE_STREAM, numFaces=1)  → 5-15 Hz adaptive
        │                        └── HandLandmarker  (on demand only)           → LATER
        │
        ▼
 LandmarkFilter (One Euro per coordinate, per landmark; visibility/presence gating)
        ▼
 SubjectAssembler  →  List<Subject> (+trackId, bbox, visibility summary, world landmarks)
        ▼
 FrameAnalysis (immutable snapshot: subjects, luma stats, device orientation, timestamps,
                frame geometry, capability/perf state)
        ▼
 ┌──────────────────────────┬───────────────────────────────┬──────────────────────────┐
 │ :core:pose               │ :core:photography             │ :core:light              │
 │  PoseMatchReport         │  RuleResults[]                │  LightReport             │
 └──────────────────────────┴───────────────────────────────┴──────────────────────────┘
        ▼
 :core:guidance  →  GuidanceCandidates → Arbiter → GuidanceState (primary + secondary)
                 →  ReadinessState (READY / NEAR / NOT_READY + blocking reasons)
        ▼
 UI (Compose): dashed guide overlay, one primary instruction banner, subject skeleton,
               readiness ring, capture button / auto-capture countdown
```

Everything downstream of `FrameAnalysis` is a **pure function of a snapshot plus the previous state**.
That is what makes the system deterministic, testable and debuggable.

---

## 4. The canonical snapshot: `FrameAnalysis`

`FrameAnalysis` is the single contract between perception and analysis. Design rules:

* **Immutable**, plain Kotlin data classes (no Android types, no Bitmap).
* Coordinate fields are **always in normalized image space** of the *analysis frame* — see §5.
* Includes everything decision logic is allowed to depend on. If it is not in `FrameAnalysis`,
  the engines may not use it.
* Carries explicit **quality metadata**: per-landmark `visibility`, `presence`, per-source `ageMs`,
  and a `stale` flag so engines can refuse to act on old data.

Sketch (authoritative version: `specs/schemas/frame-analysis.schema.json`):

```kotlin
data class FrameAnalysis(
    val timestampMs: Long,
    val frame: FrameGeometry,              // width, height, rotationDegrees, isMirrored, aspectFill info
    val device: DeviceState,               // gravity vector, rollDegrees, imuStability, thermalState, fps
    val subjects: List<Subject>,           // ordered by prominence (largest/most-visible first)
    val light: LightReport?,               // null when not computed this cycle
    val scene: SceneReport?,               // null in MVP
    val quality: AnalysisQuality,          // per-source latency, staleness, dropped frames
)

data class Subject(
    val trackId: Int,
    val landmarks: List<Landmark>,         // 33 BlazePose landmarks, normalized to image space
    val worldLandmarks: List<Landmark>?,   // metres-ish, hip origin — NOT metric-accurate (see ai-models.md)
    val face: FaceObservation?,            // 478 landmarks or null; head pose; iris
    val bbox: BBox,                        // normalized
    val visibleFraction: Float,            // share of expected landmarks with visibility >= 0.5
    val shotType: ShotType,                // HEADSHOT / CLOSE_PORTRAIT / HALF_BODY / THREE_QUARTER / FULL_BODY / UNKNOWN
    val stability: StabilityReport         // landmark motion energy, EMA-smoothed
)
```

---

## 5. Coordinate-space contract (a top source of bugs — read carefully)

There are **five** spaces in the system. Confusing them produces overlays that are offset, mirrored,
or rotated on some devices but not others. Every transform between them must exist in
`:core:geometry` as a tested function.

| Space | Definition | Used by |
| --- | --- | --- |
| `SENSOR` | Raw camera buffer orientation (width/height as delivered by ImageProxy) | CameraX, image stats |
| `ANALYSIS` | Sensor buffer rotated to upright by `rotationDegrees`; **not mirrored**; coordinates normalized `0..1` with `y` down | All engines, MediaPipe input/output |
| `PREVIEW` | What the user sees: upright, aspect-filled/cropped to the view, **mirrored for the front camera** | Overlay drawing |
| `SUBJECT` | Person-local: origin at hip midpoint, `y` down, scale = torso length 1.0 | Pose templates, matching |
| `WORLD` | 3D, hip origin, metres-ish (BlazePose world landmarks; z is *not* metric — see `ai-models.md`) | Foreshortening estimates, orientation inference |

Rules:

1. `ANALYSIS` is the **only** space in which measurements are defined. A rule never receives `PREVIEW`.
2. The overlay maps `ANALYSIS → PREVIEW` with one function that accounts for: rotation, aspect-fill crop,
   front-camera mirroring, and view padding. It is unit-tested with synthetic cases
   (portrait/landscape, front/back, 4:3 and 16:9).
3. **Anatomical vs spatial sides.** Landmark ids are anatomical (`left_shoulder` = the subject's left).
   Spatial left/right in the image must be computed, never assumed. Templates store anatomical ids only.
4. Front-camera mirroring flips *guidance semantics*: "move the camera left" for a selfie preview is the
   opposite of the physical direction unless the mirror flag is handled. The guidance layer emits
   **world-physical** directions and the UI applies mirroring (§`guidance-engine.md`).
5. Rotating the device must not invalidate a template: the template lives in `SUBJECT` space, the frame
   is in `ANALYSIS` space, and roll is a separate term in the match score.

---

## 6. Threading and backpressure model

| Stage | Thread/Executor | Cadence | Notes |
| --- | --- | --- | --- |
| CameraX ImageAnalysis | CameraX analyzer executor (single) | camera FPS (can be capped, see below) | `STRATEGY_KEEP_ONLY_LATEST` |
| FrameRouter | dedicated single thread | per frame | Decides what to compute this frame (cadence scheduler) |
| LumaBuffer | same as router (cheap) | 2 Hz | Y plane only, no RGB conversion |
| RGB conversion | dedicated thread | per inference | Reused packed ARGB array and task-owned direct RGB byte buffer; rotation done once |
| MediaPipe Pose | MediaPipe's own LIVE_STREAM thread | 15–30 Hz (target) | Result callback → filter → assembler |
| MediaPipe Face | MediaPipe's own LIVE_STREAM thread, may share pose results | 5–15 Hz adaptive | Only while a subject is tracked |
| Engines (`core:*`) | engine executor (single) | per new snapshot | Pure, allocation-light (reuse buffers) |
| UI | main thread via `StateFlow` | render rate | Only consumes immutable state |

Backpressure policy:

* `ImageAnalysis.setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)`.
* A frame is skipped if the previous inference for the same source has not completed
  (per-source "in-flight" flags). Never build a queue.
* If `quality.droppedFrames` exceeds a threshold, the scheduler degrades cadence (see §8).

**Never** run inference on the main thread. **Never** mutate Compose state from a MediaPipe callback —
bridge through a `Channel` → `Flow` → `StateFlow`.

---

## 7. Perception interfaces (`:perception:api`)

The engines must not know that MediaPipe exists. This keeps the "alternatives" from `ai-models.md`
swappable (e.g. RTMPose for multi-person later) and keeps unit tests free of Android.

```kotlin
interface PoseSource {
    val capability: SourceCapability          // maxPoses, supportsWorldLandmarks, supportsSegmentation
    fun start(config: PoseSourceConfig): Flow<PoseFrameResult>
    fun stop()
}
interface FaceSource { /* 478 landmarks, blendshapes off, head pose matrix on */ }
interface HandSource { /* 21 landmarks per hand, on-demand */ }
interface ImuSource  { /* gravity/roll, rotation-vector, stability windows */ }
interface ImageStatsSource { /* luma grid + subject mask → LightReport */ }
interface SceneSource { /* LATER: zero-shot scene tags */ }
```

Confidence semantics (must be documented in the interface KDoc, since MediaPipe's numbers are
model-specific and not calibrated probabilities):

* `visibility ∈ [0,1]` — model score: the keypoint is in frame and not occluded. **Not** a calibrated
  probability.
* `presence ∈ [0,1]` — model score: the keypoint is in frame (regardless of occlusion).
* These are consumed through **one** mapping, defined once and normatively in `pose-system.md` §4.1.1:
  `g = min(visibility, presence)` (or the single reported channel), then a hard floor `gate = 0.5`
  (`CALIBRATION_REQUIRED`) below which `usable U = 0`, else `U = (g − gate) / (1 − gate)`.
  There is deliberately **no sigmoid**: re-shaping an already-normalized score compresses the top of the
  range and gives badly occluded landmarks too much weight.
* **A landmark with `U = 0` may not contribute to a measurement** — no angles, no directions, no coverage
  weight. It is reported as *unknown*, not as a bad value, so rules can abstain instead of guessing. A
  landmark whose channels were never reported is MARGINAL and flagged (`unknown = true`); unknown must
  never be scored as failure. Coverage gating and aggregation are specified in the same section, and
  `test-plan.md` §3.1 pins the boundary behaviour at 0.0 / low / medium / high confidence.
* `pose-system.md` §4.1.1 is the single source of truth for this math: engines must not re-derive it.

---

## 8. Performance and degradation architecture

Full detail: `docs/performance-strategy.md`. Architectural requirements:

* Every stage is **individually schedulable** (start/stop, cadence, resolution) at runtime.
* A `CapabilityReport` (device tier, delegate availability, measured latency EMA) is produced at startup
  and continuously updated; the `FrameRouter` uses it to select one of three profiles
  (`LOW / MEDIUM / HIGH`) and to downgrade automatically on thermal throttling, memory pressure, or
  sustained frame drops.
* GPU delegate is used **only** if `CapabilityReport` says it is available and the app is not on an
  emulator (MediaPipe's GPU delegate is disabled on the Android emulator).
* Degradation order (cheapest to drop first): scene classifier → hands → face cadence → pose resolution
  (full→lite model) → pose cadence → analysis resolution.
* Guidance quality must degrade gracefully: at LOW tier the app still produces framing + pose guidance
  at a lower update rate, never a broken or flickering UI.

---

## 9. Temporal architecture (why the UI will not flicker)

Three layers of stability, all in `:core:geometry` + `:core:guidance`:

1. **Measurement smoothing** — One Euro filter per landmark coordinate and per scalar score; adaptive
   cutoff = low jitter when still, low lag when moving (see `guidance-engine.md` §Temporal).
2. **Decision hysteresis** — every rule threshold has an *enter* and an *exit* value
   (e.g. headroom "excessive" enters at `T_hi` and exits at `T_hi × 0.9`), plus N-of-M frame voting for
   discrete states.
3. **Guidance dwell** — a primary instruction cannot be replaced until it has been displayed for
   `minDwellMs` (default 900 ms, `CALIBRATION_REQUIRED`) or until its cause disappears and stays absent.

---

## 10. Readiness and capture path

Readiness is a state machine (NOT_READY → NEAR_READY → READY → COUNTDOWN → CAPTURED) driven by
per-criterion verdicts with explicit `required / optional / blocking` classification per shot type.

Capture itself uses CameraX `ImageCapture` on the **same `Camera` session** as Preview + ImageAnalysis
(a 3-use-case session). Consequences that the architecture must handle:

* Use-case combination must be validated against device capability (`Camera2CameraInfo` / CameraX
  `getSupportedResolutions`), with a documented fallback (drop ImageAnalysis resolution, or serialise
  ImageCapture with a lower analysis cadence).
* The captured JPEG is **not** the analysis frame. Do not attempt to reuse analysis landmarks as a
  post-capture overlay; recapture-time state is what matters (record `GuidanceState` snapshot in EXIF-style
  sidecar metadata if we later want a "why this shot" record).
* Photo destination: MediaStore (`Pictures/AI Photographer`) with an app-private fallback; no upload.
* Auto-capture is implemented but **off by default** in the MVP (product risk: surprise shutter).

---

## 11. Localization and messaging

* Engines emit **message IDs + typed parameters**, never text:
  `MessageId("guidance.pose.arm_far_from_torso.left", mapOf("amount" to 0.12f))`.
  The key registry, the per-action allowed keys and the vi/en text live in `specs/i18n/messages.json`;
  `specs/validation/validate_specs.py` fails the build if a key is not registered there.
* `:app` owns the resource files: `res/values/strings.xml` (English fallback) and
  `res/values-vi/strings.xml` (default product language).
* Terminology (headroom, lead room, S-curve…) is defined once in `docs/glossary.md` so translations stay
  consistent.
* No locale-dependent number formatting inside engines.

---

## 12. Architecture Decision Records

> Format: ID — decision — rationale — consequences — status.

**ADR-001 — Offline-only core; no `INTERNET` permission in MVP.**
*Rationale:* product requirement (local-first, $0 cost) and a differentiator. *Consequences:* no crash
reporting/analytics SDK that phones home; on-device logs only; model updates ship with app updates.
*Status:* accepted (Phase 0).

**ADR-002 — Kotlin + Jetpack Compose + CameraX, minSdk 24, single-activity.**
*Rationale:* MediaPipe Tasks Android requires minSdk ≥ 24; Compose is the required UI direction.
*Consequences:* no legacy-API support below Android 7.0. *Status:* accepted (Phase 0).

**ADR-003 — MediaPipe Tasks (Pose Landmarker + Face Landmarker) as the v1 perception runtime.**
*Rationale:* Apache-2.0 code *and* weights, purpose-built for on-device mobile, produces 33 body +
478 face landmarks with visibility/presence and world coordinates; alternatives either lack the
landmark richness (MoveNet 17 points) or need a heavier custom deployment (RTMPose + ONNX Runtime).
*Consequences:* single-person inference only (model card: multiple people is out of scope) → multi-person
needs a different approach later (ADR-008); TFLite runtime ships in the SDK, so we must not fight it with
a second runtime in v1. *Status:* accepted (Phase 0), revisit in Phase 8.

**ADR-004 — Deterministic rule engine; ML only for perception. No LLM in v1.**
*Rationale:* explainability, testability, zero cost, and no fabricated "advice". *Consequences:* guidance
is a curated set of measured instructions, not free-form language. Optional LLM remains `EXPERIMENTAL`.
*Status:* accepted (Phase 0).

**ADR-005 — Pose templates are normalized geometry (JSON), never raster images.**
*Rationale:* must support matching, mirroring, scaling, partial bodies and localization.
*Consequences:* an authoring/validation tool is required (`:tools:pose-authoring`) and templates need
calibration against real captures in Phase 6. *Status:* accepted (Phase 0).

**ADR-006 — One canonical `FrameAnalysis` snapshot + explicit 5-space coordinate contract.**
*Rationale:* eliminates the classic overlay-offset/mirroring class of bugs by construction.
*Consequences:* perception wrappers must normalize everything into `ANALYSIS` space.
*Status:* accepted (Phase 0).

**ADR-007 — One primary instruction at a time.**
*Rationale:* humans cannot follow three simultaneous corrections; competing instructions destroy trust.
*Consequences:* an arbiter with severity/confidence/persistence/priority scoring is mandatory
(`guidance-engine.md`). *Status:* accepted (Phase 0).

**ADR-008 — Multi-person is deferred but not designed out.**
*Rationale:* BlazePose model card lists multiple people as out-of-scope; a real multi-person path needs a
detector + per-person ROI pipeline (or RTMPose), which is a Phase 8 decision made with Phase 2–7 data.
*Consequences:* `FrameAnalysis.subjects` is a list; all engines are written per-subject; the pose library
carries `peopleCount` from day one. *Status:* accepted (Phase 0).

**ADR-009 — Message IDs in engines; Vietnamese-first resources in the UI layer.**
*Rationale:* product targets Vietnamese users; engines must stay locale-free and testable.
*Consequences:* a string catalogue and a "missing translation" CI check are needed. *Status:* accepted.

**ADR-010 — The app does not fight the camera's auto-exposure in MVP.**
*Rationale:* CameraX exposure control is device-divergent, and a wrong EV change is worse than advice.
*Consequences:* lighting guidance is advisory text/cues; explicit user-facing exposure controls are a
`LATER` feature with a capability gate. *Status:* accepted (Phase 0).

**ADR-011 — No identity features, ever.**
*Rationale:* privacy policy + BlazePose model card explicitly excludes surveillance/identity recognition.
*Consequences:* no face embeddings, no "person 1 vs person 2" identity persistence, no photo library
scanning. *Status:* accepted (Phase 0).

**ADR-012 — Pure engines, device-independent tests.**
*Rationale:* correctness of the product lives in the algorithms; emulator-based tests are slow and flaky.
*Consequences:* a fixture format (synthetic landmark JSON) and golden tests are required
(`test-plan.md`). *Status:* accepted (Phase 0).

**ADR-013 — Licence gate before any model ships.**
*Rationale:* several otherwise attractive models are non-commercial (see `model-licenses.md`).
*Consequences:* `docs/model-licenses.md` must be updated in the same PR that adds a model, and NOTICE
files must be bundled. *Status:* accepted (Phase 0).

**ADR-014 — Phase 1 implementation boundaries and supported API equivalents.**
*Rationale:* the approved brief allows image production inside `:feature:camera`; the phase table formerly
named `:core:image`, while the brief/handoff named `:perception:image`, and §2.1 deferred a separate image
module. Four modules suffice: app, model, geometry, camera. Capability collection remains in camera and its
debug presentation in app; `:feature:settings` stays deferred. No new analysis engines are created.
*Consequences:* a JVM test inspects an exported actual Gradle dependency graph. Android adapters never
enter core. `ImageSnapshot` carries `FrameAnalysis` plus luma; the frozen FrameAnalysis schema has no
luma property, so no invented JSON field or `LightReport` is added. Phase 1 subjects are empty.
CameraX 1.6.2 `SessionConfig` shares a `ViewPort`; cropRect is rotated into upright ANALYSIS dimensions,
then the pure transform maps it to the aspect-filled, front-mirrored preview. Rotation is applied once.
Camera2 `SCALER_STREAM_CONFIGURATION_MAP` supplies supported YUV sizes; CameraInfo
`isSessionConfigSupported(SessionConfig)` queries the combination, then binding verifies it. No fictitious
`getSupportedResolutions` API is used. Failed combinations retry lower analysis resolution, then
Preview+Analysis with serial manual capture, then Preview+Capture. JPEGs use MediaStore on API 29+ and
app-private storage on API 24–28, requiring no storage permission.
Preview camera capture callback FPS is a delivery proxy, explicitly labelled as such; displayed FPS and
frame loss must be checked with Perfetto. CameraX internal dropped-frame counts are unavailable; the
reported ratio is intentional luma cadence skips / delivered analysis frames, with internal drops UNKNOWN.
GPU eligibility is a GL/emulator preflight, not a claim that a model delegate works. Tier assignment is a
provisional RAM heuristic (`CALIBRATION_REQUIRED`), never a benchmark result. IMU stability is null until
calibrated; gravity and roll are collected. RGB benchmarking is opt-in in debug at 1 Hz; conversion uses a
reusable ARGB array and BT.601 limited-range arithmetic, with rotation in the same pass. This measures this
implementation, not a future model ingestion path. Immutable luma snapshots allocate at 2 Hz; they must
not be claimed allocation-free. No pixels from analysis are written to disk.
*Status:* implementation decision recorded by Codex Local; review pending (2026-10-03).
An optimized `profile` app build type inherits release shrinking and uses a debug signing key for local
installation only; it creates no Gradle module and includes no HUD/marker. Measurement flags can enable
the conversion benchmark and 720p request via ADB without changing release defaults. CameraX's unused
transitive Media3 network-state permission is removed in the app manifest. Backup/transfer of private
captures is excluded by platform-specific rules.
*API/toolchain sources:* [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes),
[CameraX stable releases](https://developer.android.com/jetpack/androidx/releases/camera),
[CameraInfo](https://developer.android.com/reference/androidx/camera/core/CameraInfo),
[ProcessCameraProvider](https://developer.android.com/reference/androidx/camera/lifecycle/ProcessCameraProvider),
[Compose BOM](https://developer.android.com/develop/ui/compose/bom).

---

**ADR-016 — Phase 2 perception ownership and conservative diagnostics (2026-10-06).**

Only the three Phase 2 modules in §2.1 are added. The pure perception API accepts a leased upright
unmirrored ARGB frame; CameraX only knows this API, not MediaPipe. The app wires the Android factory.
One batch at a time owns the reusable pixels/image until all accepted callbacks complete; cadence
and busy skips are separate counters. GPU task initialization/submission/closure share one worker.
Callbacks enter a bounded channel and immutable flow; lifecycle epochs discard old results. Task
creation attempts GPU only after the Phase 1 eligibility check, then CPU on failure. Diagnostics
distinguish successfully configured delegate from runtime evidence; no unobserved acceleration claim.
Face meshes associate conservatively to the pose nose and expire; no identity inference or multi-person
tracking. Frame schema fields only are used; facial matrix remains diagnostic alongside derived head
angles, with no gaze or pose-correction product behaviour. Confidence lives in core:model once, linked
to pose-system §4.1.1; filters live in geometry. core:photography only estimates visible body extent,
with confidence zero pending local capture calibration and UNKNOWN for unestablished close portraits.
All tuning seeds remain CALIBRATION_REQUIRED. No guidance engine/rules/templates are implemented.
Immutable publication and model result mapping allocate; allocation-free performance is not claimed.
Phase 1 measurement/coordinate limitations remain unchanged. Physical Phase 2 gates are required.
Status: Phase 2 implementation decision, not owner acceptance. Pure boundaries and Android adapters
are integrated. Synthetic emulator tests cover the production adapter, including rapid lifecycle
restart and geometry changes; physical perception gates remain pending.

**ADR-017 — Official upstream Core build with unchanged Vision SDK (2026-10-06).**

Stock Maven Core initializes mandatory DataTransport telemetry; exclusions fail at runtime. Use the
official v0.10.32 Core AAR target's default generated dummy logger, with no Java/C++ modification,
and the unchanged official tasks-vision 0.10.32 artifact. Exclude only its stock tasks-core edge.
Pin both binaries and local models by hash; retain Core in a restricted local Maven repository for
reproducible app builds. The build script records the upstream commit and Android environment
configuration. No fork, fake logger, new model architecture or network fallback. Version updates
require renewed graph/manifest, bytecode and runtime verification. Evidence and remaining limits:
`docs/phase-2-sdk-audit.md`. Native notices/source availability are packaged. Emulator and final
shrunk-runtime checks are reported in the session handoff. No physical-device or owner-acceptance claim.

**ADR-018 — Phase 2 automated robustness review (2026-10-07).**

Keep the same modules, single-person Tasks architecture and official telemetry-free Core. Validate
SDK landmark counts and callback timestamps before mapping, coalesce lifecycle requests, and reject
out-of-order/expired results before publication. A task error marks diagnostics unavailable and clears
affected results; bounded worker-owned recovery retries at most three times per active lifecycle
epoch (1/2/4-second engineering delays, CALIBRATION_REQUIRED), falling back to CPU after a GPU runtime
failure. Preview/capture remain independent. Input storage remains owned until completion or native
closure; cancellation after submission cannot release it early. Percentile sorting/logging runs at
most once per second rather than every result; counters and immutable result publication continue.
These are implementation safeguards, not new product behavior or measured device thresholds.
Automated fault injection belongs to adapter tests; no human tracking or physical acceptance follows
from synthetic tests. Physical tests remain PENDING_PHYSICAL_VERIFICATION.

**ADR-019 — immutable model input across overlapping recreation (2026-10-07).**

R8 profile recreation with camera permission exposed reproducible native SIGBUS. Exact upstream
`AssetManager::CachedFileFromAsset` unconditionally overwrites the relative-path cache; new task
initialization can truncate a file still mapped by an older pipeline closing asynchronously.
Use official `BaseOptions.setModelAssetBuffer` with a read-only mapped packaged asset. Package `.task`
uncompressed so Android exposes its file descriptor; keep the mapping reachable throughout task life.
There is no shared mutable cache file, SDK source patch, model modification or network operation.
This trades a small APK compression saving for immutable model ownership and requires renewed
profile/lifecycle regression and subsequent physical measurements. Do not infer physical stability
from emulator tests or reinterpret earlier measurements.


**ADR-020 - Phase 2 thermal recovery tooling and conversion hot path (2026-10-08).**
Owner authorized conditional resume with normal preflight, short windows, cooldown and
severe/critical/rapid-rise abort. Host runner allows one 30-60 s window per invocation,
rejects matrix/lifecycle batching, defaults to 15 s countdown and holds camera off for
30 s of normal-status cooldown. Shared host guard polls thermalservice every 2 s with
5 s ADB read timeout, requires readable Android status and SKIN sensor, refuses nonzero
preflight and stops app on status >=3, missing telemetry or read failure. It records
pre/during/post samples locally and aborts on SKIN rise >=2 C in a sampled interval of up to 30 s.
Rapid-rise and minimum cooldown values are conservative CALIBRATION_REQUIRED engineering
seeds, not validated human safety limits. Android protections remain untouched; host abort
is best effort due to polling/ADB latency. Discomfort requires stop regardless of telemetry.
No automatic restart after abort; new Ready per setup. Traces optional, screenshots/memory
remain ~10 s apart independently of ~2 s thermal polling. No unattended multi-setup loop.

Retained debug evidence shows substantial RGB conversion cost. Hoist source-row and rotated
destination indexing outside the pixel loop; preserve exact color arithmetic, rotation,
crop, stride, buffer-position and reusable-output contracts. No new module/dependency,
network, model, inference cadence, production thermal policy or product-flow change.
Pixel-equivalence regression covers odd crops, nonneutral chroma, strides and four rotations.
Device speed/thermal gain remains unmeasured; no resource leak established by short logs.


**ADR-021 - debug preview-only framing and camera-off countdown (2026-10-08).**
Owner paused physical testing and requested lower-overhead framing. Host framing-only previously
still launched normal perception. New debug-only framingOnly extra skips MediaPipe creation;
CameraSession PREVIEW_ONLY binds Preview alone and skips analyzer/capture binding and sensor/metrics
work. No new module/dependency or production decision logic. Perception/developer overlays omitted,
camera/aspect supplied at launch, capture disabled. The activity finishes at a 12-second monotonic
deadline retained across recreation; 12 s is CALIBRATION_REQUIRED, best effort, not a safety guarantee.
Host requires the localized preview-only label and retains finally force-stop. Release/profile ignore
the debug flag. Full-session 15-second countdown moves before camera launch to remove idle AI work.
Normal-only launch policy and severe/critical/rapid-rise/fail-closed abort unchanged. No device thermal
improvement claimed; physical validation paused. Evidence, policy rationale and uncertainties:
[Phase 2 thermal review](phase-2-thermal-review.md).


**ADR-022 - verified camera-off invariant and cleanup journaling (2026-10-08).**
Retained active-client snapshot proves project camera 0/PID 6692 despite zero runner launches.
Fix host harness assumption, not unproven product lifecycle defect. CameraIdle requires readable
active-client section, zero active camera clients and no known project/test PID. Stop only exact
project/test packages; other owners cause abort. Sample before/during cooldown and countdown,
immediately before authorized launch and after cleanup/monitor stop. Fail closed, bounded release
settling, local ownership/control-command journal and idle-only mode; cleanup attempts guard
shutdown even if stop fails. No permanent disable, normal startup change, module or thermal override.
Sampled idle does not prevent a later external launch. Evidence/root-trigger limits:
[Camera idle review](phase-2-camera-idle-review.md).

## 13. Risks owned by the architecture

| # | Risk | Mitigation already designed in |
| --- | --- | --- |
| A1 | Overlay misalignment/mirroring on some devices | 5-space contract + tested transforms + synthetic transform tests (ADR-006) |
| A2 | Flickering guidance | 3-layer temporal stability (§9) |
| A3 | Low-end devices cannot run pose at usable rate | tiered profiles + degradation order (§8) |
| A4 | Single-person detector silently drops a second person | `subjectCount` surfaced in UI; explicit "multi-person not supported in v1" state |
| A5 | Landmark jitter producing false "corrections" | visibility/presence gating + hysteresis + dwell |
| A6 | Guidance that fights the camera's AE/AF | ADR-010 (advisory only) |
| A7 | Licence contamination of the APK | ADR-013 + `model-licenses.md` gate |
| A8 | Scope creep (VLM/LLM/scene) delaying MVP | roadmap gating + `phase-status.md` "DO NOT IMPLEMENT" list |
| A9 | Pose templates that look wrong for real bodies | templates are `SUBJECT`-normalized and proportion-independent by design; calibration step in Phase 6 |
| A10 | Capture-time regression from a 3-use-case session on low-end devices | documented fallback order in §10 |

---

**ADR-015 — System-controlled orientation in Phase 1 (owner clarification 2026-10-05).**

Portrait-first is a design preference, not a runtime orientation restriction. Every build leaves
activity orientation unspecified and uses normal Android recreation; no configChanges opt-out,
verification intent override or orientation preference is used. Compose observes LocalConfiguration,
retains camera/aspect/resolution controls with rememberSaveable and fits a 4:3 or 16:9 viewport in
the actual orientation. Camera objects are recreated after layout with the current display rotation;
the previous lifecycle-bound session is disposed. The forced locale override was removed in the final
Phase 1 review; Android/app resource locale selection now applies normally. This follows Android's
[configuration handling guidance](https://developer.android.com/guide/topics/resources/runtime-changes).
No core coordinate transform or module boundary changes. Physical rotation must be owner-verified.

## 14. Open architectural questions (for the human owner / later phases)


1. **Who holds the phone?** Two product modes exist and they need different guidance:
   `PHOTOGRAPHER_MODE` (a second person holds the phone → camera-movement instructions are actionable)
   and `SELF_MODE` (tripod/stand → the *subject* must move, and instructions must be subject-directed).
   Proposed default: detect nothing, ask once, remember. Needs product confirmation.
2. **Auto-capture default.** Proposed: off in MVP, opt-in with a visible countdown.
3. **Bundled pose library size in MVP.** Proposed: 6–10 seed templates for Phase 3–6, curated later.
4. **Orientation policy.** Portrait-only MVP lock, or landscape supported from Phase 1?
   Resolved by owner on 2026-10-05: portrait-first design preference only; no orientation lock.
   System Auto rotate controls all builds. See ADR-015.
5. **Minimum supported device tier** (API level is 24, but which SoC classes are "supported" vs
   "best effort"?). Proposed: `LOW` tier = 4 GB RAM, mid-range 2019+ SoC, pose at 10–15 FPS, lite model.
