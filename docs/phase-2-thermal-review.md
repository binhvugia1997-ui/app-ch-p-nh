# Phase 2 thermal and framing review - 2026-10-08

Status: PHYSICAL TESTING PAUSED by owner. Source/evidence review and host validation only.
No ADB, camera launch, model inference, device installation or new physical capture performed
in this review session. No Phase 2 acceptance, merge, Phase 3 or Premium UI work.

## Findings grounded in retained evidence and source

- The previous --framing-only runner only changed the host collection branch. MainActivity
  still unconditionally created MediaPipePipeline; CameraSession bound Preview + ImageAnalysis
  + ImageCapture, and FrameRouter performed RGB conversion for accepted batches. Developer and
  perception overlays also remained active. Thus even one framing screenshot required full
  task initialization and several seconds of inference. This is a genuine framing-path defect.
- Each check launched a fresh task. Front/aspect controls recreated the Compose camera key,
  reconstructing CameraSession and model owners. This was intentional diagnostic reset behavior,
  but unnecessary for a framing-only screenshot. Normal full sessions still rebind on controls;
  no independent duplicate inference loop has been proven.
- Full test runner previously kept camera/inference active during the 15-second walk-in countdown
  in addition to startup/setup and the 30-second collection. This avoidable work is now removed.
- CameraX uses KEEP_ONLY_LATEST, one analyzer executor and finally closes ImageProxy. MediaPipe
  BatchGate bounds RGB ownership to one batch. TaskImage reuses its direct buffer and releases
  MPImage after callback; model close and executor shutdown paths exist. These source contracts
  and stable prior PID/nonmonotonic PSS do not establish a leak. Native allocation rates and
  actual GPU placement remain unmeasured. Result/snapshot/list allocations still occur; no
  evidence supports claiming them as the principal thermal cause or rewriting them here.
- Retained critical trace has 59.970021 s of data, 387 RGB conversion slices with mean 57.800 ms,
  maximum 139.246 ms and total elapsed duration 22.369 s. Router slices total 24.115 s and include
  conversion; luma slices total 0.273 s. Do not add nested slices or call these exclusive CPU time.
  No nonzero error/overwrite stats found by recorded query. Trace/source review supports prior
  row-index optimization and eliminating conversion entirely from framing, not a claimed measured
  improvement. No trace exists for the subsequent short framing-only attempts.
- The retained framing guard series rose from SKIN 37.9 to 39.1 C during approximately the first
  29 seconds, before the nominal 30-second cooldown ended; final SKIN 39.7 C. All samples were
  status 0. Later refusal recorded status 1 / 39.3 C, then follow-up status 1 / 40.1 C. Process
  audits found the app active again after force-stop; reactivation cause is unknown. Cooldown
  did not continuously verify camera ownership. Residual heat, charging, outside reactivation
  and other apps remain plausible; framing inference alone cannot explain this series.
- Thermal guard uses ~2 s host dumpsys sampling, no on-device inference. The normal collector
  also has a guard, so parent/child monitoring overlaps during collection. Added polling overhead
  is real but its power/CPU contribution is unmeasured; retain it rather than create an unmonitored
  handoff. Framing uses one guard, one screenshot, no trace or periodic memory collection.
- Diagnostic screenshots, memory dumps, trace streaming, UI hierarchy dumps and overlays add
  overhead. Previous warm critical window also includes that instrumentation. No power attribution
  or controlled comparison exists; do not ascribe all heat to one cause.
- Missing aspect lookup remains explained by retained launcher UI after deliberate app stop.
  No evidence establishes an independent missing-control product defect.

## Changes

Debug-only framingOnly intent flag prevents MediaPipe factory creation. CameraSession preview-only
binding contains Preview alone; no ImageAnalysis/analyzer, no ImageCapture binding, no sensor-monitor
start, no perception collector or preview metrics callback. Perception/developer overlays and controls
are omitted. Camera/aspect are selected at launch, avoiding framing-only switch/aspect reinitialization.
Capture is disabled. Release/profile builds ignore the framingOnly extra; normal product behavior is
unchanged. Host checks localized preview-only mode label and refuses an unconfirmed framing image.

Debug framing activity finishes after a 12-second monotonic deadline, retained across recreation;
host also force-stops in finally. The 12-second cap is CALIBRATION_REQUIRED engineering policy,
not a claim about safe exposure. It is not a hard real-time guarantee if Android's main thread hangs.
Slow startup/UI automation may miss the deadline: fail rather than extending it or enabling inference.
One setup screenshot does not prove full-inference tracking or identical sensor crop across use cases.

For full test invocation the 15-second countdown now completes with camera off, before launch/configure.
AI remains active during normal camera startup/control setup, which still has overhead; collection
rates describe their own evidence window and do not imply that startup was free or already measured.

## Thermal policy review

LIGHT (1) is not treated as a SEVERE/CRITICAL event. Current host launch/cooldown policy nevertheless
requires status 0; during active operation guard aborts at >=3 or rapid rise/missing telemetry.
Normal-only launch is deliberately conservative after the critical incident, not a medically validated
limit. It can block a short preview even when status is merely LIGHT. Retained evidence lacks power
and stability measurements sufficient to justify relaxing it. Keep it for this checkpoint, especially
while reactivation and warm-start behavior remain unexplained. SEVERE/CRITICAL and rapid-rise aborts,
Android protections and telemetry fail-closed behavior remain unchanged.

## Safest next physical test (not authorized to run during this pause)

After explicit owner resume and fresh Ready: phone at rest with app closed, fresh normal thermal
checks and cooldown; install the new debug build without auto-launch. Run exactly one bounded
preview-only framing screenshot (no MediaPipe, analyzer, trace, capture or countdown). Confirm
preview-only label, whole head/hands/feet, exit/camera release and thermal pre/during/post records.
Stop/cool down/review; do not immediately follow with inference. Only after that result is reviewed
should a separately authorized 30-second inference window be considered with camera-off 15-second
countdown, fresh normal preflight, thermal monitoring and immediate stop on discomfort/abort.
No completed valid portrait checks need repetition. All unfinished physical gates remain pending.


Host validation for paused investigation: debug/profile/release builds, camera unit tests,
perception API tests and app lintDebug PASSED. Host collection/thermal/framing integration suite:
11 tests PASSED (mocked ADB only, no device). Full spec validator: 0 errors, 0 warnings.
Diff whitespace check passed. Reverified previous 384-file evidence inventory: no mismatches.
No new APK installed or device check run; preview-only behavior and thermal gain pending physical
verification after explicit resume. Local trace-derived review/inventory retained, never uploaded.
