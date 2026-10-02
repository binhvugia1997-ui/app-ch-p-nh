# Phase Status — handoff between GitHub Agent and Codex Local

> **This is the live handoff document.** Whichever agent finishes work updates it. If it disagrees with
> reality, it is a bug.

Last updated: **2026-10-02** by **GitHub Agent**.

---

## CURRENT PHASE

**PHASE 0 — RESEARCH + ARCHITECTURE — COMPLETE, AWAITING HUMAN APPROVAL.**

Phase 1 has **not** started. No Android code exists in this repository.

---

## STATUS

| Item | State |
| --- | --- |
| Research (photography, pose, composition, lighting, camera angle, perspective) | ✅ done, sources recorded |
| Architecture (modules, contracts, coordinate spaces, threading, ADRs) | ✅ done (`docs/architecture.md`) |
| Rule database (measurement math, threshold policy, priorities) | ✅ done (`docs/photography-rules.md` + `specs/rules/`) |
| Error taxonomy and detectability classification | ✅ done (`docs/photography-errors.md`) |
| Pose representation, matching, corrections, dashed guide | ✅ done (`docs/pose-system.md`) |
| Pose taxonomy (solo / two-person / group) | ✅ done (`docs/pose-taxonomy.md`) |
| Machine-readable specs (schemas, seed templates, rule data) | ✅ done + validated (`specs/`) |
| Model research + licensing | ✅ done (`docs/ai-models.md`, `docs/model-licenses.md`) |
| Performance strategy | ✅ done (`docs/performance-strategy.md`) |
| Test plan | ✅ done (`docs/test-plan.md`) |
| Roadmap | ✅ done (`docs/roadmap.md`) |
| Phase 0 report | ✅ done (`docs/phase-0-report.md`) |
| Phase 1 task brief (prepared, not started) | ✅ done (`docs/tasks/phase-1-camerax-foundation.md`) |
| **Phase 1 implementation** | ⛔ **blocked pending approval** |

---

## COMPLETED WORK (this phase)

1. **Photography knowledge base** — composition techniques with when-they-help/when-they-hurt,
   portrait practice, camera angle effects, focal length vs perspective (distance), posing craft for
   solo/two-person/group.
2. **Evidence-based filtering** — rules that are commonly asserted but unsupported (rule of thirds
   compliance, "always shoot 85 mm", "low angle is always better", aesthetic scoring) are documented as
   **explicit non-rules** so no future agent re-adds them.
3. **Deterministic rule database** — 40+ candidate rules with measurement math, threshold evidence
   classes, conflicts, failure cases, confidence caps, complexity and priority.
4. **Pose system** — geometric template schema (`SUBJECT` space, torso-normalized), a matching algorithm
   built on angles/directions/orientation (not pixel distances), coverage gating, mirroring and
   rotation clamping, semantic correction codes, and the dashed-guide rendering specification.
5. **Guidance system** — instruction model, actor model (photographer vs subject), scoring, the
   one-primary-instruction rule, a resolution-compatibility table for conflicting advice, three layers of
   temporal stability, and the readiness/auto-capture state machine.
6. **Model decisions and licence gate** — MediaPipe Pose/Face Landmarker chosen (Apache-2.0 code *and*
   weights); rejections documented (Ultralytics AGPL, CLIP-IQA/TOPIQ non-commercial, MobileCLIP weights
   unverified).
7. **Multi-person finding** — the BlazePose model card lists multiple people as out-of-scope and states
   the model tracks only one person; multi-person will need a different pipeline (Phase 8).

---

## FILES CREATED / UPDATED

```
README.md                            project overview + doc index
AGENTS.md                            working rules for both agents
docs/architecture.md                 modules, data flow, coordinate contract, ADRs, risks
docs/photography-rules.md            rule database with measurement math + threshold policy
docs/photography-errors.md           error taxonomy, detectability, rejected "errors"
docs/pose-system.md                  representation, matching, corrections, dashed guide
docs/pose-taxonomy.md                solo / two-person / group posing knowledge base
docs/composition-engine.md           engine spec, contracts, evaluation order, acceptance criteria
docs/guidance-engine.md              instruction model, arbitration, temporal stability, readiness
docs/lighting-engine.md              honest limits + measurements + rules
docs/scene-understanding.md          deferred module design (Phase 7)
docs/ai-models.md                    model candidates + evaluation format + decisions
docs/model-licenses.md               licence gate + register + APK checklist
docs/performance-strategy.md         tiers, cadence, budgets, degradation ladder, measurement protocol
docs/test-plan.md                    test layers, fixtures, criteria, device matrix, severity policy
docs/roadmap.md                      Phase 1..12 with entry/exit gates and NOT-in-this-phase lists
docs/phase-0-report.md               Phase 0 final report
docs/research-sources.md             consolidated bibliography
docs/glossary.md                     terminology (EN/VI)
docs/tasks/phase-1-camerax-foundation.md   prepared task brief (DO NOT START)
specs/README.md                      how to use the machine-readable specs
specs/schemas/*.json                 6 JSON Schemas (rule set, rule, pose template, frame analysis,
                                     rule result, guidance instruction)
specs/rules/mvp-rules.json           30 rules specified to implementation depth (Phases 4-6), each with
                                     threshold status, exit values, priority and phase
specs/poses/*.json                   4 seed pose templates (SEED_UNVALIDATED)
specs/fixtures/README.md             fixture format + the "no photographs of people" rule
```

**Validation record (2026-10-02):** all 6 schemas are valid JSON Schema 2020-12; all 4 pose templates
validate against `pose-template.schema.json`; all 30 entries of `rules/mvp-rules.json` validate against
`rule.schema.json` (validate the `rules[]` elements — the file itself is a `rule-set.schema.json`
document, not a single rule). The rule IDs in the file and the `docs/` tables are cross-checked; prose-only
rules are listed as such in `photography-rules.md` §11 and are added to the file when their phase begins.

---

## ARCHITECTURE DECISIONS (see `docs/architecture.md` §12 for full ADRs)

| ADR | Decision |
| --- | --- |
| 001 | Offline-only core; **no `INTERNET` permission** in the MVP |
| 002 | Kotlin + Compose + CameraX, minSdk 24 (MediaPipe Tasks requirement) |
| 003 | MediaPipe Tasks (Pose + Face Landmarker) as the v1 perception runtime |
| 004 | Deterministic rule engine; ML only for perception; **no LLM in v1** |
| 005 | Pose templates are normalized geometry (JSON), never raster images |
| 006 | One canonical `FrameAnalysis` snapshot + explicit 5-space coordinate contract |
| 007 | Exactly one primary instruction at a time |
| 008 | Multi-person deferred but not designed out (`subjects` is a list from day one) |
| 009 | Engines emit message IDs; Vietnamese-first strings live in `:app` |
| 010 | The app does not fight the camera's auto-exposure in the MVP |
| 011 | No identity/face-recognition features, ever |
| 012 | Pure engines, device-independent tests with synthetic fixtures |
| 013 | Licence gate before any model ships |

---

## MODEL DECISIONS

| Need | Decision | Licence status |
| --- | --- | --- |
| Body pose | **MediaPipe Pose Landmarker** (lite on LOW, full on MEDIUM/HIGH) | Apache-2.0 (code + weights) — **OK to ship** |
| Face | **MediaPipe Face Landmarker** (landmarks + transformation matrix; blendshapes OFF) | Apache-2.0 — **OK to ship** |
| Hands | MediaPipe Hand Landmarker — **on demand, Phase 5+** | Apache-2.0 — OK |
| Scene | none in MVP; SigLIP (Apache-2.0) for Phase 7 | Apache-2.0 — OK (Phase 7) |
| Aesthetic | none in MVP; NIMA (Apache-2.0) only as an optional, clearly-labelled later feature | Apache-2.0 — OK, product-risky |
| VLM / LLM | none | SmolVLM2 (Apache-2.0) / Gemma (custom terms) — experimental only |
| Multi-person | RTMPose (Apache-2.0) **or** detector + per-person BlazePose ROI | Phase 8 decision |

**Rejected for licensing:** Ultralytics YOLO (AGPL-3.0), CLIP-IQA/TOPIQ/IQA-PyTorch (non-commercial),
MobileCLIP weights (terms unverified), moondream2 (licence unknown across versions), OpenPose (mixed).

---

## KNOWN RISKS (top 8)

1. **Pose quality collapses for a full-body subject at 3–4 m** (BlazePose model card lists > ~4 m as out of
   scope; the detector input is only 224×224). Mitigation: Phase 2 resolution experiment; clear UI state
   instead of false confidence.
2. **Noisy thresholds** — nearly every user-visible threshold is `CALIBRATION_REQUIRED`; shipping them
   uncalibrated risks false positives, which are the #1 trust killer (P1 bug class).
3. **Single-person perception** limits couples/groups to Phase 8 with a new pipeline.
4. **Overlay misalignment/mirroring** across devices and cameras (mitigated by the coordinate contract,
   but only device testing proves it).
5. **Thermal/battery** on continuous vision; the degradation ladder is designed but unverified.
6. **Licence contamination** (MobileCLIP/aesthetics/YOLO) if a future contributor ignores the gate.
7. **Head-top estimation** (no skull landmark in BlazePose) makes headroom slightly approximate — must be
   calibrated and capped in confidence.
8. **Product ambiguity: who holds the phone** (photographer mode vs self/tripod mode) changes guidance
   semantics; needs a human decision before Phase 5.

---

## OPEN QUESTIONS (need the human owner)

1. **Who holds the phone?** `PHOTOGRAPHER_MODE` default, `SELF_MODE` (tripod) support in MVP, or both with
   a first-launch question? (Affects guidance semantics; decision needed by Phase 5.)
2. **Auto-capture**: default OFF (proposed) — acceptable? When it is enabled later, countdown + haptics OK?
3. **Orientation**: portrait-locked for the MVP (proposed) or landscape supported from Phase 1?
4. **Minimum device tier** to claim support for: `LOW` = 4 GB mid-range 2019+ with pose at 10–15 FPS?
5. **Default pose library size** and which templates are acceptable as the first set.
6. **Repository licence** for our own code (Apache-2.0 vs MIT vs proprietary) — the owner's call.
7. **Language scope**: Vietnamese-only UI, or vi + en from the start (vi default, en fallback proposed)?
8. **App package name / applicationId** and product display name for the manifest.

---

## TASKS FOR CODEX LOCAL

**None yet — Phase 1 is blocked pending approval.** The prepared brief is
`docs/tasks/phase-1-camerax-foundation.md`; read it, do not start it.

When approved, the first work items are, in order:

1. Create the Gradle project skeleton with the module graph (`architecture.md` §2), Java/Kotlin toolchain,
   minSdk 24, Compose + CameraX dependencies pinned.
2. Implement `:core:geometry` coordinate transforms **with unit tests first**.
3. Implement CameraX preview + analysis + capture (3-use-case session) and the `FrameRouter`.
4. Implement the luma-grid producer (`:perception:image`) and the dev/perf overlay.
5. Implement `CapabilityReport` v1 (tier micro-benchmark, delegate probe, camera capability query).
6. Record measured numbers in this file (device, Android version, latency, FPS, memory, thermal).

---

## DO NOT IMPLEMENT YET

* CameraX (Phase 1 — blocked)
* MediaPipe integration (Phase 2)
* Dashed guide rendering (Phase 3)
* Pose matching (Phase 4)
* Composition rules / guidance / readiness (Phase 5)
* Any scene, aesthetic, VLM or LLM model (Phase 7+ / experimental)
* Multi-person pipeline (Phase 8)
* Auto-capture defaults ON (must stay OFF in the MVP)

---

## NEXT APPROVAL REQUIRED

The human owner must review **Phase 0** and explicitly approve it before Phase 1 starts.
Suggested review checklist:

- [ ] Architecture and module boundaries (`docs/architecture.md`) are acceptable.
- [ ] The MVP definition (Phases 1–5) matches the product intent.
- [ ] Model choices and the licence gate (`docs/ai-models.md`, `docs/model-licenses.md`) are acceptable.
- [ ] The non-rules (rule of thirds, aesthetic scoring, "ideal" camera angles) are acceptable —
      i.e. the app will *not* nag about taste.
- [ ] The open questions above are answered (at least #1, #2, #3, #7, #8).
- [ ] Phase 1 is approved to start (Codex Local).
