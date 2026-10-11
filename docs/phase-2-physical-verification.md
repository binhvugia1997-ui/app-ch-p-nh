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


### Fresh cleanup-only idle observation - 2026-10-08 19:39-19:40
Owner repeated cleanup-only request from e041702. Preserved newer descendant fe3a599 and clean
tree; no checkout/reset or evidence overwrite. Samsung SM-S918B R5CW40EE9QK connected.
No camera/MediaPipe/inference/physical test launched. Fresh local evidence:
device-evidence/phase2/physical/cleanup-idle-20261008-193941/.
31.693-second idle observation plus initial/final snapshots: no project processes, no active
camera clients, no unexpected reactivation, no device/host task-owned collectors. No stop needed.
Thermal NORMAL (0) throughout; SKIN initial/max 36.7 C, final 36.5 C; battery 34.9 -> 34.8 C.
No >=2 C/30 s rapid rise observed. Cleanup/idle verified for sampled window; no errors.
Eligible for a next short guarded preview-only check ONLY after explicit Ready and fresh
preflight; this result does not authorize automatic launch or inference/collection. Earlier
rapid-rise observation remains preserved. No Phase 2 acceptance, merge or Phase 3.


### ONE guarded rear-landscape preview-only framing check - 2026-10-08
Owner explicitly requested one check and confirmed Ready via input. Checkpoint f02d096,
branch and clean tree verified. SM-S918B R5CW40EE9QK connected; idle-only preflight verified
NORMAL (0), no rapid rise, no project PID/camera client; no launch during preflight.
Ran exactly one guarded --framing-only rear-landscape 4:3 launch on installed validated
8899dbbd861d4a320cb163d5a72722a58947e75f295f74a289f24e34352645e4 debug APK.
UI label confirms analysis disabled; developer/perception overlays absent. Local screenshot
shows full head, both hands and both feet inside frame (current pose only; motion coverage
not tested). New path skips MediaPipe factory and ImageAnalysis; no inference/collection run.
Host launch-command to automatic cleanup 9.375 s, before 12-second activity deadline. The
12-second timer is configured but was not independently exercised because host stopped earlier.
Cleanup verified zero project/test PID and zero active camera clients. No retry/errors/guard
trigger. Guard thermal status 0 throughout; before 36.0 C, after/max 36.3 C SKIN. Additional
~12-second post-shutdown observations remained status 0 / 36.3 C, zero PID/client, no reactivation.
Evidence preserved locally at guarded-preview-only-20261008/ and prior idle-only preflight at
preview-ready-preflight-20261008/ under device-evidence/phase2/physical/. Previous 384-file
inventory unchanged. Framing-only success is not full-inference tracking, performance, alignment
or landscape-matrix PASS. No repeated completed tests, merge, acceptance or Phase 3.
Safest next: keep camera stopped, cool/rest, review this result; only a separately authorized
short inference test with new Ready, fresh idle/thermal checks and camera-off countdown may follow.


### One authorized rear-landscape inference window - 2026-10-08
Continued from e1a511aa2797ddb440a2f76d1b73ad8b1e9fd82b on the existing Phase 2 branch.
Owner accepted prior preview-only framing and confirmed Ready. Exactly one launch, no retry.
Samsung SM-S918B / Android 16 API 36, installed validated debug APK
8899dbbd861d4a320cb163d5a72722a58947e75f295f74a289f24e34352645e4.
Camera-off idle preflight: NORMAL (0), stable SKIN 35.8-35.9 C, no project PID/camera client.
Runner repeated guarded cooldown and 15-second camera-off countdown before rear-landscape
4:3 inference. Delivered analysis 640x480/crop 640x480. Local MediaPipe logs report pose=GPU,
face=GPU; hardware execution placement was not independently profiled.

Collection completed: 30.000 s requested sampling window, 30.679 s collector wall time;
29.787 s counter interval. Launch-command to force-stop was 40.024 s including startup/UI
verification and collection teardown, so this was not a 30-second total camera-on limit.
Counter deltas: pose completed/detected 230/230 (7.721 Hz), face completed/detected 56/1
(1.880 Hz), errors 0; offered 894, accepted 230, busy skipped 17, cadence skipped 647.
Face coverage is insufficient for PASS. Saved preview shows pose landmarks; diagnostic overlay
obscures the upper body/head area, limiting visual alignment assessment. Prior full-body framing
remains accepted, but numerical overlay alignment and motion/face coverage remain unverified.
Last rolling task-to-callback latency p50/p95 ms: pose 37.664/41.864, face 24.405/49.330,
batch 76.804/94.933. These are rolling snapshots, not exact window-only percentiles.
Last RGB conversion/rotation rolling p50/p95 35.920/49.567 ms. App-reported preview capture
~30 Hz does not measure display FPS or CameraX internal drops. Adaptation degradation level
4 -> 6 is app policy, not Android thermal status. Optimized-build performance, CPU/power,
allocation rate, display FPS and thermal endurance are unmeasured in this run.
Sampled PSS 459420-495563 KiB; RSS 540388-577760 KiB; peak between samples unknown.

Android thermal status NORMAL (0) throughout both guards, no rapid-rise guard trigger.
Runner preflight SKIN 35.3 C, near-launch 35.4 C; end/max 37.2 C. Post-stop three snapshots
across ~10 seconds: NORMAL (0), SKIN 37.2 -> 37.0 -> 37.0 C, no project/test PID and no
active camera clients. Runner cleanup also verified zero clients/PIDs; host collector/logcat
process inventory empty. No test errors, automatic retry, thermal override or further launch.
Evidence retained locally under device-evidence/phase2/physical/guarded-inference-one-20261008/
and inference-one-preflight-20261008/. Earlier 384-file preserved inventory hashes unchanged.
No source changes. Collection completion is not landscape matrix PASS or owner acceptance.
Remaining: sufficient face coverage, front/mirroring, capture/offline/lifecycle outstanding items,
overlay alignment and optimized/performance gates per the existing matrix; completed evidence
is preserved and not repeated. Safest next: camera-off cooldown and evidence review; only a
separately authorized bounded setup with fresh Ready/normal-status idle preflight may follow.
Prefer a strict total camera-on deadline for future short runs to bound startup overhead too.
No merge, Phase 3 or Premium UI.

Session validation: 20 host-only Python regression tests passed; cached full spec validation
0 errors/0 warnings; git diff --check passed. No source/APK changes, so prior validated
build/lint results remain applicable; builds were not rerun for this evidence-only update.


### Face-only verification preparation - 2026-10-10 (physical pending)
Continued from 768634306b90811004bc6b20de5c2a27211ebf52; branch/clean baseline verified.
Reviewed existing face evidence and source: 1/56 detected in prior 29.787-second window,
zero errors, 1.880 Hz, rolling face p50/p95 24.405/49.330 ms. Small face at full-body distance
and pose-dependent scheduling are plausible limits, not a proven crop/rotation/confidence bug.
Bundled MediaPipe 0.10.32 defaults checked locally: detection/presence/tracking 0.5 unchanged.
No confirmed product defect or threshold change. See docs/phase-2-face-review.md and ADR-023.
Added debug-only isolated face mode: no pose construction/inference or subject prerequisite,
existing face cadence/backpressure and safety retained, no pose-model rebuild in isolated mode.
Runner --face-only requires a single 30-second collection and current-PID mode confirmation.
Added pure 478 finite XYZ/optional confidence validity helper/tests and faceValid478 aggregate
counter; old logs remain readable. Count/finite validity is not a visual accuracy claim.

ADB device list empty; idle-only attempt failed get-state before thermal access/camera launch.
Evidence: device-evidence/phase2/physical/face-only-idle-20261010/ and face-only-review-20261010/.
No APK installed, inference or physical test run; current device thermal/idle/cleanup unverified.
Prior 384-file evidence inventory unchanged, pose evidence preserved. No completed tests repeated.
Pending exactly one close-face setup: rear-landscape, one face 50-100 cm, even lighting; initial
forward face then gentle left/right turns; no full-body requirement. Reconnect device and wait
for owner Ready before any launch. Fresh NORMAL/stable idle checks and 15-second camera-off
countdown mandatory; thermal monitoring, partial evidence preservation and no automatic retry.
Report face completed/detected/valid478, rates/rolling latency, sampled memory/thermal trend,
actual launch-to-stop time including startup, and verified post-stop camera/process/collector release.
No acceptance, merge, Phase 3 or Premium UI. Physical face-only behavior remains unverified.

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


### Rapid-rise reconstruction - 2026-10-11 (HOST REVIEW ONLY)
Continued from 650bb980ad9ee7b068b0055ed18d56ab32c081ad on the existing Phase 2 branch.
No ADB/device commands, camera launch, inference, install or physical retry in this review.
Raw source: ignored guarded-face-portrait-20261011/thermal-guard.json (42 samples),
commands.jsonl (42 matching thermal requests), child thermal-guard.json, four raw thermal
snapshots, samples.json and app.log. All prior evidence retained; parent/child summaries unchanged.

Thermal timeline (UTC, command REQUEST times, not exact sensor acquisition times):
- 08:25:24.083: first parent request; NORMAL / 34.5 C. Camera-off preflight remained 34.5-34.6 C.
- 08:26:20.217: request for parent sample 28, NORMAL / 34.5 C,
  monotonic 5064894.5621182; near end of camera-off countdown, 3.176 s before launch command.
- 08:26:23.393: sole app launch command; subsequent log metrics confirm face-only inference.
- 08:26:28.546: last reported 34.5 C (sample 32), request 5.153 s after launch.
- 08:26:30.636: first reported rise to 35.1 C, request 7.243 s after launch (startup inference).
- 08:26:38.928: first reported 35.9 C, request 15.535 s after launch (collector/inference active).
- 08:26:49.359: sample 42 request, NORMAL / 36.6 C, monotonic 5064923.6914649;
  first force-stop request 08:26:49.412. Launch-to-stop command interval 26.019 s.
- Child monitor later reported 36.7 C; post-stop samples fell 36.5 -> 36.3 C with zero camera/PIDs.

Exact trigger reconstruction: guard selects earliest sample with 0 < elapsed <= 30 s.
Sample 27 at 5064892.4930286 is excluded (31.1984363 s old). Sample 28 is selected:
5064923.6914649 - 5064894.5621182 = 29.1293467 s;
36.6 - 34.5 = 2.1000000000000014 C in floating-point, exceeding unchanged >=2 C threshold.
The baseline precedes launch but is correctly inside the rolling window. The reported rise
occurs after launch, during startup and collected inference; no rise is reported in preflight
or countdown. Using the last 34.5 C sample also gives +2.1 C in 20.7824940 s, so this stop
is not caused by including a countdown baseline. No baseline reset at launch is appropriate.

Findings: no confirmed measurement/calculation defect causing this interruption. All 42 parent
monotonic timestamps strictly increase, sampling intervals 2.055-2.108 s; no unit mismatch,
reordered/duplicated timestamps or expired-window baseline at trigger. Wall clock is used only
for command audit, never for thermal subtraction. Both monitor processes use host monotonic
clock but separate histories: child starts at 35.1 C, so its observed +1.6 C does not trigger;
this is not contradictory evidence. Android NORMAL and the independent rapid-rise guard can
legitimately disagree about whether the engineering stop condition is met.
Parser consistently selects mType=3 / mName=SKIN. Four saved full dumps show cached/HAL
SKIN pairs equal at 35.1, 35.9, 36.6 and 36.7 C; AP/BAT are not accidentally substituted.
Parent polls retain parsed values and host completion timestamps, not complete raw payloads
or HAL acquisition timestamps. Thus cache freshness for every parent sample, physical sensor
accuracy and exact update latency cannot be proved. Repeated plateaus are compatible with
HAL/cache update cadence, not proof of stale data. Evidence supports a real reported rise;
causal heat contributions from camera, conversion, inference, display or diagnostics remain
unmeasured. No claim that inference alone caused it. Earliest-in-window comparison is existing
policy, not a rolling-minimum calculation; this run's flat baseline does not distinguish them.

Fixes: no production or guard changes justified. No thresholds, Android protections or launch
requirements changed. Added five host regression tests: full sanitized 42-sample replay and
exact selected baseline; inclusive 30 s / expired boundary; non-positive elapsed exclusion;
child-versus-parent history; SKIN selection among AP/BAT and equal cached/HAL entries.
Validation: 43 Python host tests PASS; debug/profile/release assemble, app lintDebug and 28
geometry/perception API/camera Kotlin unit tests PASS (Gradle incremental tasks accepted).
Initial validation command used nonexistent JVM testDebugUnitTest tasks; corrected to :test
for pure JVM modules and reran successfully. No device instrumentation executed.
Spec validation and diff whitespace checks required before checkpoint; results recorded below.
Partial face results remain 160/160 valid478 over 16.610 s, 9.633 Hz, NOT a completed 30 s PASS.
Next safe action: remain paused with camera off; review workload using existing source/logs.
Any new physical attempt needs separate owner authorization plus fresh normal/stable idle
preflight and Ready. No automatic retry, merge, owner acceptance, Phase 3 or Premium UI.
Final host validation: spec validator 0 errors / 0 warnings; diff check PASS; original 384-file evidence inventory rehashed with zero mismatches.
