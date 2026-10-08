# Phase 2 — consolidated solo physical verification

Status: **PHYSICAL TESTING PAUSED BY OWNER**, 2026-10-08.
No camera launch or sustained inference; only retained-evidence/source review and host tests.
Prior Ready/resume instructions below are historical and overridden by this pause.
Remaining gates are PENDING_PHYSICAL_VERIFICATION. No owner acceptance.
Reference device: Samsung SM-S918B / Android 16 / API 36 only. Do not generalize its results.
This is the single current physical session plan; historical measurements remain in phase-status.md.

## Preparation before the owner returns

Recheck the checkpoint branch/remote and device serial/model/API/authorization/permission.
The final diagnostic APK was installed tonight; reinstall only if needed or if the device build differs.
Preserve completed valid evidence rather than rerunning it. Recheck
merged manifest and model/notice hashes. Keep debug and optimized-profile evidence in separate new
directories under ignored `device-evidence/phase2/physical/`. Never commit/upload screenshots or logs.
No phone is required for `tools/run-phase2-solo.py --plan` or collector regression tests.

The runner requires an installed app with permission already granted. It creates a fresh activity
task, uses observed UI bounds for existing diagnostic controls, and stops the app afterward.
It checks camera permission/displayed orientation first and the `--lifecycle` option batches
home/resume and camera switch/return with local snapshots. It does
not change OS rotation, connectivity or permissions. A missing control stops the run for diagnosis.
The collector rejects overwritten evidence, interrupted collection, process/session changes and
nonmonotonic counters. COMPLETE means collection finished, **not** a tracking or accuracy PASS.
Rolling p50/p95 must not be averaged or described as window-only percentiles.

## Four consolidated physical setups

One tester, no helper: secure the USB-connected phone, good light, roughly 3–4 m where practical,
head/hands/feet visible. Use locally saved preview screenshots to review framing. Start from the
computer and walk into frame during the 15-second session countdown. No precise target pose needed.

Repeat for **rear portrait, rear landscape, front portrait, front landscape**. Physically rotate/reposition
the phone between setups; automation must not substitute forced viewport rotation as physical evidence.
Each debug setup batches both aspects at both requested analysis resolutions. Adaptive degradation can
return to 480p: inspect delivered resolution in Phase1Baseline records and screenshots, and mark an
unachieved 720p condition pending rather than claiming a comparison. Verify actual camera/orientation/
aspect in evidence, not solely the requested directory name. Camera rebinds reset metric sessions.

Example (substitute authorized serial and a NEW output path; run only when the owner is ready):

```powershell
& .tools/python/cpython-3.13.16-windows-x86_64-none/python.exe tools/run-phase2-solo.py `
  --adb C:/Users/anelb/AppData/Local/Android/Sdk/platform-tools/adb.exe `
  --serial R5CW40EE9QK --configuration rear-portrait --debug-matrix `
  --output device-evidence/phase2/physical/session-rear-portrait --trace --lifecycle
```

Each 60-second sequence: 0–10 s still full body; 10–20 raise/lower both arms; 20–30 step left/right;
30–40 turn head; 40–50 approach for a clearly visible face; 50–60 still, then leave view. Repeat after
each countdown. Verify 33-point single-person pose follows motion, 478-point face follows head motion,
no stale/frozen overlay, correct front mirroring, loss/return behavior and qualitative alignment.
These observations do not establish numerical reprojection accuracy or multi-person support.

## Lifecycle, optimized baselines and soak

After tracking works, automate background/foreground and camera-switch return within the same setup;
compare before/after local screenshots and continued counters. Verify manual capture through the existing
button and saved URI; retain any photo locally only. Do not change connectivity without recording and
restoring its exact state. A physical airplane-mode run remains pending until performed; no-INTERNET
manifest plus emulator network-denial tests are independent automated evidence.

Install profile APK after debug combinations. Run one fresh 60-second baseline per requested 480p/720p
using the same runner without `--debug-matrix`, adding `--analysis720p` for the latter. Profile defaults
to rear 4:3; existing camera switch supports front. Debug controls are absent. Record actual resolution,
model/delegate configuration and degradation. Profile uses R8 but includes instrumentation ABI keep
rules: its memory footprint is not an exact release measurement. Native GPU placement is not proven
by a configured delegate label.

Run `--seconds 300 --trace` at a stable tracking configuration for thermal/preview/backlog/soak review.
Retain rolling pose/face/batch latency, effective inference rate, cadence/busy skips, errors, conversion
metrics, PSS/RSS and thermal samples. CameraX internal drops remain UNKNOWN. Review Perfetto
FrameTimeline if available; trace collection alone is not a preview stability measurement.
Check application exit info, AndroidRuntime and crash/ANR records before/after; an interrupted or
poorly framed run is incomplete. Quantitative image reprojection remains unmeasured.

Estimate: **four physical setup confirmations plus one optimized baseline/soak setup**. Additional
interaction is needed only for permission/connectivity confirmation or a real framing/device problem.
Automation cannot judge anatomical alignment or substitute a real person with a synthetic fixture.

## Completion handoff

Review actual evidence; fix software defects and repeat affected checks on the final APK. Record only
tests that ran, measurements that exist and remaining pending gates in phase-status.md. Validate,
commit/push the same Phase 2 branch and update Draft PR #3. No acceptance, merge, Premium UI or Phase 3.
Numerical reprojection accuracy remains unmeasured; unsupported-device fallback remains
hardware-unverified; results from Samsung SM-S918B must not be generalized to other devices.


## Safe checkpoint and exact resume order - 2026-10-07

Branch: `codex/phase-2-pose-face-detection`; Draft PR #3, no merge. Exact checkpoint SHA is
recorded after commit in ignored local `device-evidence/phase2/physical/resume-20261007/checkpoint-ref.json`
and in the owner handoff. The tracked checkpoint is the commit containing this section:
`git log -1 --format=%H -- docs/phase-2-physical-verification.md`.
Samsung SM-S918B, Android 16 / API 36 only. Debug APK SHA-256:
`93238a714c763fe961fa0c335b5da593211f38f4615db6513c84faec743a975d`.
Profile APK retained unchanged: `a30d2ffc920e1a4323fcabc4bab5a34df233f8c930788572ef4aec24ac0f2419`.

### Completed collection; preserve and review first

Rear portrait has four completed 60-second windows and a completed 15-second lifecycle-return
window. Screenshots/video show real human full-body pose, arm/side changes, facial landmarks,
and clearing when the visible subject/face is lost. Successful validated callbacks enforce 33
pose / 478 face output points. Qualitative evidence is not numerical anatomical alignment or
complete head-turn/freshness acceptance. Transient lateral misalignment is under review,
not a diagnosed/fixed product bug. Still/head-turn/return behavior must be judged from retained
evidence before deciding whether a targeted additional segment is necessary.
CameraX preview operational; rear portrait both aspect controls exercised. Background/resume,
camera switch/return snapshots and continued human detections captured. Retain these completed
checks; front tracking/mirroring and remaining orientations still require their own setups.

The following are actual DEBUG diagnostic counters/rates and the last rolling task-to-callback
latency snapshots. Percentiles include earlier samples in the same task's rolling window, are
not capture-window-only, and must not be averaged. No optimized baseline or target PASS claimed.

| Window | Pose / face detections | Pose / face Hz | Pose p50 / p95 ms | Face p50 / p95 ms | Busy / cadence skips | Errors |
| --- | --- | --- | --- | --- | --- | --- |
| Portrait 480p 4:3 | 258 / 3 | 6.683 / 1.320 | 48.017 / 55.595 | 26.102 / 43.325 | 0 / 1396 | 0 |
| Portrait 480p 16:9 | 370 / 9 | 7.414 / 1.545 | 45.025 / 68.896 | 30.544 / 59.201 | 16 / 1337 | 0 |
| Portrait requested 720p 16:9, delivered 480p | 329 / 9 | 7.353 / 1.477 | 49.834 / 78.881 | 29.351 / 72.437 | 96 / 1261 | 0 |
| Portrait requested 720p 4:3, delivered 480p | 394 / 25 | 7.386 / 1.685 | 55.685 / 67.379 | 38.581 / 57.237 | 77 / 1286 | 0 |
| Portrait lifecycle return, 15 s | 70 / 6 | 7.220 / 1.096 | 56.120 / 62.191 | 35.240 / 64.386 | 70 / 282 | 0 |
| Landscape 480p 4:3, partial body only | 295 / 5 | 7.372 / 1.315 | 51.226 / 70.173 | 28.771 / 51.402 | 3 / 1354 | 0 |

Requested 720p adapted to 480p before capture; sustained comparison is pending. Portrait fourth
window and landscape first window overlap screen recording, so label overhead explicitly.
Memory/thermal samples and trace files are preserved in each run; thermal reached MODERATE (2).
These are short diagnostic observations, not five-minute soak or release/profile footprint.
CameraX internal dropped frames remain UNKNOWN. Initial portrait trace ring overwrote data:
first trace retained ~30 s, so its compositor result is not a whole-60-second result. Preserve
raw traces for bounded retained-window analysis; no whole-window stability gate marked PASS.
No final sustained crash/ANR audit completed. Existing exit-info records are retained for review.

### Partial/invalid landscape

`rear-landscape/01-480p-4x3/` finished capture, but table edge obscured lower legs/feet.
Preserve as partial-body/camera-orientation evidence; full-body landscape gate is NOT passed.
`rear-landscape/02-480p-16x9/` was interrupted for framing correction: INCOMPLETE,
invalid counter window, no accepted rates/PASS. Partial trace retrieved; no evidence overwritten.
The other landscape matrix windows never ran. No completed landscape lifecycle suite.

### Bugs and instrumentation fixes tonight

- Windows cp1252 UI decoding crashed before human capture. Runner now explicitly decodes ADB
  text as UTF-8; four portrait windows and landscape startup reran successfully without this error.
- System scheduler tracing filled the initial ring. Runner now uses `tools/phase2-perfetto.pbtxt`:
  app gfx/view/task traces, process stats and FrameTimeline, streaming file writes and periodic
  flush. Final 5-second smoke parsed without nonzero error/overwrite stats. Long-run retention
  still requires review. First smoke's missing-flush warning retained, fixed in final template.
- Host interrupt bypassed runner cleanup; owned device trace stopped/retrieved explicitly and
  metadata marked incomplete. Future interruption must include owned-process/trace audit.
- No Android production bug established or code/APK changed tonight. Table obstruction is a
  setup failure, not app failure. Lateral overlay lag remains an evidence-review question.

### Remaining work without repeating completed valid checks

1. Confirm checkpoint SHA/remote, clean tree, ADB/device/build; review retained portrait evidence
   for movement alignment, stationary/head-turn/loss/return and freshness. Only a demonstrated
   evidence gap or affected code fix warrants targeted repeat.
2. First human setup: **rear landscape**, raise lens above table edge, slight downward tilt,
   whole head/hands/feet visible at roughly 3-4 m. Check framing before requesting confirmation;
   then use 15-second countdown and fresh output directory. Complete missing full-body landscape,
   both aspects/requested resolutions and landscape lifecycle/switch behavior. Preserve first
   completed partial-body window; rerun only checks invalidated by its obstruction.
3. Front portrait and front landscape setups: full-body/face movement, mirroring, alignment,
   both aspects/requested resolutions, physical orientation transition and appropriate lifecycle.
4. Manual capture/saved URI check; offline airplane-mode physical test with exact connectivity
   state recorded/restored, local pose/face/model operation. No INTERNET manifest reconfirmed
   tonight; no network/cloud/upload dependency introduced. Physical offline gate is still pending.
5. Final optimized profile 480p/720p 60-second human baselines, actual delivered resolution,
   task/delegate/degradation and conversion/freshness/skips/FrameTimeline/memory/thermal review;
   final 300-second soak and sustained crash/ANR review. No profile collection ran tonight.
6. Fix reproduced Phase 2 defects if found, rerun only relevant automated/physical checks,
   then required final validation and accurate documentation/commit/push. No owner acceptance.

### Safe shutdown and local evidence

After the owner reconnected the phone, camera app and installed project library test app were
force-stopped. No task-owned device trace/logcat/screenrecord/project app or host countdown/
collector/logcat/trace-processor remains. Auto-rotation restored/read back to original
accelerometer_rotation=0, user_rotation=0. Connectivity unchanged. Normal ADB/system daemons
are not verification sessions. Android's instrumentation dump subcommand was unsupported;
absence of project test processes is the cleanup evidence, not that failed query.

Evidence root: `device-evidence/phase2/physical/resume-20261007/` (ignored, local only).
Completed portrait: `rear-portrait-utf8/`; failed pre-capture decoding attempt: `rear-portrait/`.
Partial landscape: `rear-landscape/`; instrumentation-only smoke: `trace-stream-smoke/`
and final `trace-stream-flush-smoke/`. Logs, screenshots, motion recordings/extracted frames,
raw traces, counter summaries, memory/thermal samples, exit-info and review artifacts preserved.
`checkpoint-inventory.json` hashes local files; `stop-state.json` and `device-state.json` record
cleanup/restoration; `checkpoint-ref.json` records exact committed/pushed SHA after checkpoint.
No raw evidence, pictures or video committed/uploaded. Closing Codex/disconnecting/shutting down
is safe after the final checkpoint push is verified. Resume only when the owner requests it.


## Owner stop / critical thermal incident ? 2026-10-08

All Phase 2 physical testing is STOPPED by explicit owner instruction. Do not relaunch the
camera or resume testing. No production code, APK, architecture or thermal policy changed.
Samsung SM-S918B / Android 16 / API 36, existing debug diagnostic build only.

THERMAL INCIDENT: rear-landscape attempt is INCOMPLETE. All seven retained first-window
thermal samples report CRITICAL (4); initial SKIN 48.7 C, later SKIN 48.9 C, follow-up
48.8 C. App was force-stopped; host runner exited and device process audit found no project
app, logcat, screenrecord or perfetto. Trace metadata confirms STOPPED_CAPTURE_RETRIEVED.
The raw first 480p 4:3 collector completed its 60-second window before the stop took effect;
retain its COMPLETE summary unchanged as diagnostic evidence, not a physical gate PASS.
Matrix session.json is INCOMPLETE. No later aspect/resolution/lifecycle window ran.

EVIDENCE PRESERVED: all earlier portrait/partial-landscape/checkpoint files unchanged.
Rehashed all 325 entries of the previous checkpoint inventory: 0 mismatches. New 384-file
inventory, incident metadata, prior-inventory verification, retained failed-control UI and
process audit are local under device-evidence/phase2/physical/thermal-stop-20261008/.
Raw evidence remains ignored and must not be committed/uploaded. New run remains at
rear-landscape-resume-20261008-1820/; setup screenshots retained separately.

LOG-ONLY INVESTIGATION: critical state already existed at the first collector sample, so
these logs cannot attribute onset to this 60-second window. App remained open during setup;
pre-collection thermal history, ambient conditions and other workload attribution are absent.
Perception logs report degradationLevel=6 throughout but continue pose/face callbacks,
configured GPU labels (actual native placement NOT proven), preview capture near 29.7 FPS
(not display FPS), analysis delivery near 26.8 FPS and delivered 640x480. Last rolling
RGB conversion p50/p95 is 56.65/69.45 ms; pose 69.19/97.78 ms, face 46.68/86.93 ms,
batch 128.57/181.64 ms. These are rolling snapshots, not window-only percentiles.
Continued camera/conversion/inference load despite degradation, warm start, and diagnostic
trace/screenshot overhead are possible contributors, not established root causes. Preview
screenshots show charging indicator and floating overlays; their power/CPU contributions
are unmeasured. PSS ranges 416827?447304 KiB, RSS 499664?528272 KiB with nonmonotonic
variation: no leak established by this short window. Same PID/session and zero inference
errors; no AndroidRuntime exception found in retained app log. No duplicate pipeline or
resource leak proven. Collector did not abort automatically on thermalStatus=4; the manual
stop occurred after the first collector window finished. Preflight/abort thermal handling
is a tooling follow-up requiring a documented policy before any future physical testing.
No new device experiment was run to investigate causes.

MISSING CONTROL: runner traceback occurred while selecting the second aspect after deliberate
app force-stop. Retained /data/local/tmp/phase2-solo.xml was copied locally; its packages are
com.sec.android.app.launcher and com.google.android.googlequicksearchbox, with no test app.
Thus the failed aspect lookup is consistent with the stopped app/launcher foreground, not
evidence of an independent app UI defect. Do not classify as an independent bug without
contradicting evidence; no reproduction or camera relaunch performed.

REMAINING: review retained first-window human motion/framing (a lateral sample places the
subject partly beyond the left edge); no full-body landscape acceptance yet. Other landscape
aspects/requested resolutions/lifecycle, front portrait/landscape, manual capture/offline
physical gates, optimized baselines and soak remain pending. Preserve valid portrait checks;
repeat only demonstrated invalid/missing checks if the owner later authorizes testing.
All physical work remains stopped, regardless of thermal recovery. Owner acceptance pending.

SAFE CHECKPOINT: same codex/phase-2-pose-face-detection branch and Draft PR #3; documentation
checkpoint only. Exact SHA and verified remote head recorded after push in ignored local
thermal-stop-20261008/checkpoint-ref.json. No raw images/logs/traces added to Git.


## Thermal recovery preparation - 2026-10-08

Owner authorized conditional resume from 532426bec5d8eff82316040339ee17ab64369583;
this supersedes the earlier unconditional STOP only for the new safe workflow.
Physical collection still requires Ready per setup and fresh normal-status checks.
Verified branch codex/phase-2-pose-face-detection, origin and exact remote/local checkpoint;
initial working tree clean. Rehashed all 384 previous inventoried evidence files: unchanged.
Local-only preflight: device-evidence/phase2/physical/thermal-recovery-20261008/.
Samsung SM-S918B / Android 16: initial thermal 0, SKIN 36.2 C, BAT 34.7 C, AP 38.3 C;
no camera process. USB charging active; no OS thermal/connectivity override. This initial
normal reading does not replace the next launch preflight.

LOG ANALYSIS: first previous sample already critical; onset/root cause unknown. Continued
camera/inference/conversion workload despite degradation 6 and diagnostic overhead are
possible contributors. No excessive concurrent inference batches established. Reusable
RGB/direct task buffers and existing backpressure remain unchanged. Nonmonotonic PSS/RSS
and stable PID do not establish allocation leak or duplicate pipeline. Native allocations,
actual GPU placement, charging/other-app contribution and allocation rates remain unmeasured.
Missing aspect error remains consistent with launcher foreground after app stop, not an
independently established UI bug.

FIXES: shared host thermal guard, bounded single-window runner and cooldown (ADR-020).
Single --aspect selection resumes missing conditions; matrix/lifecycle batching rejected.
Abort force-stops app and marks session incomplete, without automatic retry. RGB conversion
hoists row/destination indexing outside pixel loop; output compared against reference for
odd crops, nonneutral chroma, strides, positions and four rotations. Device improvement is
unmeasured; no thermal-fix claim. Production inference schedule unchanged. Keep traces optional
and screenshot/memory cadence ~10 s while thermal polling runs independently at ~2 s.

NEXT PHYSICAL SETUP after Ready: rear landscape 480p 4:3, targeted 30-second full-body/lateral
framing and motion check. Prior window has lateral clipping, warranting only targeted invalid/
missing coverage, not repeating valid portrait tests. Guarded framing review and 15 s countdown
required. Stop/cool down and review before other landscape conditions or front setups.
If framing is wrong, stop and correct. Capture/offline/lifecycle, optimized baselines and soak
remain pending; long soak deferred until short-window thermal behavior is understood.
Awaiting new Ready; no camera relaunched. No interrupted PASS, Phase 3, Premium UI, merge
or owner acceptance.

Validation: camera debug unit tests (including new conversion equivalence), perception API
tests, debug/profile/release builds and app lintDebug PASSED. Python evidence/thermal suite:
8 tests PASSED. Full spec validation using cached jsonschema/referencing: 0 errors, 0 warnings.
No new device behavior/performance test or acceptance claimed. Prepared debug APK hash: 915b900a923e8a5e95583f983fbca9b1eb6bab48bf98b4f00912fe0772cb5e11.
Guarded --framing-only saves a setup screenshot then stops app without collecting test metrics;
use after Ready to review framing before the separately bounded test invocation.
Camera remains stopped and new APK device verification remains pending Ready.


### Guarded rear-landscape framing check - 2026-10-08
Owner confirmed new Ready. Clean checkpoint ab02d7bf5c37186d0782448366d630541116b5e2
verified; fresh initial device check status 0, SKIN 37.8 C, BAT 35.9 C, app stopped.
Installed prepared debug APK 915b900a923e8a5e95583f983fbca9b1eb6bab48bf98b4f00912fe0772cb5e11.
Ran only --framing-only after guarded 30-second camera-off normal-status cooldown.
Local evidence: device-evidence/phase2/physical/recovery-framing-20261008/.
Guard samples all status 0; final SKIN 39.7 C. Camera auto-stopped after setup snapshot;
runner exited 0, session FRAMING_ONLY_NO_TEST_COLLECTION. Pose overlay visibly present,
but subject lower legs/feet clipped at preview bottom: full-body framing NOT ready.
No countdown, physical test collection, performance PASS or repeated completed tests.
Next: reposition subject/phone to include whole head/hands/feet with margin; owner Ready
retained for this setup, but request adjustment notification and recheck before collection.
Camera stays stopped meanwhile; fresh cooldown/thermal checks required on next launch.

Post-check process audit found an app process present after runner exit (cause unestablished).
Explicitly force-stopped again; pidof empty and camera service Active Camera Clients [] verified.
No capture/logcat/perfetto processes observed. Keep app closed pending framing correction.


### Adjusted framing refused by thermal preflight - 2026-10-08
Owner requested full-body recheck with no collection if head/feet cropped. Guard refused
before camera launch: Android status 1 (LIGHT), SKIN 39.3 C; required status is 0.
Fresh local evidence recovery-framing-adjusted-20261008/ retained, session INCOMPLETE.
No framing image, countdown or 30-second collection ran. No automatic retry.
Read-only follow-up showed status 1, SKIN 40.1 C, BAT 36.4 C and app PID 24691 owning
rear camera 0 despite runner refusal/force-stop. Cause of reactivation unestablished;
not evidence that guarded runner launched it. Explicitly force-stopped again and verified
pidof empty / Active Camera Clients []. Owner advised to keep app closed during cooldown;
agent will reopen only after fresh normal checks. Framing remains unverified, no PASS.
Prior evidence unchanged. Physical testing stopped on elevated preflight as instructed.
Next: cool phone with app closed; fresh thermal preflight and guarded head/feet review,
then 15-second countdown only if framing passes and thermal remains acceptable.


### Physical pause / source-only thermal review - 2026-10-08

Owner paused ALL physical testing and camera/inference launches. Prior Ready is not permission
to resume during this pause. Reviewed retained logs/trace/source only; no ADB or device activity.
Findings and next safe test: [phase-2-thermal-review.md](phase-2-thermal-review.md), ADR-021.
Genuine defect fixed: framing-only host path previously initialized full AI/analysis. New debug
preview-only path creates no MediaPipe pipeline and binds no analyzer/capture, omits sensor/
diagnostic overlay work, selects camera/aspect at launch, and closes within a best-effort 12 s
monotonic deadline. Host rejects missing preview-only label. Full countdown moves before camera
launch, eliminating 15 s of idle inference. LIGHT launch policy reviewed but kept conservative;
SEVERE/CRITICAL/rapid-rise abort and Android protections unchanged. Root thermal cause unresolved.
Trace query retained locally at device-evidence/phase2/physical/framing-source-review-20261008/;
no source evidence modified. Device gains/unattended shutdown behavior UNVERIFIED.
All physical gates pending and tests paused; no acceptance/merge/Phase 3/Premium UI.


Host validation for paused investigation: debug/profile/release builds, camera unit tests,
perception API tests and app lintDebug PASSED. Host collection/thermal/framing integration suite:
11 tests PASSED (mocked ADB only, no device). Full spec validator: 0 errors, 0 warnings.
Diff whitespace check passed. Reverified previous 384-file evidence inventory: no mismatches.
No new APK installed or device check run; preview-only behavior and thermal gain pending physical
verification after explicit resume. Local trace-derived review/inventory retained, never uploaded.


### One preview-only request blocked before launch - 2026-10-08
Owner Ready authorized one bounded rear-landscape preview-only check, no AI/analysis/collection.
Checkpoint 9d57e8d6851d60c8257939f4b38dae83e99ea5c9 / clean tree verified. Initial read status 0,
SKIN 38.3 C; later pre-install read status 0 / SKIN 39.8 C. Installed validated debug APK
8899dbbd861d4a320cb163d5a72722a58947e75f295f74a289f24e34352645e4 without launching.
During camera-off cooldown guard observed status 1 / SKIN 40.3 C and refused launch.
Runner cameraLaunches=0, INCOMPLETE, no framing screenshot or countdown/collection/inference
requested. Preview-only mode and 12-second automatic shutdown NOT EXERCISED; no PASS.
Subsequent audit unexpectedly found app PID 6692 owning rear camera 0 despite runner refusal
and cleanup. Reactivation source UNKNOWN; explicitly force-stopped again, pidof empty and
Active Camera Clients [] verified. Final post-stop status 1 / SKIN 40.1 C; maximum across
recorded samples 40.3 C. Full head/hands/feet visibility UNKNOWN (no new framing image).
Local evidence: device-evidence/phase2/physical/preview-only-once-20261008-191941/;
previous evidence/checkpoints unchanged. No retry; owner advised to keep app closed.
Errors: cooldown normal-status requirement failed; unexpected external app reactivation
unresolved. Testing remains stopped with elevated thermal state; prior unfinished gates pending.


### Camera reactivation investigation / camera-idle invariant - 2026-10-08
PHASE: 2; STATUS: partial, physical camera/inference testing PAUSED.
Checkpoint 400a688 verified; same branch/Draft PR #3. No new camera/inference/installation or
instrumentation launch. See [camera idle review](phase-2-camera-idle-review.md), ADR-022.
Retained active client was actual rear camera 0 owned by app PID 6692. Root trigger unknown;
zero harness launches did not imply idle. Confirmed harness defect fixed: missing camera/PID
verification during cooldown and after stop. Added fail-closed ownership invariant, exact
project/test-package stop/verification, idle-only mode, command journal and resilient cleanup.
Normal user startup and all thermal protections unchanged. No product lifecycle bug claimed.
Device currently disconnected; no current device cleanup/thermal state certified. Latest
retained cleanup had empty PID and clients []; current host capture-process inventory empty.
Next: reconnect for cleanup/idle-only diagnostics, no launch. NOT SAFE TO RESUME physical tests.
Evidence preserved; no interrupted PASS, merge, owner acceptance, Phase 3 or Premium UI.


Validation: 20 host collection/thermal/ownership/framing regression tests PASSED, including
idle-only no-launch, reactivation-before-launch abort and guard shutdown despite stop failure.
Camera unit/perception API tests, debug/profile/release builds and lintDebug PASSED (host only).
Full spec validation: 0 errors, 0 warnings. Diff check passed. Rehashed previous 384-file
inventory: 0 mismatches. No physical test, camera launch, inference or instrumentation run.
Current device unavailable; final live cleanup remains UNVERIFIED, not PASS. Local-only
review evidence: device-evidence/phase2/physical/camera-idle-review-20261008/.


### Device cleanup and idle verification ONLY - 2026-10-08
Owner authorized cleanup-only from e041702ea84b91b0575cfa7285e3e9458bba1c1d.
Checkpoint/clean tree verified; Samsung SM-S918B serial R5CW40EE9QK connected/authorized.
No camera launch, MediaPipe, inference, installation, physical test or thermal override.
Initial, post-cleanup, ~3-second interval and final samples: no com.aiphotographer process,
no active camera client. 30.746-second idle observation; no reactivation observed. No device
logcat/perfetto/screenrecord candidates; no host task-owned collectors in inventory.
No package/process needed stopping. Device cleanup/idle VERIFIED for sampled window only.
Thermal status NORMAL (0) throughout. Battery 34.6 -> 34.9 C, USB charging unchanged.
Skin initially 35.7 C, minimum 35.6 C, final/max 38.3 C. Rise of 2.7 C within a sampled
interval under 30 seconds meets existing rapid-rise abort seed, despite no observed project
camera/inference. Root heat contribution remains unresolved; no new attribution claim.
SAFE FOR NEXT SHORT TEST: NO; require cooling/stable thermal trend and fresh idle check,
then explicit authorization/Ready. No automatic retry or physical gate PASS.
Local evidence: device-evidence/phase2/physical/cleanup-idle-20261008-193104/ includes raw
process/camera/thermal snapshots, battery reports, device/host times, collector inventory,
report and safety review. Previous 384 inventoried files remain unchanged. Prior checkpoint
preserved; physical tests remain paused. No merge, acceptance, Phase 3 or Premium UI.
