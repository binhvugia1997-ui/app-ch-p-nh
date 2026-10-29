# Phase 2 collector lifecycle audit - 2026-10-11

Base checkpoint: e457edadb131cefb936a21954cd30f31d7529ce2.
Host-only source and retained-evidence review. No device or emulator connection, camera launch,
MediaPipe execution, installation or physical verification. Draft PR #3 remains unmerged.
Related workload map: [diagnostic overhead audit](phase-2-diagnostic-overhead-audit.md).

## Confirmed cleanup gaps

- The runner previously called `process.wait()` without checking its latched thermal guard until
  the child exited. Retained Face-only evidence shows the parent force-stopped the app immediately
  on rapid rise, but the collector continued screenshots/shell diagnostics until its 30 s timer.
  The app was already stopped; the extra diagnostic work was unnecessary. Unbounded waiting could
  also retain an owned Python collector if it failed to exit.
- The collector closed logcat before trace cleanup and summary writing without isolating shutdown
  errors. A terminate/wait/kill exception could skip later cleanup and leave only the initial
  INCOMPLETE placeholder. Early logcat exit was not recognized as invalid evidence.
- An optional trace configuration could exceed the requested window. A returned trace PID was
  signaled by scoped shell command, but actual release was not verified before calling retrieval
  successful. This was not active in the prior Face-only attempt (`--trace` absent).
- `ThermalGuard.close` joined for 15 s but did not report a still-live monitoring thread.
  A join timeout is bounded; a still-live thread is not confirmed stopped.

No historical orphaned collector or trace is proven. Historical post-stop snapshots showed no
project PID, camera client or host collector. These are source-level prevention/verification fixes,
not a reclassification of the previous thermal interruption.

## Updated ownership and cancellation

Runner launches one owned collector process in a distinct process group. Its wait loop checks the
parent thermal guard every 250 ms while the collector runs; it has a collection deadline of
`seconds + 45 s`. On guard failure, nonzero child exit, interrupt or deadline, it writes a unique
cancellation token under the new ignored session directory and force-stops only the three project
packages. The collector checks the token between bounded diagnostics and during sleep at <=250 ms
intervals, marks its window INCOMPLETE, retains already written raw files, and shuts down owned
logcat/optional trace. The runner waits up to 90 s for orderly exit. If still alive, it terminates
only that collector PID and descendants (Windows taskkill /PID /T /F; POSIX process group), then
waits another 5 s. Cleanup failures are surfaced, not treated as success. The parent still runs
its existing independent `CameraIdle.stop_and_verify`, latched guard close and final camera-idle
check in `finally`. No automatic retry. Bounded shell timeouts and collector deadline fail closed.

Collector thermal guard remains independent for standalone use. It still polls every 2 s and
force-stops project packages on thermal failure. Any collector failure also stops only project
packages and records a camera/PID idle snapshot. Active external camera owners are never killed;
failed idle verification is reported. Saved summary distinguishes timer completion from valid
counter window; interruptions stay INCOMPLETE, and raw evidence is never overwritten.

Logcat termination has 5 s graceful wait and 5 s kill wait; an early exit invalidates the
collection. Logcat shutdown failure no longer skips trace cleanup or writing an INCOMPLETE report.
Optional Perfetto must contain exactly one bounded `duration_ms` matching the requested collection
seconds. Its shutdown rechecks process ownership using this capture's unpredictable ID before
signaling the PID, then observes bounded release. Unreadable ownership, ownership change or persistent trace marks the
trace/report incomplete rather than touching an unrelated process. No trace was used in the
historical Face-only run. No Samsung system service is stopped or altered.

The remaining limit is host/ADB availability: a lost connection or OS-level process-control
failure can prevent device cleanup verification. The harness now reports that state rather than
declaring the device idle. Host tests exercise the orchestration with mocks; physical cleanup of
the revised code has not yet been verified and requires separate owner authorization.

## Build configuration and main thread

MainActivity and CameraSession are shared among debug/profile/release; ordinary camera and
Face/Pose processing are unchanged. Shared metrics/Trace/logging still exist in all builds.
Debug DeveloperOverlay renders landmarks and polls /proc/self/status and PowerManager at 1 Hz;
profile/release DeveloperOverlay and controls are no-op. Debug's synchronous RSS file read runs
inside a main-dispatcher LaunchedEffect, including while the detail panel is collapsed. Its cost
and background lifecycle duration are unmeasured. Moving it to an IO dispatcher and gating on
lifecycle would be reasonable, but cannot be asserted safe for current Compose observations
without an executable Android lifecycle regression; this session makes no app/UI code change.
The collector remains host-side; UI capture, shell dumps and logcat are optional/scoped evidence
work, not production app workload. Device sensor and thermal monitor freshness are untouched.

## Automated validation

Python host suite: 56 tests PASS; focused collector/guard suite: 13 tests PASS after
final trace ownership check. Kotlin unit suites: 37 tests PASS (incremental unchanged modules).
Debug/profile/release Android builds and lintDebug PASS. Spec validator: 0 errors, 0 warnings.
Diff whitespace check PASS. Original 384-file evidence inventory rehashed: zero mismatches.
Newer ignored evidence was only read. No instrumentation/phone/emulator tests executed.

Host mocks verify intended ordering, cancellation, timeout, evidence status and scoped commands.
Actual Windows process-tree termination, ADB disconnect recovery and camera release for this
revision remain unverified on a device. A failed cleanup must be treated as unsafe to proceed;
the next separately authorized physical session begins with fresh idle/thermal checks.


### SM-S918B collector-cleanup preflight after 8ecf05f - 2026-10-11 (STOPPED BEFORE READY)
Branch/checkpoint verified at 8ecf05f08ff0e6e49b6a1b8e415797a344afc110; working tree initially clean.
ADB showed only serial R5CW40EE9QK, model SM-S918B, Android API 36. Initial read-only
thermal NORMAL (0), SKIN 33.4 C and battery 31.8 C. First camera-off idle-only runner
preflight completed: 22 thermal samples 33.3-33.4 C, all NORMAL; 22 camera snapshots with
zero clients/project PIDs; no collector remained. No app or camera launch.

Installed debug APK was the earlier 433055806bdda915289c0580ddf7cdd97165ba71a752ff56f527d01f5e34e989,
not the host-built current 53241b20a527b5e370e81cee1bb7ed398c6534d5f3d9bec85a9efccc97f00ea1.
Installed the current debug APK with ADB `install -r` on this device only (app data retained);
verified installed SHA-256 matches current build and CAMERA permission remains granted.
No activity or camera was launched by this action.

Fresh post-install camera-off preflight returned IDLE_VERIFIED_NO_CAMERA_LAUNCH. All 22 thermal
samples stayed Android NORMAL (0), but SKIN rose 34.3 -> maximum 36.2 C; maximum pairwise rise
within 30 s was +1.9 C in 29.078 s, near the existing +2 C guard. Last preflight reading
was 36.1 C. A later read-only sample was NORMAL / 35.5 C, battery 32.9 C. The unchanged
guard correctly did not fire; the rising trend was judged insufficiently stable to ask for
Ready or add camera/inference load. This is a conservative human stop, not a guard defect or
claim that the installed APK caused the rise. No thermal threshold/protection was altered.

Five early camera-off snapshots recorded external camera 1 owned by com.facebook.orca PID
12672; it released without intervention before the required consecutive idle samples. No
Samsung/Facebook service was killed, disabled or modified. Final read-only camera clients []
and no project app/test PID. Host inventory found no Phase 2 collector/logcat process.
No Face/Pose inference, collection, cancellation exercise or physical camera test ran; the
revised collector cleanup is NOT_VERIFIED_ON_DEVICE. The pending single 30-second rear-portrait
Face-only verification was not attempted; prior valid evidence and completed tests remain valid.
Raw evidence is retained locally in ignored face-cleanup-preflight-after8ecf05f-20261011/
and face-post-install-preflight-after8ecf05f-20261011/, with a separate preflight-review.json.
Earlier 384-file preservation inventory rehashed with zero mismatches. No source changes or
new host tests in this session; previous 56 Python / 37 Kotlin tests, builds, lint and spec
validation remain applicable. Next safest action: keep camera off, allow natural cooldown,
then run a separately authorized fresh NORMAL/stable trend and camera-idle preflight before
requesting Ready. Do not auto retry, claim Phase 2 acceptance, merge PR #3 or start Phase 3.

### Device cleanup observation - 2026-10-11
The separately authorized single SM-S918B rear-portrait Face-only collection completed its
30.078 s counter window with the revised runner/collector. The child produced a COMPLETE
summary, exited, and the parent recorded COMPLETE_REQUIRES_EVIDENCE_REVIEW. The parent's final
ownership snapshots and an independent ADB check showed no project PID or active camera client;
host inventory found no retained Phase 2 collector/logcat process. All thermal samples stayed
NORMAL (0), so this verifies normal collector shutdown and camera cleanup on a device, not
cooperative cancellation or forced teardown following a thermal abort. No trace was enabled.
Raw logs and snapshots remain ignored in face-ready-one-after9a411d8-20261011/; no prior
evidence was overwritten. See [phase status](phase-status.md) for Face and thermal metrics.
