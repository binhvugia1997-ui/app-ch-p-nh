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
