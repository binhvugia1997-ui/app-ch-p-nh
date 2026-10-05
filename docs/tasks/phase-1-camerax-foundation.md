# Task Brief — Phase 1: CameraX Foundation

> **STATUS: IMPLEMENTED; PHYSICAL-DEVICE VERIFICATION PENDING.** The human owner approved Phase 0 and
> authorized Phase 1 on 2026-10-03; see `docs/phase-status.md`. Acceptance remains pending.

Owner: **Codex Local**. Reviewer: GitHub Agent (against this brief and the referenced specs).

---

## 1. Goal

A camera application that does nothing intelligent yet, but is a **solid, measurable platform**:
preview + analysis + capture bound correctly, a structured frame pipeline with cadence control,
a verified coordinate contract, and a performance baseline that later phases can be judged against.

Explicit non-goals: **no ML model, no MediaPipe, no guidance, no dashed overlay** (a debug marker and a
debug HUD are in scope).

---

## 2. Deliverables

### 2.1 Project skeleton

* Gradle (Kotlin DSL, version catalog), minSdk **24**, target/compile against the current stable SDK.
* Modules: **only what Phase 1 needs** (`architecture.md` §2.1): `:app`, `:core:model`, `:core:geometry`,
  `:feature:camera`, plus `:perception:image` for the Y-plane/luma producer (fold it into
  `:feature:camera` if it does not need its own boundary yet — the module split exists so the image
  statistics producer can be swapped, not to have a folder).
  Do **not** create `:core:photography`, `:core:pose`, `:core:guidance`, `:core:light`,
  `:perception:api`, `:perception:mediapipe`, `:feature:guide`, `:feature:poselib`, `:feature:settings`,
  `:benchmark` or `:tools:pose-authoring` yet: they arrive in the phases listed in `architecture.md` §2.1.
  Empty modules are build complexity with no test value.
* Dependency-rule enforcement (`architecture.md` §2): `core:*` must not depend on Android SDK, `perception:*`
  or `feature:*` — and it must be enforced by a **test** from Phase 1 onward (a JVM test that inspects the
  Gradle module graph), so the small graph cannot quietly grow the wrong edges.
* R8 enabled for release; debug-only dev screen.

### 2.2 Camera pipeline (`:feature:camera`)

* `Preview` + `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`) + `ImageCapture` on one `Camera` session.
* `ImageAnalysis` target resolution configurable (480p default; 720p switch for the Phase-2 experiment).
* Device capability query + documented fallback when a 3-use-case session is unsupported
  (fallback order: reduce analysis resolution → disable capture during analysis binding → degrade to
  Preview+ImageCapture with a "capture-only" mode message).
* Camera selector (front/back), tap-to-focus (optional, but no manual exposure control — ADR-010),
  torch off by default.
* Permission flow (`CAMERA`) with a clear explanation screen and a usable "no permission" state.

### 2.3 `FrameRouter` (`:feature:camera` + `:core:model`)

* Single background executor; per-frame decision of what to compute (cadence scheduler skeleton, all
  stages stubbed).
* Per-source in-flight flags; drop frames rather than queue.
* Stage-latency histograms (p50/p95) exposed to the dev screen.

### 2.4 Coordinate contract (`:core:geometry`)

* `SENSOR → ANALYSIS → PREVIEW` transforms with aspect-fill cropping, front-camera mirroring and display
  rotation, exactly as specified in `docs/architecture.md` §5.
* **Unit tests first** (see §4). A debug overlay draws a synthetic crosshair at a known ANALYSIS
  coordinate; it must land on the same physical feature on screen for: front/back camera × portrait/landscape
  × 4:3 and 16:9 preview.

### 2.5 Minimal `FrameAnalysis` production

* Frame geometry, device state (via `SensorManager`: gravity/rotation vector), an empty `subjects` list,
  and the luma grid from the Y plane (this is the only "analysis" allowed in Phase 1).
* `LightReport` is **not** required in Phase 1; the luma grid is enough to prove the cheap path works.

### 2.6 `CapabilityReport` v1 (`:core:model` + `:feature:settings`)

* Tier heuristic + a place for the first-launch micro-benchmark (the benchmark itself arrives in Phase 2
  with a model to benchmark; in Phase 1, measure the YUV→RGB conversion and log it).
* GPU delegate probe (OpenGL ES version via `ActivityManager.deviceConfigurationInfo`,
  emulator detection).
* Camera capability report (supported resolutions, 3-use-case session support).

### 2.6a UX orientation

Portrait-first MVP UX (owner clarification 2026-10-05): portrait is the initial design preference, not an
orientation lock. System Auto rotate controls orientation in every build. No debug flag is required.
The coordinate transforms and analysis must remain **landscape-correct and tested** in both orientations.
The overlay uses the single tested `ANALYSIS → PREVIEW` transform; no orientation-specific geometry branches.

### 2.7 Dev screen

* FPS (preview + analysis), per-stage latency, dropped frames, memory RSS, thermal status, delegate/GL
  info, current tier, current analysis resolution. This screen is a **Phase 1 deliverable** — every later
  phase measures itself with it.

---

## 3. Explicitly out of scope

* MediaPipe, any model, any landmark.
* Rules, guidance, readiness, overlay templates.
* Auto-capture.
* Scene, lighting rules, aesthetic scoring.

---

## 4. Required tests

**JVM (`:core:geometry`)** — must be written before the camera code that uses them:

1. Rotation round-trip for 0/90/180/270.
2. Aspect-fill crop mapping: a known analysis point → the expected preview point for 4:3 and 16:9 previews,
   centre-cropped and edge-cropped cases.
3. Front-camera mirroring: anatomical vs spatial side assertions (see `architecture.md` §5 rule 3).
4. Degenerate inputs (zero-size view, zero-size frame) do not throw.

**Instrumented:** the debug crosshair test from §2.4 on the reference device, both cameras.

**Device:** the acceptance gates below.

---

## 5. Acceptance gates

| Gate | Criterion | How measured |
| --- | --- | --- |
| **Preview performance — baseline (mandatory)** | Preview FPS, analysis FPS, per-stage latency (p50/p95), dropped frames, memory RSS and thermal status are **measured and recorded** in `phase-status.md` with the device model, Android version, build type and method | dev screen + Perfetto 60 s |
| Preview performance — target (not a failure condition) | ≥ 30 FPS preview with analysis bound, no preview stutter attributable to analysis | same measurement; a miss is recorded as a decision, not a blocked phase (`performance-strategy.md` §0, §9) |
| **Cold start — baseline (mandatory)** | launch → first preview frame is measured and recorded | `adb shell am start -W` + timestamp log |
| Cold start — target | ≤ 1.2 s on the reference device | same measurement |
| Every unmeasured target | marked `NOT_MEASURED` with the reason (no device, emulator only, thermal) | `phase-status.md` |
| Analysis cadence | analysis frames delivered at the configured rate, `droppedFrameRatio` reported honestly | dev screen |
| Coordinate correctness | crosshair test passes on both cameras, both orientations | manual + screenshot |
| Capture | a capture during analysis produces a correctly rotated, correctly exposed JPEG in the expected location | manual |
| Capability report | reports tier, GL version, emulator flag, camera capability **on the reference device** (more if available) | dev screen screenshot |
| No crashes | 10-minute session, no crash/ANR | `adb logcat`, `dumpsys` |

Record every measured number in `docs/phase-status.md` using the template in `AGENTS.md`, including device
model and Android version.

---

## 6. Deliverables to hand back to the GitHub Agent

1. Updated `docs/phase-status.md` (status, reference device, measured numbers, targets that could not be
   measured marked `NOT_MEASURED`, deviations, blockers, questions).
2. Any architecture deviation as a proposed ADR addition (do not silently deviate).
3. The raw latency table (p50/p95 per stage) — it becomes the baseline for the Phase 2 budget.
4. Answers to: was the 3-use-case session supported on every tested device? What fallback was needed?
   What was the measured YUV→RGB conversion cost at 480p and 720p?

---

## 7. Risks specific to this phase

| Risk | Mitigation |
| --- | --- |
| CameraX use-case combination limits on some devices (documented Camera2 guaranteed-configuration issue) | query capability first; implement the documented fallback; record which devices needed it |
| YUV→RGB conversion silently dominating the budget | make it a named stage in the latency histogram from day one |
| Coordinate transform "looks fine" on the developer's device only | unit tests + crosshair on both cameras/orientations, recorded as evidence |
| Scope creep (someone adds a model "to test") | this brief; dev-screen only, no ML in Phase 1 |
| Emulator used for performance numbers | ADR: L5 measurements are device-only; emulator numbers are marked invalid |
| A target treated as a gate before it is measured | `performance-strategy.md` §0 classes: only measured numbers may fail a phase; `G-P1a` (baseline) is the mandatory gate |
