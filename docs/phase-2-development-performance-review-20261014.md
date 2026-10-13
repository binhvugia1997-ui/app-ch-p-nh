# Phase 2 development-machine performance review - 2026-10-14

Branch: `codex/phase-2-pose-face-detection` at base 502eb07. Status: host-only review
and one source-level conversion cleanup. No ADB, physical camera, on-device inference,
30-second run, installation, thermal-policy change or Phase 3 work. The latest
[thermal interruption review](phase-2-face-thermal-review-20261014.md) remains the
physical evidence boundary: its collection was INCOMPLETE and cannot establish a
performance or thermal PASS. Raw evidence under `device-evidence/` was only read.

## Code and retained measurements

| Area | Confirmed behavior or measurement | Interpretation |
| --- | --- | --- |
| CameraX/router | `CameraSession` binds Preview, ImageAnalysis and ImageCapture when supported. Analysis is YUV with `KEEP_ONLY_LATEST` on a single router executor; `ImageProxy.close()` is in `finally`. The latest interrupted debug log reports 919 delivered frames/30.613 s, about 30.019 Hz for app-reported preview capture and analysis, 60 luma samples and zero router errors. | These are app counters, not display FPS or CameraX internal drop counts. Camera binding/FPS changes would alter product behavior and lack a causal thermal basis. |
| YUV/RGB | `FrameRouter` calls `perception.acquire` before RGB conversion. Accepted frames use a reused `IntArray`; the opt-in RGB benchmark was false. The latest last rolling YUV/RGB rotation p50/p95 was 30.904/43.884 ms at delivered 640x480 with upright crop 480x640, with 295 conversions in the cumulative app record. | Conversion is a measured wall-clock span, not CPU utilization, energy or full Face latency. The historical values precede this code change. |
| Buffers | `TaskImage` retains one direct RGB buffer per task/geometry until its asynchronous callback, then releases the `MPImage`; the router and pipeline reuse their pixel arrays. Per-frame Plane wrappers and result/domain objects remain. | No demonstrated per-frame direct-buffer allocation or leak at stable dimensions. Zero-copy/native input changes would affect ownership and require separate evidence. |
| Face schedule | Face-only skips Pose construction/submission. The schedule starts at a 100 ms Face period on the target tier; one `BatchGate` token and a bounded callback channel prevent an inference queue. The interrupted run's cumulative 291 Face completions/30.193 s is about 9.64 Hz, with four busy skips, degradation level 0 and zero Pose completions. | This is consistent with intended cadence; no duplicate Face work or scheduling defect is shown. The collector window was invalid, and configured GPU is not native GPU load evidence. |
| Diagnostics | Router trace/counters, approximately 2 Hz Phase1 summaries, approximately 1 Hz Phase2 summaries, debug-only 478-point overlay and 1 Hz RSS polling, and host thermal/memory/screenshot collection are confirmed. `FrameRouter` also computes the luma summary twice during a publication. | Some work is redundant, but its CPU/thermal cost is unmeasured; changing debug/lifecycle behavior or required safety/evidence collection is not justified by this run. |

No retained CPU utilization, per-stage power, native GPU utilization, display frame timing,
allocation trace or controlled before/after device comparison exists. Camera, conversion,
native Face work, rendering and diagnostics remain plausible heat contributors, not
confirmed causes. The thermal guard correctly stopped on the recorded SKIN rise.

## One bounded source cleanup

`ImagePlanes.rgb` previously fetched U and V for every output pixel, although the
4:2:0 chroma X coordinate is shared by horizontally adjacent sensor pixels. It now
reads U/V only when that coordinate changes within the sensor row, retaining the same
stride-aware index, color formula, crop, rotation and destination order. At an even,
aligned 640x480 sensor crop, this reduces the number of chroma `ByteBuffer.get` calls
per conversion from 614,400 to 307,200. This is an operation-count calculation from
the code, **not** a measured runtime, FPS, energy or temperature improvement. Odd crop
origins and widths preserve the original chroma boundaries.

The existing scalar-reference RGB test was extended across crop widths 3-6, crop
origins 0-2, pixel strides 1-2 and all four rotations. It checks the exact output and
buffer-position contract for each deterministic randomized case. There is no API,
module, architecture, thermal threshold, Face/Pose configuration, permission or
privacy change. No other speculative optimization was implemented.

## Validation and handoff

- `:feature:camera:testDebugUnitTest` passed. Full offline Gradle invocation for
  `:app:assembleDebug`, `:app:assembleProfile`, `:app:assembleRelease`, `test`,
  `:app:lintDebug`, `:app:lintProfile`, `:app:lintRelease` and
  `:feature:camera:lintDebug` passed: 322 actionable tasks, 299 executed and 23
  up to date. XML reports contain 41 Kotlin tests, zero failures/errors. Built debug
  APK SHA-256: `AD16C2385972309D08D5E60B59DE060D1B2F7FA314DABE08A7B55AA8FC9FF2BC`.
- Phase 2 host Python suite passed 59 tests. Its first invocation had 19 setup errors
  because the fresh worktree lacked the ignored `device-evidence/` directory used for
  test temporary files. Creating that empty directory resolved the setup failure;
  the rerun passed. All device interactions in these tests are mocked.
- Full offline spec validation with cached `jsonschema` and `referencing` passed with
  0 errors/0 warnings. Documentation/code diff whitespace check passed.
- No device model/API, latency/FPS/memory/thermal result was measured for this new
  build. Earlier SM-S918B evidence belongs to the preceding APK; it is not evidence
  of a speed or thermal improvement from this edit. Do not install or rerun it
  automatically.

On this machine, ignored raw physical files remain under the original checkout at
`I:/AI-Photographer/device-evidence/`; the separate Phase 2 worktree has only an empty
ignored `device-evidence/` directory for host test temporary files.

Next: keep the camera off and preserve all raw local evidence. A future controlled
comparison or Face tracking verification needs separate owner authorization, a fresh
NORMAL/stable exact-device camera-idle preflight and explicit Ready. Existing thermal
and ownership guards remain intact. Phase 2 physical gates and owner acceptance are
pending; Draft PR #3 is not merged and Phase 3 has not begun.
