# Phase Status — handoff between GitHub Agent and Codex Local

> **This is the live handoff document.** Whichever agent finishes work updates it. If it disagrees with
> reality, it is a bug.

Last updated: **2026-10-03** by **GitHub Agent** (owner review round 1 applied).

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
specs/schemas/*.json                 7 JSON Schemas (rule set, rule, pose template, frame analysis,
                                     rule result, guidance instruction, message catalog)
specs/i18n/messages.json             localization contract: instruction -> actor, allowed message keys,
                                     vi/en text, one explanation per rule
specs/validation/validate_specs.py   cross-file validator (run it before claiming a phase done)
specs/rules/mvp-rules.json           30 rules specified to implementation depth (Phases 4-6), each with
                                     threshold status, exit values, priority and phase
specs/poses/*.json                   5 pose templates: 4 seeds + 1 explicit mirrored twin (SEED_UNVALIDATED)
specs/fixtures/README.md             fixture format + the "no photographs of people" rule
```

**Validation record (2026-10-03):** `python3 specs/validation/validate_specs.py` → **0 errors**.
It checks: JSON Schema 2020-12 validity and instance validity (7 schemas, 5 templates, 30 rules, message
catalog); the whole guidance chain (rule → `suggestedActions` ⊆ `allowedActions` ⊆ instruction id enum →
message key → vi/en text → one explanation per rule); rule hygiene (id prefixes vs categories, duplicate ids,
phase ranges, severity vs `blockingAllowed`, the frozen blocking set, hysteresis direction on every
threshold, conflict references and symmetry); pose-template contracts (BlazePose landmark ids, components,
scale reference present, a `required` set, the mirror contract including an exact-x-mirror check between a
handed template and its `_m1` twin); and documentation cross-references (every rule id mentioned in `docs/`
must exist in the rule file or be listed as prose-only; every `guidance.*` key must exist in the catalog;
every `specs/...` path mentioned must exist).

Structural note: validate `rules[]` elements against `rule.schema.json`; the file itself is a
`rule-set.schema.json` document. The count of `CALIBRATION_REQUIRED` thresholds is frozen in the validator
(53 today) so that the number can only change deliberately.

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

## KNOWN RISKS (top 9)

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
8. **Scale reference for partial templates** — a headshot has no hips, so the matcher must fall back to
   shoulder width (proportion-sensitive) or inter-ocular distance (noisy). The matcher reports which
   reference it used (`pose-system.md` §4.3); the fallbacks are `CALIBRATION_REQUIRED` and must be measured
   before headshot matching is called reliable.
8. **Product ambiguity: who holds the phone** (photographer mode vs self/tripod mode) changes guidance
   semantics; needs a human decision before Phase 5.

---

## PRODUCT DECISIONS RECORDED BY THE OWNER (2026-10-03)

These are settled. They are repeated in the documents that depend on them, and no agent may re-open them
without a new explicit owner decision.

| # | Decision | Consequences |
| --- | --- | --- |
| D1 | **Primary mode: another person (the photographer) holds the phone.** Tripod/self-shooting is a future secondary mode | `PHOTOGRAPHER_MODE` is the MVP default and the only mode needed for Phases 1–5. `SELF_MODE` stays specified (`guidance-engine.md` §2) but is not implemented in the MVP; the mode switch exists in `:feature:settings`. Automatic mode detection is optional; no first-launch question is required before Phase 5 |
| D2 | **Auto-capture OFF by default** in the MVP | Readiness still computes; the shutter stays manual. Auto-capture remains implemented behind a setting (default OFF) and is verified in Phase 9 |
| D3 | **Portrait-first MVP UX**, architecture stays landscape-capable | Portrait lock by default (flag-controlled); transforms, tests and the overlay must be correct in both orientations (`tasks/phase-1-camerax-foundation.md` §2.6a) |
| D4 | **MEDIUM tier is the primary MVP target**; LOW must degrade gracefully; HIGH may enable additional analysis | Tier profiles stay as designed; LOW-tier *numbers* are `NOT_MEASURED` until a LOW device exists (`performance-strategy.md` §2, §9.1) |
| D5 | **Initial pose library: 20 high-quality SOLO poses** — 8 standing full-body, 4 three-quarter, 4 half-body/portrait, 2 sitting, 2 walking/leaning | Target recorded in `pose-taxonomy.md` §6 with concrete family assignments; the 4 authored seeds count towards it; two-person templates move entirely to Phase 8 |
| D6 | **The repository is PUBLIC and stays public**; no open-source licence is added automatically | Public visibility is **not** permission to reuse: no licence means "all rights reserved" by default. Third-party/model licence auditing continues as before (`model-licenses.md`), and the *absence* of a licence for our own code is now a recorded state rather than an open question |
| D7 | **Vietnamese is the primary MVP language**; localization must support English later; **no Vietnamese strings in domain/analysis logic** | `specs/i18n/messages.json` holds vi + en for every message key (authored in parallel, not retrofitted); engines emit ids and keys only (ADR-009) |
| D8 | **applicationId / package: `com.aiphotographer.app`** (proposed, adopted unless the owner says otherwise) | Codex Local must inspect any existing Android project before renaming anything; there is no Android project yet, so the first `settings.gradle.kts` uses this namespace |

### Still open (owner input needed, none of it blocks Phase 1)

1. **Which four seed poses should be promoted/keep first** when the 20-pose library is authored (currently
   four are authored: weight-shift standing, relaxed A-pose, hands-in-pockets, three-quarter headshot).
2. **Reference device**: record the model/Android version Codex Local will use, so every measurement row has
   a named device.
3. **LOW-tier support claim**: if no LOW device becomes available, do we ship with modelled-but-unmeasured
   LOW behaviour, or do we raise the minimum supported tier (and say so on the store listing)?
4. **Release scope**: closed testing / internal testing track and the Play data-safety declaration wording
   ("camera processed on-device; nothing collected") — needs the owner's account, not the agent's.

## TASKS FOR CODEX LOCAL

**None yet — Phase 1 is blocked pending approval.** The prepared brief is
`docs/tasks/phase-1-camerax-foundation.md`; read it, do not start it.

When approved, the first work items are, in order:

0. Run `python3 specs/validation/validate_specs.py` (needs `pip install jsonschema`) and keep it green in CI;
   add any new spec file to the validator when it is created.
1. Create the Gradle project skeleton with **only the Phase 1 modules** (`architecture.md` §2.1: `:app`,
   `:core:model`, `:core:geometry`, `:feature:camera`, `:perception:image`), Java/Kotlin toolchain,
   minSdk 24, Compose + CameraX dependencies pinned, and the module-dependency test that keeps the graph
   honest.
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
Suggested review checklist (round 1 corrections are already applied; see the review notes in this file):

- [x] Architecture and module boundaries (`docs/architecture.md`) — **reviewed in round 1**, module phasing
      added (§2.1): Phase 1 creates five modules, not fifteen.
- [x] MVP definition (Phases 1–5) matches the product intent; product decisions D1–D8 recorded above.
- [x] Model choices and the licence gate (`docs/ai-models.md`, `docs/model-licenses.md`) are acceptable.
- [x] Non-rules (rule of thirds, aesthetic scoring, "ideal" camera angles) — the app will not nag about taste.
- [ ] **Round 2 review:** the mirror contract (`pose-system.md` §4.4), the usability/confidence mapping
      (§4.1.1), the performance classes (`performance-strategy.md` §0) and the owner decisions (D1–D8).
- [ ] **Phase 1 approval:** explicit "start Phase 1" from the owner. Until then Codex Local must not write
      Android code.
