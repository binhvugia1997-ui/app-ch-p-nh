# Phase 2 Face-only performance audit - 2026-10-11

Status: HOST-ONLY REVIEW; physical testing paused; no owner acceptance.
Base checkpoint: e07dc9649878d72ce003b56ce264363ef765b3ea.
No ADB calls, device connection, camera/inference launch, install or physical retry.
All source, tests and documentation remain in English. No architecture/module change.

## Retained measurements and limits

Source: ignored `device-evidence/phase2/physical/guarded-face-portrait-20261011/`.
The earlier SM-S918B / Android 16 API 36 debug attempt was INCOMPLETE, not PASS.
Same-session partial counter interval: 160/160 face detections and valid478 outputs over
16.609919577 s, 9.632797995 Hz. Cumulative last record includes startup: 233 completions,
221 detections/valid478, zero Pose completions, zero pipeline errors, degradation level 0.
Last rolling face task-to-callback p50/p95: 40.958906/55.081979 ms; batch 72.977344/87.195104 ms.
Last router log: 714 delivered frames in 23.852842074 s (29.93354 Hz), 231 RGB conversions,
47 luma samples. RGB conversion p50/p95 29.752605/38.378333 ms; luma 1.248125/7.037812 ms;
router 0.469792/35.784167 ms. Percentiles are rolling and have different sample populations;
do not sum percentiles, infer a CPU utilization percentage, or treat them as full-window metrics.
PSS sampled 221781-250379 KiB, RSS 282888-315624 KiB before stop; peak/GC/allocations unknown.
Android NORMAL (0); reported SKIN +2.1 C in 29.1293467 s caused correct independent guard stop;
max observed 36.7 C. This does not identify the causal heat source. See face review reconstruction.

## Pipeline audit

| Stage | Confirmed source behavior | Performance interpretation |
| --- | --- | --- |
| Camera acquisition | `CameraSession` binds Preview + ImageAnalysis + ImageCapture when supported. Requested 640x480 fallback; retained delivered 640x480, upright crop 480x640. ImageAnalysis uses KEEP_ONLY_LATEST and default YUV output, one router executor. | Camera/preview still run about 30 Hz while Face cadence is about 10 Hz. Changing FPS/use-case binding is a separate camera behavior change; not justified here. |
| Backpressure | `FrameRouter` calls `acquire` before RGB work. `BatchGate` admits one batch; monotonic timestamp/cadence checks reject early. `ImageProxy.close` is in finally. | No unbounded image queue or RGB conversion for every delivered frame. Cadence skip counters do not represent CameraX internal drops. |
| Conversion | `ImagePlanes.rgb` directly reads stride/position/crop-aware YUV, rotates once into reused IntArray; no JPEG/Bitmap conversion. RGB benchmark fallback is opt-in, false in the retained run. | Conversion is measurable work (~30 ms rolling median). It is gated to accepted inference frames. Native/vectorized/direct-RGB alternatives require equivalence and device profiling; not implemented. |
| Task input ownership | `TaskImage` reuses one direct RGB ByteBuffer per task/geometry. It packs ARGB ints into RGB bytes each accepted frame; creates MPImage wrapper, released after callback. | A full-frame packing pass and byte puts are confirmed. No per-frame Bitmap or direct-buffer allocation at stable dimensions. Zero-copy redesign could violate asynchronous ownership; deferred. |
| Face invocation | One FaceLandmarker, LIVE_STREAM detectAsync, one face, blendshapes off, transform matrices on; unchanged model/default confidence. Pending atomic plus batch token/epoch; 5 s watchdog. | No sustained inference busy loop. Head-pose output and 478 landmarks preserved. Native allocations and internal GPU/CPU execution not profiled. |
| Delegate | GPU attempted if eligible/non-emulator; CPU fallback on initialization failure, recovery can select CPU. Retained state says configured GPU, no errors/recovery. | Configured delegate is not proof of all operations executing on GPU; no claimed GPU power measurement. |
| Pose isolation | `faceOnly` skips makePose, Pose submission and SubjectAssembler. Face gate does not require pose subjects. Pose-driven model swap is disabled. | No Pose inference in source or retained counters. Generic schedule/quality bookkeeping and luma/device monitoring remain. Constructor creates an idle assembler but it is not used per Face batch. |
| Scheduling | Medium tier starts at facePeriodMs=100; thermal/busy adaptation retained. Worker is single-thread ScheduledThreadPoolExecutor; callbacks enter bounded Channel(2); cancelled watchdog tasks removed. | About 9.63 Hz is consistent with 100 ms cadence. No scheduling rate increase, duplicate Face inference, spinning channel loop or unbounded callback queue observed. |
| Model lifetime | Initialization on lifecycle refresh, geometry change, recovery or model change; buffers resized only with geometry/size. Sources close before rebuild. | Retained mode/session and zero errors show no repeated initialization in this attempt; lifecycle/geometry changes legitimately recreate sources. Native leaks cannot be ruled out from sampled PSS alone. |
| Landmark processing | Native landmarks validated, converted to domain list/IDs; bounds computed; head matrix decoded. Pipeline validates valid478 metric independently. | Redundant filtered list and four bounds scans confirmed and optimized below. Domain/ID allocations and repeated validation remain; they protect immutable output/contracts. |
| UI | Router snapshot on perception completion plus 2 Hz luma updates. Compose collects lifecycle-aware flows. Debug overlay maps/draws 478 points, polls RSS/thermal at 1 Hz even with detail panel collapsed; bottom UI/status observes full perception state. Profile/release overlay is no-op. | Potential recomposition/drawing and debug-only overhead; no frame-time/recomposition trace to quantify. Geometry mapping supports preview mirror separately; no coordinate change made. |
| Device state | DeviceMonitor.state is requested on analyzer frames, including rejected frames; reads PowerManager thermal status. Gravity/rotation-vector sensors at SENSOR_DELAY_GAME; rotation callback creates matrices. | Binder/sensor and allocation costs plausible, not timed. Altering thermal polling could weaken freshness, so unchanged. Rotation work can be redundant after gravity arrives, but no sensor trace/host Android regression harness supports a safe change here. |
| Diagnostics | Phase1 logs at 2 Hz, Phase2 about 1 Hz, bounded latency windows sorted at those refreshes; Trace sections around stages. Host runner and collector each poll thermal ~2 s; collector additionally dumps thermal/memory and takes PNG screenshots ~10 s. No Perfetto enabled in retained attempt. | Bounded but additional work is confirmed; contribution to temperature unmeasured. Child diagnostics continued after parent's stop until timer; no inference after force-stop. Do not interpret its COMPLETE timer as physical PASS. |
| Cleanup | Lifecycle deactivation invalidates gate and queues source close; close cancels retry, releases images/batch, closes channel/scope/worker. Camera session clears analyzer, unbinds owned config, removes observers and stops monitor/executor. | Retained zero app/test PIDs, camera clients and host collectors support successful cleanup. No leak or reactivation demonstrated by this evidence. |

## Confirmed optimization

`MediaPipeFaceSource` previously validated all points, allocated a filtered copy whose predicate
was already guaranteed, then ran minOf/maxOf separately for x/y (four additional traversals).
`FaceLandmarkValidity.bounds` now performs the same contract validation and all four extrema
in one traversal, without an intermediate landmark list. The callback still validates native
values/count/timestamp, retains the original complete list and head matrix, and rejects invalid
results with FACE_RESULT_INVALID. The pipeline's independent valid478 metric remains unchanged.
No model, input size, inference rate, delegate policy, confidence, landmark output, UI behavior,
thermal threshold, Android protection or physical harness change. Benefits are structural only:
one intermediate list and repeated scans removed per detected face, not a measured thermal fix.
This local pure helper stays in the existing perception API boundary; no ADR/module change needed.

Regression tests cover randomized 478-point bounds against the prior min/max reference,
out-of-frame coordinates without clamping, input immutability, malformed counts/nonfinite
coordinates/confidence, and degenerate bounds with valid optional confidence. Existing validity,
scheduling, geometry/rotation/stride and lifecycle instrumentation regressions remain applicable.

## Remaining hypotheses and safest next action

No evidence proves a native leak, repeated model initialization, Pose running in Face-only,
wrong rotation/crop, thermal calculation error or CPU/GPU fault. Conversion, RGB packing,
preview/display, debug overlay, screenshots, Binder polling and native task work can all contribute.
No per-stage power/CPU/GPU load, GC trace, sustained optimized-build baseline, profiler comparison
or causal thermal experiment exists. Do not claim the small bounds change prevents rapid rise.
Keep physical tests paused and camera off. Next safe work is a host-only design/review for fewer
optional diagnostics while preserving thermal/ownership checks and evidence semantics. Any later
physical profiling needs separate owner authorization, cooldown, fresh NORMAL/stable camera-idle
preflight and Ready; no automatic retry or six-minute collection. Phase 2 stays incomplete.

## Automated validation

Host Python suite: 43 tests PASS, with mocked ADB/clock/camera interactions only.
Kotlin unit tests: 35 PASS (model 3, photography 1, geometry 11, perception API 14, camera 6),
including three new bounds regressions. Incremental unchanged-module results accepted.
App debug/profile/release builds and lintDebug PASS; complete Gradle invocation succeeded.
App and MediaPipe debug instrumentation APKs compile successfully; no device test execution.
Spec validator: 0 errors / 0 warnings. Original 384-file preservation inventory rehashed with
zero mismatches. All newer evidence unchanged by this session, which only read existing logs.
No production APK installed; device behavior of the new helper remains physically unverified.
Instrumentation APK compilation is not device execution. Previous checkpoints are retained.
