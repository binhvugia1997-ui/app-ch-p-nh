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
