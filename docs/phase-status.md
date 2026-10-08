# Phase Status — handoff between GitHub Agent and Codex Local

> **This is the live handoff document.** Whichever agent finishes work updates it. If it disagrees with
> reality, it is a bug.

Last updated: **2026-10-08** by **Codex Local** (physical testing paused; source-only thermal review; safe checkpoint).

---

## CURRENT PHASE

**PHASE 0 — ACCEPTED by the human owner.** Owner explicitly reported PR #1 merged and authorized
"START PHASE 1" on 2026-10-03.

**PHASE 1 — ACCEPTED by the human owner on 2026-10-06.**
PR #2 was merged by the human owner; local main synchronized to `81df0022fce4935a067ec1b47cbfa786a8bcbe42`.

**PHASE 2 — IN PROGRESS: START AUTHORIZED by the human owner on 2026-10-06.**
Approved Phase 2 implementation is complete. The official unmodified Core source build with
upstream default dummy logging and unchanged Vision artifact preserves the privacy/offline contract.
ADR-018/019 document automated robustness fixes and immutable model ownership. Automated
non-physical validation is complete; manual human-subject gates remain PENDING_PHYSICAL_VERIFICATION.
Overall status is partial; deferred physical checks are not implementation failure.
OWNER_ACCEPTANCE_PENDING. Draft PR #3 remains open. No Premium UI or Phase 3 implementation.
Historical Phase 1/2 measurements and known limitations remain unchanged.

Latest resumable state: see the safe checkpoint at the end of this document.
Current solo session: [phase-2-physical-verification.md](phase-2-physical-verification.md).
**PHYSICAL TESTING PAUSED BY OWNER: no camera launch or inference. Source review and host validation only. Previous landscape attempt INCOMPLETE.**

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
| Phase 1 task brief | ACCEPTED by the human owner on 2026-10-06 |
| **Phase 1 implementation** | ACCEPTED by the human owner on 2026-10-06; existing evidence, measurements and limitations remain unchanged |

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

Implement and verify only `docs/tasks/phase-2-pose-face-detection.md` on
`codex/phase-2-pose-face-detection`. Phase 1 acceptance/merge and explicit Phase 2 start authorization
are recorded above. Resolve the SDK privacy blocker before packaging a perception runtime, then
complete Phase 2 physical verification/measurement. Do not start Phase 3 or claim Phase 2 acceptance.

---

## DO NOT IMPLEMENT YET

* Dashed guide rendering (Phase 3)
* Pose matching (Phase 4)
* Composition rules / guidance / readiness (Phase 5)
* Any scene, aesthetic, VLM or LLM model (Phase 7+ / experimental)
* Multi-person pipeline (Phase 8)
* Auto-capture defaults ON (must stay OFF in the MVP)

---

## OWNER ACCEPTANCE

The human owner approved Phase 0 and authorized Phase 1 on 2026-10-03, and explicitly accepted
**Phase 1 on 2026-10-06**. This records the owner's decision, not acceptance by Codex Local.
PR #2 was subsequently merged by the owner. Phase 2 start was explicitly authorized on 2026-10-06;
this does not imply Phase 2 acceptance or Phase 3 authorization.
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

### Physical verification started — 2026-10-03

Owner authorized ADB verification on Samsung SM-S918B, Android 16/API 36, serial R5CW40EE9QK,
build `samsung/dm3qxxx/dm3q:16/BP4A.251205.006/S918BXXSAFZH3:user/release-keys`.
Existing debug APK installed successfully; `am start -W` returned Status ok (initial debug TotalTime
1316 ms, not the optimized first-displayed-preview baseline). Both instrumentation tests passed on
the physical phone. Home/foreground returned Status ok and rear session rebound successfully.

Camera logs report rear ID 0 and front ID 1 FULL sessions, requested 640x480, original three-use-case
query true, no fallback. Capability reports 7416156160 bytes RAM, GL ES 3.2, emulator false, MEDIUM
heuristicOnly true; this does not establish a calibrated reference device or model delegate performance.
Rear analysis log reports 640x480 buffer / upright crop 480x640. JPEG save events to MediaStore IDs
14673 and 14674 occurred during the session without an automated shutter action; physical JPEG
orientation/exposure inspection remains pending. Preview streaming and delivery have log evidence;
visual crop/mirror correctness is not yet verified.

Permission revocation returned without shell error but subsequent UI/package state showed camera
permission granted. Camera state error code 5 appeared during that interval, followed by successful
rebind. A controlled denial/settings/recovery test remains pending; do not classify it as passed.
No AndroidRuntime crash appeared in collected app logs; lastanr reported none since boot. These are
short-session observations, not the required ten-minute soak result.

Raw evidence is local and ignored under `device-evidence/samsung-sm-s918b/` (UI XML, debug metrics
and functional logs). Optimized 480p/720p traces, displayed FPS, cold first-frame baseline, RSS,
thermal/battery progression, screen off/on, airplane mode and physical coordinate matrix remain
NOT_MEASURED/pending. No physical performance gate is claimed from debug rolling metrics.

Verification paused for owner interaction: keep phone unlocked, point cameras toward non-person
targets, confirm rear/front preview, and prepare to rotate the phone for the coordinate matrix.
Do not touch controls during subsequent automated runs unless instructed; the permission test needs
an uncontested UI sequence. Phase 1 remains partial and unaccepted. Phase 2 was not started; no
INTERNET permission, cloud API or MediaPipe dependency was added. No push or merge performed.

### Controlled physical verification and trace collection — 2026-10-03

The owner visually confirmed live, correct rear and front previews. Controlled ADB switching bound
camera IDs 0 and 1 successfully, both FULL, query true, no fallback. Automated manual shutter actions
saved rear MediaStore ID 14677 and front ID 14678. Rear metadata: 4080x3060, orientation 90 degrees,
`Pictures/AI Photographer/AI_1791030771998.jpg`. JPEG EXIF/displayed exposure and landscape capture
still need inspection. Home/foreground returned Status ok, HOT TotalTime 126 ms; no app crash observed
in collected AndroidRuntime logs; `lastanr` again reported none since boot. Two physical instrumentation
tests remain PASS. No uninterrupted ten-minute soak is claimed.

The permission UI changed between dump and denial tap; the tap reached a preview control instead.
Explicit `pm revoke --user 0` was observed granted=false, but the subsequent dialog/grant sequence
was not controlled reliably. Denial, repeated denial and Settings recovery remain pending; no pass claimed.

Existing optimized `profile` APK installed for baselines, RGB benchmark enabled at 1 Hz. Perfetto
v58.2 trace processor analyzed local files (no uploads). Queries/CSV are in ignored evidence directory.
Stage percentiles use nearest rank, complete nonnegative slices, across retained trace bounds; router
includes stage work. App-layer presentation rate counts distinct non-dropped FrameTimeline surface tokens
for the app TextureView-containing layer, not a count of unique camera image contents.

| Optimized rear-camera measurement | 480p | 720p |
| --- | --- | --- |
| Buffer / upright crop | 640x480 / 480x640 | 1280x720 / 720x960 |
| Valid steady-state window | NOT_MEASURED: interrupted by screen/lock activity | 59.993022 s, no errors/overwritten chunks |
| App-layer presented frames / rate | NOT_MEASURED | 1800 / 30.003490 Hz |
| Analysis delivered slices / rate | NOT_MEASURED | 1798 / 29.970152 Hz |
| Luma samples / rate / p50 / p95 | NOT_MEASURED | 116 / 1.933558 Hz / 0.572969 / 3.246614 ms |
| RGB + rotation samples / rate / p50 / p95 | NOT_MEASURED | 59 / 0.983448 Hz / 48.000833 / 55.783073 ms |
| Router samples / p50 / p95 | NOT_MEASURED | 1798 / 0.040469 / 4.626094 ms |
| Intentional luma skips in trace window | NOT_MEASURED | 1682/1798 = 93.55%; internal CameraX drops UNKNOWN |
| Camera capture callback rate, cumulative log | 30.0065 Hz over 108.0764 s after reset; not steady-window result | 29.9365 Hz over 291.7508 s after reset |
| RSS min/max / high-water, process stats | Interrupted trace: not steady baseline | 122.035156 / 157.953125 / 163.777344 MiB; 61 samples |
| Cold Activity TotalTime, `am start -W` | 373 ms repeat | 339 ms |
| Launch-to-STREAMING proxy | 948.361926 ms repeat; not displayed-content latency | 31749.524987 ms, lock-screen-confounded; invalid cold baseline |
| First actual displayed camera frame | NOT_MEASURED: content onset not established | NOT_MEASURED: lock-screen-confounded launch |

720p evidence: `phase1-steady720p-full.pftrace`, `720p-trace-summary.csv`, `720p-full-log.txt`,
memory/thermal dumps under `device-evidence/samsung-sm-s918b/`. The first 64 MiB trace retained only
38.164 s with 1356 overwritten chunks; it is superseded by the 256 MiB evidence config. The 480p full
trace retained 59.986881 s but only 374 router / 26 luma / 14 RGB samples and multiple app-layer lifetimes.
It contains fingerprint/lock-screen UI and a third-party Hanzii LockScreenActivity; its low full-window
rate must not be described as app camera throughput. It remains raw diagnostic evidence only.

Thermal/battery conditions materially limit interpretation: initial thermalservice status was **4
(CRITICAL)** with battery temperature 44.9 C, then sampled status 2 (MODERATE), then 3 (SEVERE)
after the valid 720p window and at the end. This was not a cooled reference run. USB power was true;
battery level changed 26% to 34%, final reported battery temperature 42.3 C. These snapshots span the
session, not a timed ten-minute battery test; battery drain is NOT_MEASURED. The no-severe target was
not demonstrated. Camera work was force-stopped after status 3 was reviewed. No architecture change
is inferred from this thermally constrained, charging session; cooled validation/review remains required.

Paused for owner setup: let the phone cool, increase screen timeout to 10 minutes, disable Hanzii's
lock-screen feature temporarily, keep unlocked on a non-person target. Then repeat controlled permission,
480p/cold baseline and ten-minute soak, and perform physical rotation/coordinate/JPEG checks with owner.
Screen off/on, airplane mode, fallback on unsupported hardware and other device tiers remain pending.
Phase 1 stays partial and unaccepted. Phase 2 NOT started; no INTERNET/cloud/MediaPipe; no push/merge.

### Cooled repeat — controlled physical verification, 2026-10-03

Owner reported cooled/unlocked phone with 10-minute screen timeout; ADB confirmed timeout 600000 ms,
initial thermal status 1 (LIGHT), battery temperature 37.8 C, USB power true, level 33%.
Existing optimized profile APK was used for both baseline repeats with RGB benchmark enabled at 1 Hz.
Every five-second warmup/trace thermal sample stayed LIGHT, including each start/end. Each recording
retained a complete approximately 60-second steady window with no trace errors or overwritten chunks.
These results supersede the interrupted/hot baseline attempts above for comparison; prior data remains
historical evidence. They apply only to this SM-S918B/Android 16 build, not calibrated MEDIUM hardware.

| Optimized rear-camera measurement | 480p | 720p |
| --- | --- | --- |
| Buffer / upright crop, portrait 4:3 | 640x480 / 480x640 | 1280x720 / 720x960 |
| Retained steady window | 59.993499 s | 59.993065 s |
| App-layer presented / submitted frames | 1795 / 1795 | 1797 / 1797 |
| Presented app-layer rate | 29.919908 Hz | 29.953462 Hz |
| Analysis delivered slices / rate | 1795 / 29.919908 Hz | 1797 / 29.953462 Hz |
| Luma n / rate / p50 / p95 | 115 / 1.916874 Hz / 0.599948 / 2.521563 ms | 115 / 1.916888 Hz / 0.585469 / 2.012656 ms |
| RGB + rotation n / rate / p50 / p95 | 59 / 0.983440 Hz / 23.496823 / 39.774896 ms | 59 / 0.983447 Hz / 41.368646 / 51.147761 ms |
| Router n / p50 / p95 | 1795 / 0.044635 / 3.691614 ms | 1797 / 0.046928 / 3.564427 ms |
| Intentional luma skips / delivered | 1680/1795 (93.59%) | 1682/1797 (93.60%) |
| CameraX internal discarded count | UNKNOWN, not exposed | UNKNOWN, not exposed |
| Camera capture callback cumulative rate / elapsed | 29.952903 Hz / 85.066879 s | 29.985118 Hz / 85.042186 s |
| RSS min / max, 61 process-stat samples | 120.609375 / 152.730469 MiB | 129.636719 / 156.195312 MiB |
| RSS watermark max | 154.058594 MiB | 158.386719 MiB |
| Cold Activity TotalTime / WaitTime (`am start -W`) | 270 / 279 ms | 264 / 266 ms |
| Activity-construction to STREAMING proxy | 844.054271 ms | 648.343073 ms |
| Traced launch to first presented camera-texture frame | 976.352865 ms | 791.476719 ms |
| Thermal during baseline repeats | LIGHT (1) | LIGHT (1) |

First-presented-frame method: in the separate 15-second cold trace, take `launching:
com.aiphotographer.app` start; find the first app RenderThread `acquireBuffer` child named
`SurfaceTexture-*` (this app has one preview TextureView); follow its ancestors to `DrawFrames <token>`;
join that token to the app's non-dropped actual FrameTimeline surface frame; presentation is slice
`ts+dur`. 480p token 143759346 was acquired at 394244617464103 ns and presented at
394244627241708 ns, launch 394243650888843 ns. 720p token 143843500 was acquired at
394378778819625 ns and presented at 394378782996396 ns, launch 394377991519677 ns. Both were
On-time Present. This is camera-buffer-to-compositor evidence, not the splash/first generic UI frame.

The presented rates are slightly below the numeric 30 Hz target; do not round them into a claimed pass.
No app-layer submitted frames were reported dropped in these windows. This does not expose internal
CameraX drops or prove every presentation contains a unique camera image. RGB cost is material for
later budget review; no conversion/model architecture change or Phase 2 work was made here.

Evidence: `cooled-480p/720p-{cold,steady}.pftrace`, matching summary CSV/log/memory/battery/thermal
files, `cold.sql` and `baseline.sql` under ignored `device-evidence/samsung-sm-s918b/`. Perfetto v58.2
analysis is local. All arithmetic/calibration flags elsewhere remain hypotheses unless measured here.

Permission sequence is now controlled and PASS: reset runtime permission state; deny twice using the
system dialog; verify Vietnamese explanation after each; open app-specific Settings; revoke via actual
Camera Settings radio; Back to app yields explanation; grant via actual Settings radio; Back to app
restores FULL camera. The earlier recovery assertion failed because `am start` brought the task with
Settings still on top; correct Back navigation resolved the harness issue, no production code changed.
UI XML and command event JSON preserve each stage. Ten-minute soak and physical rotation/crop/JPEG
matrix are being completed separately; Phase 1 is not accepted.

### Ten-minute soak and current manual checkpoint

Existing debug APK, RGB benchmark OFF, timed with host monotonic clock after initial camera-ready UI:
**600.000514 s completed**, with camera switches at approximately 126/484 s, background/resume near
262 s, resolution switch near 374 s. All four post-action UI snapshots were camera-ready; capability
logs report rear/front FULL at both 640x480 and 1280x720, query true, no fallback. Thermal began at
**MODERATE (2)** and every approximately 25-second sample/end stayed 2; no severe state during this soak.
Collected AndroidRuntime/Phase1Camera/analyzer logs contain no crash/error; `lastanr` reports none since
boot. This satisfies the observed ten-minute crash/ANR check on this phone, not general device coverage.

Airplane mode was enabled by ADB for 56.35 s (recorded command timestamps), with camera-ready UI after
enable and analysis continuing in the log. It was restored; a final ADB read confirmed airplane_mode_on=0.
USB power remained true. Battery level 37% to 39%, battery temperature 39.8 C to 39.9 C; plugged-in
charge delta is not battery drain. Debug dumpsys TOTAL RSS began 230784 KiB and ended 172144 KiB
(PSS 141289/140149 KiB; final swap PSS 40601 KiB). These are snapshots; no broad memory-stability claim.
Evidence: `cooled-soak-result.json`, event JSON, UI snapshots, logs, battery/thermal/memory dumps.

Actual JPEG EXIF inspected from the two previously saved files: rear 14677 tag 274 = 6 (rotate 90 CW),
4080x3060; front 14678 tag 274 = 8 (rotate 270 CW), 4000x3000. Both decode and remain in MediaStore
Pictures/AI Photographer. Front displays upright using EXIF; the earlier rear image is nearly dark,
so expected exposure/scene orientation cannot be verified from it. Repeat with an uncovered camera
aimed at a well-lit non-person target. Images remain ignored local evidence, never committed.

Screen off/on initiated by KEYCODE_SLEEP then KEYCODE_WAKEUP; the phone is awake on its lock screen
(`mDreamingLockscreen=true`). Verification stopped for owner unlocking, as requested. Post-unlock
camera resume remains pending. The `allowLandscape` start delivered to an existing activity instance;
it must be force-stopped/relaunched with that flag before the physical rotation test, since the flag
is read in onCreate. No production change is needed.

Next owner action: unlock normally; hold the phone upright in portrait with both cameras uncovered;
aim rear camera at well-lit printed text/box; confirm preview resumes. Then complete the physical
rear/front × portrait/landscape × 4:3/16:9 coordinate/crop matrix and portrait/landscape JPEG checks.
Fallback paths remain untested on unsupported physical hardware; only this phone has been measured.
Owner acceptance remains pending. No Phase 2, INTERNET permission, cloud API, MediaPipe, push or merge.

### Portrait matrix checkpoint and clipping correction

Owner unlocked the phone and confirmed live rear preview resumed: screen-off/on recovery PASS with
owner observation, followed by successful FULL rear bind in ADB logs. Debug app was force-stopped and
restarted with `allowLandscape=true` so the onCreate flag is active for the next rotation test.
Thermal status before this checkpoint was MODERATE (2).

Rear portrait 4:3 and 16:9 screenshots/captures collected. MediaStore 14681: 4080x3060, rotation 90;
14682: 4080x2294, rotation 90; both decode upright with visible scene detail in Pictures/AI Photographer.
The earlier dark-rear exposure limitation is resolved for these captures at a qualitative level; a
stationary edge target is still needed to verify exact preview/JPEG crop and coordinate alignment.
The scene/phone moved between preview and capture, so no precise alignment pass is claimed.

Physical screenshot revealed a Phase 1 UI defect: for portrait 16:9, TextureView content painted outside
its allocated `[42,62][679,1195]` preview bounds. Added Compose `clipToBounds()` to the existing preview
container in MainActivity; no coordinate math, camera pipeline, module boundary or product mode changed.
All app debug/release/profile builds and lint passed; Gradle `test` passed (unchanged pure tests up-to-date).
Rebuilt debug APK installed on the phone and both portrait layouts screenshot-rechecked: 16:9 side margins
are now clean; 4:3 stays within `[0,149][720,1109]`. Marker centres approximately (201,629) for 16:9 and
(180,629) for 4:3 match quarter-width/half-height mapping in the actual view bounds. This verifies rendered
placement and clipping, not independent physical target correspondence or front mirroring yet.

Updated debug APK SHA256: `00008AB17F482449D06DA206F931A99F79AAAF6EC54543AB4406AD7C6088A070`.
Evidence: `rear-portrait-*` and `clip-rear-portrait-*` under the ignored device evidence directory.
The cooled baseline and ten-minute soak tables above apply to the pre-clipping APK based on `9de90a7`;
they must not be described as measurements of the newly rebuilt APK. Repeat optimized baselines/soak
after remaining matrix fixes, if any, to establish final-build evidence.

Paused for required physical action: rotate to landscape, keep rear camera selected, aim at a stationary
well-lit non-person edge target and hold still. Then collect rear landscape 4:3/16:9; front-camera target
repositioning and portrait/landscape checks follow. Phase 1 remains partial and unaccepted.

### Rear landscape checkpoint

Rear landscape rotation 1 was observed with FULL camera session. Both aspect captures saved:
MediaStore 14686 (2292x3060, orientation 0) and 14688 (1728x3060, orientation 0).
JPEGs decode with upright legible target text and visible detail; qualitative exposure/save PASS.
The current portrait-first viewport remains tall and narrow in landscape. Rendered marker placement
matches the allocated view bounds, but independent physical crop/target correspondence is still pending:
the first landscape preview/JPEG pair showed different framing, so no alignment PASS is claimed.

A tightly bracketed retry first found the launcher. Bringing the existing app task forward succeeded;
thermal status was MODERATE (2), battery sensor 38.8 C. The bracket saved MediaStore 14690, but screenshots
show portrait orientation and a different scene without the prepared printed target. It is excluded from
landscape matrix evidence. No camera implementation change was made from this inconclusive comparison.
Ignored local evidence: `rear-landscape-*`, including `rear-landscape-bracket-*`.

Required next owner action: restore landscape, rear camera and stationary printed non-person target;
confirm live preview and leave the phone fixed in that position during automation. Front physical matrix
and final-build optimized baselines/soak remain pending. Phase 1 is partial; acceptance remains with owner.
No Phase 2, INTERNET permission, cloud API, MediaPipe dependency, push or merge was added/performed.

### Controlled landscape target repeat

Owner restored stationary printed target and landscape. Bracketed captures now verify rotation 3
(opposite landscape direction) and consistent preview/JPEG framing by visual target-edge comparison.
Rear 4:3: MediaStore 14692, 2292x3060, orientation 180. Rear 16:9: MediaStore 14693,
1728x3060, orientation 180. Both save/decode with visible text/detail and matching scene rotation.
Target text is sideways in both preview and decoded JPEG because of target placement, not independently
claimed upright text. Cyan marker is quarter-width/half-height in both allocated preview bounds and
over the same printed A region near the corresponding normalized JPEG point. Qualitative rear landscape
crop/marker comparison PASS; no numerical reprojection error or independent analysis landmark is measured.
Saved-result text changes preview container size; before/after screenshots reflect that layout change.
No camera-source fix was required by this repeat. Thermal MODERATE (2), BAT 39.7 C.
Evidence: `rear-landscape-4x3-bracket-*` and `rear-landscape-bracket-*` (16:9), ignored local files.

Paused for required physical front-camera setup: keep landscape, select front with the camera-switch
button, reposition phone so front lens sees a well-lit printed non-person target with a clear edge,
and hold fixed. Confirm front live preview before resuming. Front portrait follows; final-build baseline
and soak remain pending. Owner acceptance is not granted by this report.

### Front landscape checkpoint

Owner selected front camera and prepared printed target. Rotation 3 observed; camera-ready UI and
successful manual saves in both aspects. MediaStore 14694: 2248x3000, orientation 180 (4:3);
14695: 1694x3000, orientation 180 (16:9). Printed text is mirrored in preview and unmirrored in decoded
JPEG, as expected. Qualitative framing after accounting for horizontal mirror and exposure/detail PASS.
Cyan marker renders at x=0.75, y=0.5 in allocated preview bounds in both aspects. It lands on plain wood
in this setup, so independent precise edge-target alignment is NOT_VERIFIED; no reprojection error claimed.
Thermal before these checks MODERATE (2), BAT 40.5 C. Evidence: ignored `front-landscape-4x3-bracket-*`
and `front-landscape-bracket-*`. A filtered last-2500-lines capability/crash query returned no entries;
it does not establish a fresh capability or no-crash log result. Prior FULL front capability evidence
remains recorded above; app remained responsive and saved successfully during this checkpoint.

Next physical action: keep front selected, rotate upright portrait, place a distinct printed corner/edge
under the cyan right-quarter crosshair, confirm live preview and hold fixed. Then collect front portrait
and independent edge comparison. Exact front landscape edge alignment remains pending; final-build
optimized baselines/soak remain pending. No Phase 2, INTERNET, cloud API, MediaPipe, push or merge.

### Front portrait checkpoint (2026-10-04)

ADB phone R5CW40EE9QK remains authorized; initial thermal LIGHT (1), BAT 36.7 C.
Portrait rotation assertion passed. Front 4:3 MediaStore 14700: 4000x3000, orientation 270;
front 16:9 MediaStore 14701: 4000x2248, orientation 270. Both manual saves PASS.
4:3 preview contains mirrored printed text; decoded JPEG is unmirrored with qualitatively matching
framing/detail. Target itself is upside down in both; no claim of upright target placement.
Crosshair renders at right-quarter/half-height near the box side in 4:3. No numerical alignment error
measured. In 16:9 the printed target is outside the view and the visible surface is blurred in preview
and JPEG; physical edge correspondence and well-lit detail check remain NOT_VERIFIED for this pair.
This evidence does not establish whether target displacement came from movement or a camera defect.
No implementation change made from the inconclusive 16:9 target comparison.
Ignored evidence: `front-portrait-4x3-bracket-*`, `front-portrait-16x9-bracket-*`.

Paused for required physical setup: leave current front portrait 16:9 layout selected; reposition the
phone/target until a sharply focused printed corner or edge is directly under the cyan crosshair,
with text visibly readable (mirrored is expected). Hold fixed and confirm readiness. Do not toggle
aspect or camera. Then repeat this bracket and complete outstanding landscape edge/final-build baseline
and soak verification. Phase 1 remains partial and unaccepted; no push or merge.

### Front portrait 16:9 edge repeat

Owner repositioned printed box. MediaStore 14702 saved: 4000x2248, orientation 270.
Bracketed preview now shows readable mirrored text and a distinct box edge near the cyan crosshair;
decoded JPEG shows unmirrored text, visible detail and qualitatively corresponding framing/edge.
Front portrait 16:9 qualitative exposure, crop/mirror and near-edge correspondence PASS, superseding
the blurred-target limitation for this combination. This is visual comparison, not a measured numerical
reprojection error or an independently detected analysis landmark. Saved-result text resizes the preview
container after capture; before/after positions must be compared in normalized view coordinates.
Evidence: `front-portrait-16x9-bracket-*`, now containing the repeat (MediaStore 14702).

Required next owner action: keep front selected, rotate landscape, keep current 16:9 selected and align
the same readable box edge under cyan right-quarter crosshair; hold fixed. Complete outstanding front
landscape near-edge comparison, then repeat final-build optimized baselines and soak. Phase 1 stays
partial and owner acceptance pending. No Phase 2, INTERNET, cloud API, MediaPipe, push or merge.

### Orientation blocker and correction (2026-10-04)

Owner reports UI stays portrait despite system Auto rotate. Front-landscape verification acceptance is
reopened: previous landscape screenshots are historical observations only, not a completed acceptance
gate. Coordinate/rotation physical matrix remains pending on the corrected APK.
ADB activity dump identified a normal MAIN/LAUNCHER intent for this app and requestedOrientation PORTRAIT.
Manifest has no orientation/configChanges override. Root cause: MainActivity's default portrait lock
only bypassed by an ephemeral debug intent extra read in onCreate. Normal launcher starts lose that
extra; an existing lock was not explicitly reset and onNewIntent did not apply the setting.

Fix: debug-only allowLandscape preference persists across launcher starts/recreation, and explicit extras
update it in onCreate/onNewIntent. Enabled selects SCREEN_ORIENTATION_USER (respects system Auto rotate);
disabled explicitly restores portrait. Release/profile ignore this preference and retain portrait-first
policy. No module/camera geometry architecture change. Debug verification setting defaults false.
Regression instrumentation verifies USER through recreation and launcher-style start and restores PORTRAIT
when disabled. Debug/release/profile builds, all unit tests, app lint for all variants, camera debug lint
and connected instrumentation PASS. Initial lint UseKtx finding fixed and full checks rerun successfully.

Final debug APK reinstalled successfully and launched with allowLandscape=true.
SHA256 `738A479962C282C3A14A593225B22CDA4D59B3893ABAC4D81C7EE338E3C7FB19`.
Observed install-following debug launch TotalTime 2351 ms / WaitTime 2353 ms is not an optimized first-frame
baseline. All previous performance tables are historical; final-build optimized baselines and 10-minute
soak must be rerun after owner physically verifies rotation. They are NOT_MEASURED for this corrected APK.
Next: owner rotate normally portrait to landscape, confirm UI reflows and preview remains live, return
portrait then landscape again and report result. Do not accept Phase 1 or start Phase 2.

### Owner rotation confirmation and final-matrix setup checkpoint

Owner physically confirmed portrait -> landscape -> portrait -> landscape works after the orientation
fix: UI reflows and live preview remains active throughout. Rotation behavior PASS by owner observation.
Final-build front-landscape matrix remains pending. Automated setup initially found the launcher with
UI rotation 0; bringing the existing camera task forward succeeded, but a second UI dump still reported
rotation 0. The landscape assertion stopped before any photo capture, so no portrait evidence is counted
as landscape and no forced-rotation workaround was used. Local evidence:
`final-front-landscape-4x3-bracket-initial.xml` and `controlled-rear-bracket-events.json`.
Thermal MODERATE (2), BAT 38.6 C on foreground retry. No production changes this checkpoint.

Required physical action: keep app visible, rotate into landscape, select front if needed, aim at a
readable non-person box edge beneath cyan right-quarter crosshair, and keep fixed with controls untouched.
If UI stays portrait, report that instead of confirming readiness. Once the matrix is complete, install
rebuilt profile and rerun 480p/720p baseline and final debug soak; these remain NOT_MEASURED for final build.
No Phase 2, INTERNET permission, cloud API, MediaPipe dependency, push or merge. Acceptance remains pending.

### Final-build front landscape and thermal interruption

Owner confirmed front camera, 16:9 and fixed target. UI dump confirmed landscape rotation 1 before
capture. Final debug APK front 16:9 MediaStore 14703: 1686x3000, orientation 0; front 4:3
14704: 2250x3000, orientation 0. Both manual saves, decoded readable detail, mirrored preview versus
unmirrored JPEG and qualitative target framing PASS. Crosshair is right-quarter/half-height in each
view and qualitatively corresponds to the target graphic/text boundary after mirroring. No numerical
reprojection error measured. Current landscape viewport remains tall/narrow. Ignored evidence:
`final-front-landscape-16x9-bracket-*`, `final-front-landscape-4x3-bracket-*`.

Rebuilt optimized profile installed successfully, SHA256
`BC6492F2C0CAF38BD6BF1A086DD4258E4EBFF16DE3FCBEBE07CC3A39F1017227`.
Final 480p attempt started at MODERATE (2), warmup remained 2, then reached SEVERE (3) at the first
steady trace poll. Harness immediately force-stopped the camera app. This window is ABORTED/INVALID
as a final 60-second baseline, not a performance pass. Activity cold launch TotalTime 428 ms/WaitTime
430 ms is observed but is not first camera-frame presentation. Raw evidence `final-480p-*` retained
locally; no old baseline substituted. Final 720p and final-build 10-minute soak NOT_MEASURED, postponed
because thermal status reached 3. No production code changed this checkpoint.

Required owner action: leave app stopped, keep USB connected, let phone cool, keep timeout at 10 minutes,
then unlock with rear lens unobstructed and reply ready. Check thermal status before restarting; aim for
NONE/LIGHT for comparable controlled baselines. Do not reinstall debug manually: profile is installed
for baseline; automation will restore debug for soak. Phase 1 remains partial and owner acceptance pending.

### Comprehensive orientation correction (2026-10-05)

Owner reproduced the blocker and explicitly superseded the prior portrait-lock requirement:
portrait-first is initial UX preference only. All prior physical rotation PASS observations are historical;
the current correction is NOT_PHYSICALLY_VERIFIED. Matrix, baseline and soak are paused at owner request.

Root cause confirmed in production code: profile/release always selected SCREEN_ORIENTATION_PORTRAIT,
while debug alone could bypass it through a persistent verification setting. The previous session installed
profile for baselines, so it reinstated the lock. That previous workaround never corrected all variants.
Manifest and merged manifests contain neither screenOrientation nor configChanges overrides. No other
orientation setters found. The locale wrapper also copied the entire configuration, unnecessarily fixing
orientation/size values in its override; it now overrides locale alone. Activity normal recreation remains
enabled. No synthetic display orientation, ADB forced rotation or sensor override is used.

Removed PORTRAIT_FIRST BuildConfig flag, runtime orientation setters and all production reads/writes of
allowLandscape verification state. Existing stored settings/extras are inert. onNewIntent only retains
the current intent. Compose observes actual LocalConfiguration; preview ratio is 3:4/9:16 in portrait,
4:3/16:9 in landscape, fitted within constraints with clipping. Camera/aspect/resolution/RGB controls use
rememberSaveable through recreation. CameraSession already reads display rotation and shared viewport
after PreviewView layout; its old lifecycle-bound session is disposed and the new session binds after
layout. Orientation is included in the Compose camera key as well. No core transform branching added.
ADR-015 and task brief reflect the owner's updated policy. No new modules or dependencies.

Updated instrumentation exercises obsolete preference/intent values false and true, three recreations
each, SINGLE_TOP new intent and normal launch; no app orientation restriction is asserted throughout.
Three physical instrumentation tests PASS (zero failures/errors/skips). They do not simulate physical
rotation or prove real camera correctness after a sensor transition. A concurrent Gradle output-cache
conflict occurred during validation; final validation is rerun sequentially before handoff.

Physical steps after reinstall: Auto rotate ON, launch normally from icon without debug extras; rear
4:3 rotate portrait -> landscape -> portrait five times, waiting for live preview after each. Repeat
front 16:9; confirm camera/aspect selection retained and landscape preview has landscape proportions.
Press Home in landscape and reopen icon, then remove app from Recents and relaunch while landscape.
Repeat after portrait relaunch. Report any frozen/black/stretched preview, lost camera/aspect choice
during rotation, or portrait lock. Do not mark PASS until owner reports these results. No performance
baseline/soak until owner verifies the correction. No Phase 2, INTERNET, cloud API, MediaPipe, push/merge.

Final sequential validation PASS: debug/release/profile APK builds, JVM tests, app lint in all variants,
camera debug lint and debug test APK assembly (3m54s); connected instrumentation 3 tests PASS, zero
failures/errors/skips. Spec validator 0 errors/0 warnings and git diff check PASS. Diff inspected.
Corrected debug APK installed and launched normally without extras. SHA256
`90C33FC6861EF20BE5274CBD47047D4AC293BDCBE90B173D044E3A5C303BDAA9`.
Activity launch TotalTime 1142 ms / WaitTime 1143 ms is installation-check evidence only, not a baseline.
No matrix, performance baseline or soak run after this correction. All previous measurements apply to
earlier APKs and must not be represented as current-build performance. Work remains uncommitted.

### Owner verifies comprehensive rotation correction

Owner reports physical rotation PASS on the corrected debug APK: rear 4:3 and front 16:9 each passed
five repeated portrait/landscape transitions; live, correctly sized, unstretched preview and selections
preserved. Home/reopen in landscape, Recents removal/relaunch in landscape, and portrait relaunch then
landscape also PASS by owner observation. This verifies the requested physical rotation behavior; it is
not owner acceptance of Phase 1 or proof of the coordinate target matrix.

Resumed read-only setup inspection confirms UI rotation 1 and a landscape-shaped 4:3 preview with rear
quarter-width marker. Thermal LIGHT (1), BAT 39.2 C. Current image is featureless/blurred with no usable
printed edge target. No photo, matrix pass, baseline or soak claimed from this setup. Ignored evidence
`rotation-corrected-current.xml/png`. Required physical action: keep landscape and rear 4:3, uncover lens
and aim at a well-lit printed box/straight edge, positioned under cyan left-quarter crosshair; hold fixed.
Then collect corrected-build rear landscape pair, front landscape pair and any remaining portrait target
checks, followed by optimized 480p/720p baselines and final soak. Current-build baselines remain pending.
No production changes, Phase 2, INTERNET permission, cloud API, MediaPipe dependency, push or merge.

### Corrected rear landscape target attempt

Rotation 1 assertion passed; manual save MediaStore 14742, 4080x3060, orientation 0, succeeded.
Bracketed screenshots show substantially different framing before and after capture, and JPEG is heavily
blurred. Save PASS only; coordinate/crop comparison and exposure/detail acceptance NOT_VERIFIED for this
attempt. No camera bug cause inferred from this inconclusive evidence. Ignored files:
`corrected-rear-landscape-4x3-bracket-*`. No production changes or baseline/soak run.
Required physical action: keep rear landscape 4:3, brace phone on a stable support if available, align
a stationary printed box edge under cyan left-quarter marker, verify clear preview, then leave phone and
target untouched until automation completes. Repeat 4:3 before advancing to 16:9. Phase 1 stays partial.

### Stable corrected rear landscape repeat

Stable bracketed rear landscape 4:3 MediaStore 14743: 4080x3060, orientation 0; 16:9
14744: 4080x2296, orientation 0. Rotation 1 verified. Before/after 4:3 previews retain the same scene;
both decoded JPEGs have readable printed detail and qualitatively matching preview framing. Rear landscape
save, exposure/detail, crop and near barcode/box boundary correspondence PASS by visual comparison.
Marker placement matches left-quarter/half-height of each actual view; no numerical reprojection error
or analysis landmark measured. This supersedes the blurred 14742 attempt for rear landscape checks.
Ignored evidence `corrected-rear-landscape-4x3-bracket-*` (14743) and `corrected-rear-landscape-16x9-bracket-*`.
No production changes. Next physical action: keep landscape/current 16:9, select front, reposition so front
lens sees stationary readable box edge under cyan right-quarter marker, brace/hold fixed. Then collect
corrected front landscape pair and proceed through remaining physical setup/baselines/soak. Acceptance
pending; no Phase 2, INTERNET, cloud API, MediaPipe, push or merge.

### Corrected front landscape pair

Stable front landscape 16:9 MediaStore 14745: 4000x2252, orientation 180; 4:3
14746: 4000x3000, orientation 180. Landscape assertion passed. Both JPEG saves, readable printed detail,
mirrored preview/unmirrored JPEG and qualitative crop/near-table-edge correspondence PASS. The marker
renders at right-quarter/half-height, near the corresponding table region after mirroring; no numerical
reprojection error measured. Evidence `corrected-front-landscape-16x9-bracket-*` and
`corrected-front-landscape-4x3-bracket-*`, ignored locally. No production change or baseline/soak run.
Required next physical action: keep front/current 4:3, rotate portrait, realign printed edge under cyan
right-quarter marker, ensure clear preview and hold stationary. Repeat corrected-build portrait pairs
before final baselines/soak. Phase 1 acceptance remains pending.

### Front portrait setup assertion

Owner reported portrait readiness, but two consecutive UI dumps reported rotation 3 with landscape
control bounds. Portrait assertion stopped both attempts before shutter or aspect changes. No portrait
matrix evidence collected and no physical rotation failure cause inferred. Local evidence
`corrected-front-portrait-4x3-bracket-initial.xml` and harness events retained. Required physical action:
hold phone upright in portrait (not flat), wait for visibly tall UI, realign front target and hold fixed.
If UI stays landscape, owner should report it as a rotation failure for investigation. No production
changes or baseline/soak run; acceptance pending.

### Corrected front portrait pair

Portrait assertion passed on owner-confirmed setup. Front 4:3 MediaStore 14747: 3000x2250,
orientation 270; 16:9 14748: 4000x2248, orientation 270. Both manual saves, readable printed detail,
mirrored preview/unmirrored JPEG and qualitative framing PASS. Crosshair renders at right-quarter/
half-height inside printed target; a distinct edge is not directly under it, so no precise edge alignment
or numerical reprojection error is claimed. HUD shows R720P during this checkpoint, recorded as observed
rather than assumed default 480p. Evidence `corrected-front-portrait-4x3-bracket-*` and
`corrected-front-portrait-16x9-bracket-*`. No production changes or baseline/soak run.
Next physical action: keep portrait and current 16:9, select rear, aim at stationary readable printed edge
under cyan left-quarter marker and hold fixed. Complete rear portrait pair, then final profiling/soak.
Owner acceptance pending; no Phase 2, INTERNET, cloud API, MediaPipe, push or merge.

### Safe Phase 1 checkpoint (2026-10-06)

Checkpoint preserves the current production orientation/clipping fixes, regression tests, ADR-015,
verification instructions and measured/historical results. Owner verified repeated physical rotation
and relaunch on the corrected debug APK. Phase 1 remains PARTIAL, not complete or accepted.

Remaining checks:
- Corrected-build rear portrait 4:3 and 16:9 target/JPEG checks: PENDING.
- Precise independent physical edge alignment where previously inconclusive: PENDING; qualitative
  comparisons above do not establish numerical reprojection accuracy.
- Corrected-build optimized 480p and 720p cold-start/60-second Perfetto baselines: PENDING.
- Corrected-build 10-minute physical soak, lifecycle/switching/airplane/crash/ANR evidence: PENDING.
- GitHub Agent review and human Phase 1 acceptance: PENDING.

Raw phone JPEGs/screenshots/traces and local verification helpers remain preserved under ignored
`device-evidence/samsung-sm-s918b/`; they are not included in the public Git checkpoint. Recorded results
and evidence paths are committed in this document. The checkpoint alone does not carry those raw files
to a different checkout/machine; retain that local evidence directory when resuming. No reset/revert,
discard, push, merge, Phase 2, INTERNET permission, cloud API or MediaPipe dependency introduced.

### Resumed rear portrait 16:9 capture (2026-10-06)

Connected physical SM-S918B serial R5CW40EE9QK. UI rotation 0 and portrait 16:9
TextureView bounds [42,62][679,1195] asserted before shutter; R720P observed.
Manual save MediaStore 14758 succeeded: 4080x2294, orientation 90. Decoded JPEG
is upright with readable printed detail and qualitatively corresponding scene/framing.
Save, displayed orientation and readable detail PASS by inspection. Precise edge alignment
and numerical reprojection accuracy remain NOT_VERIFIED: marker was below the printed
rectangle edge. The save message changes available preview height (after screenshot shows
a smaller 16:9 preview), so bracket screenshots have different view bounds; do not interpret
this alone as physical camera movement or claim identical pixel framing.
Ignored evidence preserved under device-evidence/samsung-sm-s918b/resume-rear-portrait-16x9-*
(initial/saved XML, before/after PNG, JPEG, metadata). No previous evidence discarded.
Stopped for physical realignment: retain rear portrait 16:9; put the bottom edge of the
lower-left blue printed book rectangle directly through the centre of the cyan crosshair,
keep text sharp and brace the phone/target. Repeat precise 16:9 check before changing to 4:3,
which will need its own alignment. Final current-build baselines, soak, review and owner
acceptance remain pending. No new performance measurements, production changes or Phase 2.

### Corrected rear portrait pair and visible edge repeat (2026-10-06)

Physical SM-S918B: portrait rotation 0 verified. Rear 16:9 MediaStore 14764,
4080x2294 orientation 90; rear 4:3 MediaStore 14769, 3060x2295 orientation 90.
Both saves, portrait display, readable printed detail and qualitative preview/JPEG crop
correspondence PASS by visual inspection. The target itself is upside down in both preview
and JPEG; this is preserved scene orientation, not an inferred camera rotation failure.
The observed target differs from the owner's named blue rectangle: the outer vertical
book edge crosses the cyan marker in 16:9, and remains visually near its centre in 4:3.
Bracketed 16:9 views retain stable bounds/framing. Changing aspect clears the save message;
4:3 save then reduces available preview height again, preserving 4:3 shape. Visual edge
correspondence PASS for these rear portrait observations; no numerical reprojection error
or independent analysis-space target coordinate measured. Do not claim the entire precise
coordinate gate complete solely from overlay/JPEG visual correspondence.
Evidence: ignored precise-rear-portrait-16x9-* and precise-rear-portrait-4x3-* under
 device-evidence/samsung-sm-s918b/. Prior raw evidence preserved. R720P observed.
Selected front camera, retaining portrait 4:3, for the unresolved front portrait edge check.
Stopped for physical interaction: aim front lens at a stationary readable non-person target,
place a distinct straight edge directly through cyan right-quarter/half-height marker and
brace phone/target. No front capture yet. Final optimized baselines, soak, review and owner
acceptance pending. No production code changes, Phase 2, push or merge.

### Front portrait 4:3 visible edge repeat (2026-10-06)

Physical SM-S918B, UI portrait rotation 0, 4:3 TextureView [0,149][720,1109]
confirmed before capture; HUD R480P observed (do not assume prior R720P persisted).
Manual JPEG MediaStore 14774: 4000x3000, orientation 270. Save, readable detail,
mirrored preview/unmirrored JPEG and qualitative crop correspondence PASS by inspection.
Outer book edge meets the cyan right-quarter marker in the pre-capture preview; the
corresponding unmirrored left-side edge is visually near quarter-width in the decoded JPEG.
This resolves the previously absent direct edge target for front portrait 4:3 as a visual
observation; numerical reprojection error and independent analysis-space coordinate remain
unmeasured. Save message again changes available preview height, so compare normalized
scene framing rather than raw screen coordinates across bracket screenshots.
Evidence preserved locally: precise-front-portrait-4x3-* (initial/saved XML, setup/before/
after PNG, JPEG, metadata). Selected front portrait 16:9. Immediate rebind screenshot was
black; subsequent settled screenshot confirms live readable preview, not sustained failure.
16:9 edge is now to the right of the cyan centre (roughly x580 versus x520 in screenshot).
Stopped before 16:9 shutter for physical realignment: retain front portrait 16:9 and shift
phone/target until the outer vertical book edge crosses the cyan centre, keep it sharp and
stationary. Local precise-front-portrait-16x9-setup.xml/png and -settled.png retained.
Final optimized baselines, soak, review and owner acceptance remain pending; no production
code change, Phase 2, push or merge.

### Front portrait 16:9 visible edge repeat (2026-10-06)

Physical SM-S918B portrait rotation 0 confirmed; 16:9 TextureView [42,62][679,1195],
HUD R480P. Manual save MediaStore 14783: 4000x2248 orientation 270. Save, readable
printed detail, mirrored preview/unmirrored JPEG and qualitative crop/edge correspondence
PASS by visual inspection. Outer edge meets cyan centre in preview and the corresponding
unmirrored edge is visually near quarter-width in JPEG. Save message changes preview bounds
as previously recorded; no numerical reprojection error measured. Ignored evidence:
precise-front-portrait-16x9-initial/saved.xml, -ready/before/after.png, -capture.jpg,
-metadata.txt. Prior evidence preserved. Post-capture thermal status NONE (0), BAT 37.9 C,
battery 89 percent; these are setup observations, not final baseline/soak numbers.
An inspection tap accidentally enabled RGB instrumentation after capture; inspected true
state and issued a second tap to restore false. Captured JPEG preceded that toggle.
Rear and front portrait direct-edge observations now recorded. Earlier corrected landscape
pairs have qualitative near-edge correspondence but no direct centre-edge repeat. Next
physical setup: retain front 16:9, rotate landscape, wait for wide live UI, align a stationary
straight readable edge through the cyan right-quarter/half-height marker, brace and hold.
Stop for owner setup before landscape shutter. Final optimized baselines, 10-minute soak,
review and owner acceptance remain pending. No production changes, Phase 2, push or merge.

### Front landscape direct edge pair (2026-10-06)

Physical SM-S918B landscape rotation 3 confirmed, R480P, RGB benchmark false.
Front 16:9 MediaStore 14790: 4000x2250 orientation 180; front 4:3 MediaStore 14796:
4000x3000 orientation 180. Both saves, readable detail, mirrored preview/unmirrored JPEG
and qualitative crop/direct-edge correspondence PASS by inspection. The outer book edge
visually meets the right-quarter cyan marker in both pre-capture views and corresponds to
the near-quarter unmirrored JPEG edge. No numerical reprojection error or independent
analysis-space feature measured. Save message changes preview allocation as previously
recorded. Evidence retained: precise-front-landscape-16x9-* and -4x3-* XML/PNG/JPEG/metadata
under ignored device-evidence/samsung-sm-s918b/. No previous evidence discarded.
Selected rear camera, retaining landscape 4:3. Next physical action: aim rear lens at a
stationary readable non-person straight edge, align it directly through cyan left-quarter/
half-height centre, brace and hold. Rear landscape direct-edge pair remains pending;
final optimized baselines, soak, review and owner acceptance pending. No production changes,
Phase 2, push or merge.

### Rear landscape 4:3 direct edge repeat (2026-10-06)

Physical SM-S918B landscape rotation 1, 4:3 TextureView [593,56][1013,371],
R480P and RGB benchmark false confirmed before capture. Manual save MediaStore 14802:
4080x3060 orientation 180. Save, readable detail and qualitative crop/direct-edge
correspondence PASS by visual inspection; outer book edge meets left-quarter marker and
corresponding JPEG edge is visually near quarter-width. Numerical reprojection error and
independent analysis-space feature remain unmeasured. Ignored precise-rear-landscape-4x3-*
XML/PNG/JPEG/metadata preserved. Earlier evidence untouched.
Selected rear landscape 16:9. Settled setup screenshot shows readable target but distinct
edge no longer passes through cyan centre; target scene orientation also differs from the
4:3 setup. No cause inferred and no 16:9 shutter/pass claimed. Stop for physical alignment:
retain rear landscape 16:9, place a distinct printed/book straight edge through cyan
left-quarter/half-height centre, brace and hold. Setup XML/PNG retained under
precise-rear-landscape-16x9-setup.*. Final optimized baselines, soak, review and owner
acceptance pending. No production changes, Phase 2, push or merge.

### Rear landscape 16:9 direct edge; final baseline preparation (2026-10-06)

Physical SM-S918B landscape rotation 1 confirmed, 16:9 view [523,56][1083,371],
R480P and RGB benchmark false. Manual save MediaStore 14810: 4080x2296 orientation 0.
Save, readable detail and qualitative preview/JPEG crop/direct-edge correspondence PASS by
inspection. The outer book edge visibly meets cyan left-quarter centre and corresponds to
near-quarter-width JPEG edge. No numerical reprojection error or independent analysis-space
feature measured. All eight corrected-build camera/orientation/aspect combinations now have
recorded direct-edge visual observations; this does not establish numerical coordinate accuracy.
Ignored precise-rear-landscape-16x9-* XML/PNG/JPEG/metadata retained; prior evidence preserved.
Camera force-stopped for baseline setup. Thermal LIGHT (1), BAT 39.7 C observed after target
checks; timeout still 600000 ms. Existing optimized profile APK installed successfully,
SHA256 F5ED7C351BFF7A49724D037C938553EC348EB262D982D58F3C7CD73CAAAEE9A5,
then app force-stopped. Final corrected-build 480p/720p baselines and soak NOT_MEASURED yet.
Next owner setup: leave camera app stopped, keep USB connected, place phone upright portrait
with rear lens unobstructed and aimed at well-lit stationary non-person target; allow cooling,
then unlock and leave screen on (10-minute timeout), with third-party lock-screen interruptions
disabled as in prior controlled run. Do not launch/reinstall the app; automation starts profile
cold launch after verifying thermal conditions. Stop for physical setup before baseline launch.
Phase 1 remains partial; review/owner acceptance pending. No production changes, Phase 2,
INTERNET permission, cloud API, MediaPipe dependency, push or merge.

### Final corrected-build optimized baselines (2026-10-06)

Physical Samsung SM-S918B, Android 16, serial R5CW40EE9QK; corrected optimized profile
APK SHA256 F5ED7C351BFF7A49724D037C938553EC348EB262D982D58F3C7CD73CAAAEE9A5.
Rear portrait 4:3, RGB benchmark ON (1 Hz), luma grid at 2 Hz. Airplane mode already ON.
All five-second warmup/steady/start/end thermal samples NONE (0) for both runs. USB
powered true, battery 89 percent throughout; temperature 33.7 to 34.8 C for 480p,
34.8 to 34.8 C for 720p. Both complete approximately 60-second windows retained;
no nonzero trace errors or overwritten chunks from the existing audit query. MainActivity
focused at end of both runs. These measurements supersede older-build baselines for the
corrected checkpoint only; do not extend results to untested devices/tiers.

| Corrected optimized rear-camera measurement | 480p | 720p |
| --- | --- | --- |
| Actual buffer / upright crop | 640x480 / 480x640 | 1280x720 / 720x960 |
| Retained steady window | 60.003053 s | 60.002265 s |
| App-layer presented / submitted frames | 1800 / 1800 | 1800 / 1800 |
| Presented app-layer rate | 29.998474 Hz | 29.998867 Hz |
| Analysis router slices / rate | 1799 / 29.981808 Hz | 1798 / 29.965535 Hz |
| Luma n / rate / p50 / p95 | 115 / 1.916569 Hz / 0.561927 / 1.376927 ms | 116 / 1.933260 Hz / 0.612864 / 2.500364 ms |
| RGB + rotation n / rate / p50 / p95 | 59 / 0.983283 Hz / 21.015469 / 34.585468 ms | 59 / 0.983296 Hz / 43.212865 / 54.967291 ms |
| Router n / p50 / p95 | 1799 / 0.042969 / 3.233178 ms | 1798 / 0.051093 / 4.270209 ms |
| Intentional luma skips / delivered slices | 1684 / 1799 | 1682 / 1798 |
| CameraX internal discarded count | UNKNOWN, not exposed | UNKNOWN, not exposed |
| Capture callback cumulative rate / elapsed | 30.013317 Hz / 84.096002 s | 30.009093 Hz / 84.041194 s |
| RSS min / max, 61 process-stat samples | 121.109375 / 153.210938 MiB | 126.105469 / 158.871094 MiB |
| RSS watermark max | 156.371094 MiB | 160.457031 MiB |
| Cold Activity TotalTime / WaitTime | 317 / 321 ms | 281 / 284 ms |
| Activity construction to STREAMING proxy | 930.394479 ms | 772.600469 ms |
| Traced launch to first presented camera texture | 1074.894896 ms | 883.574063 ms |
| Thermal start / all polls / end | NONE (0) | NONE (0) |

Cold audit: 480p launch 118247945101667 ns, first acquired camera texture
118249011155521 ns, DrawFrames token 28387063, presentation 118249019996563 ns;
720p launch 118351648927565 ns, acquired 118352524548711 ns, token 28420466,
presentation 118352532501628 ns. Both On-time Present. Existing cold.sql follows
SurfaceTexture acquireBuffer through DrawFrames to actual FrameTimeline presentation;
these are camera-texture presentation measurements, not splash/Activity completion.
Rates are slightly below 30 Hz; do not round into a numeric target pass. No submitted
app-layer drops reported; this does not prove unique camera contents on every presentation.
Router includes stage work; do not sum stage durations again. Intentional skip counts derive
from router minus luma slices in retained window; cumulative callback logs are separate.

Local evidence: checkpoint-final-480p/720p-{cold,steady}.pftrace, -summary.csv,
-cold-summary.csv, matching event/log/launch/memory/battery/thermal/focus/ANR files;
baseline.sql, cold.sql, checkpoint_final_verify.py in ignored device-evidence/samsung-sm-s918b/.
Final corrected debug APK restored (SHA256 90C33FC6861EF20BE5274CBD47047D4AC293BDCBE90B173D044E3A5C303BDAA9)
and ten-minute soak started at NONE (0). Soak result pending; Phase 1 remains partial,
review/owner acceptance pending. No production changes, Phase 2, push or merge.

### Final corrected-build ten-minute soak (2026-10-06)

Physical Samsung SM-S918B, Android 16 (fingerprint
samsung/dm3qxxx/dm3q:16/BP4A.251205.006/S918BXXSAFZH3:user/release-keys), corrected
checkpoint debug APK SHA256 90C33FC6861EF20BE5274CBD47047D4AC293BDCBE90B173D044E3A5C303BDAA9.
Host monotonic duration 600.0004430999979 s, completed=true. Camera switches near 126/481 s,
background/resume near 260 s and resolution switch near 372 s all returned camera-ready.
Post-action XML confirms portrait rotation 0, RGB benchmark OFF, 480p then 720p controls.
Every approximately 25-second thermal sample and start/end stayed NONE (0).
Airplane mode was already ON (1), remained ON for the full run, and final read confirms 1;
no connectivity setting was changed. Analysis continued offline. Camera app force-stopped
at completion. Filtered AndroidRuntime/Phase1Camera/Phase1Baseline log has no error/FATAL
entries; independent crash buffer is empty; lastanr reports no ANR since boot. These are
observed results for this phone/build, not general device guarantees.

USB powered true; battery 89 to 89 percent, temperature 34.8 to 36.7 C. This is not a
battery-drain measurement. Debug dumpsys TOTAL RSS 231044 KiB initially, 218332 KiB at
end; 24 collected snapshots range 208592 to 268968 KiB. TOTAL PSS 142067 to 151525 KiB;
TOTAL SWAP PSS 366 to 25262 KiB. Snapshot evidence does not establish a long-term leak
claim or replace optimized-profile RSS baselines. Final pipeline session reports errors=0,
1280x720 buffer / 720x960 crop; rolling debug latency is not substituted for Perfetto data.
FULL sessions on rear/front and 480p/720p observed; unsupported-device fallback remains
hardware-unverified on this fully supporting phone.

Evidence preserved under ignored device-evidence/samsung-sm-s918b/: checkpoint-final-soak-*
(result JSON, initial/action XML, log, crash buffer, ANR, battery, memory and thermal dumps),
checkpoint-final-events-soak-events.json, checkpoint-final-device-fingerprint.txt. Earlier
raw captures/traces/helpers remain intact. No production source or spec changes; git diff
check PASS. No Phase 2, INTERNET permission, cloud API, MediaPipe, push or merge.

Current handoff: corrected rear portrait pair, all eight direct-edge visual observations,
final optimized 480p/720p baselines and final ten-minute soak are recorded. Coordinate
observations are manual/qualitative; numerical reprojection error and independent feature
coordinates remain unmeasured and must not be claimed. Final review must assess this evidence
against the manual crosshair gate. Phase 1 remains PARTIAL pending GitHub Agent review and
explicit human owner acceptance; Phase 2 must not start. No further physical interaction
is currently required by these completed baseline/soak measurements.

### Final Phase 1 review validation checkpoint (2026-10-06)

PHASE: 1
STATUS: partial — implementation/physical verification finished; review and owner acceptance pending.
FILES: docs/phase-status.md, docs/phase-1-verification.md (production fixes/tests already in 6c863a5).
DEVICES TESTED: Samsung SM-S918B, Android 16, optimized profile baselines; debug soak/instrumentation.
MEASURED: exact final optimized baseline table and 600.0004430999979-second soak above.
DEVIATIONS FROM SPEC: none introduced; manual coordinate evidence is qualitative, numerical accuracy unmeasured.
BLOCKERS: none for preparing owner review; GitHub Agent review/explicit owner acceptance remain required.
NEXT PROPOSED: review Phase 1 evidence and obtain owner acceptance; do not start Phase 2.

Final validation on the unchanged corrected production source:
- Gradle debug/release/profile APKs, all JVM tests, app debug/release/profile lint,
  camera debug lint and debug test APK assembly: BUILD SUCCESSFUL, 24 s,
  271 tasks (9 executed, 262 up-to-date). Existing valid task outputs reused where unchanged.
- JVM reports: 11 tests across CoordinatesTest (5), ModuleGraphTest (1), SchedulerTest (2)
  and ImagePlanesTest (3), zero failures/errors.
- Connected instrumentation explicitly rerun (--rerun-tasks): BUILD SUCCESSFUL, 46 s,
  95 tasks executed; SM-S918B Android 16, 3 tests, zero failures/errors/skips. Synthetic
  renderer combinations and ImageProxy closure tested; these do not replace physical targets.
- Spec validation: offline workspace Python/jsonschema runner, 0 errors, 0 warnings.
- Final diff/whitespace check PASS. Diff reviewed: documentation only; no Phase 2 code,
  cloud APIs, MediaPipe, secrets, generated/build artifacts or raw device evidence included.
  Debug/release/profile merged manifests inspected: no INTERNET or ACCESS_NETWORK_STATE
  permission (the source remove directive strips a transitive network-state permission).
- Local validation logs remain ignored: review-checkpoint-gradle-validation.txt and
  review-checkpoint-instrumentation.txt under device-evidence/samsung-sm-s918b/.

Validation regenerated APK archives: current debug SHA256
0FB5FB35ED4AEC124A572340538E57A0943F83424DD20BC0ED373CB6CC133762,
profile SHA256 275693CA3F4089C0BB463AD1124597D2675F613229B36E29048C4BA7B2642718.
These archives differ from the exact earlier physical-run hashes recorded above. Production
source remained unchanged; do not label the regenerated archives as the exact binaries used
for baseline/soak. Instrumentation covers the regenerated debug build. Measured-run hashes and provenance remain recorded separately from regenerated archives; raw evidence is preserved.

Review checkpoint uses existing codex/phase-1-camerax-foundation branch and repository-local
Git identity. Only appropriate tracked documentation is committed; ignored evidence remains
on disk. Owner acceptance is not recorded on the owner's behalf. No push, merge or Phase 2.

### PR #2 final review fixes (2026-10-06)

PHASE: 1
STATUS: partial — review findings addressed; GitHub review and explicit owner acceptance pending.
FILES: app/src/main/kotlin/com/aiphotographer/app/MainActivity.kt;
docs/tasks/phase-1-camerax-foundation.md; docs/phase-status.md.
DEVICES TESTED: no device connected for this review-fix session; physical locale switching not rerun.
MEASURED: no new device measurements; earlier SM-S918B / Android 16 evidence remains recorded above.
DEVIATIONS FROM SPEC: none; removed the forced Vietnamese Activity locale so normal Android/app
resource selection applies. Vietnamese resources remain in values-vi; English fallback remains in values.
The task brief now records implementation and physical verification complete, with qualitative coordinate
evidence, numerical reprojection accuracy unmeasured, and review/owner acceptance pending.
VALIDATION: debug/release/profile APK builds and app lint PASS; 11 existing JVM tests freshly rerun
with zero failures/errors; spec validation 0 errors, 0 warnings; English/Vietnamese string keys match
(18); diff/whitespace inspection PASS. All three merged manifests have no INTERNET permission.
No new user-facing strings, cloud API, MediaPipe dependency or Phase 2 work added.
BLOCKERS: none for re-review; physical locale switching remains unverified for this fix.
NEXT PROPOSED: update PR #2 on the existing branch for GitHub review and explicit owner acceptance.
Do not merge or start Phase 2.

### Explicit Phase 1 owner acceptance (2026-10-06)

PHASE: 1
STATUS: ACCEPTED by the human owner on 2026-10-06, by explicit owner instruction.
FILES: docs/phase-status.md; docs/tasks/phase-1-camerax-foundation.md.
This acceptance supersedes earlier pending-acceptance status entries; historical evidence is unchanged.
Numerical reprojection accuracy remains unmeasured. Unsupported-device fallback remains
hardware-unverified. Results from Samsung SM-S918B must not be generalized to other devices.
All other recorded limitations, measurements and measurement provenance remain unchanged.
No production changes or new device measurements. Phase 2 has not started; PR #2 has not been merged.
NEXT PROPOSED: final merge review of PR #2; no Phase 2 work authorized by this documentation update.

### Phase 2 pure-contract checkpoint and SDK privacy blocker (2026-10-06)

PHASE: 2
STATUS: blocked — pure foundation validated; compliant runtime decision required.
BRANCH: codex/phase-2-pose-face-detection, from synchronized main 81df002.
FILES: core/model, core/geometry, core/photography, perception/api, settings.gradle.kts;
docs/architecture.md, docs/roadmap.md, docs/ai-models.md, docs/model-licenses.md,
docs/tasks/phase-2-pose-face-detection.md, docs/phase-2-sdk-audit.md, this handoff.
IMPLEMENTED: schema-compatible optional subject/world/face types; one normative usability mapping;
One Euro filter; pure source/pipeline interfaces, single-batch lifecycle epoch ownership guard;
subject filtering/assembly with conservative face association and freshness; diagnostic head-matrix
decoder, visible-body shot classification and schedulable degradation/recovery decisions.
The new pure modules are not wired into the shipping camera app yet. No pose matcher or guidance.
DEVICES TESTED: none connected (ADB checked at start and final verification); no Phase 2 device test.
MEASURED: NOT_MEASURED — pose/face FPS and latency, preview impact, memory, thermal, actual delegate,
full-body 480p/720p experiment and shot-type calibration all require the compliant runtime/device.
VALIDATION: safe shipping checkpoint debug/release/profile APKs, all JVM tests, app lint for all three
variants, camera debug lint and debug test APK assembly PASS (2m34s, 285 tasks). JVM XML reports
21 tests, zero failures/errors (11 existing + 10 new). Connected instrumentation NOT_RUN: no device.
Spec validation PASS, 0 errors/0 warnings. Diff/whitespace and packaging inspection PASS; no model
assets or MediaPipe/transport dependencies in APK notice inventory, all merged manifests no INTERNET.
No project license, cloud API, secrets, raw evidence or build outputs added to tracked files.
DEVIATIONS FROM SPEC: Phase 2 runtime/module wiring deliberately held outside shipping build because
stock SDK telemetry contradicts the privacy contract. Only two of the three planned modules included;
mediapipe adapters/models and attempted wiring preserved under ignored
device-evidence/phase2/blocked-integration/. API interface methods describe batch ownership explicitly.
BLOCKERS: stock Tasks SDK remote telemetry; owner decision requested for an audited telemetry-free
source-modified artifact or an approved alternative architecture. See docs/phase-2-sdk-audit.md.
KNOWN LIMITATIONS: all Phase 1 limitations remain valid: numerical reprojection accuracy unmeasured,
unsupported-device fallback hardware-unverified, SM-S918B results not generalizable. New tuning seeds,
face association, head-angle conventions and shot classification remain uncalibrated/device-unverified.
NEXT PROPOSED: resolve the SDK privacy decision, finish compliant adapters, then physical Phase 2
verification and measurements. No Phase 2 acceptance, Phase 3+ work or merge performed.

### Phase 2 deeper artifact/runtime investigation (2026-10-06)

PHASE: 2
STATUS: partial — investigation continuing under owner instruction; no fork permitted unless necessary.
FILES: docs/phase-2-sdk-audit.md; docs/tasks/phase-2-pose-face-detection.md; this handoff;
tools/audit-mediapipe-artifacts.py; tools/mediapipe-privacy-probe; tools/build-mediapipe-upstream.sh;
.github/workflows/mediapipe-upstream-audit.yml.
DEVICES TESTED: Medium_Phone_API_37.0 x86_64 emulator / Android 17, isolated synthetic-image SDK probe.
No SM-S918B connected. This is not physical camera/landmark acceptance.
MEASURED: local telemetry queue counts 2 after pose, 3 after face; no Phase 2 performance baseline.
VALIDATION: isolated stock/excluded APKs and test APKs build; 4 stock + 4 exclusion instrumentation
tests PASS. Stock Pose/Face initialize and process black images without INTERNET; excluded transport
causes NoClassDefFoundError for both landmarkers, asserted by the exclusion tests. Complete runtime
graphs exported for both flavors. All 39 published Core POM/AAR versions inspected.
Loopback-only socket creation fails with EPERM in both apps. All four transport-free early Vision
alphas inspected: none contains the required Pose/Face APIs. Shipping debug/release/profile build,
lint and 3 existing app instrumentation tests PASS on the same emulator; 21 JVM tests remain green.
DECISION: previous demand for a modified SDK was premature. Official unmodified upstream default
dummy logger is a candidate; Linux audit build prepared using pinned v0.10.32 source and official
AAR targets. No Java/C++ source changes or fork. Outputs remain audit evidence until validated.
BLOCKERS: compliant runtime not yet demonstrated; ordinary upstream-build investigation continues.
Shipping app/dependency graph unchanged. Phase 1 limitations remain exactly valid: numerical
reprojection unmeasured, fallback hardware-unverified, SM-S918B evidence not generalizable.
NEXT PROPOSED: build/audit official source artifacts, verify Pose/Face runtime and notices, then
continue bounded camera integration. No Phase 2 acceptance, Phase 3+ work or merge.
Prepared (not yet executed against source AARs): upstream dummy-factory, no local transport database,
and Pose/Face LIVE_STREAM callback tests in the isolated probe. The artifact checker rejects known
telemetry identifiers in stock Core; native identifier scan is bounded, not a universal privacy proof.

---

## Phase 2 local implementation handoff — 2026-10-07

PHASE: 2
STATUS: partial — implementation and available local validation complete; physical gates pending.
BRANCH: codex/phase-2-pose-face-detection
PR: Draft #3, https://github.com/binhvugia1997-ui/app-ch-p-nh/pull/3

This entry supersedes the earlier SDK-blocker checkpoint, not its recorded observations. Owner
authorized deeper investigation and options A/B/C; no privacy-policy exception was used.

IMPLEMENTED: the three approved modules (`perception:api`, `perception:mediapipe`,
`core:photography`), pure confidence/usability, smoothing, freshness/association and diagnostic
shot estimation; single-person local Pose/Face adapters, bounded CameraX RGB ownership, lifecycle
epochs, geometry resets, cadence/degradation, latency/drop counters and resource-backed diagnostic
landmark visualization. Existing camera switching, capture and aspect/rotation contracts are reused.
No pose matching, target guides, product guidance, recommendations, composition engine, auto-capture,
scene models or multi-person production pipeline.

SDK DECISION: option C. Unmodified official MediaPipe v0.10.32 source commit
`8317ba78778738ba90a521e7e4580a2ba0129c81` builds Core with upstream's default dummy logger.
Core AAR SHA256 `f05d8c4432613342fa15d93914d0d7079381b941f4e1cd069dee9f41aa5c365f` is
vendored in the restricted local Maven repository; unchanged official `tasks-vision:0.10.32`
supplies Vision/JNI. Only Vision's stock `tasks-core` edge is excluded. No fork, SDK source patch,
fake logger or reflective telemetry bypass. Rebuild script, pinned CI and ADR-017 record provenance.
Core-only build and native-source notice collection passed in upstream audit CI.

PRIVACY: stock Core unconditionally initializes DataTransport and queues task metrics locally;
absence of INTERNET prevents transmission but does not prevent that collection. Removing transport
dependencies breaks both landmarkers. All 39 published Core artifacts were inspected; early
transport-free Vision alphas lack the required APIs. The selected production graph has 107 unique
components and no stock Core, DataTransport or Firebase encoder artifacts. Factory bytecode calls
the real upstream dummy logger. Known telemetry identifiers are absent in the selected AAR payloads
and production DEX; this bounded static scan is not proof of every native call path. Emulator runtime
probes show no transport class/database; loopback socket fails with EPERM. Debug/release/profile
merged manifests have neither INTERNET nor ACCESS_NETWORK_STATE. No cloud endpoint, API key,
analytics configuration, model download or frame upload is introduced in production. Audit probes
are isolated developer tools, not production dependencies.

MODELS / LICENSE: unchanged official version-1 float16 pose lite/full and face bundles are local
assets with verified hashes (model-licenses.md §8). All three APKs contain those exact assets and
all 36 retained third-party notice texts. Models/code are Apache-2.0; other runtime/native terms and
Eigen MPL source availability are recorded and packaged. No project-wide license was added.
Combined model size is 18,934,540 bytes, exceeding the previous approximate <15 MB hypothesis;
both pose variants are retained for tier/degradation selection. This is a documented target
deviation, not a silently reinterpreted measurement or requirement.

COORDINATES: crop-local upright unmirrored ANALYSIS inputs; rotation once, normalized landmarks,
Phase 1 crop/preview projection and explicit preview-only front mirroring. Filter/face state resets
on source geometry/lifecycle changes. World coordinates retain their nonmetric caveat. Deterministic
tests cover all four YUV rotations with padded offset crops, preview combinations, usability floors,
unknown channels and known facial-matrix axes. Numerical physical reprojection remains unmeasured.

VALIDATION ACTUALLY EXECUTED:
- Gradle `test assembleDebug assembleRelease assembleProfile`, app debug/release/profile lint,
  camera/adapter debug lint and runtime export: PASS (final build 30 s; offline cached dependencies).
- 23 JVM tests: PASS, zero failures/errors.
- Production adapter debug instrumentation: 1 PASS (rapid stop/resume, one lease, cadence/busy
  rejection and rear/front-like portrait/landscape geometry with synthetic frames).
- App debug instrumentation: 4 PASS; profile instrumentation: 2 PASS, including packaged-model
  production initialization/inference after R8 and launcher recreation. XML counts verified.
- Isolated SDK probes: 4 stock + 4 exclusions + 8 upstream PASS. Exclusion tests assert the expected
  initialization failure, not working detection. Upstream includes IMAGE, sequential LIVE_STREAM,
  actual 33-pose/478-face fixture landmarks, matrix output and disabled blendshapes.
- Spec validator: PASS, 0 errors, 0 warnings. Model/Core/Vision hashes, complete production graph,
  merged manifests, APK models/notices, vi/en resource-key parity and git diff inspected: PASS.
  Staged whitespace warnings are confined to verbatim upstream notice texts (trailing spaces/blank
  EOF lines retained); source/documentation diff check is clean.

FIXES / EVIDENCE CAUTIONS: source test folder corrected so all eight upstream tests actually execute.
Zero-test runner successes are rejected. R8 JNI/reflection and protobuf field retention fixes a real
shrunk-runtime initialization failure; only two unused classic-Graph missing proto types are narrowly
suppressed. Profile test-facing ABI keeps differ from release and cannot establish exact release
footprint. A final emulator package-manager Broken-pipe failure was recovered by cold-starting the
same AVD; failed zero-test run is excluded, debug instrumentation rerun passed. No device data or
raw fixture images/logs are committed; intentional model and vetted Core artifacts are tracked.

DEVICES TESTED: Android 17 x86_64 Medium_Phone_API_37.0 emulator, CPU, debug/profile and isolated
probe builds. No physical SM-S918B connected in this session. Synthetic tests do not establish real
camera landmark tracking, mirror accuracy, GPU delegate execution, device robustness or acceptance.
MEASURED: Phase 2 phone pose/face/conversion latency/rate, preview FrameTimeline, memory impact,
thermal run, 480p/720p full-body quality at 3–4 m and physical reprojection: NOT_MEASURED. Emulator
test durations are test evidence, not performance baselines. Watchdog, scheduling/filter/freshness
seeds remain CALIBRATION_REQUIRED; no model-card confidence is converted with invented sigmoid math.

KNOWN LIMITATIONS: numerical reprojection accuracy remains unmeasured; unsupported-device fallback
remains hardware-unverified; results from Samsung SM-S918B must not be generalized to other devices.
No previous Phase 1 result is claimed as Phase 2 evidence. Physical eight camera/orientation/aspect
combinations, capture/lifecycle during inference, visible body/face tracking, thermal/performance and
GPU/fallback verification remain pending. No human Phase 2 acceptance.
BLOCKERS: no remaining local implementation/build/license blocker; physical verification only.
HUMAN ACTION REQUIRED: connect/unlock authorized SM-S918B, approve USB if prompted, and arrange
one consolidated full-body/face posing session for both cameras, orientations and aspects.
NEXT PROPOSED: physical Phase 2 verification and factual measurements, then GitHub review and
explicit owner acceptance. Phase 3 has not started. No merge performed.

### Physical verification preparation — 2026-10-07

Authorized ADB device confirmed as Samsung SM-S918B, Android 16 / API 36. Clean Phase 2 starting
checkpoint `e6aa2a36c08261be89ad9e45789572f871a7a4a3` was preserved. Current debug APK installed
successfully and launcher opened; APK SHA256
`bc1fb1a65282eb94824d01e3a4e14b829f7e2c826198d9135fe739cabfc0bc89`.
Camera permission was denied, so live inference did not begin. The app's Android camera permission
prompt was opened for human confirmation; no permission was granted automatically. Consolidated
rear-camera full-body/face setup requested. Initial thermal status 0; original auto-rotation setting
0 and user rotation 0 recorded without changes. Profile manifest still has no INTERNET permission.
Local collection helper prepared for app counters, latency summaries, memory/thermal samples and
optional Perfetto; live collection has not run. Actual pose/face tracking, combinations, lifecycle,
latency/rates, memory, soak and accuracy remain pending, not PASS. No Phase 3 or acceptance claim.

### First physical inference repair checkpoint — 2026-10-07

Owner confirmed rear setup. Initial live run failed after its first results: MPImage closed and
recycled the reused bitmap, leading to repeated pose submission errors. Preview remained active, but
that run is not PASS. Exact upstream ownership behavior verified; app adapters now use supported
task-owned direct RGB buffers rather than bitmap-backed images. Same-geometry repeat coverage
added; the regression instrumentation actually ran on SM-S918B and PASS (1 test, CPU synthetic
input). No SDK source modification or privacy-policy change.

Fixed debug rerun collected a 20.017143482-second counter window: 600 offered, 150 accepted/pose
completed, 37 face completed, 0 busy skips, 450 intentional cadence skips, 0 errors; effective
rates 7.493576700 pose / 1.848415586 face Hz. Pose detection counter rose 144, face detection 0.
Last rolling windows (not window-only quantiles) had pose count 252, p50/p95 46.901719/56.335833 ms;
face count 64, 26.606250/41.800729 ms. Delegates configured GPU; native calculator placement is not
fully established by that configuration label. Degradation level 6. Sampled thermal status 1,
PSS 440037–475262 KiB, RSS 533904–570096 KiB. These are debug diagnostic observations, not optimized
baselines. CameraX internal drops remain UNKNOWN.

Local screenshot showed a near-field arm/desk, with neither full body nor face visible. Landmark
presence is not accurate full-body tracking evidence. Consolidated corrected rear framing requested;
full-body/face, alignment, other combinations, lifecycle/offline and soak remain pending. No phone
photographs/raw evidence are committed. Rebuilt debug/profile/release, JVM tests and app
debug/release/profile plus adapter debug lint PASS. Phase 1 accuracy/fallback/device-generalization
limitations remain unchanged. No Phase 3 or owner acceptance. Local collection helper's first real
run completed and recorded counters/latency/memory/thermal; no unmeasured compositor claim.

### Owner-requested safe pause — 2026-10-07

STATUS: partial; physical verification paused by the owner because remaining human full-body/face
tests cannot be completed now. This is a scheduling deferral, not a failed verification gate.
Implementation/fix checkpoint: `c8d82899b74c19027bf08612d6b38bc18e719712`, pushed on
`codex/phase-2-pose-face-detection`; Draft PR #3. No Phase 2 owner acceptance or merge.

SAFE STOP: no active task-specific host collector/logcat/instrumentation/performance process found.
No active phone Perfetto or instrumentation session found. Camera application and library test
application force-stopped on SM-S918B; camera app PID absent afterward. No active measurement was
interrupted. The completed short run and regression report are retained. Unrecorded camera uptime
after that run is not a soak or valid measurement. No phone rotation/connectivity settings were
changed in this session; original rotation settings remain recorded above. No production change
made as part of the pause. No UI integration or Phase 3 work started as part of this request.

COMPLETED PHYSICAL CHECKS:
- Authorized USB ADB, model SM-S918B, Android 16 / API 36 confirmed; current debug build installed
  and launched, camera permission confirmed through actual live preview/inference.
- Rear preview observed with near-field arm/desk. Live submission failure reproduced, diagnosed
  and fixed; corrected 20-second debug inference run completed without recorded inference errors.
- Same-geometry repeat, single-frame ownership and stop/resume/geometry regression PASS on the
  real phone with CPU synthetic input (1 instrumentation test). This does not verify real-camera
  background/foreground behavior or human tracking.
- Profile merged manifest reconfirmed without INTERNET permission. Existing complete dependency,
  bytecode/native identifier, dummy logger, manifest/model/license and emulator privacy audit
  results remain preserved; no new network/privacy exception or SDK source patch.

PENDING PHYSICAL CHECKS (not PASS, not failed): full-body pose tracking during movement; actual
visible-face landmark/head tracking; front preview; physical portrait/landscape transitions;
rear/front × portrait/landscape × 4:3/16:9 combinations; camera switching, manual capture and
real-camera background/foreground recovery during inference; qualitative overlay alignment and
front mirroring; sustained backlog/preview stability; explicit offline run on the phone; optimized
480p/720p full-body comparison around 3–4 m; GPU execution/fallback evidence; sustained crash/ANR
audit. The brief repaired run is not sufficient to mark the final no-crash/ANR or soak gate PASS.

COMPLETED MEASUREMENTS: limited debug counter window, rolling task-to-callback pose/face latency
summaries, sampled process PSS/RSS and thermal state exactly as recorded in the preceding checkpoint.
Face inference completed but no face was detected; pose detections on the observed partial scene
are not verified anatomical accuracy. Do not relabel these numbers as full-body, optimized-profile,
steady-state or acceptance measurements. No numerical reprojection measurement was collected.
PENDING MEASUREMENTS: fresh optimized-profile 60-second 480p/720p baselines with a visible person,
pose/face rates and latency, conversion cost, skipped work, compositor FrameTimeline, steady-state
memory, five-minute thermal/soak interval and sustained crash/ANR review. CameraX internal drops
remain UNKNOWN unless a supported measurement is obtained. No fabricated or substituted values.

SOLO TEST PLAN FOR RESUME (no second person required):
1. Confirm this pushed branch and clean tree, reconnect/unlock the authorized phone and recheck
   model/API/permissions. Install the current debug diagnostic build. Verify ignored evidence
   exists locally; retain old evidence separately from new run directories.
2. Prepare automation before asking for positioning: delayed starts allow the tester to walk into
   frame; automate aspect/resolution changes, local screenshot samples and counter/trace capture.
   Use the computer's locally captured preview for framing and later qualitative review. Keep
   photographs/screenshots/recordings ignored; do not upload or commit them.
3. Ask for one consolidated setup per physical camera/orientation: prop the USB-connected phone
   securely, include the tester's face/head, hands and feet at roughly 3–4 m in good light when
   practical. The tester starts the delayed run, walks into view, raises/lowers both arms, steps
   sideways and turns the head. No precise photographic pose or external helper is required.
   If a full-body face is too small, include a closer face segment in the same setup and label it.
4. Run both aspects and 480p/720p plus applicable switching/lifecycle checks automatically while
   each setup remains valid. Request physical rotation/front-camera repositioning in consolidated
   bundles; preserve/restore any temporary rotation/connectivity settings. Clearly distinguish
   forced viewport rotation from a real physical transition. Compare front mirrored movement and
   overlay alignment against the locally recorded preview, without claiming numerical accuracy.
5. Collect optimized-profile baselines and a sustained thermal run only after tracking works;
   rerun on the final APK after any bug fix. Label interrupted/poorly framed runs incomplete and
   exclude them from valid baselines. Review actual crash/ANR and trace records, document measured
   results and remaining limits, validate, commit/push and update the same Draft PR. No automatic
   Phase 2 acceptance, merge, UI integration expansion or Phase 3 transition.

EVIDENCE / RECOVERY: `device-evidence/phase2/physical/rear-debug-fixed/` contains completed raw
log/samples/summary; `pipeline-regression.txt` contains the actual phone test result. Local preview
screenshot retained only under ignored physical evidence. `pause-inventory.json` records 14 files,
their sizes/hashes and evidence limitations. Existing MediaPipe audits/build provenance remain
unchanged. Only factual handoff/task documentation is tracked for this stop; no raw evidence or
build artifacts added to Git. Closing Codex is safe; reopening uses repository documentation and
the retained local evidence rather than relying on chat. This pause does not delete device evidence.

KNOWN LIMITATIONS remain unchanged: numerical reprojection accuracy remains unmeasured;
unsupported-device fallback remains hardware-unverified; results from Samsung SM-S918B must not
be generalized to other devices. Human full-body/face physical verification is the remaining
dependency, now explicitly planned for a solo tester. Resume only when the owner is available.


### Phase 2 autonomous non-physical review - 2026-10-07

Overall STATUS: partial. IMPLEMENTATION_COMPLETE; AUTOMATED_VALIDATION_COMPLETE;
PENDING_PHYSICAL_VERIFICATION; OWNER_ACCEPTANCE_PENDING. Manual deferral is not implementation
failure. The owner authorized automated work while unavailable; no human positioning was requested.
Branch remains codex/phase-2-pose-face-detection; existing Draft PR #3, no merge.

IMPLEMENTATION REVIEW / REPAIRS:
- Approved modules/interfaces and 33-landmark single-person contract retained. Raw visibility/presence
  and unknown-channel semantics remain normative; no sigmoid or validated calibration claim.
- ADR-018: bounded initialization/runtime recovery, unavailable error diagnostics, lifecycle job
  coalescing, callback shape/timestamp checks, post-submit lease ownership and worker-owned closure.
  Session IDs are process-unique diagnostic epochs; they are not identity recognition.
- Router rejects future, old-session, wrong-geometry and expired snapshots; unavailable source ages
  saturate rather than overflow. Unusable-person loss resets filtering/session; stale face diagnostics
  are not drawn. Rotation/dimensions/crop/strides/plane bounds are validated before conversion.
- Percentile sorting/logging is capped at 1 Hz; plane wrappers are allocated only for admitted work.
  Latest-frame backpressure, one RGB lease, bounded callback channel and coalesced recovery remain.
- Native SIGBUS reproduced during R8 profile recreation with camera permission. Exact upstream relative
  asset loading overwrites shared cache files; overlapping pipelines can still map them. ADR-019 uses
  official model-buffer input backed by per-task read-only mapped, uncompressed packaged assets.
  No MediaPipe source/model modification, fork or privacy exception. Failed runs remain excluded.
  Repair confirmed by three consecutive profile suites and a final suite on the final APK.

ACTUAL AUTOMATED VALIDATION:
- JVM: 29 tests PASS, including confidence/freshness, geometry/corners/rotation/crop/mirror, ownership,
  scheduling, filter and shot-estimator contracts. Tool evidence-window regression: 3 Python tests PASS.
- Android 17 / API 37 x86_64 emulator only: adapter 9 tests PASS (fault/lifecycle/timestamp ownership,
  actual full/lite Tasks, repeated direct RGB storage and no-person/no-face outputs); debug app 5 PASS;
  R8 profile app 2 PASS. Counts inspected; crashed/zero-test runs do not count as passes.
- SDK audit rerun: stock control 4, excluded negative control 4, adopted upstream 8 tests PASS.
  Exclusion control proves initialization fails; it is not an approved production solution.
- Debug/release/profile builds PASS; strict app debug/release/profile, camera debug and adapter debug
  lint PASS. Spec validator PASS: 0 errors, 0 warnings. Final diff/scope inspection performed.
- Exported runtime: 110 artifacts / 107 unique components; pinned registry unchanged. DataTransport,
  Firebase transport/analytics and known telemetry transport identifiers absent. Exact Core/Vision
  bytecode/native identifier checker PASS, dummy factory true, four native ABIs retained.
- Final merged manifests for all variants lack INTERNET. No cloud endpoint/API key/analytics config
  added. Models are local, exact hashes unchanged, all required third-party notices packaged.
  APK bytes: debug 81,379,811; release 66,403,157; profile 66,975,245. Models total 18,934,540 bytes.
  APK sizes are build artifacts, not device memory measurements. No project-wide license added.

AUTOMATION / RESUME:
The single consolidated plan is [phase-2-physical-verification.md](phase-2-physical-verification.md).
`tools/run-phase2-solo.py` batches diagnostic aspects/resolutions after countdowns, selects observed
controls, checks permission/displayed orientation, collects local screenshots/counters/thermal/memory,
and optionally home/resume plus switch/return. `tools/collect-phase2-device.py` refuses overwritten
runs, rejects process/session/reset/interrupted counter windows and retains rolling percentiles honestly.
Trace capture has a unique ID and only its matching PID is stopped; evidence remains ignored.
Emulator smoke tests completed four debug configurations with four retrieved traces, then front
selection/home/resume/switch-return with local snapshots and continued inference. This validates
controls/capture machinery, not real-human tracking, physical rotation, numerical accuracy or phone
performance. Actual Windows signal interruption tests also PASS with and without Perfetto: owned
capture stopped, partial trace retained, INCOMPLETE recorded and valid rate claims rejected.
Estimated later human actions: four physical setup confirmations plus one optimized baseline/soak
setup, with countdowns allowing a solo tester to walk into frame. No second person is required.

PENDING_PHYSICAL_VERIFICATION:
Real full-body pose/arm/side movement, visible face/head tracking, still/loss/freshness, actual front/rear
portrait/landscape and both aspect combinations, qualitative mirrored overlay alignment, real camera
switch/lifecycle/manual capture/offline behavior, optimized 480p/720p baselines with delivered-resolution
review, sustained preview/backlog/crash/ANR evidence, memory/thermal and five-minute soak. Final repaired
APK must be used. No Samsung phone was connected during this automated review. Historical limited
20-second debug observations remain exactly as recorded above; they are not new or optimized baselines.

KNOWN LIMITATIONS: numerical reprojection accuracy remains unmeasured; unsupported-device fallback
remains hardware-unverified; results from Samsung SM-S918B must not be generalized to other devices.
Calibration-required constants remain unvalidated; CameraX internal drops remain UNKNOWN. Profile
includes instrumentation ABI keep rules, so its footprint is not exact release footprint. No universal
native-network proof or GPU calculator-placement claim is inferred from bounded audits/configuration.
No unresolved non-physical engineering blocker or new human privacy/license/product decision identified.
No Premium UI implementation, Phase 3 behavior, owner acceptance or merge. Remaining meaningful work
requires the owner to return for the consolidated solo physical session.


### Phase 2 resumed solo physical verification - 2026-10-07 (session record)

Local/remote HEAD verified at `998444e3f7b05eea06a470a501599ef7c24828e6`; initial tree clean.
Authorized USB Samsung SM-S918B, Android 16/API 36 confirmed. Final checkpoint debug APK installed
(SHA-256 `93238a714c763fe961fa0c335b5da593211f38f4615db6513c84faec743a975d`),
CameraX live preview and actual human pose/face detections observed. All final merged manifests lack INTERNET.

Runner failure reproduced before collection: Windows cp1252 decoding of Vietnamese UI XML failed.
ADB text decoding now explicitly UTF-8; same setup rerun completed all four 60-second windows and
15-second lifecycle-return collection. Three evidence-window regressions PASS; no app/APK change.
Raw screenshots/logs/video/traces remain ignored locally in
`device-evidence/phase2/physical/resume-20261007/rear-portrait-utf8/`.
Full-body pose and face overlay visible; arm/side changes and overlay clearing observed qualitatively.
Motion alignment/head-turn and remaining physical configurations still require final evidence review.
No numerical reprojection or overall physical PASS claimed. Four windows: pose detection deltas
258/370/329/394; face detection deltas 3/9/9/25; inference errors 0 in each.
Lifecycle return: pose detections 70, face detections 6, inference errors 0.
Requested 720p conditions degraded to delivered 480p; these are not sustained 720p results.
Diagnostic windows are not optimized baselines; fourth includes a local screen recording.
Thermal samples reached MODERATE (2). First trace overwrote its ring and retained ~30 s;
whole-window compositor/trace claims are invalid. Remaining optimized baselines/soak are pending.

Temporary OS auto-rotation enabled for actual physical landscape transitions; original values
accelerometer_rotation=0 and user_rotation=0 must be restored at session end.
Session incomplete; OWNER_ACCEPTANCE_PENDING. No Phase 3, Premium UI or merge.

Trace capture repair: Phase 2 now uses app-focused gfx/view/inference tracing with streaming
file output every second, omitting system-wide scheduler event flood. Five-second no-person
physical smoke collection completed; trace retrieved and parsed. This is instrumentation verification,
not a human/performance baseline. Long-window retained bounds/overwrites still require inspection.

Rear-landscape first 60-second window completed with 295 pose detections, 5 face detections,
0 inference errors. Actual landscape confirmed, but table edge obscured lower legs/feet: full-body
landscape gate remains pending. Second window interrupted for framing correction and excluded.
Host collectors absent afterward; exact owned device trace stopped and partial trace retrieved.
No product defect inferred from physical obstruction. Repositioning/retry required.
Final streaming trace configuration adds periodic flush; second 5-second smoke parsed with no
nonzero trace error/overwrite stats. Long-window verification still pending.


### Owner-requested safe checkpoint - 2026-10-07

STATUS: partial; verification stopped for the night at the owner's explicit request.
Branch `codex/phase-2-pose-face-detection`; existing Draft PR #3. No owner acceptance,
Phase 3, Premium UI, merge, or new physical test/soak during checkpoint creation.
The authoritative remaining-work list and measurement table are in
[phase-2-physical-verification.md](phase-2-physical-verification.md), checkpoint section.

COMPLETED: Samsung SM-S918B / Android 16 / API 36 and checkpoint/remote verified; final
unchanged diagnostic APK installed and live CameraX preview observed. Rear portrait four
60-second diagnostic collections plus 15-second background/resume and camera-switch return
completed. Human full-body pose, face overlay, arm/side changes, and clearing on loss were
observed qualitatively. Callback validation enforces 33 pose / 478 face points; successful
physical detections did not report validation/inference errors. No numerical alignment or
complete movement/head-turn/freshness acceptance is inferred. Review existing evidence first.
No INTERNET permission in final debug/profile/release merged manifests reconfirmed.

PARTIAL: rear landscape 4:3 480p completed 60-second collection (295 pose / 5 face detections,
0 inference errors), but lower legs/feet obscured by table: partial-body evidence only.
Landscape 16:9 interrupted and excluded from valid rates/PASS; requested 720p landscape windows
never ran. Portrait requested 720p windows adapted to 480p and do not establish sustained 720p.
Diagnostic timing/memory/thermal exists; optimized baselines, final soak and final crash/ANR
acceptance remain pending. Portrait traces with overwritten rings do not establish whole-window
preview performance. Streaming capture repaired; two 5-second engineering smoke runs preserved,
final flush-enabled smoke parsed with no nonzero trace error/overwrite stats. No new app defect
proven; transient motion misalignment needs evidence review/reproduction before a product fix.

FIXES: explicit UTF-8 ADB UI decoding; new app-focused Phase 2 Perfetto template with streamed
file output and periodic flush, omitting system scheduler flood. No Android production code,
SDK/model/dependency/privacy change, rebuild, or automatic-validation repeat required tonight.
Python compile/plan checks, 3 previously run collection regression tests, actual runner rerun,
trace parse checks, final spec validation and diff checks are the bounded verification evidence.
Host interrupt terminated the landscape runner without its final cleanup; exact owned trace
was stopped/retrieved manually, metadata marked INCOMPLETE. Do not assume host process termination
runs Python finally; audit owned processes and trace metadata when interrupting future runs.

SAFE STOP CONFIRMED after owner reconnected the unlocked phone: project camera app and installed
project library test app force-stopped. No host runner/countdown/logcat/collector/trace processor
and no device project app/logcat/screenrecord/task-owned trace process remains. Original
accelerometer_rotation=0 and user_rotation=0 restored and read back. Device instrumentation
subcommand is unsupported on this Android build; absence of project test/app processes, rather
than that failed command, is the shutdown evidence. Shared ADB/server/system services untouched.
Connectivity was not changed. No evidence deleted or uploaded.

LOCAL HANDOFF: `device-evidence/phase2/physical/resume-20261007/` contains evidence, shutdown
state, hashes/inventory and a post-commit `checkpoint-ref.json` with exact checkpoint commit SHA.
Evidence is ignored, not on GitHub: preserve this workspace when resuming. Resolve the tracked
checkpoint with `git log -1 --format=%H -- docs/phase-2-physical-verification.md`.
FIRST NEXT SETUP: rear camera landscape, lens raised clear of table edge, slight downward tilt,
roughly 3-4 m, whole head/hands/feet visible. Recheck branch/remote/device and review retained
valid evidence first; verify framing before a fresh 15-second countdown after owner confirmation.
Never repeat completed valid portrait collections merely to complete the report.

### Phase 2 camera relaunch / awaiting Ready — 2026-10-08

STATUS: partial; setup only, no test collection or countdown started.
DEVICES TESTED: Samsung SM-S918B, Android 16, existing diagnostic app via ADB.
Restored foreground MainActivity; camera service reports active camera ID 0 for the app.
Display restored to landscape (accelerometer_rotation=0, user_rotation=1); pre-action values
were 1 and 0. This display setting is not evidence of physical landscape placement.
Two local setup screenshots show an active scene with changing preview capture FPS
29.49 then 28.00 (UI snapshots, not a measured test-window/display FPS claim).
Framing is NOT ready: preview shows sideways desk/computer equipment, no full-body subject.
Owner must physically position the phone in landscape facing the test space, lens clear of
support/table edge, head/hands/feet visible. Recheck framing before pending rear-landscape
collection; use explicit --countdown 15 only after owner confirms Ready.
Preserved all previous evidence/checkpoint data; no completed tests restarted, no reinstall,
app-data clear, log clear, connectivity change, photo capture or test runner execution.
New setup evidence only: device-evidence/phase2/physical/relaunch-20261008-1818/.
BLOCKERS: physical framing and owner Ready confirmation. No new acceptance/PASS claimed.
NEXT PROPOSED: recheck physically corrected rear-landscape framing, await Ready, then resume
only missing/invalid landscape checks in a fresh evidence directory.

Ready confirmation received on 2026-10-08; pre-countdown framing snapshot retained at
`device-evidence/phase2/physical/ready-20261008/framing.png`. Scene is now upright in landscape
and live pose/face overlays are visible. However, the visible subject's lower legs/feet are
cropped by the preview bottom. Full-body framing gate remains unmet. Countdown/collector
not started; no completed tests repeated. Next: move subject farther back or adjust camera
aim/distance to include whole head, hands and feet, then recheck before the authorized
15-second countdown. Previous Ready authorization is retained for this pending collection.

### Adjusted framing / rear-landscape thermal stop — 2026-10-08

STATUS: partial. Owner Ready retained; owner requested another framing check after adjustment.
Local `framing-20261008-181905/preview.png` shows full head/hands/feet within preview.
Started only pending rear-landscape matrix using explicit --countdown 15, --trace, --lifecycle,
fresh evidence `device-evidence/phase2/physical/rear-landscape-resume-20261008-1820/`.
No prior valid portrait tests repeated or prior evidence/checkpoint files overwritten.
DEVICE: Samsung SM-S918B / Android 16 / API 36, existing debug diagnostic APK.
THERMAL: all first-window samples report status 4 (critical); sample SKIN 48.9 C, BAT 44.7 C.
Stopped app on observing critical thermal state. Collector had already finished its first
60-second window before stop took effect: summary is COMPLETE, 58 metric records,
60.802126383-second counter window, pose detections 339, face detections 16, errors 0,
pose completion 6.4635897357346535 Hz, face completion 1.5459985627456934 Hz.
These are diagnostic completion rates under critical thermal conditions, not performance PASS.
CORRECTION to live commentary: first collection finished; overall matrix is INCOMPLETE,
not an interrupted first counter window. Retain first window for evidence review; do not
repeat it unless review demonstrates an invalid check. No other matrix windows/lifecycle ran.
Runner exited on missing aspect control after deliberate app stop; not a diagnosed UI defect.
Owned trace STOPPED_CAPTURE_RETRIEVED. Follow-up device process check shows no app,
logcat, screenrecord or perfetto process. Host runner exited code 1. Thermal remains 4,
SKIN 48.8 C on follow-up. Camera remains stopped for cooling; landscape settings retained.
BLOCKERS: critical thermal state; remaining 16:9/720p-request/lifecycle checks pending.
NEXT: cool phone, verify thermal recovery and retained first-window framing/tracking, then
resume only missing valid checks with fresh evidence and 15-second countdowns.
No owner acceptance, whole-window preview stability or numerical alignment PASS claimed.


## Owner stop / critical thermal incident ? 2026-10-08

All Phase 2 physical testing is STOPPED by explicit owner instruction. Do not relaunch the
camera or resume testing. No production code, APK, architecture or thermal policy changed.
Samsung SM-S918B / Android 16 / API 36, existing debug diagnostic build only.

THERMAL INCIDENT: rear-landscape attempt is INCOMPLETE. All seven retained first-window
thermal samples report CRITICAL (4); initial SKIN 48.7 C, later SKIN 48.9 C, follow-up
48.8 C. App was force-stopped; host runner exited and device process audit found no project
app, logcat, screenrecord or perfetto. Trace metadata confirms STOPPED_CAPTURE_RETRIEVED.
The raw first 480p 4:3 collector completed its 60-second window before the stop took effect;
retain its COMPLETE summary unchanged as diagnostic evidence, not a physical gate PASS.
Matrix session.json is INCOMPLETE. No later aspect/resolution/lifecycle window ran.

EVIDENCE PRESERVED: all earlier portrait/partial-landscape/checkpoint files unchanged.
Rehashed all 325 entries of the previous checkpoint inventory: 0 mismatches. New 384-file
inventory, incident metadata, prior-inventory verification, retained failed-control UI and
process audit are local under device-evidence/phase2/physical/thermal-stop-20261008/.
Raw evidence remains ignored and must not be committed/uploaded. New run remains at
rear-landscape-resume-20261008-1820/; setup screenshots retained separately.

LOG-ONLY INVESTIGATION: critical state already existed at the first collector sample, so
these logs cannot attribute onset to this 60-second window. App remained open during setup;
pre-collection thermal history, ambient conditions and other workload attribution are absent.
Perception logs report degradationLevel=6 throughout but continue pose/face callbacks,
configured GPU labels (actual native placement NOT proven), preview capture near 29.7 FPS
(not display FPS), analysis delivery near 26.8 FPS and delivered 640x480. Last rolling
RGB conversion p50/p95 is 56.65/69.45 ms; pose 69.19/97.78 ms, face 46.68/86.93 ms,
batch 128.57/181.64 ms. These are rolling snapshots, not window-only percentiles.
Continued camera/conversion/inference load despite degradation, warm start, and diagnostic
trace/screenshot overhead are possible contributors, not established root causes. Preview
screenshots show charging indicator and floating overlays; their power/CPU contributions
are unmeasured. PSS ranges 416827?447304 KiB, RSS 499664?528272 KiB with nonmonotonic
variation: no leak established by this short window. Same PID/session and zero inference
errors; no AndroidRuntime exception found in retained app log. No duplicate pipeline or
resource leak proven. Collector did not abort automatically on thermalStatus=4; the manual
stop occurred after the first collector window finished. Preflight/abort thermal handling
is a tooling follow-up requiring a documented policy before any future physical testing.
No new device experiment was run to investigate causes.

MISSING CONTROL: runner traceback occurred while selecting the second aspect after deliberate
app force-stop. Retained /data/local/tmp/phase2-solo.xml was copied locally; its packages are
com.sec.android.app.launcher and com.google.android.googlequicksearchbox, with no test app.
Thus the failed aspect lookup is consistent with the stopped app/launcher foreground, not
evidence of an independent app UI defect. Do not classify as an independent bug without
contradicting evidence; no reproduction or camera relaunch performed.

REMAINING: review retained first-window human motion/framing (a lateral sample places the
subject partly beyond the left edge); no full-body landscape acceptance yet. Other landscape
aspects/requested resolutions/lifecycle, front portrait/landscape, manual capture/offline
physical gates, optimized baselines and soak remain pending. Preserve valid portrait checks;
repeat only demonstrated invalid/missing checks if the owner later authorizes testing.
All physical work remains stopped, regardless of thermal recovery. Owner acceptance pending.

SAFE CHECKPOINT: same codex/phase-2-pose-face-detection branch and Draft PR #3; documentation
checkpoint only. Exact SHA and verified remote head recorded after push in ignored local
thermal-stop-20261008/checkpoint-ref.json. No raw images/logs/traces added to Git.

Checkpoint validation: git diff --check passed. Spec cross-reference validator: 0 errors,
1 warning (jsonschema/referencing unavailable; structural schema checks skipped). No specs,
rule/message references, production code or APK changed. Full schema validation not claimed.


## Thermal recovery preparation - 2026-10-08

Owner authorized conditional resume from 532426bec5d8eff82316040339ee17ab64369583;
this supersedes the earlier unconditional STOP only for the new safe workflow.
Physical collection still requires Ready per setup and fresh normal-status checks.
Verified branch codex/phase-2-pose-face-detection, origin and exact remote/local checkpoint;
initial working tree clean. Rehashed all 384 previous inventoried evidence files: unchanged.
Local-only preflight: device-evidence/phase2/physical/thermal-recovery-20261008/.
Samsung SM-S918B / Android 16: initial thermal 0, SKIN 36.2 C, BAT 34.7 C, AP 38.3 C;
no camera process. USB charging active; no OS thermal/connectivity override. This initial
normal reading does not replace the next launch preflight.

LOG ANALYSIS: first previous sample already critical; onset/root cause unknown. Continued
camera/inference/conversion workload despite degradation 6 and diagnostic overhead are
possible contributors. No excessive concurrent inference batches established. Reusable
RGB/direct task buffers and existing backpressure remain unchanged. Nonmonotonic PSS/RSS
and stable PID do not establish allocation leak or duplicate pipeline. Native allocations,
actual GPU placement, charging/other-app contribution and allocation rates remain unmeasured.
Missing aspect error remains consistent with launcher foreground after app stop, not an
independently established UI bug.

FIXES: shared host thermal guard, bounded single-window runner and cooldown (ADR-020).
Single --aspect selection resumes missing conditions; matrix/lifecycle batching rejected.
Abort force-stops app and marks session incomplete, without automatic retry. RGB conversion
hoists row/destination indexing outside pixel loop; output compared against reference for
odd crops, nonneutral chroma, strides, positions and four rotations. Device improvement is
unmeasured; no thermal-fix claim. Production inference schedule unchanged. Keep traces optional
and screenshot/memory cadence ~10 s while thermal polling runs independently at ~2 s.

NEXT PHYSICAL SETUP after Ready: rear landscape 480p 4:3, targeted 30-second full-body/lateral
framing and motion check. Prior window has lateral clipping, warranting only targeted invalid/
missing coverage, not repeating valid portrait tests. Guarded framing review and 15 s countdown
required. Stop/cool down and review before other landscape conditions or front setups.
If framing is wrong, stop and correct. Capture/offline/lifecycle, optimized baselines and soak
remain pending; long soak deferred until short-window thermal behavior is understood.
Awaiting new Ready; no camera relaunched. No interrupted PASS, Phase 3, Premium UI, merge
or owner acceptance.

Validation: camera debug unit tests (including new conversion equivalence), perception API
tests, debug/profile/release builds and app lintDebug PASSED. Python evidence/thermal suite:
8 tests PASSED. Full spec validation using cached jsonschema/referencing: 0 errors, 0 warnings.
No new device behavior/performance test or acceptance claimed. Prepared debug APK hash: 915b900a923e8a5e95583f983fbca9b1eb6bab48bf98b4f00912fe0772cb5e11.
Guarded --framing-only saves a setup screenshot then stops app without collecting test metrics;
use after Ready to review framing before the separately bounded test invocation.
Camera remains stopped and new APK device verification remains pending Ready.


### Guarded rear-landscape framing check - 2026-10-08
Owner confirmed new Ready. Clean checkpoint ab02d7bf5c37186d0782448366d630541116b5e2
verified; fresh initial device check status 0, SKIN 37.8 C, BAT 35.9 C, app stopped.
Installed prepared debug APK 915b900a923e8a5e95583f983fbca9b1eb6bab48bf98b4f00912fe0772cb5e11.
Ran only --framing-only after guarded 30-second camera-off normal-status cooldown.
Local evidence: device-evidence/phase2/physical/recovery-framing-20261008/.
Guard samples all status 0; final SKIN 39.7 C. Camera auto-stopped after setup snapshot;
runner exited 0, session FRAMING_ONLY_NO_TEST_COLLECTION. Pose overlay visibly present,
but subject lower legs/feet clipped at preview bottom: full-body framing NOT ready.
No countdown, physical test collection, performance PASS or repeated completed tests.
Next: reposition subject/phone to include whole head/hands/feet with margin; owner Ready
retained for this setup, but request adjustment notification and recheck before collection.
Camera stays stopped meanwhile; fresh cooldown/thermal checks required on next launch.

Post-check process audit found an app process present after runner exit (cause unestablished).
Explicitly force-stopped again; pidof empty and camera service Active Camera Clients [] verified.
No capture/logcat/perfetto processes observed. Keep app closed pending framing correction.


### Adjusted framing refused by thermal preflight - 2026-10-08
Owner requested full-body recheck with no collection if head/feet cropped. Guard refused
before camera launch: Android status 1 (LIGHT), SKIN 39.3 C; required status is 0.
Fresh local evidence recovery-framing-adjusted-20261008/ retained, session INCOMPLETE.
No framing image, countdown or 30-second collection ran. No automatic retry.
Read-only follow-up showed status 1, SKIN 40.1 C, BAT 36.4 C and app PID 24691 owning
rear camera 0 despite runner refusal/force-stop. Cause of reactivation unestablished;
not evidence that guarded runner launched it. Explicitly force-stopped again and verified
pidof empty / Active Camera Clients []. Owner advised to keep app closed during cooldown;
agent will reopen only after fresh normal checks. Framing remains unverified, no PASS.
Prior evidence unchanged. Physical testing stopped on elevated preflight as instructed.
Next: cool phone with app closed; fresh thermal preflight and guarded head/feet review,
then 15-second countdown only if framing passes and thermal remains acceptable.


### Physical pause / source-only thermal review - 2026-10-08

Owner paused ALL physical testing and camera/inference launches. Prior Ready is not permission
to resume during this pause. Reviewed retained logs/trace/source only; no ADB or device activity.
Findings and next safe test: [phase-2-thermal-review.md](phase-2-thermal-review.md), ADR-021.
Genuine defect fixed: framing-only host path previously initialized full AI/analysis. New debug
preview-only path creates no MediaPipe pipeline and binds no analyzer/capture, omits sensor/
diagnostic overlay work, selects camera/aspect at launch, and closes within a best-effort 12 s
monotonic deadline. Host rejects missing preview-only label. Full countdown moves before camera
launch, eliminating 15 s of idle inference. LIGHT launch policy reviewed but kept conservative;
SEVERE/CRITICAL/rapid-rise abort and Android protections unchanged. Root thermal cause unresolved.
Trace query retained locally at device-evidence/phase2/physical/framing-source-review-20261008/;
no source evidence modified. Device gains/unattended shutdown behavior UNVERIFIED.
All physical gates pending and tests paused; no acceptance/merge/Phase 3/Premium UI.


Host validation for paused investigation: debug/profile/release builds, camera unit tests,
perception API tests and app lintDebug PASSED. Host collection/thermal/framing integration suite:
11 tests PASSED (mocked ADB only, no device). Full spec validator: 0 errors, 0 warnings.
Diff whitespace check passed. Reverified previous 384-file evidence inventory: no mismatches.
No new APK installed or device check run; preview-only behavior and thermal gain pending physical
verification after explicit resume. Local trace-derived review/inventory retained, never uploaded.


### One preview-only request blocked before launch - 2026-10-08
Owner Ready authorized one bounded rear-landscape preview-only check, no AI/analysis/collection.
Checkpoint 9d57e8d6851d60c8257939f4b38dae83e99ea5c9 / clean tree verified. Initial read status 0,
SKIN 38.3 C; later pre-install read status 0 / SKIN 39.8 C. Installed validated debug APK
8899dbbd861d4a320cb163d5a72722a58947e75f295f74a289f24e34352645e4 without launching.
During camera-off cooldown guard observed status 1 / SKIN 40.3 C and refused launch.
Runner cameraLaunches=0, INCOMPLETE, no framing screenshot or countdown/collection/inference
requested. Preview-only mode and 12-second automatic shutdown NOT EXERCISED; no PASS.
Subsequent audit unexpectedly found app PID 6692 owning rear camera 0 despite runner refusal
and cleanup. Reactivation source UNKNOWN; explicitly force-stopped again, pidof empty and
Active Camera Clients [] verified. Final post-stop status 1 / SKIN 40.1 C; maximum across
recorded samples 40.3 C. Full head/hands/feet visibility UNKNOWN (no new framing image).
Local evidence: device-evidence/phase2/physical/preview-only-once-20261008-191941/;
previous evidence/checkpoints unchanged. No retry; owner advised to keep app closed.
Errors: cooldown normal-status requirement failed; unexpected external app reactivation
unresolved. Testing remains stopped with elevated thermal state; prior unfinished gates pending.


### Camera reactivation investigation / camera-idle invariant - 2026-10-08
PHASE: 2; STATUS: partial, physical camera/inference testing PAUSED.
Checkpoint 400a688 verified; same branch/Draft PR #3. No new camera/inference/installation or
instrumentation launch. See [camera idle review](phase-2-camera-idle-review.md), ADR-022.
Retained active client was actual rear camera 0 owned by app PID 6692. Root trigger unknown;
zero harness launches did not imply idle. Confirmed harness defect fixed: missing camera/PID
verification during cooldown and after stop. Added fail-closed ownership invariant, exact
project/test-package stop/verification, idle-only mode, command journal and resilient cleanup.
Normal user startup and all thermal protections unchanged. No product lifecycle bug claimed.
Device currently disconnected; no current device cleanup/thermal state certified. Latest
retained cleanup had empty PID and clients []; current host capture-process inventory empty.
Next: reconnect for cleanup/idle-only diagnostics, no launch. NOT SAFE TO RESUME physical tests.
Evidence preserved; no interrupted PASS, merge, owner acceptance, Phase 3 or Premium UI.


Validation: 20 host collection/thermal/ownership/framing regression tests PASSED, including
idle-only no-launch, reactivation-before-launch abort and guard shutdown despite stop failure.
Camera unit/perception API tests, debug/profile/release builds and lintDebug PASSED (host only).
Full spec validation: 0 errors, 0 warnings. Diff check passed. Rehashed previous 384-file
inventory: 0 mismatches. No physical test, camera launch, inference or instrumentation run.
Current device unavailable; final live cleanup remains UNVERIFIED, not PASS. Local-only
review evidence: device-evidence/phase2/physical/camera-idle-review-20261008/.


### Device cleanup and idle verification ONLY - 2026-10-08
Owner authorized cleanup-only from e041702ea84b91b0575cfa7285e3e9458bba1c1d.
Checkpoint/clean tree verified; Samsung SM-S918B serial R5CW40EE9QK connected/authorized.
No camera launch, MediaPipe, inference, installation, physical test or thermal override.
Initial, post-cleanup, ~3-second interval and final samples: no com.aiphotographer process,
no active camera client. 30.746-second idle observation; no reactivation observed. No device
logcat/perfetto/screenrecord candidates; no host task-owned collectors in inventory.
No package/process needed stopping. Device cleanup/idle VERIFIED for sampled window only.
Thermal status NORMAL (0) throughout. Battery 34.6 -> 34.9 C, USB charging unchanged.
Skin initially 35.7 C, minimum 35.6 C, final/max 38.3 C. Rise of 2.7 C within a sampled
interval under 30 seconds meets existing rapid-rise abort seed, despite no observed project
camera/inference. Root heat contribution remains unresolved; no new attribution claim.
SAFE FOR NEXT SHORT TEST: NO; require cooling/stable thermal trend and fresh idle check,
then explicit authorization/Ready. No automatic retry or physical gate PASS.
Local evidence: device-evidence/phase2/physical/cleanup-idle-20261008-193104/ includes raw
process/camera/thermal snapshots, battery reports, device/host times, collector inventory,
report and safety review. Previous 384 inventoried files remain unchanged. Prior checkpoint
preserved; physical tests remain paused. No merge, acceptance, Phase 3 or Premium UI.


### Fresh cleanup-only idle observation - 2026-10-08 19:39-19:40
Owner repeated cleanup-only request from e041702. Preserved newer descendant fe3a599 and clean
tree; no checkout/reset or evidence overwrite. Samsung SM-S918B R5CW40EE9QK connected.
No camera/MediaPipe/inference/physical test launched. Fresh local evidence:
device-evidence/phase2/physical/cleanup-idle-20261008-193941/.
31.693-second idle observation plus initial/final snapshots: no project processes, no active
camera clients, no unexpected reactivation, no device/host task-owned collectors. No stop needed.
Thermal NORMAL (0) throughout; SKIN initial/max 36.7 C, final 36.5 C; battery 34.9 -> 34.8 C.
No >=2 C/30 s rapid rise observed. Cleanup/idle verified for sampled window; no errors.
Eligible for a next short guarded preview-only check ONLY after explicit Ready and fresh
preflight; this result does not authorize automatic launch or inference/collection. Earlier
rapid-rise observation remains preserved. No Phase 2 acceptance, merge or Phase 3.


### ONE guarded rear-landscape preview-only framing check - 2026-10-08
Owner explicitly requested one check and confirmed Ready via input. Checkpoint f02d096,
branch and clean tree verified. SM-S918B R5CW40EE9QK connected; idle-only preflight verified
NORMAL (0), no rapid rise, no project PID/camera client; no launch during preflight.
Ran exactly one guarded --framing-only rear-landscape 4:3 launch on installed validated
8899dbbd861d4a320cb163d5a72722a58947e75f295f74a289f24e34352645e4 debug APK.
UI label confirms analysis disabled; developer/perception overlays absent. Local screenshot
shows full head, both hands and both feet inside frame (current pose only; motion coverage
not tested). New path skips MediaPipe factory and ImageAnalysis; no inference/collection run.
Host launch-command to automatic cleanup 9.375 s, before 12-second activity deadline. The
12-second timer is configured but was not independently exercised because host stopped earlier.
Cleanup verified zero project/test PID and zero active camera clients. No retry/errors/guard
trigger. Guard thermal status 0 throughout; before 36.0 C, after/max 36.3 C SKIN. Additional
~12-second post-shutdown observations remained status 0 / 36.3 C, zero PID/client, no reactivation.
Evidence preserved locally at guarded-preview-only-20261008/ and prior idle-only preflight at
preview-ready-preflight-20261008/ under device-evidence/phase2/physical/. Previous 384-file
inventory unchanged. Framing-only success is not full-inference tracking, performance, alignment
or landscape-matrix PASS. No repeated completed tests, merge, acceptance or Phase 3.
Safest next: keep camera stopped, cool/rest, review this result; only a separately authorized
short inference test with new Ready, fresh idle/thermal checks and camera-off countdown may follow.


### One authorized rear-landscape inference window - 2026-10-08
Continued from e1a511aa2797ddb440a2f76d1b73ad8b1e9fd82b on the existing Phase 2 branch.
Owner accepted prior preview-only framing and confirmed Ready. Exactly one launch, no retry.
Samsung SM-S918B / Android 16 API 36, installed validated debug APK
8899dbbd861d4a320cb163d5a72722a58947e75f295f74a289f24e34352645e4.
Camera-off idle preflight: NORMAL (0), stable SKIN 35.8-35.9 C, no project PID/camera client.
Runner repeated guarded cooldown and 15-second camera-off countdown before rear-landscape
4:3 inference. Delivered analysis 640x480/crop 640x480. Local MediaPipe logs report pose=GPU,
face=GPU; hardware execution placement was not independently profiled.

Collection completed: 30.000 s requested sampling window, 30.679 s collector wall time;
29.787 s counter interval. Launch-command to force-stop was 40.024 s including startup/UI
verification and collection teardown, so this was not a 30-second total camera-on limit.
Counter deltas: pose completed/detected 230/230 (7.721 Hz), face completed/detected 56/1
(1.880 Hz), errors 0; offered 894, accepted 230, busy skipped 17, cadence skipped 647.
Face coverage is insufficient for PASS. Saved preview shows pose landmarks; diagnostic overlay
obscures the upper body/head area, limiting visual alignment assessment. Prior full-body framing
remains accepted, but numerical overlay alignment and motion/face coverage remain unverified.
Last rolling task-to-callback latency p50/p95 ms: pose 37.664/41.864, face 24.405/49.330,
batch 76.804/94.933. These are rolling snapshots, not exact window-only percentiles.
Last RGB conversion/rotation rolling p50/p95 35.920/49.567 ms. App-reported preview capture
~30 Hz does not measure display FPS or CameraX internal drops. Adaptation degradation level
4 -> 6 is app policy, not Android thermal status. Optimized-build performance, CPU/power,
allocation rate, display FPS and thermal endurance are unmeasured in this run.
Sampled PSS 459420-495563 KiB; RSS 540388-577760 KiB; peak between samples unknown.

Android thermal status NORMAL (0) throughout both guards, no rapid-rise guard trigger.
Runner preflight SKIN 35.3 C, near-launch 35.4 C; end/max 37.2 C. Post-stop three snapshots
across ~10 seconds: NORMAL (0), SKIN 37.2 -> 37.0 -> 37.0 C, no project/test PID and no
active camera clients. Runner cleanup also verified zero clients/PIDs; host collector/logcat
process inventory empty. No test errors, automatic retry, thermal override or further launch.
Evidence retained locally under device-evidence/phase2/physical/guarded-inference-one-20261008/
and inference-one-preflight-20261008/. Earlier 384-file preserved inventory hashes unchanged.
No source changes. Collection completion is not landscape matrix PASS or owner acceptance.
Remaining: sufficient face coverage, front/mirroring, capture/offline/lifecycle outstanding items,
overlay alignment and optimized/performance gates per the existing matrix; completed evidence
is preserved and not repeated. Safest next: camera-off cooldown and evidence review; only a
separately authorized bounded setup with fresh Ready/normal-status idle preflight may follow.
Prefer a strict total camera-on deadline for future short runs to bound startup overhead too.
No merge, Phase 3 or Premium UI.

Session validation: 20 host-only Python regression tests passed; cached full spec validation
0 errors/0 warnings; git diff --check passed. No source/APK changes, so prior validated
build/lint results remain applicable; builds were not rerun for this evidence-only update.
