# Phase Status — handoff between GitHub Agent and Codex Local

> **This is the live handoff document.** Whichever agent finishes work updates it. If it disagrees with
> reality, it is a bug.

Last updated: **2026-10-06** by **Codex Local** (corrected-build physical evidence and final baselines/soak recorded; review/acceptance pending).

---

## CURRENT PHASE

**PHASE 0 — ACCEPTED by the human owner.** Owner explicitly reported PR #1 merged and authorized
"START PHASE 1" on 2026-10-03.

**PHASE 1 — PARTIAL: CORRECTED-BUILD PHYSICAL CHECKS AND FINAL BASELINES/SOAK RECORDED; REVIEW/OWNER ACCEPTANCE PENDING.**
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
| **Phase 1 implementation** | Local and physical instrumentation PASS; corrected-build target observations, final optimized 480p/720p baselines and final soak recorded; coordinate evidence review/owner acceptance pending |

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
