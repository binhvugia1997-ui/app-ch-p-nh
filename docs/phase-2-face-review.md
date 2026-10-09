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
