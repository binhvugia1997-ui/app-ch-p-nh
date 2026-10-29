# Phase 2 physical-test harness review: project task cleanup - 2026-10-29

Status: HARNESS-ONLY CHANGE. No ADB command, device contact, camera launch, APK install, inference
or physical retry happened in this session. The aborted 2026-10-29 session stays INCOMPLETE and no
Phase 2 gate is claimed or re-scored here.

Base: `a7ecedf` (Phase 5 worktree `codex/phase-5-smart-framing-engine`), harness copied unchanged into
the dedicated branch `codex/phase-2-task-cleanup-harness`. The `tools/` payload is identical on the
Phase 2 (`6ecc863`) and Phase 5 (`a7ecedf`) branches except two test files, so the fix applies to both
without touching production code.

## 1. Root cause of the aborted Face-only preflight

The retained local evidence of the aborted session
(`device-evidence/phase2/physical/face-only-20261029/`, ignored, never uploaded) explains the abort
from the device's own log, read-only at the time:

| Time (2026-10-29) | Event |
| --- | --- |
| 19:02:44 | manual controlled launch `am start -n com.aiphotographer.app/.MainActivity`; `Start proc 25243:com.aiphotographer.app for next-top-activity {…/.MainActivity}` |
| 19:05:18 | `am force-stop` killed PID 25243; the package's **task record remained** in the activity stack |
| 19:06:17 | the runner's own `stop_project()` issued `am force-stop` again |
| 19:06:22.230 | platform: `Start proc 30236:com.aiphotographer.app for next-top-activity {…/.MainActivity}` |
| 19:06:22.6 | runner sample recorded `projectPid 30236` and aborted the preflight |

So `am force-stop` is a **process** stop, not a **task** cleanup. A surviving top task can be resumed
by the platform, whose process start then repopulates the package during the very window the preflight
asserts to be quiet. The runner had no task-level cleanup and no way to attribute such a relaunch, so
it could only abort.

Two limits on this finding, stated plainly:

* The retained log holds only the AMS lines above. Whether the platform would have resumed the task
  without the second force-stop (for example on an unrelated focus change) is **not established**.
* Camera ownership (`Active Camera Clients`) was `[]` in every sample and the thermal guard never
  tripped, so this was a harness/model mismatch, **not** a camera leak, a third-party owner or a heat
  event. No product camera-lifecycle defect is established by this evidence.

## 2. Harness changes

`tools/phase2_tasks.py` (new):

* `project_tasks(dump, packages)` parses `dumpsys activity activities` task blocks and returns only
  tasks whose **root** package is an exact member of the passed project package tuple. Attribution
  reads the task header and its first lines only, so an unrelated task that merely embeds one project
  activity is not claimed. A block whose root package cannot be read is skipped, never guessed.
* `ProjectTasks.clean()` is idempotent: on a clean device it runs **no** command at all; otherwise it
  probes the removal verb, removes each attributable task id, re-reads the dump and refuses to
  continue if any task survived.
* `ProjectTasks.removal()` runs `am task` (a help/usage call that changes no state) and requires this
  build's own text to document a `remove` subcommand before any id is passed. If the build documents
  none, or the call fails, the session refuses to start with an explicit error.
* `ProjectTasks.require_clean()` is the task-level preflight invariant; `inventory()` writes every
  observation to `project-tasks.jsonl` next to the existing `camera-idle.jsonl`.
* `await_quiescence(check, tasks, read_pid, …)` requires two separated samples with no project task
  **and** no project process after cleanup, and raises on the first offending sample.

`tools/run-phase2-solo.py`:

* Builds the task owner from the same explicit serial-targeted `adb()` helper (audited in
  `commands.jsonl`), so every device command stays serial-qualified.
* `stop_project_and_reset()` = `stop_project()` + `tasks.clean()`; it is the `CameraIdle` stop
  callback, so both the mid-session stop path and the final cleanup leave **no project task** behind
  for the next session.
* Session start: `stop_project()` → `tasks.clean()` → `guard.start()` → `await_quiescence(…)` →
  the unchanged 30-second NORMAL observation → the unchanged bounded stable-idle wait.
* `tasks.require_clean()` is also asserted in `--idle-only` verification and immediately before the
  app launch, after the existing `idle.require_idle()`.
* The thermal guard still receives the plain `stop_project`, so a thermal emergency path cannot be
  delayed or masked by task work, and its thresholds are untouched.

## 3. Command availability, and what is deliberately not used

`am` task subcommands differ between releases and no ADB command was authorized here, so the harness
does not hardcode a guess: availability is confirmed at run time from the device's own help text, and
the probe result is part of the session's evidence. Whether `am task` documents `remove` on
SM-S918B / Android API 37 is **UNVERIFIED** until the first authorized session prints it; if it does
not, the session stops before any camera or PID observation and reports the build as unsupported
rather than improvising.

`am stack remove` is deliberately **not** used as a fallback: it takes a different id space and can
remove a whole root-task stack, which is wider than "this project's task". No `pm`, `am kill`,
`settings`, connectivity, build or thermal command was added.

## 4. Safety invariants preserved

* Zero project PID and zero active camera client are still asserted at every preflight sample; the
  invariant was strengthened, never relaxed.
* A project task or process appearing **after** successful cleanup is a hard failure
  (`Project activity reappeared after task cleanup`), never permission to continue; a single clean
  sample is not accepted as evidence.
* Cleanup can only address tasks whose root package is an exact project package
  (`com.aiphotographer.app`, `com.aiphotographer.app.test`,
  `com.aiphotographer.perception.mediapipe.test`); only integer ids parsed from the dump are passed to
  `am`, and only after the capability probe.
* Thermal thresholds, rapid-rise window, NORMAL-only preflight, countdown, foreground and release
  checks, bounded availability wait, no-retry policy and evidence journaling are unchanged.
* No production source, CameraX/MediaPipe engine, UI, thermal guard or Gradle file was modified.

## 5. Host-side validation

`python -m unittest test_phase2_camera_idle test_phase2_thermal test_phase2_tasks
test_phase2_cleanup test_phase2_collection test_phase2_framing test_phase2_runner` → **86 tests, 0
failures**. New coverage:

* `test_phase2_tasks.py` (20 tests): exact-package attribution, deduplication of the recents recap,
  unattributable task skipped, unrelated task embedding a project activity not claimed, no command on
  a clean device, ordered removal of several tasks with integer ids only, surviving task fails,
  undocumented `remove` refuses without removing, unreadable help refuses, `require_clean` naming the
  stale id, journal content, and the quiescence gate (first-sample failure, second-sample failure,
  single-sample rejection).
* `test_phase2_runner.py` (5 tests): with a fake serial-qualified `adb`, a `--idle-only` session
  removes the stale task **before** the first camera-idle observation, an already clean device issues
  no removal, a recreated process aborts with `INCOMPLETE` and no `am start`, an undocumented removal
  stops before any observation, and the session JSON records the failure reason.

No device, emulator or Gradle run was needed: this change is Python host tooling only.

## 6. Smart Framing runtime observation (review only, no change)

The Phase 5 engine is reachable by construction: `MainActivity` creates
`SmartFramingController(camera.perception, SmartFramingSettings(context), …)`, the controller collects
`pipeline.state` on `Dispatchers.Default`, and `SmartFramingSettings.DEFAULT_ENABLED` is `true`, so a
fresh install runs the engine without any setup step.

What a physical smoke test can and cannot observe today:

* The controller logs only on failure, under the `SmartFraming` tag. A healthy run therefore produces
  **zero** log lines, so "no `SmartFraming:E` line" proves only that the engine did not throw.
* Convergence of perception snapshots (`Phase2Perception`) proves the input stream exists, not that the
  engine produced a decision, and the overlay is owned by the separate UI model and absent on this
  build.
* Therefore a smoke test on the current APK can establish reachability and the absence of engine
  exceptions, and must be reported as such. Guidance, overlay behaviour, G-P4, thresholds and
  calibration stay **NOT VERIFIED**.

Closing that gap needs one debug-only, change-gated diagnostic line (for example on strategy or
instruction id change) so a release build keeps today's behaviour exactly. That is a production-source
change and is **not** part of this harness session; it is the recommended next small task.

## 7. Exact next physical procedure (needs separate owner authorization)

1. Rebuild the debug APK from the chosen branch; record commit, path, SHA-256, application id,
   versionCode and signing certificate. Do not reuse an older artifact.
2. `adb -s <serial> get-state` must be `device`; confirm model and API level, and that the serial is
   the intended phone. Never `adb -d`.
3. Do **not** launch the app by hand before the runner. That preliminary launch is exactly what
   created the surviving task on 2026-10-29.
4. Run one guarded session, for example
   `python tools/run-phase2-solo.py --adb <adb> --serial <serial> --configuration rear-portrait
   --face-only --aspect 4:3 --seconds 30 --output device-evidence/phase2/physical/<new-dir>`.
5. Expect, in order: `am force-stop` of the three project packages → `am task` probe → `am task remove
   <id>` if any project task exists → two clean post-cleanup samples → 30-second NORMAL observation →
   bounded stable-idle wait → 15-second countdown → one launch → one 30-second collection.
6. Abort conditions stay as documented: `Project activity reappeared after task cleanup`, "cannot be
   cleaned safely" (no documented `remove`), any project PID or camera client during preflight, any
   thermal stop, foreground loss or collector error. No automatic retry.
7. Keep `camera-idle.jsonl`, `project-tasks.jsonl`, `commands.jsonl`, `thermal-guard.json` and
   `session.json` locally; never upload them.
8. A human face at 50-100 cm must be in frame for the Face continuity gate; without it the collection
   is INELIGIBLE and no acceptance may be claimed.

Emulator runs remain invalid evidence for SM-S918B FPS, thermal behaviour or Phase 2 acceptance.
