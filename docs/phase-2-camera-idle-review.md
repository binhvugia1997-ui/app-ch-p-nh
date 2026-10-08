# Phase 2 camera reactivation / camera-idle invariant - 2026-10-08

Physical camera/inference tests remain PAUSED. Investigated checkpoint
400a6888647b32e8efad15ca2198f71b37b824b4 on codex/phase-2-pose-face-detection.
No app/camera/inference launch, instrumentation or install in this investigation.

## What is established

Retained shutdown-camera-clients.txt is a camera-service active-client snapshot, not a
historical CONNECT event: rear Camera ID 0, PID 6692, Client Package Name
com.aiphotographer.app. shutdown-pid.txt independently records 6692. Thus the project
camera was actually active at that snapshot. Runner cameraLaunches=0 does not prove
camera-off; it only says this invocation never reached its explicit am start branch.

Approximate host artifact write times (not Android event timestamps) are 19:19:41
preflight, 19:19:52 failed cooldown/finalizer report, 19:20:09 active PID/client snapshot,
19:20:21 explicit force-stop/empty PID/Active Camera Clients [] confirmation, and
19:20:33 final thermal read. Status rose from 0 / SKIN 39.8 C to 1 / 40.3 C during
preflight; final explicit stop read was 1 / 40.1 C. No continuous ownership sampling
exists for that cooldown. These times do not establish when PID 6692 was started or
whether it was active before the report. Earlier app PIDs differ, but no start-time,
calling UID, activity history or incident event-buffer log was retained for PID 6692.

The confirmed root cause of the inconsistent camera-off claim is the harness: it
trusted force-stop completion and a local launch counter, checking only thermal state.
It never verified camera ownership or project process absence during cooldown. The
caller/root trigger of reactivation is UNKNOWN; a surviving/relaunched previous session,
launcher/user/system activity restore or instrumentation/external launch cannot be
confirmed or excluded. Do not label it definitely external or a CameraX leak.

## Lifecycle and indirect-launch review

MainActivity onCreate composes CameraApp; normal startup creates MediaPipePipeline and
CameraSession. CameraSession start obtains ProcessCameraProvider and binds to LifecycleOwner.
Returning to STARTED resumes normal camera use and perception by design. Activity recreation
constructs a new session while DisposableEffect closes the old one. Normal launcher/exported
MAIN activity is intentionally supported; no project service, receiver or scheduled self-launch
code was found in the reviewed app manifest/source. No permanent camera disable or product startup change is justified.

CameraSession callbacks check closed before pending binds; close clears analyzer, cancels
perception scope, removes observers, unbinds its SessionConfig, stops sensors and shuts down
analyzer executor. FrameRouter finally closes ImageProxy. MediaPipe close is idempotent,
invalidates batch gate and queues source/watchdog/dispatcher cleanup on owning worker;
lifecycle refresh checks closed/epoch eligibility before reinitializing. No source/evidence
proves an independent CameraX reopen-after-close defect. These are reviewed code paths, not
new physical verification. Cached app PID alone is not proof of active camera, but is rejected
by the host idle policy because its inference/previous session status is otherwise unknown.

OrientationInstrumentedTest explicitly uses ActivityScenario.launch/recreate and startActivity;
other instrumented tests initialize perception without camera. Such tests can launch work if
invoked by am instrument/connected tests. Host Gradle assemble/test/lint tasks used here do not
invoke them. tools/run-phase2-solo.py intentionally launches MainActivity only after preflight;
collector does not launch it. Latest one-shot script returned before am start. adb install -r
is not treated as camera launch evidence; package replacement/restore behavior was not logged
well enough for attribution. Safe getprop/dumpsys/pidof/log reads do not explicitly launch
MainActivity. No task-owned host runner/logcat/trace-processor was found in the current inventory.

## Fixes and regression coverage

Shared CameraIdle parses only Active Camera Clients, excluding historical event logs, and
records timestamped owner camera ID/PID/package plus known project/test PID inventory.
Missing/malformed telemetry or read failure is not idle. It requires ZERO active camera clients
(including other apps, which are never killed) and no known project/test process. Stop callback
force-stops only app, app.test and perception.mediapipe.test packages, then boundedly verifies
release. Unknown/other active clients abort rather than being terminated.

Runner stops/verifies before thermal cooldown, samples idle throughout cooldown and camera-off
countdown, checks again immediately before authorized launch, and verifies after cleanup and
monitor shutdown. Any unexpected activation aborts, finalizer stops project/test packages and
records incomplete status. A --idle-only path performs stop/cooldown/invariant verification
without any activity launch. Main ADB control commands are timestamp-journaled locally.
Cleanup now runs monitor shutdown even if force-stop/ownership verification fails and preserves
cleanup errors in session metadata instead of claiming successful completion. No thermal policy
or Android protection changed. Invariants are sampled, not a permanent lock or a guarantee
against a new launch after the final observation; future testing requires another check.

Regression tests cover owner PID 6692, historical CONNECT exclusion, malformed telemetry,
other-app camera, cached project process, bounded release retries, failed cleanup, reactivation
after successful idle and mocked runner reactivation during cooldown with zero launch commands.
Normal user camera startup remains unchanged; only the host verification workflow is hardened.

## Current device cleanup and remaining risk

ADB currently lists no connected device. Read-only inspection could not query live activity,
exit info or camera events. No current on-device cleanup can be certified; the latest retained
verified stop remains the explicit empty PID/client confirmation above. No host-owned capture
process found now. Do not infer current safety from disconnected state or old confirmation.

Safe to resume physical testing: NO. Reconnect for cleanup-only diagnostics first, inspect
activity/camera event history if retained, stop known project/test packages and use idle-only
verification with NORMAL thermal state. No camera/inference launch is authorized by that
procedure. Actual reactivation caller and real-device invariant behavior remain unresolved.


Validation: 20 host collection/thermal/ownership/framing regression tests PASSED, including
idle-only no-launch, reactivation-before-launch abort and guard shutdown despite stop failure.
Camera unit/perception API tests, debug/profile/release builds and lintDebug PASSED (host only).
Full spec validation: 0 errors, 0 warnings. Diff check passed. Rehashed previous 384-file
inventory: 0 mismatches. No physical test, camera launch, inference or instrumentation run.
Current device unavailable; final live cleanup remains UNVERIFIED, not PASS. Local-only
review evidence: device-evidence/phase2/physical/camera-idle-review-20261008/.
