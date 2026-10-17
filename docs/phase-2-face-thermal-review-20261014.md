# Phase 2 Face-only thermal interruption review - 2026-10-14

Status: HOST-ONLY EVIDENCE REVIEW. The interrupted attempt is INCOMPLETE; no Phase 2
physical gate or owner acceptance is claimed. No ADB/device contact, camera launch,
inference, physical retry, APK change or thermal-policy change was made in this review.

Raw local source (ignored, never commit/upload):
`device-evidence/phase2/physical/face-continuity-ready-20261014-0445/`.
Target: Samsung SM-S918B / Android API 36, serial R5CW40EE9QK. Requested rear portrait,
Face-only, debug, 480p 4:3. The app reported portrait UI rotation 0, delivered analysis
640x480 with upright crop 480x640. The single app start followed a fresh NORMAL/stable-idle
preflight and a 15-second camera-off countdown. This review reads saved files only.

## Exact stop condition

`tools/phase2_thermal.py` parses `Thermal Status` and the `mType=3, mName=SKIN` value.
Its unchanged rapid-rise guard selects the earliest prior sample with a positive age of at
most 30 seconds and stops when current SKIN minus that sample is at least 2 C. Android
status >=3 is a separate stop condition; preflight requires status 0.

The parent history has 45 strictly ordered samples, all Android NORMAL (0). Its first
sample was 34.1 C. At triggering sample index 44, SKIN was 37.3 C (host monotonic
151451.3819071). The earliest eligible sample was index 30, 35.2 C at
151422.5212519: **+2.1 C in 28.861 s**. The 34.9 C sample at index 29 was 30.959 s old
and excluded by the existing window. The child collector guard independently recorded
35.2 -> 37.3 C in 22.726 s and also latched the rapid-rise failure. These separate
histories agree on the stop, but their elapsed intervals should not be mixed.

The parent command journal gives request times, not sensor acquisition times: first
thermal request 2026-10-13 21:44:38.335 UTC, sole app-start request
21:45:37.045 UTC, index 28 request 21:45:36.441 UTC at 34.9 C, selected baseline
request 21:45:40.603 UTC at 35.2 C, trigger request 21:46:09.488 UTC at 37.3 C,
and first app force-stop request 21:46:09.550 UTC. Thus 34.1 -> 34.9 C was already
reported during camera-off preparation, and the selected +2.1 C window began after the
app-start request. Launch-command to stop-command time is 32.504 s, not measured hardware
camera-on time. The observed parent range was 34.1-37.3 C. All Android status samples
remained NORMAL (0), which does not override the independent rise guard. The samples do
not provide sensor calibration, exact update latency or heat-source attribution.

`session.json` records the rapid-rise reason, `INCOMPLETE` and `thermalStop`. The child
summary records 23.675 s wall collection, `INCOMPLETE` and
`counterWindowInvalid=true`; this is not a completed 30-second measurement. The cancel
journal records the same reason. Final collector and runner camera-idle snapshots show
zero active camera clients and zero project/test PID. The host runner and collector exited.
No automatic retry occurred. The cleanup error in `session.json` is the latched thermal
guard failure, not evidence that camera release failed.

## Workload evidence and limits

| Area | Retained observation | What it does not prove |
| --- | --- | --- |
| CPU | No CPU utilization, per-thread time, frequency, energy or power sample is retained. Source audit confirms gated YUV-to-RGB conversion, RGB packing, diagnostics and UI work. | No CPU hotspot or causal CPU share of heat can be assigned. |
| GPU | App mode log says Face READY/configured GPU; Pose STOPPED/disabled_face_only. `dumpsys meminfo` has graphics PSS of 91,372-92,364 KiB. | Configured delegate and graphics memory do not measure native execution placement, GPU utilization or power. |
| Face | Last same-PID/session cumulative record at app elapsed 30.193 s: 291 Face completions, 268 detections, 268 valid478, 266 continuity pairs, zero disjoint pairs, one reacquisition, zero Pose completions/errors, degradation level 0. This is about 9.64 cumulative Face completions/s. Last rolling Face p50/p95 was 39.242/49.487 ms and batch p50/p95 73.322/86.210 ms. | The counter window was interrupted; these are diagnostic cumulative/rolling values, not a completed 30-second rate, identity proof or isolated window percentiles. Native Face execution time/power is not separately profiled. |
| Camera and conversion | Last Phase1 cumulative record: 919 delivered analysis frames over 30.613 s, app-reported preview capture 30.019 Hz, analysis 30.019 Hz, 295 RGB conversions, 60 luma samples, zero router errors; delivered 640x480, upright crop 480x640. Last rolling YUV/RGB rotation p50/p95 was 30.904/43.884 ms. `RGB benchmark: false` is visible in retained screenshots. | Preview capture/analysis rates are not display FPS; 859 cadence skips are intentional router gating, not measured CameraX internal drops. Conversion timing is not total CPU time or heat contribution. |
| Memory | Three same-PID collector samples at 0.053, 11.044 and 21.840 s: PSS 265,581 -> 255,572 -> 247,513 KiB; RSS 363,324 -> 354,000 -> 346,076 KiB. Graphics PSS stays about 91,000-92,000 KiB. | Three samples cannot establish peak allocation, GC, native leak absence or long-run memory stability. The downward trend is not an optimization result. |

Three saved preview screenshots show one visible face with the Face landmark overlay at
the sampled moments. They are sparse and cannot establish the complete head-turn,
distance, landmark alignment or identity-continuity gate. Zero disjoint pairs does not
prove stable identity; the one reacquisition denotes a detection gap. The log is filtered
to project baseline/perception and AndroidRuntime error tags, so zero reported pipeline
errors is not a system-wide crash/ANR audit. No Perfetto/CPU/GPU trace was retained.

## Causality and safe optimization scope

Confirmed: the host guard correctly stopped on the recorded +2.1 C/28.861 s SKIN rise;
Face-only ran with Pose disabled; the source and logs show about 30 Hz camera/analysis
and about 10 Hz accepted Face work before cancellation; cleanup released the project
camera. The CPU/GPU distribution of the work and the cause of the temperature change
are **UNKNOWN**. Camera ISP/preview/display, YUV conversion/RGB packing, Face native work,
debug 478-point drawing, host thermal/memory/screenshot polling, charging/ambient effects
and other system workload are plausible contributors, not established causes. Existing
source audits in [Face performance](phase-2-face-performance-audit.md) and
[diagnostic overhead](phase-2-diagnostic-overhead-audit.md) identify bounded work and
already completed pure-code reductions; no device thermal benefit was measured for them.

No additional production rate, model, resolution, delegate or camera-binding change is
justified as a thermal fix by this interrupted single run. The measurable ~31 ms rolling
conversion median makes conversion a reasonable **host-only** algorithm review target,
provided output equivalence and existing contracts are preserved; it does not show
conversion caused the rise or that a redesign would reduce temperature. The confirmed
debug-only RSS polling and duplicated diagnostic activity can be scoped for an optional
future measurement plan, but safety thermal polling, camera-idle checks, cleanup and
required evidence must remain. No optional diagnostic reduction should be credited with
a thermal benefit without a separately authorized controlled comparison. Existing
rapid-rise +2 C/30 s, NORMAL preflight, Android >=3 stop and all ownership guards stay
unchanged. Do not disable Android/Samsung protection or silently reduce evidence.

Next: preserve the phone camera-off and the raw files; retain this attempt as partial
diagnostic evidence. Any future profiling or 30-second verification needs separate owner
authorization, a new exact-device NORMAL/stable thermal and camera-idle preflight, and
explicit Ready. Review a controlled one-person scene and full tracking evidence only in
such a separately authorized session. PR #3 remains Draft; no Phase 3 start.
