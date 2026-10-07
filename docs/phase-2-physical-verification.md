# Phase 2 — consolidated solo physical verification

Status: **PARTIAL / SAFE CHECKPOINT**, owner stopped for the night on 2026-10-07.
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
