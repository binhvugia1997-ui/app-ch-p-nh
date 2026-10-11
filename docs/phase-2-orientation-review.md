# Phase 2 orientation investigation - 2026-10-11

Checkpoint: 69259a4c9ae7c926ab8283e3afb26e263b258e6e. No camera launch, inference,
physical test, APK installation or Android settings change in this investigation.

## Findings and limits

The immediate abort cause is confirmed: runner requested rear-landscape while the app displayed
portrait. Saved startup-ui.xml root belongs to com.aiphotographer.app, bounds [0,0][720,1544],
hierarchy rotation 0. This is not a system-overlay window misclassified as the app. The runner's
orientation check correctly stopped before collection. The original error said no capture started,
but startup inference had run: that wording is a confirmed harness defect.

Why Android selected portrait cannot be established from retained evidence. Auto-rotation settings,
physical phone orientation and window policy were not captured at the attempt. Rotation lock or
physical positioning are possible explanations, not proven root causes. Earlier Phase 1 window
snapshots contain USER_ROTATION_FREE, but cannot establish the setting on 2026-10-10.

| Layer | Evidence / conclusion |
| --- | --- |
| Android device rotation | UI rotation 0 at attempt. Missing contemporaneous accelerometer_rotation/user_rotation; lock vs positioning unknown. |
| Activity / UI | Manifest has no screenOrientation/configChanges override; no setRequestedOrientation. MainActivity observes LocalConfiguration, fits portrait/landscape viewport, and recreates/disposes session with orientation. No product orientation bug confirmed. |
| CameraX / input | Session targets current PreviewView display rotation for Preview/ImageCapture/ImageAnalysis. FrameRouter rotates cropped YUV once using imageInfo.rotationDegrees. Logs show sensor 640x480 -> upright 480x640, consistent with portrait. Does not establish physical phone orientation or rotation-lock settings. |
| Runner | --configuration is an expectation/check, not a request to Android to rotate. rear-landscape was an unnecessary choice for isolated close-face verification. Both orientations already supported; mismatch must remain an abort. |

Existing accepted guarded-preview-only-20261008/framing.png is landscape (1544x720), confirming
that prior build/device session supported landscape. The incomplete face attempt has no screenshot;
its saved app UI hierarchy and logs provide portrait evidence. Inspecting the earlier landscape
screenshot is not a new camera test. Native startup logs: 33/62 face detections, 33 valid478,
zero pose inference, status NORMAL and max observed skin 35.6 C; retained as partial evidence only.

Current safe read-only ADB diagnostics: intended SM-S918B R5CW40EE9QK absent; devices lists a
Samsung SM-N981N R3CR204Z0KT instead. Queries explicitly addressed absent R5CW40EE9QK and failed;
no commands changed or launched anything on the other phone. Current settings therefore remain
unknown; even a current reading would not prove the historical setting. Raw review evidence is
local/ignored at device-evidence/phase2/physical/orientation-review-20261011/.

## Confirmed harness fix and regression coverage

- Correct mismatch error: test collector not started; camera/startup inference may already have run.
- Persist failureReason, requested/displayed orientation and UI hierarchy rotation in session record.
- Save startup UI XML automatically and read-only system rotation settings immediately before launch,
  so future mismatch investigations need not rely on recollection. Settings are diagnostic only;
  never force portrait/landscape or alter Android protections.
- Face-only plan recommends rear-portrait. Explicit landscape support and strict mismatch abort remain.
- Mocked regression tests cover face-only portrait success, landscape success, mismatch in either
  direction, evidence retention, cleanup/no collector on mismatch, and absence of settings writes.
  Existing geometry/camera rotation tests run on host. Device orientation instrumentation was NOT run.

No Kotlin/manifest/CameraX changes justified. Portrait-first UX and landscape support remain intact.
Historical evidence remains unchanged; 384-file preserved inventory verified with zero mismatches.

## Smallest next physical setup (prepared, not authorized or run)

Recommend rear-portrait, one clearly visible face at 50-100 cm with even lighting. Face forward,
then gentle left/right head turns; no full-body or additional landscape/framing test required.
One face-only 30-second collection (shortest supported window), requested 480p 4:3, no trace/matrix.
Use existing installed validated face-only debug APK; harness-only changes need no new installation.
Fresh connected SM-S918B, NORMAL (0), stable trend, zero active clients/project PIDs, explicit Ready,
and 15-second camera-off countdown required. Startup/verification/teardown adds time beyond the
collection window; record total camera-on duration. Monitor thermal throughout, abort on existing
guards/discomfort, preserve partial evidence, no automatic retry, stop and verify release afterward.
Measure detected/completed/valid478, rate/rolling latency, sampled memory and thermal trend.
This is not Phase 2 acceptance or authorization to resume now.

Prepared command (only after authorization and Ready; output must be a new timestamped directory):

```powershell
$faceEvidence = 'device-evidence/phase2/physical/face-portrait-' + (Get-Date -Format yyyyMMdd-HHmmss)
& .tools/python/cpython-3.13.16-windows-x86_64-none/python.exe tools/run-phase2-solo.py --adb C:/Users/anelb/AppData/Local/Android/Sdk/platform-tools/adb.exe --serial R5CW40EE9QK --configuration rear-portrait --face-only --seconds 30 --countdown 15 --output $faceEvidence
```

## Validation

Host debug/profile/release builds and app lintDebug passed. Geometry 11, perception API 11,
camera 6 Kotlin unit tests passed. Python collection/thermal/idle/framing suite 26 tests passed.
No instrumentation or physical tests run. Full spec validation and whitespace check recorded in
phase-status handoff. No merge, Phase 3, Premium UI or owner acceptance.
