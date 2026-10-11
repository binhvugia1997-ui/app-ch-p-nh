# Phase 2 face verification review - 2026-10-10

Continued from 768634306b90811004bc6b20de5c2a27211ebf52. Prior pose evidence preserved.

Existing evidence: guarded-inference-one-20261008/01-480p-4x3 under ignored local
`device-evidence/phase2/physical/`. Window 29.787 s: 56 face completed, 1 face detected,
zero pipeline errors, face rate 1.880 Hz; last rolling face latency p50/p95 24.405/49.330 ms.
Whole-session last counters 89 face completed / 15 detected differ from window deltas;
startup detections must not be attributed to the measured collection. Face coverage remains pending.

Input review:
- FrameRouter uses CameraX cropRect; ImagePlanes converts the cropped YUV into upright RGB,
  applying rotation exactly once. TaskImage packs RGB bytes at upright geometry dimensions;
  no additional face ROI/crop or MediaPipe rotation option is supplied. Front analysis is not
  mirrored; preview coordinates are transformed separately. Prior delivered analysis was 640x480.
  Crop/stride/rotation unit tests exist. No confirmed crop/rotation/channel defect found; actual
  landmark alignment across phone orientations remains a physical gate.
- Prior full-body framing puts the face relatively small in the image. This is a plausible cause
  of misses, not proven by logs; no confidence/face-size measurement was recorded. Startup face
  detections show the task can return faces, but do not establish close-face stability.
- TaskSources leaves MediaPipe confidence thresholds at dependency defaults. Inspected bundled
  tasks-vision 0.10.32 FaceLandmarkerOptions.builder bytecode via local javap: detection, presence
  and tracking defaults each 0.5. No tuning justified. Per-landmark visibility/presence may be absent;
  absence is unknown, never fabricated confidence. No observed per-face confidence metric available.
- Normal face execution is gated on prior pose subjects, with existing facePeriodMs (100 ms at
  medium/high tier baseline; reduced with stillness/pressure). Previous app adaptation level 4->6
  explains 500 ms face cadence. This is documented product scheduling, not a confirmed bug.
  It cannot isolate a close-face-only check, so the debug diagnostic mode bypasses pose dependency
  and pose initialization/inference, while retaining cadence/backpressure/thermal policy.
- Native callback requires matching timestamp, at most one face, exactly 478 landmarks for each
  detected face, finite XYZ and valid reported confidence channels. Nonfinite/count failures produce
  FACE_RESULT_INVALID. Prior zero errors support passing this contract for reported detections;
  no historical explicit 478-valid counter existed. New helper/tests and faceValid478 aggregate
  counter make future measurement explicit. Out-of-frame normalized coordinates are permitted,
  not clamped or rejected; count/finite validity does not prove visibility, accuracy or identity.

Confirmed product defects: none established. Changes are diagnostic isolation/observability only.
New mode avoids unnecessary face rebuild when the pose model would adapt; normal mode unchanged.
No source thresholds, Android thermal protections, model artifacts or network/privacy behavior changed.

One pending physical test:
- One visible face, 50-100 cm from rear camera, landscape, even lighting; no full-body requirement.
- Fresh normal (0), stable camera-off cooldown and zero clients/PIDs. Wait for fresh Ready.
- 15-second camera-off countdown; start one debug --face-only --seconds 30 run, no trace or batching.
- Face forward initially, then gentle left/right head turns. Existing screenshots/memory sampling
  retained locally; no automatic retry. Thermal polling and fail-closed/rapid-rise/severe guards apply.
- Report window completed/detected/valid478, rates, rolling latency, sampled memory, thermal trend,
  total camera-on duration (startup adds overhead), and post-stop zero client/PID/collector verification.
- Collection COMPLETE is not acceptance. Preserve partial evidence if aborted.

Device check this session: ADB devices list empty. Idle-only preflight failed at get-state before
thermal access or any launch; raw command journal retained in face-only-idle-20261010/.
No current thermal, camera-idle or cleanup state can be claimed while disconnected. No APK installed,
no camera launch, inference or physical test performed. Reconnect and Ready are required.

Validation: final debug/profile/release builds, app lintDebug, 11 perception API unit tests and
6 camera unit tests passed. Host-only Python harness suite 23 tests passed; full cached spec
validation 0 errors/0 warnings; diff whitespace check passed. New isolated-pipeline instrumentation
regression compiled successfully but was NOT run on-device. Raw host build log retained locally
in face-only-review-20261010/final-host-validation.txt. Prepared debug APK SHA-256:
433055806bdda915289c0580ddf7cdd97165ba71a752ff56f527d01f5e34e989 (not installed).


### Authorized face-only attempt after Ready - 2026-10-10 (INCOMPLETE)
Owner confirmed Ready. Continued from a3c79e870187498c349af50fbd433be16006587b;
clean tree and validated APK hash checked. Samsung SM-S918B R5CW40EE9QK reconnected,
Android 16 API 36. Camera-off idle preflight NORMAL (0), stable SKIN around 34.7 C,
zero project/test PID and active camera clients. Installed prepared debug APK with -r,
no data/evidence deletion or install auto-launch. SHA-256:
433055806bdda915289c0580ddf7cdd97165ba71a752ff56f527d01f5e34e989.
Fresh runner cooldown and 15-second camera-off countdown passed. Exactly ONE rear-landscape
--face-only launch attempted; no retry. Runner UI check found displayed PORTRAIT, bounds
[0,0][720,1544], and aborted before the 30-second collector. This proves a displayed-orientation
mismatch, not the phone's physical orientation or a CameraX/rotation bug; system auto-rotate/setup
cause unverified. No Android orientation/thermal setting changed. Attempt is INCOMPLETE, not PASS.
Launch-command to force-stop command 9.204 s. Partial startup logs retained after cleanup.

PID 31647 logs confirm mode FACE_ONLY, pose STOPPED/disabled_face_only, face READY/configured GPU.
Last startup snapshot: pose completed/detected 0/0; face completed/detected 62/33;
faceValid478 33, errors 0, elapsed 6.433 s. Thus all 33 reported detected results passed the
478 finite XYZ/valid-or-absent confidence contract; this does not establish visual accuracy.
First-to-last startup counter interval 6.295 s gives face completion rate ~9.690 Hz;
not a valid 30-second collection rate. Last rolling face p50/p95 41.453/55.592 ms (62 samples).
GPU is configured delegate, hardware execution not independently profiled. Early no-face startup
results cannot establish detection failure under a settled close-face/head-turn setup. No preview
screenshot or memory sampling collector ran; close-face framing, 30-second detection success,
head-turn coverage and memory are UNMEASURED. Validity observed only on partial startup results.

Runner thermal guard NORMAL (0) throughout, SKIN initial 35.0 C, end/max 35.2 C;
no rapid-rise/thermal guard trigger. Post-stop three read-only samples across ~10 seconds:
NORMAL (0), SKIN 35.6 -> 35.5 -> 35.5 C; maximum observed including post-stop 35.6 C.
Battery 33.0 C in each post-stop sample. Runner cleanup and all three later snapshots:
zero project/test PID, zero active camera clients, no unexpected reactivation. Host collector/logcat
inventory empty; 30-second collector never started. No sustained run, repeat or new launch.
Evidence retained locally at face-ready-preflight-20261010/ and guarded-face-only-20261010/
under device-evidence/phase2/physical/, including startup-ui.xml, partial-startup-app.log,
partial-review.json, thermal guard, ownership journal and post-stop diagnostics.
Prior 384-file inventory hashes unchanged; previous pose evidence preserved.
No confirmed code defect or source change in this session. Existing automated builds/tests/lint
from preparation remain applicable. Physical face-only test is still pending completion.
Safest next: keep camera off, cool/rest, resolve displayed-orientation setup before a separately
authorized attempt. Owner may choose portrait for a face-only check or arrange landscape/system
Auto rotate; either needs explicit authorization and fresh Ready, normal/stable idle preflight.
Do not automatically retry this exhausted single-launch authorization. No acceptance, merge or Phase 3.


### Face-only orientation investigation - 2026-10-11
Continued from 69259a4c9ae7c926ab8283e3afb26e263b258e6e; clean branch/checkpoint verified.
No camera/inference/physical tests or APK install. Retained app XML confirms portrait/rotation 0;
CameraX logs 640x480 -> upright 480x640. Runner landscape expectation caused the correct abort.
Historical auto-rotation/physical positioning unknown; no Activity/CameraX defect established.
Earlier landscape screenshot inspected, demonstrating retained landscape support. Current target
SM-S918B absent; only different Samsung SM-N981N attached. Read-only queries used target serial,
failed safely; other phone untouched. No current target thermal/rotation/idle claims made.
Confirmed harness bug fixed: misleading no-capture wording despite startup inference. Runner now
saves startup XML, read-only rotation settings, expected/displayed orientation and failureReason.
Both orientation modes supported; mismatch still aborts without collector/retry. No product code,
manifest, global orientation lock, model/confidence threshold or thermal protection changes.
Regression suite 26 host Python tests passed, including portrait/landscape success, mismatches in
both directions, evidence/cleanup and no settings writes. Host debug/profile/release builds,
lintDebug and 28 Kotlin unit tests (geometry 11, perception API 11, camera 6) passed.
Previous 384-file inventory hashes unchanged; partial face attempt remains INCOMPLETE, not PASS.
Details: docs/phase-2-orientation-review.md. Raw build/diagnostic review retained locally under
orientation-review-20261011/. Smallest prepared next: one rear-PORTRAIT face-only 30-second
window at 50-100 cm, even lighting and gentle head turns; no full-body requirement or extra
framing run. Needs separate authorization, fresh Ready, target device normal/stable/idle checks
and 15-second camera-off countdown. No launch now; no retry/acceptance/merge/Phase 3.


### Rear-portrait face-only prelaunch idle guard stop - 2026-10-11
Continued from d66183846336f28e3b45bc1f803c0c5cd1b658df, clean tree verified.
Owner authorized one pending rear-portrait face-only window, explicitly SM-S918B only and
new Ready before launch. ADB get-serialno R5CW40EE9QK and ro.product.model SM-S918B verified;
Android API 36. No commands targeted SM-N981N. Ready requested; no launch before confirmation.
Camera-off idle-only preflight on verified SM-S918B aborted before camera/inference launch.
At 2026-10-11 04:05:59.881 UTC (11:05:59.881 Asia/Bangkok), active camera 1 client appeared:
com.samsung.android.smartface, PID 31894. Project/test PID remained empty. This is external
Samsung ownership, not AI Photographer reactivation; trigger/purpose not established.
Existing zero-active-client guard correctly failed closed. Do not relax it or stop external owners.
No project camera launch, Pose/Face inference, collector or 30-second test ran; no automatic retry.

Thermal NORMAL (0) for all guard samples; SKIN initial 38.2 C, final 38.0 C, min 37.9 C,
max 38.2 C; no rapid-rise/thermal abort. Final two cleanup snapshots showed zero active camera
clients and no project/test PID after the external client released; no Samsung process stopped.
No device settings changed, so no settings restoration needed. Camera-off observation was
INCOMPLETE due to intermittent external ownership even though final snapshots were idle.
The authorized 30-second face-only verification remains PENDING, with all metrics unmeasured
for this session. Existing partial face/valid pose evidence retains its earlier status.
Raw evidence preserved locally in device-evidence/phase2/physical/face-portrait-ready-idle-20261011/.
Previous 384-file inventory hashes unchanged. No code changes or repeated physical tests.
Next: remain camera-off; separately resume only after Ready and a fresh stable NORMAL/idle
preflight without external camera activity. No inference/collection launched automatically.
No merge, Phase 3, Premium UI or acceptance claim.


### Fresh idle-only preflight stopped on Samsung client - 2026-10-11
Continued from cfb77c88c0a680ec8087e6ec060421f9f4f809a0, clean checkpoint verified.
ADB serial R5CW40EE9QK and model SM-S918B verified; no other device used. Fresh camera-off
--idle-only preflight performed as requested. Initial host collector inventory empty; no project
PID throughout. Before completion, at 2026-10-11 04:08:48.205 UTC (11:08:48.205 Asia/Bangkok),
camera 1 active client com.samsung.android.smartface PID 8505 appeared. Guard correctly aborted;
preflight INCOMPLETE. No project app launch, camera/inference, Pose, Face collection, or retry.
No Ready requested because checks failed. Samsung service was not disabled, killed or modified;
no device/thermal settings changed. External activation purpose remains unestablished.
Thermal NORMAL (0) throughout; SKIN initial/max 39.2 C, final/min 39.1 C; no rapid-rise trigger.
Final cleanup snapshot had zero active clients and project/test PID, but this does not negate
intermittent ownership during preflight or establish a stable idle interval. Existing runner only
stops exact project packages; no external owner stopped. No collectors were launched.
Evidence preserved at device-evidence/phase2/physical/face-idle-resume-20261011/; previous
384-file inventory unchanged. No code changes; no build/unit-test rerun required for doc-only
update. Face detection/valid478/rate/latency/memory unmeasured in this session; authorized one
rear-portrait 30-second face-only window remains pending, with no completed physical test repeated.
Stop here as requested. Next: keep project camera off; resolve/review intermittent external camera
ownership without changing Samsung services, then separately request fresh preflight. Only after
all checks pass request Ready; no automatic retry, merge, Phase 3 or acceptance claim.


### Bounded camera availability preflight improvement - 2026-10-11
Continued from b62b1b940bcea4064799b17e1974a1e44588c49d; clean branch/checkpoint verified.
Reviewed both Samsung ownership journals: camera 1 smartface PID 31894/8505, project PID empty,
next empty snapshot 0.743/0.630 s later without Samsung intervention. Exact occupancy duration
and activation purpose unknown. Confirmed harness issue: strict single-sample assertion in
readiness phase aborted the session on transient external ownership.
Added CameraIdle.observe_preflight/wait_available: unexpected project activity or unreadable
telemetry aborts immediately; external clients remain occupied until three consecutive empty
samples two seconds apart. Availability wait has one non-resetting monotonic 30-second deadline.
Original 30-second NORMAL-only thermal observation remains; external ownership is journaled
there without declaring idle. Thermal monitor/rapid-rise/severe/critical guards remain active.
Availability wait is additional to thermal observation (~60 s maximum plus bounded ADB calls),
not a hard real-time 30-second total preflight cap. Countdown/prelaunch/post-cleanup zero-client
checks remain strict; NORMAL required through countdown. No Samsung-specific bypass or service
stop, no Android settings/thermal protection changes, no product/MediaPipe/CameraX changes.
ADR-024 and docs/phase-2-camera-availability-review.md describe the scoped safety change.
38 host Python tests passed including all requested scenarios and runner no-launch integration;
Android debug/profile/release builds, lintDebug, geometry/perception/camera unit tests passed.
Previous 384-file evidence hashes unchanged. Host validation log retained locally in
availability-review-20261011/. No ADB/device test, camera launch, installation or inference run.
No completed physical tests repeated; face-only 30-second physical verification still pending.
READY FOR FACE-ONLY TEST: harness prepared, device readiness unmeasured; only after separate
resume authorization, fresh verified SM-S918B normal/stable/available checks and explicit Ready.
No automatic retry/test, merge, Phase 3, Premium UI or acceptance claim.


### Authorized face-only test blocked by ADB disconnection - 2026-10-11
Continued from 061e9b8a6ae365f10e2c226f369782c3c3a05866, clean checkpoint verified.
Exact target serial R5CW40EE9QK/model SM-S918B verified over ADB, Android API 36.
Validated debug APK hash remains 433055806bdda915289c0580ddf7cdd97165ba71a752ff56f527d01f5e34e989;
no install/source change. Started updated idle-only preflight before requesting Ready.
After ~12.445 seconds of thermal samples, ADB disconnected during the initial 30-second
thermal observation. Seven retained readings NORMAL (0), SKIN 33.4 C throughout; available
camera ownership snapshots had no clients/project PID, no observed Samsung activation.
Required thermal observation and subsequent bounded three-sample availability phase did NOT
complete. Session INCOMPLETE; telemetry loss failed closed. Read-only diagnostic afterwards
showed empty adb devices list and explicit target device-not-found error. No automatic retry.
No Ready requested, camera launch, Pose/Face inference, screenshot/collector or 30-second test.

Initial project-only stop completed before monitoring. Final stop/release verification attempts
failed because the device disappeared; cleanupErrors and thermalStop saved in session.json.
Do not claim current camera/PID/thermal state or successful final device cleanup while disconnected.
No Samsung service or Android settings changed. Face detection, valid478, latency, rate, memory
and launched-app portrait orientation are unmeasured this session. Physical face-only verification
remains pending, not PASS. Evidence retained locally at
 device-evidence/phase2/physical/stable-face-ready-preflight-20261011/.
Previous 384-file inventory hashes unchanged; earlier valid/partial pose and face evidence preserved.
No source changes; prior host builds/tests/lint applicable. Stop here; reconnect verified SM-S918B,
then separately resume fresh full preflight and request explicit Ready only after all checks pass.
No automatic camera launch, retry, Phase 2 acceptance, merge, Phase 3 or Premium UI.


### Reconnected face-only preflight blocked by LIGHT thermal status - 2026-10-11
Continued from 6404146950bd5486920bdec0d3c50e053f976634; clean checkpoint verified.
Owner clarified previous ADB disconnection was intentional manual USB unplug. Its cause is now
known; do not classify/investigate it as an unexplained ADB fault or project bug. Historical raw
logs and incomplete preflight status remain preserved.
Target reconnected and verified by ADB: serial R5CW40EE9QK, model SM-S918B, Android API 36.
Fresh idle-only preflight stopped on first thermal sample: LIGHT (1), SKIN 40.1 C. NORMAL (0)
launch requirement correctly rejected it. Only one thermal sample retained; stable trend not
established, 30-second observation and stable-availability phase not completed. No Ready requested.
No camera/app launch, Pose/Face inference, collector, physical test or automatic retry.
Final camera-idle snapshots verified zero active clients and project/test PID. session.json records
thermalStop/cleanupErrors from the latched thermal rejection; this does not mean camera release
failed. Samsung services and Android/device/thermal settings untouched. No restoration needed.
Face detection/valid478/rate/latency/memory and launched-app orientation unmeasured this session.
Single rear-portrait face-only window remains pending; interrupted/blocked attempts not PASS.
Evidence preserved locally in device-evidence/phase2/physical/face-reconnected-preflight-20261011/;
previous 384-file evidence inventory unchanged. No code changes; prior host build/test/lint
validation applicable. Stop here with project camera off; cool/rest, then separately resume fresh
NORMAL/stable thermal and camera availability checks, followed by explicit Ready only on pass.
No automatic retry, owner acceptance, merge, Phase 3 or Premium UI.


### ONE rear-portrait face-only attempt after Ready - 2026-10-11 (THERMAL ABORT / INCOMPLETE)
Continued from f8da811efc7cab00669f34fe48cab52a300d5176 with the validated stable-idle harness.
Samsung SM-S918B serial R5CW40EE9QK / Android 16 API 36 verified. Initial idle-only preflight
passed: NORMAL (0), stable SKIN 35.8 -> 35.7 C, three consecutive idle samples, no project PID,
clients/collectors or observed reactivation. Owner then explicitly confirmed Ready. Exactly ONE
rear-portrait --face-only --seconds 30 launch attempted; no retry or Pose inference.
Fresh runner 30-second NORMAL observation, bounded stable availability wait and 15-second
camera-off countdown passed. Validated installed debug APK:
433055806bdda915289c0580ddf7cdd97165ba71a752ff56f527d01f5e34e989.
Mode confirmation: pose STOPPED/disabled_face_only, face READY/configured GPU. UI portrait,
rotation 0; delivered analysis 640x480 -> upright crop 480x640. No orientation mismatch.
Saved ~10-second preview shows one face with face landmarks over it; numerical alignment,
exact distance and complete head-turn coverage not independently measured.

Parent rapid-rise guard STOPPED app: SKIN 34.5 -> 36.6 C across 29.130 seconds triggered
>=2 C within 30 s, despite Android status NORMAL (0). Parent preflight first 34.5 C, stop/max
36.6 C; child diagnostic monitor reached maximum 36.7 C after stop. No SEVERE/CRITICAL event,
no thermal override or changed settings. Host launch-command to FIRST force-stop command
26.019 s; this is not an exact hardware camera-on duration. Session INCOMPLETE, not PASS.
Collector timer reached 30.001 s (31.133 s wall), but app PID was missing at 21.766/30.001 s
samples. Raw collector summary COMPLETE means its timer finished; counterWindowInvalid=true.
Overall session/thermalStop/review-result.json correctly record interruption. Do not use this
as a completed 30-second inference test or full-window performance result. Diagnostic collection
continued after immediate app stop until its timer, without inference; all collectors then exited.

Partial evidence only: first/last same-PID/session metric snapshots span 16.610 s with
160 face completions, 160 detections, 160 valid478 observations, zero pose completions/errors.
Partial face completion rate 9.633 Hz, detection success 160/160 (100%) within that counter
interval; not a full 30-second rate or head-turn acceptance result. Last cumulative snapshot
includes startup: 233 completed / 221 detected / 221 valid478; every reported detected face
passed the 478 finite XYZ/valid-or-absent confidence contract. Zero source errors recorded.
Last rolling task-to-callback face latency p50/p95 40.959/55.082 ms (233 samples), not
exact partial/full-window percentiles. Configured GPU is logged, execution placement unprofiled.
PSS sampled before stop 221781-250379 KiB; RSS 282888-315624 KiB. Later samples null after
force-stop; process peak, optimized-build performance, CPU/power/allocations remain unmeasured.

Runner final snapshots and three read-only post-stop checks: zero active camera clients and
project/test PIDs; host collector/logcat inventory empty. Post-stop thermal NORMAL (0), SKIN
36.5 -> 36.5 -> 36.3 C over ~10 seconds. No unexpected ownership errors reported. No device
settings changed, so nothing to restore; Samsung services untouched.
Evidence retained locally under device-evidence/phase2/physical/face-cooled-preflight-20261011/
and guarded-face-portrait-20261011/, including review-result.json preserving overall INCOMPLETE
without overwriting raw collector output. Previous 384-file inventory hashes unchanged.
No code changes; prior automated builds/tests/lint remain applicable. Face-only 30-second gate
still pending; partial validity/portrait behavior observed, not owner acceptance. Remaining Phase 2
includes outstanding front/mirroring, capture/offline/lifecycle, overlay alignment and optimized
performance/thermal gates per existing matrix; do not repeat completed valid tests.
Safest next: keep project camera off, natural cooldown and review rapid-rise evidence before any
new physical authorization. No automatic retry, safety weakening, merge, Phase 3 or Premium UI.
