# Phase 2 — consolidated solo physical verification

Status: **PENDING_PHYSICAL_VERIFICATION**, not an implementation failure. No owner acceptance.
Reference device: Samsung SM-S918B / Android 16 / API 36 only. Do not generalize its results.
This is the single current physical session plan; historical measurements remain in phase-status.md.

## Preparation before the owner returns

Build/install the final diagnostic APK, recheck serial/model/API and authorization, camera permission,
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
computer and walk into frame during the default 20-second countdown. No precise target pose needed.

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
