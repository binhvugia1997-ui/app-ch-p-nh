# Phase 2 diagnostic overhead audit - 2026-10-11

Base: f22c7cb012f3744f206a7365a64e658fe37f93aa; Draft PR #3 remains unmerged.
Scope: source and retained logs only. Physical testing remains paused. No ADB/device connection,
phone/emulator camera, model inference, install or physical verification in this session.
Related: [Face pipeline audit](phase-2-face-performance-audit.md),
[Face verification review](phase-2-face-review.md), [handoff](phase-status.md).

## Workload classification

A = normal application path, including instrumentation currently compiled into every build.
B = build-specific diagnostics (debug/profile); C = physical verification harness;
D = Android/Samsung services and native camera/display/system work. These are source categories,
not measured power attribution. A shared diagnostic is not essential photography computation,
but it is currently present in release/profile as well as debug.

| Operation / source | Category and execution context | Frequency / retained evidence |
| --- | --- | --- |
| Camera Preview, YUV acquisition, ImageCapture binding, CameraX backpressure | A, CameraX/native camera threads; binding/lifecycle on Android main; D HAL/ISP/camera service | Retained analysis about 29.93 Hz. Face cadence about 10 Hz. KEEP_ONLY_LATEST and close in finally. |
| Crop/geometry/freshness, device state and lease acquisition | A, Phase1FrameRouter analysis executor | Every delivered frame; DeviceMonitor.state reads PowerManager thermal status even if acquire later rejects the frame. No new sampling throttles. |
| YUV RGB/rotation, luma, RGB packing, Face inference and results | A, conversion/luma on analysis executor; packing/submission/results aggregation on Phase2Perception worker; native callback thread converts/validates landmarks; native delegate internals unspecified | Conversion only on accepted lease (~10 Hz), luma about 2 Hz, Face-only Pose disabled. RGB benchmark optional, not enabled in retained run. |
| Synchronous Trace and bounded latency counters | A shared diagnostic, analysis executor; async face Trace/latency records on worker | Router Trace each delivered frame; conversion/luma Trace only when those stages run. Async face trace per accepted batch. No Perfetto trace collected in this attempt. Trace activation/system backend is D. |
| PipelineMetrics / summaries / Phase1Baseline Log.i | A shared diagnostic, inside synchronized analyze | At most about 2 Hz; 33 retained metric lines, not 30 log lines per second. Summary sorting is synchronous here. |
| PerceptionMetrics / summaries / Phase2Baseline Log.i | A shared diagnostic, worker and synchronized pipeline section | Refresh about 1 Hz; 17 retained metric lines. No per-landmark log output or full-frame logging. |
| Camera/init/failure logs | A shared diagnostic, main/worker depending on caller | Event-driven; mode confirmation retained separately. Filtered collector log contains no Phase2Perception or AndroidRuntime error lines. Absence from filtered logs is not a system-wide error audit. |
| StateFlow delivery, PerceptionStatus, capture controls/adaptation effect | A, main-thread Compose; router subscription uses Dispatchers.Default and synchronized perceptionResult | Updates can follow each inference completion plus 2 Hz luma; some consumers observe full PerceptionState even when only statuses/level are used. No recomposition trace. |
| Crosshair/478-point overlay, coordinate objects/draw calls, diagnostic panel | B debug-only, main-thread Compose/Canvas with native rendering contributions | Face snapshots about 10 Hz; actual redraw/display rate unmeasured. Debug panel starts collapsed, overlay still draws. Profile/release DeveloperOverlay is no-op. |
| Debug RSS + thermal display | B debug-only, LaunchedEffect on main dispatcher | /proc/self/status file read and PowerManager call every 1 s even with collapsed panel; composition cancellation on disposal. LaunchedEffect itself is not repeatOnLifecycle, so polling may continue while an Activity is stopped but its composition survives; retained background duration unmeasured. This RSS is not PSS. |
| Sensor state | A, DeviceMonitor listener callbacks normally main; D sensor stack | Gravity + rotation vector SENSOR_DELAY_GAME; matrix arrays can be allocated in rotation callbacks even after gravity exists. Actual sensor rate/CPU unmeasured. |
| Thermal preflight/countdown + guard | C host main and dedicated Python guard thread; ADB launches device dumpsys, D thermalservice/HAL | Parent 42 thermal requests/samples in commands.jsonl; 30 s NORMAL observation, stable-idle wait then 15 s camera-off countdown retained. Guard loop waits 2 s after each completed read; actual spacing 2.055-2.108 s. |
| Collector thermal guard | C second host guard thread; D dumpsys/thermalservice | 16 samples in separate child history, roughly 2 s apart. Overlaps parent during collection; cannot simply remove because parent has earlier baseline and child supports standalone operation. |
| Collector thermal snapshots | C host collector main loop; D dumpsys | Four additional full dumps at diagnostic sampling points; separate from safety monitors and preserved as raw evidence. |
| Memory / PID sampling | C host collector main loop, D meminfo/process services | Four meminfo/PID attempts, PSS/RSS retained only while PID exists. Sample starts 0.061, 10.844, 21.766, 30.001 s; snapshots sequential, not simultaneous with screenshots/thermal. Peak/GC unknown. |
| PNG screenshots | C host subprocess and PNG/file handling; D screencap/compositor/encoding | Four saved previews, approximately 10 s spacing. Last two after project stop. Runner always passes --preview-samples; standalone collector opt-in. No automatic frequency increase or screenshot removal. |
| Camera ownership / project PID | C host main; D media.camera and process telemetry | 37 audited camera dumps / idle snapshots across preflight/countdown/cleanup; no ongoing idle check while project legitimately owns camera. Post-stop three read-only confirmation samples stored separately. |
| UI hierarchy / mode / rotation settings | C host main; D uiautomator / settings / logcat | One hierarchy dump, its XML read, two rotation-setting reads, one current-PID mode logcat snapshot before collection. No repeated framing/model restart loop. |
| Logcat stream / collectors | C host child process writes filtered log file, D logd serves stream | One runner, one collector, one owned streaming logcat plus short-lived ADB commands; independent safety threads, no sustained device test instrumentation process. Child diagnostics continue to timer after parent force-stop; final cleanup verified zero owned collectors. |
| Host audit I/O | C host main/guard threads | Each command appends JSONL; each guard rewrites its small sample history at ~2 s; snapshots/PNG/log written locally under ignored evidence. Host disk cost does not directly equal phone workload, though device shell commands do execute on phone. |
| Thermal protections, HAL updates, preview compositor, Samsung smartface | D, system/native threads | Android protection active and unchanged. Earlier transient smartface ownership is documented; current accepted preflight was idle. No system trace supports attributing this run's heat to smartface or other Samsung services. |

## Duplicated work and blocking

Confirmed: overlapping parent/child thermal polling plus raw thermal snapshots; full StateFlow
observation by status-only UI; hidden-panel RSS reads; source-level per-callback coordinate/list
allocations; summary sorting/copies. Those operations are real, but their thermal/CPU impact is
unmeasured. Shared summaries currently take an extra array copy; FrameRouter also requests luma
summary for quality separately from the metrics-map summary. This second summary remains unchanged.

Synchronization is purposeful: analyze holds router lock through conversion/luma/reporting, so
result publication can wait; pipeline acquire/completion state is synchronized, TaskImage has one
owned buffer per source, batch gate allows one outstanding batch. Log formatting/writes and sort
cost can lengthen these sections. Host subprocess.run, UI hierarchy, screencap and meminfo are
blocking operations on host collection/control paths; OS work is on device services, not the app
analysis thread. Debug /proc read is synchronous main-thread file I/O. No durations for these
blocking operations were retained, so no latency/heat reduction claim follows from this audit.

The parent waits for collector exit while its independent guard still monitors/stops the app.
This correctly stopped inference, but diagnostics continued to the child's timer. The child's
COMPLETE means timer completion only; parent INCOMPLETE and invalid counter window remain binding.
No unbounded collector or unexpected restart evidenced. A future owned-child cancellation design
must preserve partial files, standalone safety and cleanup; no such protocol change in this patch.

## Does instrumentation change scheduling?

No diagnostic flag in the retained invocation changes FacePeriodMs, model quality or thermal
thresholds. The RGB benchmark flag would add conversions on otherwise skipped frames, but it was
false. Trace, summaries, logging, UI and ADB work can contend for CPU and lengthen accepted batches,
indirectly affecting achieved rate/backpressure even with unchanged nominal cadence. Actual causal
impact is unmeasured. Debug inference results must not be treated as optimized release baselines.
Both MediaPipe task completion and trace timings include callback/worker delays; no new inference
or rendering disable switch is introduced. Existing physical evidence retains its original build.

## RGB conversion interpretation

Historical last rolling conversion p50/p95 is 29.752605/38.378333 ms at 640x480 sensor / 480x640
upright crop. This measures YUV crop/channel conversion and rotation into IntArray, including
Trace begin/end and wall-clock preemption; it excludes the later TaskImage RGB-byte packing and
native Face execution. It records only accepted conversions, not all delivered camera frames.
Per-frame geometry/Plane wrappers and scheduling outside the measured block are not included.
The latency window record lock is acquired after duration is computed; summary sorting is outside
the conversion block, though contention/preemption can indirectly affect later work.

Source uses scalar stride-aware buffer reads, chroma lookup and integer color math per pixel,
reused IntArray output and row/destination increments (already optimized earlier). No Bitmap,
JPEG, network transfer, double rotation or RGB conversion on every camera frame is evidenced.
Packing incurs another accepted-frame pass into reused direct buffer for asynchronous ownership.
Do not sum conversion/face/batch percentiles or subtract them to derive stage overhead. Quantities
come from different populations and overlapping spans. SIMD/native/direct RGB or lower camera FPS
would need separate design/equivalence/profile work; not justified by current causal evidence.

## Low-risk change

LatencyWindow.summary previously used values.copyOf(count).sortedArray(): one snapshot copy plus
another array copy made by sortedArray before sorting. It now sorts the first snapshot in place.
Live ring order, capacity, count, clamping, synchronization, nearest-rank p50/p95 and all diagnostic
frequencies/output fields stay unchanged. One source-level LongArray payload copy of 8*n bytes per
nonempty summary is removed (n = retained count), not a measured allocation/thermal saving.
No diagnostics removed, no sampling shared across guards, no safety/polling/inference/quality
change. Two regression tests cover repeated summaries without live-ring mutation, wrap/clear/
negative values, and equivalence against nearest-rank reference across capacities 1, 4, 19, 4096.
Existing scheduling regressions retain no-queue/independent-cadence coverage. No architecture change.

## Safety, evidence and next action

Keep NORMAL/stable preflight, rapid-rise and severe/critical stops, camera ownership invariant,
explicit Ready, independent cleanup checks, timestamps/counters, valid478 contract and partial
window semantics. Do not disable Samsung services or Android protections. Keep screenshots/memory
available as meaningful evidence; any future reduced-diagnostic mode needs explicit documented
scope, retained required metrics and host regression coverage rather than silent diagnostic removal.

No physical attempt is authorized by this audit. Safest next work stays host-only: review a bounded
collector cancellation and explicitly selectable diagnostic plan while preserving required safety
and evidence. No new comparative device run until separately authorized with fresh preflight/Ready.
All prior raw files remain local/ignored; no evidence rewritten or uploaded. No merge, Phase 2
acceptance or Phase 3. Validation results follow after checks complete.


## Host validation

Python host tests: 43 PASS (all ADB/camera interactions mocked). Kotlin unit tests: 37 PASS
(model 5, photography 1, geometry 11, perception API 14, camera 6), including two new latency
summary regressions. Debug/profile/release builds and lintDebug PASS; full Gradle invocation succeeded.
Spec validation 0 errors / 0 warnings; diff whitespace check PASS. Rehashed original 384-file
evidence inventory: zero mismatches. Newer raw evidence only read; no overwrite or upload.
No installed APK/device behavior change claimed. No phone/emulator tests executed.
