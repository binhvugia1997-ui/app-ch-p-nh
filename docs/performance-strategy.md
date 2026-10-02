# Performance Strategy — realistic mobile deployment

Everything numeric in this document is a **hypothesis** until measured on a real device (Phase 10 and,
for the pipeline itself, Phase 1–2). The document exists so that the architecture is built with a budget
from day one, instead of discovering at the end that the app cannot run at 15 FPS on half the target market.

---

## 1. Targets

| Metric | Target | Rationale |
| --- | --- | --- |
| Preview smoothness | ≥ 30 FPS preview, no dropped frames caused by analysis | the camera must never stutter because of AI |
| Guidance update latency (frame → on-screen instruction) | ≤ 150 ms p50, ≤ 250 ms p95 | beyond ~250 ms the instruction feels disconnected from the subject's motion |
| Pose update rate | 15 FPS target on MEDIUM, ≥ 10 FPS on LOW, 30 FPS on HIGH | "the skeleton follows me" |
| Face update rate | 5–15 FPS adaptive (5 FPS is enough for head pose/headroom) | the face changes slowly |
| Overlay animation | 60 FPS rendering even when perception is at 15 FPS (interpolation) | visual quality is decoupled from model cost |
| Cold start to preview | ≤ 1.2 s on MEDIUM | a camera app must feel instant |
| Cold start to first guidance | ≤ 2.5 s on MEDIUM | model init + first inference |
| Steady-state memory | ≤ 250 MB RSS on LOW, ≤ 400 MB on MEDIUM | avoid low-memory kills on 4 GB devices |
| APK size (MVP) | ≤ 40 MB total incl. models | affects install conversion and updates |
| Battery | ≤ 12 % per 10 minutes of continuous guidance on MEDIUM | a photo session lasts minutes, not hours, but thermals follow battery drain |
| Thermal | after 10 minutes continuous: no more than one degradation step; no "severe" thermal status | continuous vision on a phone always throttles eventually — the goal is graceful degradation |

---

## 2. Device tiers

| Tier | Representative devices (class) | RAM | SoC class | Profile |
| --- | --- | --- | --- | --- |
| **LOW** | entry/mid 2019–2021 (Snapdragon 6xx, Helio G-series, Exynos 850-class) | 3–4 GB | no usable GPU delegate or weak GPU | 480p analysis, pose **lite** @ 10–15 FPS, face @ 5 FPS, no scene, luma-only lighting, reduced overlay detail |
| **MEDIUM** | mainstream 2021–2023 (Snapdragon 7-series, Dimensity 7xxx, Tensor mid) | 4–8 GB | GPU delegate usable | 720p when full-body, pose **full** @ 15–20 FPS, face @ 10 FPS, optional scene @ 0.5 FPS |
| **HIGH** | flagship 2022+ (Snapdragon 8-series, Tensor G2+) | 8 GB+ | GPU delegate fast | 720p–1080p, pose **full** @ 25–30 FPS, face @ 15 FPS, scene @ 1–2 FPS, optional NIMA |

Tier detection: `ActivityManager.MemoryInfo` + CPU/SoC heuristics + a **one-time on-device micro-benchmark
at first launch** (run the pose model 10 times, measure; cache the result). The micro-benchmark is the only
reliable signal — vendor claims and `Build.SOC_MODEL` do not predict MediaPipe performance.

---

## 3. Pipeline cadence (conceptual scheduler)

```
Camera             30 FPS  (ImageAnalysis may be capped lower; preview stays 30)
 ├ Pose            15 FPS  (LOW: 10)         MediaPipe Pose Landmarker (LIVE_STREAM)
 ├ Face             5 FPS  (adaptive: only while a subject is tracked; back off to 1 FPS when static)
 ├ Hands           on demand only (when the active template involves hands)
 ├ Lighting         2 FPS  (Y-plane grid only; immediate re-run on AE change)
 ├ IMU            60–100 Hz (raw), reduced to variance features at 10 Hz for the arbiter
 ├ Composition    per pose frame (pure math, ~0.2 ms)
 ├ Guidance       per composition/pose update (~0.5 ms)
 └ Scene           0.5–2 FPS (Phase 7+, first thing to be dropped)
```

Rules:

* **Never** run two MediaPipe tasks on the same frame if one of them is already behind that frame.
* Pose results drive everything else; if the pose rate falls, face and lighting rates fall with it
  (they are downstream consumers of "a subject exists").
* When the subject is still (landmark velocity ≈ 0) for > 1 s, halve the pose rate and re-enable at motion.
  A still subject does not need 30 Hz.

---

## 4. Where the time goes (budget model, MEDIUM tier)

| Stage | Budget | Notes |
| --- | --- | --- |
| YUV → RGB + rotation (for MediaPipe) | 3–8 ms | the classic hidden cost; reuse buffers, rotate during conversion, avoid `YuvImage` → JPEG |
| Pose inference (full, GPU) | 8–20 ms | model-card numbers are historical; measure |
| Pose post-processing + filtering | 1–3 ms | 33 landmarks × One Euro; keep allocation-free |
| Face inference (GPU, every 3rd pose frame) | 5–15 ms | amortized ≈ 2–5 ms/frame |
| Luma grid + statistics | 0.2 ms | 64×64, 2 Hz |
| Engines (rules + pose matching + guidance) | 0.5–2 ms | must stay pure and allocation-light |
| Overlay drawing | 1–4 ms | GPU-composited Compose Canvas |
| **Total added CPU/GPU work** | **≈ 15–35 ms per analysed frame** | i.e. a 15–25 FPS analysis pipeline while preview keeps 30 FPS |

---

## 5. Degradation ladder (automatic, ordered)

Trigger conditions: > 15 % dropped analysis frames over 3 s, thermal status ≥ `MODERATE`, memory pressure,
or measured stage latency exceeding 2× the budget.

```
1. Scene classifier off                     (highest cost / lowest value)
2. Hands off
3. Face cadence 10 → 5 → 2 FPS
4. Pose model full → lite                   (quality drop, keep the rate)
5. Pose cadence 20 → 15 → 10 → 8 FPS
6. Analysis resolution 720p → 480p
7. Lighting cadence 2 → 1 FPS               (last, it is nearly free)
8. Guidance update rate capped at 5 Hz      (UI still animates)
```

Recovery is the reverse order with **hysteresis** (recover only after 15 s of stable headroom above the
threshold) to prevent oscillation. Every change is logged with the trigger and reported in the dev screen.

Non-negotiable at every tier: the app keeps giving *framing* and *pose* guidance, and it never shows
stale guidance as if it were live (the UI shows a "low confidence" state instead).

---

## 6. Efficiency rules for the implementation (Codex Local)

1. **Analysis resolution choices must be deliberate.** CameraX default is 640×480 for ImageAnalysis;
   MediaPipe's internal inputs are 224×224 (detector) and 256×256 (landmarker), so very high analysis
   resolutions buy little — **except** when the subject is small in frame (full-body at 3–4 m), where a
   720p input is plausibly the difference between detecting and not detecting. **Phase 2 must measure
   480p vs 720p for full-body framing.** (`CALIBRATION_REQUIRED`.)
2. **Y-plane only for statistics.** Never allocate a `Bitmap` for lighting.
3. **One reusable RGBA buffer**, rotated once, locked to the analysis rotation.
4. **No allocation in the frame loop.** Pre-allocate landmark arrays, rule result lists, path builders.
   Allocation → GC → frame drops.
5. **Backpressure: `KEEP_ONLY_LATEST`** and a per-source in-flight flag. Never queue frames.
6. **Never block the UI thread**; bridge MediaPipe callbacks through a `Channel` → `Flow` → `StateFlow`.
7. **GPU delegate capability check** at startup (OpenGL ES 3.1+, not an emulator, not a known-bad driver
   list) with a CPU fallback; log which delegate is actually in use (MediaPipe can silently fall back).
8. **Measure with Perfetto traces and Macrobenchmark**, not with `System.currentTimeMillis()` logging
   inside the frame loop.
9. **Profile release builds with R8/minification on**, not debug builds.
10. **Avoid two runtimes** until Phase 8 forces it (ONNX Runtime + MediaPipe doubles native code size).

---

## 7. Measurement protocol (what must be recorded, by whom)

For every performance claim, record in `docs/phase-status.md`:

```
device model, Android version, build type (release), tier
analysis resolution and pose model variant
p50 / p95 latency per stage (from Perfetto, 60 s window)
effective pose FPS and face FPS
dropped-frame ratio
RSS memory high-water mark
battery delta over 10 minutes
thermal status progression (NONE → LIGHT → MODERATE …) and whether degradation triggered
```

Tools: Android Studio Profiler, Perfetto (`adb shell perfetto`), Macrobenchmark, `adb shell dumpsys thermalservice`,
`adb shell dumpsys meminfo <pkg>`, `adb shell dumpsys batterystats`.

**A performance claim without this table is not a claim, it is a hope.**

---

## 8. Risks

| Risk | Mitigation | Verification phase |
| --- | --- | --- |
| MediaPipe GPU delegate silently falls back to CPU | log the actual delegate; benchmark both; keep the CPU path within budget on MEDIUM | 2 |
| Emulator results mislead (GPU disabled) | device-only performance testing, from the start | 2 |
| YUV→RGB conversion dominates the budget | measure it as its own stage; consider `ImageProcessingOptions` with the right rotation to avoid a second rotation pass | 1–2 |
| Pose quality collapses for full-body at 3–4 m | resolution experiment (§6.1) + surface a clear "move closer" state rather than lying about pose quality | 2 |
| Thermal throttling after ~10 min | degradation ladder + measured acceptance | 10 |
| 3-use-case capture session unsupported on some devices | CameraX capability query + documented fallback | 1, 5 |
| APK size creep from optional models | asset budget per phase; models are gated by tier at build time or runtime capability | 7, 12 |
| Emulator/CI-only testing hiding real problems | ADR: no phase is "done" without a device run | all |

---

## 9. Acceptance gates

| Gate | Where | Criterion |
| --- | --- | --- |
| G-P1 | Phase 1 | Preview ≥ 30 FPS with ImageAnalysis bound; cold start ≤ 1.2 s on MEDIUM |
| G-P2 | Phase 2 | Pose ≥ 15 FPS on MEDIUM, ≥ 10 FPS on LOW; guidance latency p95 ≤ 250 ms |
| G-P3 | Phase 3 | Overlay renders at 60 FPS with perception at 15 FPS; no visible jitter (human check) |
| G-P4 | Phase 5 | Full pipeline (pose+face+lighting+engines) ≤ 35 ms per analysed frame on MEDIUM |
| G-P5 | Phase 10 | 10-minute continuous session: memory stable, ≤ 1 degradation step, no ANR, battery within budget |
| G-P6 | Phase 12 | APK ≤ 40 MB, cold start ≤ 1.2 s, all of the above re-verified on the release build |
