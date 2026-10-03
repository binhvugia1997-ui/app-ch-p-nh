# Phase Status — handoff between GitHub Agent and Codex Local

> **This is the live handoff document.** Whichever agent finishes work updates it. If it disagrees with
> reality, it is a bug.

Last updated: **2026-10-03** by **Codex Local** (Phase 1 implementation session).

---

## CURRENT PHASE

**PHASE 0 — ACCEPTED by the human owner.** Owner explicitly reported PR #1 merged and authorized
"START PHASE 1" on 2026-10-03.

**PHASE 1 — PARTIAL: IMPLEMENTED AND LOCALLY VERIFIED; PHYSICAL-DEVICE BASELINE BLOCKED.**
Phase 1 is not accepted. Phase 2 has not started.

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
| Phase 1 task brief | Authorized by owner; implementation handed back for review |
| **Phase 1 implementation** | ✅ local build/JVM/instrumented emulator checks; ⛔ physical-device acceptance pending |

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

**Validation record (2026-10-03, branch `arena/01a0fd5d-app-ch-p-nh`, commit `250b629`):**
`python3 specs/validation/validate_specs.py` → **0 errors, 0 warnings**.
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

Implement and verify only `docs/tasks/phase-1-camerax-foundation.md`. Phase 0 owner approval is recorded above.

Remaining Phase 1 work:

1. Connect the MEDIUM reference phone, authorize ADB, and record exact model/Android build.
2. Follow `docs/phase-1-verification.md` for physical camera/permission/capture/lifecycle and crosshair checks.
3. Record the two resolution baselines, cold start, RSS/thermal and 10-minute session with Perfetto evidence.
4. Ask GitHub Agent to review and the owner to accept Phase 1. Do not proceed to Phase 2 beforehand.

---

## DO NOT IMPLEMENT YET

* MediaPipe integration (Phase 2)
* Dashed guide rendering (Phase 3)
* Pose matching (Phase 4)
* Composition rules / guidance / readiness (Phase 5)
* Any scene, aesthetic, VLM or LLM model (Phase 7+ / experimental)
* Multi-person pipeline (Phase 8)
* Auto-capture defaults ON (must stay OFF in the MVP)

---

## NEXT APPROVAL REQUIRED

The human owner approved Phase 0 and authorized Phase 1 on 2026-10-03. The next approval is
**Phase 1 acceptance**, after device evidence and GitHub Agent review. Codex Local must not accept it.
Suggested review checklist (round 1 corrections are already applied; see the review notes in this file):

- [x] Architecture and module boundaries (`docs/architecture.md`) — **reviewed in round 1**, module phasing
      added (§2.1): Phase 1 creates four modules with image production folded into camera (ADR-014).
- [x] MVP definition (Phases 1–5) matches the product intent; product decisions D1–D8 recorded above.
- [x] Model choices and the licence gate (`docs/ai-models.md`, `docs/model-licenses.md`) are acceptable.
- [x] Non-rules (rule of thirds, aesthetic scoring, "ideal" camera angles) — the app will not nag about taste.
- [x] **Round 2 review:** owner reported Phase 0 reviewed/approved on 2026-10-03; the mirror contract (`pose-system.md` §4.4), the usability/confidence mapping
      (§4.1.1), the performance classes (`performance-strategy.md` §0) and the owner decisions (D1–D8).
- [x] **Phase 1 start approval:** explicit "START PHASE 1" from the owner on 2026-10-03.

---

## PHASE 1 IMPLEMENTATION HANDOFF — 2026-10-03, Codex Local

PHASE: 1
STATUS: partial
BRANCH: `codex/phase-1-camerax-foundation` (based on merged main `5f8bbc9`)
FILES: Gradle wrapper/catalog/root scripts; `app/`; `core/model/`; `core/geometry/`;
`feature/camera/`; `lint.xml`; `third_party/`; `tools/phase1-perfetto.pbtxt`;
`docs/architecture.md`; `docs/model-licenses.md`; `docs/tasks/phase-1-camerax-foundation.md`;
`docs/phase-1-verification.md`; this handoff.
DEVICES TESTED: **no physical phone**. Functional checks only on `Medium_Phone_API_37.0` AVD,
`sdk_gphone64_x86_64`, Android 17/API 37, debug APK; emulator is not MEDIUM hardware evidence.
MEASURED: **NOT_MEASURED on physical hardware**. Emulator timing/FPS logs are invalid for L4/L5 gates.
DEVIATIONS FROM SPEC: ADR-014 records image production folded into camera, capability UI in app,
schema-preserving luma envelope, supported CameraX/Camera2 API equivalents, and instrumentation limits.
BLOCKERS: no reference phone attached/authorized; mandatory G-P1a baseline and physical camera gates
cannot be satisfied. Owner acceptance is pending, not granted by this agent. The owner supplied Git
author identity for this repository only; the implementation is ready for the local Phase 1 commit.
QUESTIONS: none.
NEXT PROPOSED: connect the MEDIUM phone and execute `docs/phase-1-verification.md`, then GitHub Agent
review and owner Phase 1 acceptance. No Phase 2 work is authorized by this handoff.

### Implemented state

- Four modules only. JVM core has no Android/feature/perception dependencies; a test inspects the actual
  exported Gradle module graph. Geometry tests were authored before camera consumption.
- CameraX shared-viewport Preview + KEEP_ONLY_LATEST ImageAnalysis + ImageCapture; lifecycle binding,
  front/back selector, torch off, permission explanation/recovery, camera error/retry state and JPEG saving.
  Fallback order: lower analysis size → analysis-only with serial capture → capture-only.
- One analyzer executor, per-stage cadence/in-flight scheduler, disabled future-source slots, always-close
  ImageProxy handling, named Perfetto sections and bounded p50/p95 latency histograms.
- Upright unmirrored crop geometry, empty subjects, luma snapshot at 2 Hz, gravity/rotation-vector adapter,
  nullable uncalibrated IMU stability, RAM tier heuristic, GL/emulator preflight and camera size/session query.
- Debug-only crosshair/HUD, configurable 480p/720p requests and opt-in reusable ARGB/rotation benchmark.
  Release/profile omit the HUD/marker; `profile` uses release optimization with debug signing for testing.
- Vietnamese primary resources + English fallback; no analysis/domain user-facing strings, no model,
  guidance, pose matching, dashed pose template or automatic shutter.
- Third-party licences/notices bundled and displayed offline; exact 101-component runtime registry checked
  by the build. No project open-source licence added. Backup/transfer excluded for private images.

### Local validation evidence

| Check | Actual result |
| --- | --- |
| `assembleDebug`, `assembleRelease`, `assembleProfile` | PASS; release/profile R8 + resource shrinking enabled |
| JVM tests | PASS: geometry 5, actual module graph 1, scheduler/histogram 2, plane stride/crop/conversion 3 (11 total) |
| App lint debug/release/profile; camera lint debug | PASS, no issues; no lint baseline |
| `connectedDebugAndroidTest` | PASS: 2 tests on the API 37 AVD only (8 crosshair renderer combinations; proxy closure success/skip/failure) |
| Spec validator | PASS, 0 errors / 0 warnings; workspace-local Python 3.13 + jsonschema used because python3 was a Store alias |
| Packaged permissions (`aapt dump permissions`) | CAMERA + AndroidX app-local signature receiver permission; no INTERNET; unused inherited ACCESS_NETWORK_STATE removed |
| Emulator functional startup | Vietnamese no-permission screen and synthetic camera preview inspected; FULL session reported; manual shutter saved a synthetic JPEG to MediaStore; no AndroidRuntime/Phase1Camera startup error |
| Latest preview layout | Rebuilt and screenshot rechecked after BoxWithConstraints sizing fix; back 4:3 and front 16:9 previews, markers and diagnostics fit within visible bounds |

Generated evidence: JVM XML under module `build/test-results/`; instrumentation XML under
`app/build/outputs/androidTest-results/connected/debug/`; lint reports under module `build/reports/`;
local emulator screenshots under ignored `device-evidence/`. These are local evidence, not physical-device
acceptance. Reproduction commands and raw data collection are in `docs/phase-1-verification.md`.
The accidentally canceled Gradle run was resumed from the existing tree; final rebuild/lint passed.

### Required physical baselines — none obtained

| Device / build / method | Metric | 480p | 720p | Reason |
| --- | --- | --- | --- | --- |
| Reference MEDIUM / optimized profile / Perfetto 60 s | Preview displayed FPS and camera capture callback FPS | NOT_MEASURED | NOT_MEASURED | No physical phone |
| Same | Analysis delivery FPS, luma cadence, router skips, CameraX drops | NOT_MEASURED | NOT_MEASURED | No physical phone; internal CameraX drops are not exposed |
| Same | Luma p50/p95 ms + sample count | NOT_MEASURED | NOT_MEASURED | No physical phone |
| Same | YUV→ARGB + rotation p50/p95 ms + sample count | NOT_MEASURED | NOT_MEASURED | No physical phone |
| Same | Router p50/p95 ms + sample count | NOT_MEASURED | NOT_MEASURED | No physical phone |
| Same / `am start -W` + preview trace | Launch → first displayed preview frame | NOT_MEASURED | NOT_MEASURED | No physical phone; STREAMING log is a proxy |
| Same / process stats + dumpsys | RSS/high-water mark | NOT_MEASURED | NOT_MEASURED | No physical phone |
| Same / thermalservice + 10-minute session | Thermal progression, battery delta, crash/ANR | NOT_MEASURED | NOT_MEASURED | No physical phone |
| Physical cameras / screenshots + JPEG inspection | Both cameras/orientations/aspects, capture rotation/exposure/storage | NOT_MEASURED | NOT_MEASURED | Synthetic emulator cannot prove camera correctness |
| Reference phone / HUD + logs | Tier, GL, camera resolutions, combination support/fallback | NOT_MEASURED | NOT_MEASURED | No physical phone |
| LOW / HIGH physical hardware | All targets | NOT_MEASURED | NOT_MEASURED | No LOW/HIGH devices |

The raw latency table intentionally has no invented values. Was the three-use-case combination supported
on every tested **physical** device? **UNKNOWN — none tested.** Which fallback was required on physical
hardware? **UNKNOWN.** YUV conversion cost at 480p and 720p? **NOT_MEASURED.** Preview ≥30 FPS and cold start
≤1.2 s remain targets, not achieved claims. All heuristic/unmeasured calibration requirements remain open.

**Confirmed:** Phase 2 was NOT started; no INTERNET permission, cloud API, or MediaPipe dependency was
added. Auto-capture remains OFF (not implemented in Phase 1). Nothing is pushed to main or merged here.
