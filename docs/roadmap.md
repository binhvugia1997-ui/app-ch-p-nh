# Roadmap — Phase 0 to Phase 12

Rules of the roadmap (from `AGENTS.md`):

* Work phase by phase. A phase starts only when the previous one is **accepted by the human owner**.
* Every phase has entry criteria, deliverables, exit criteria and an explicit **NOT in this phase** list.
* Every phase must be verified on a real device before it is called done.
* No phase may silently pull work forward from a later phase.

Current status: **Phases 0 and 1 accepted and merged. Phase 2 authorized on 2026-10-06; in progress.**

---

## Phase 0 — Research + architecture ✅ (this phase)

**Goal:** produce the intellectual and architectural foundation; decide what is knowable, what is
deterministic, what is ML, what is subjective, and which models may legally ship.

**Deliverables (as of this phase):** everything in `docs/` and `specs/`; the Phase 0 report;
the Codex Local handoff in `docs/phase-status.md`.

**Exit criteria:** the human owner accepts the architecture, the model choices and the MVP definition.

**NOT in this phase:** any Android code, any CameraX, any MediaPipe integration, any APK.

---

## Phase 1 — CameraX foundation

**Entry:** Phase 0 accepted.

**Goal:** a camera app that does nothing clever but is a solid, measurable platform: preview + analysis +
capture, correct coordinate handling, a structured frame pipeline, and a hard performance baseline.

**Deliverables**

1. Android project skeleton with the module graph from `architecture.md` §2 (empty modules are fine).
2. CameraX: `Preview` + `ImageAnalysis` (KEEP_ONLY_LATEST) + `ImageCapture`, 3-use-case session with a
   documented fallback for devices that cannot bind all three.
3. `FrameRouter` with the cadence scheduler skeleton, per-source in-flight flags, and a stage-latency
   histogram (dev overlay).
4. `CapabilityReport` v1: device tier micro-benchmark hook, GPU-delegate availability check, camera
   capability query.
5. Coordinate contract implementation in `:core:geometry` (ANALYSIS→PREVIEW), with unit tests and a debug
   overlay proving that a synthetic marker lands where expected for front/back, portrait/landscape.
6. `FrameAnalysis` produced with **empty** subjects (no perception yet) + luma grid.
7. Permission flow, minimal Compose camera screen, dev/perf screen.

**Exit gates**

* **G-P1a (baseline, mandatory):** preview FPS, analysis FPS, per-stage latency (p50/p95), cold start,
  memory and thermal measured on the reference device and recorded with the method.
* **G-P1b (target, [TGT] → [GATE] once measured):** preview ≥ 30 FPS with analysis bound, cold start
  ≤ 1.2 s. A miss is a recorded decision, not a blocked phase (`performance-strategy.md` §0).
* 3-use-case session verified on the reference device (model + Android version recorded); an owner who can
  borrow other devices may extend this, but one physical device is sufficient (§9.1).
* Rotation/mirroring correctness demonstrated on both cameras of the reference device, portrait and
  landscape (the UX is portrait-first, but the transforms must be correct for both, per the owner decision
  that the architecture stays landscape-capable).

**NOT in this phase:** any ML model, any guidance, any overlay beyond the debug marker.

**Task brief:** `docs/tasks/phase-1-camerax-foundation.md`.

---

## Phase 2 — Pose + face perception

**Goal:** reliable landmarks at a usable rate, plus confidence handling and the measurement pipeline.

**Deliverables**

1. `:perception:mediapipe` with `PoseSource` (lite/full per tier) and `FaceSource` (5–15 FPS adaptive),
   LIVE_STREAM mode, delegate selection + logging of the **actual** delegate in use.
2. Landmark filtering (One Euro) and `SubjectAssembler` → `FrameAnalysis.subjects`.
3. Usability/confidence semantics implemented exactly as specified (`architecture.md` §7).
4. Skeleton debug overlay (solid lines, no dashed guide yet) to validate alignment visually.
5. Shot-type estimator calibrated on local captures.
6. The 480p vs 720p full-body experiment, with results recorded.

**Exit gates**

* Pose ≥ 15 FPS on MEDIUM, ≥ 10 FPS on LOW; combined pose+face guidance latency p95 ≤ 250 ms.
* Skeleton alignment verified visually on the reference device (both cameras, portrait and landscape); more devices if available.
* LOW-tier numbers are `NOT_MEASURED` unless a LOW device is available — record that honestly instead of extrapolating.
* Full-body detection at 3–4 m: success rate recorded, and the resolution decision documented.
* Thermal behaviour over a 5-minute run recorded.

**NOT in this phase:** pose templates, dashed guide, any rule beyond subject presence/size.

---

## Phase 3 — Dashed pose guide

**Goal:** the signature feature: a vector, template-driven dashed overlay with translation, scale, mirror,
partial bodies and stable anchoring.

**Deliverables**

1. Pose template loader/validator + the seed templates already authored in `specs/poses/` (4 solo seeds + 1 mirrored twin).
2. `:core:pose` guide geometry generation (anchor, scale clamp, handedness, per-component emphasis).
3. Compose `Canvas` renderer with dashed bones, joint rings, arrows/arcs, occlusion styling.
4. Anchoring modes (`ANCHOR_TO_SUBJECT/FACE/FRAME`) with EMA smoothing and handedness hysteresis.
5. Authoring tool (`:tools:pose-authoring`): validate + render a preview sheet.

**Exit gates**

* G-P3: overlay 60 FPS while perception runs at 15 FPS.
* Guide is visually correct for: front camera mirrored, arm raised on either side, headshot partial
  template, subject at 2 m and 4 m.
* A human can identify the target pose from the guide alone (L6 test; ≥ 3 participants when available, minimum 1 with the limitation recorded).

**NOT in this phase:** match scoring, corrections, guidance text.

---

## Phase 4 — Pose matching + corrections

**Goal:** turn the guide into a closed feedback loop: score the match, tell the subject what to change.

**Deliverables**

1. Full matcher per `pose-system.md` §4 (angles, directions, orientation, positions, coverage gating,
   Procrustes with rotation clamp, mirroring).
2. Component scoring + bands + `PoseStatus.UNVERIFIABLE` gate.
3. Correction generator with latch/hysteresis and the top-k policy.
4. Score smoothing and per-component bands exposed for the guide's solid/dashed emphasis.
5. Message IDs + Vietnamese/English strings for the correction catalogue.

**Exit gates**

* All matching robustness tests from `test-plan.md` §3.3 pass (proportion changes, rotation clamp,
  mirrored templates, invisible limbs, noise).
* L6: a subject reaches a `MATCHED` band on the intended pose within 3 instructions, with no
  false-positive correction on a deliberately-different-but-good pose.
* Correction churn: no more than one correction change per second in a scripted "nervous subject" trace.

**NOT in this phase:** composition rules, lighting, auto-capture.

---

## Phase 5 — Composition engine + camera guidance + readiness

**Goal:** complete the MVP: framing/composition rules, camera-move guidance, the arbiter, readiness and
manual capture (auto-capture implemented but off).

**Deliverables**

1. `:core:photography` rules: framing set + advisory composition set, with thresholds from
   `specs/rules/mvp-rules.json` (calibration-required values gated out of user-visible hard errors).
2. Head-top estimator + headroom rules calibrated on local captures.
3. `:core:guidance` arbiter, dwell/cooldown, resolution-compatibility table, `GuidanceState` emission.
4. Readiness state machine + manual capture flow (and auto-capture behind a setting, default OFF).
5. The one-instruction UI banner with the "why?" explanation sheet.

**Exit gates**

* G-P4: full pipeline ≤ 35 ms per analysed frame on MEDIUM.
* One-primary invariant holds over a 60-second scripted session (no flicker, no contradictory pair).
* L6: novices produce measurably better framing (size/headroom/crop) after 60 seconds of guidance (≥ 3 participants when available, minimum 1 with the limitation recorded).
* Zero false-positive framing errors on a set of 30 deliberately-good photos (audited by a human).

**NOT in this phase:** pose library browsing, scene, lighting beyond the six basic rules, groups.

**→ Phases 1–5 constitute the MVP. Phase 6+ is a separate product increment.**

---

## Phase 6 — Pose library + recommendations

**Goal:** more than a single template: a curated, validated library with ranking.

**Deliverables:** validated templates (`VALIDATED` provenance), difficulty levels, shot-type/context tags,
thumbnail generation from geometry, user selection UI, ranking by scene tags (Phase 7) and shot type,
favourites/history (local only).

**Exit gates:** the owner's target library is complete — **20 high-quality SOLO poses** — and every template
is `VALIDATED`: 8 standing full-body, 4 three-quarter, 4 half-body/portrait, 2 sitting, 2 walking/leaning
(`pose-taxonomy.md` §6 maps these onto the pose families, and the 4 authored seeds count towards it).
Matcher calibration per template; library navigation usable without explanation (L6).

---

## Phase 7 — Lighting + scene

**Goal:** the lighting engine (§`lighting-engine.md`) fully calibrated, plus the deferred scene module
(§`scene-understanding.md`) if its exit criteria are met.

**Deliverables:** lighting rules with calibration data; `SceneSource` with an Apache-2.0 encoder and the
curated ≤ 25-tag taxonomy; tag-correction UI; template ranking integration.

**Exit gates:** lighting verdicts match human labels on the local fixture set; scene tags measurable and
user-correctable; scene cost within budget on MEDIUM.

**NOT in this phase:** any generative model.

---

## Phase 8 — Multi-person

**Goal:** couples, friends, family, groups — the hardest architectural step.

**Critical constraint discovered in Phase 0:** the BlazePose model card lists **multiple people in an image
as out of scope** and states the model tracks only one person when several are present. Multi-person
therefore requires a different pipeline:

* **Option A:** person detector (licence-clean) → per-person ROI crop → BlazePose per crop (2–5 people
  max, cost scales linearly).
* **Option B:** RTMPose-s/m via ONNX Runtime Mobile (Apache-2.0, quantized 18 MB variant), replacing the
  pose stack on capable devices.
* **Option C:** run BlazePose with `numPoses > 1` and accept the model's limitation — **not acceptable as
  the primary answer**, may be used only as a weak fallback with an explicit "unreliable" state.

**Deliverables:** subject detection + tracking (stable ids across frames), per-subject analysis,
relational features from `pose-taxonomy.md` §3, group guidance (one instruction for the group, or one per
actor within the max-two rule), two-person templates.

**Exit gates:** stable ids for 3 subjects over 30 s; relational features measured; L6 with a real couple.

---

## Phase 9 — Integrated AI Photographer

**Goal:** the full experience: continuous guidance across pose + composition + lighting, pose suggestions,
readiness, auto-capture, one coherent voice.

**Deliverables:** unified guidance narrative, session flow (choose intent → guide → capture → review),
optional auto-capture with countdown, optional "why this shot" record.

**NOT in this phase:** VLM/LLM phrasing (still `EXPERIMENTAL`).

---

## Phase 10 — Performance + device optimisation

**Goal:** hit the numbers on real hardware across tiers.

**Deliverables:** tier profiles finalised, degradation ladder verified, memory/thermal profiling, APK size
reduction (model packaging, minification, ABI splits), startup optimisation, battery measurements,
per-device bug list.

**Exit gates:** G-P5 (10-minute session) on LOW/MEDIUM/HIGH; APK ≤ 40 MB; no ANR; degradation behaves.

---

## Phase 11 — UX refinement + testing

**Goal:** the app feels good: overlay aesthetics, instruction wording in Vietnamese, onboarding, failure
messaging (low light, unsupported device, no subject), accessibility (large text, colour-blind-safe cues,
haptics), the licence screen, privacy explanation.

**Exit gates:** L6 sessions with 5+ users; wording comprehension; no P0/P1 bugs; accessibility checks.

---

## Phase 12 — Release APK

**Goal:** ship.

**Deliverables:** signing config, release build, Play Store listing (privacy policy, data safety form:
on-device camera processing, no collection, no `INTERNET` permission), device-support statement,
licence/attribution screen, versioning, crash-free session requirement (no analytics, so this is
measured locally during beta).

**Exit gates:** G-P6 re-verified on the release build; licence checklist from `model-licenses.md` §3
complete; a manual regression pass over the L6 scenarios.

---

## Deferred/experimental backlog (explicitly not scheduled)

| Item | Status | Note |
| --- | --- | --- |
| Aesthetic scoring (NIMA) | EXPERIMENTAL | licence-clean but product-risky; behind an "ideas" surface, never blocking |
| Small VLM scene description | EXPERIMENTAL | SmolVLM2-500M on flagship only; never in the live loop |
| LLM phrasing | EXPERIMENTAL | needs Gemma-terms review; our catalogue is better |
| Hands | NEXT (Phase 5+) | on-demand only |
| Leading lines / symmetry / frame-within-frame | LATER | requires scene segmentation and an aesthetic model |
| Live audio guidance | LATER | valuable in `SELF_MODE` (tripod) where the subject cannot read the screen |
| Video mode | LATER | out of the current product scope |
| Cloud sync / sharing | REJECT | violates local-first policy |
| Face recognition / identity | REJECT | privacy + model-card scope (permanent) |
| Exposure/WB control from the app | LATER | capability-gated, ADR-010 |
