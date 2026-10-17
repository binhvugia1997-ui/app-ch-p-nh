# Phase 2 camera availability review - 2026-10-11

Base checkpoint b62b1b940bcea4064799b17e1974a1e44588c49d. Host-only change, no real ADB
commands, camera launch, installation, physical test or inference in this session.

## Evidence and root cause

Local ownership journals:
- face-portrait-ready-idle-20261011/camera-idle.jsonl: camera 1, Samsung
  com.samsung.android.smartface PID 31894 at 04:05:59.881 UTC; next empty snapshot 0.743 s later.
- face-idle-resume-20261011/camera-idle.jsonl: camera 1, same package PID 8505 at
  04:08:48.205 UTC; next empty snapshot 0.630 s later.

Both projectPid fields were empty. Only project packages were stopped by cleanup; Samsung
services were never stopped/disabled/modified. Observations establish transient ownership and
subsequent release, not exact occupancy duration or why Samsung opened the camera. The original
preflight used the strict zero-client assertion for every sample, causing immediate session abort
on a legitimate transient external owner. No product camera lifecycle defect established.

## Improved preflight

1. Stop only exact project/test packages; start existing thermal guard.
2. Retain 30-second NORMAL-only thermal observation and ownership journaling. External ownership
   is recorded as occupied, not declared idle. Unexpected project PID or project camera client and
   unreadable ownership telemetry abort immediately.
3. Enter one bounded availability wait: timeout 30 s, three consecutive idle samples at 2 s
   intervals. Every sample must show zero active camera clients and zero project PID. Any busy
   sample resets the streak. External owners are all treated as occupied, with no Samsung bypass.
4. Check thermal failure latch and NORMAL-only status before/after wait samples; independent
   existing 2-second thermal monitor remains active throughout. Existing severe/critical,
   rapid-rise, missing-telemetry and project-only stop responses retained. Countdown also requires
   NORMAL status, matching the prelaunch policy.
5. Persistent ownership fails with recorded last owner/package/PID and timeout; project activation
   fails immediately, without waiting for it to disappear. Strict countdown/immediate prelaunch
   and final cleanup checks remain; later ownership activity is not implicitly permitted.

The availability deadline is monotonic and never resets. It is an additional bounded phase after
the existing 30-second thermal observation, so preflight may take roughly 60 s plus bounded ADB
latency. It is not a 30-second total preflight cap or a hard real-time cancellation of in-flight ADB.
No automatic retry/test launch, Android settings change or system-service action added. Timing
values are operational test-harness seeds, not medical limits or calibrated thermal thresholds.
Ownership observations and errors remain local in the existing JSONL/session records.

## Automated validation

38 host Python tests pass, covering transient Samsung release, persistent occupancy timeout,
other external owners, streak reset, multiple separated idle samples, thermal interruptions
(severe, rapid rise, and non-NORMAL launch policy), unexpected project process/client,
unreadable telemetry, slow reads past deadline, and runner no-launch/cleanup behavior.
Runner integration uses mocked ADB/time/thermal guards; printed countdown/collection messages
from these tests do not represent device operations. Geometry/perception/camera unit tests,
debug/profile/release builds and lintDebug pass. New raw host log is local/ignored at
availability-review-20261011/host-validation.txt under device-evidence/phase2/physical/.
Previous 384-file evidence inventory rehashed unchanged; pose/face partial evidence preserved.
Spec validation result recorded in phase-status. No physical success or owner acceptance claimed.

## Next physical setup

Harness is prepared for a separately authorized rear-portrait face-only 30-second window on
verified SM-S918B, no Pose. No test authorized or started automatically by this change. Fresh
NORMAL/stable thermal observation, stable camera availability and explicit Ready remain mandatory.
No device readiness/thermal/availability state measured this session. Device verification of the
new wait remains pending. Keep Samsung services unchanged; no merge, Phase 3 or Premium UI.
