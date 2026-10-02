# PHASE 0 REPORT — AI Photographer

Date: 2026-10-02 · Agent: GitHub Agent (Research, Architecture, Specification, Review)
Phase 1: **NOT started.** Android implementation: **not started** (by design).

---

## Research Completed

Broad, multi-source research across four areas, with every source classified as **[PRIMARY]**, **[OFFICIAL]**,
**[CRAFT]** or **[WEAK]** in `docs/research-sources.md`:

1. **Photography craft** — composition techniques and *when they fail* (rule of thirds, centering, symmetry,
   leading lines, negative space, headroom, lead room, horizon, tangents/mergers); portrait practice
   (headshots → environmental, standing/sitting/walking/leaning), camera height and angle effects,
   focal length vs perspective, solo/two-person/group posing.
2. **Photographic perception** — what single-camera pose estimation can actually measure
   (joint-angle RMSE ≈ 8–11°), which composition properties are computable, which need scene semantics,
   which are irreducibly subjective.
3. **On-device models** — MediaPipe Pose/Face/Hand Landmarker, MoveNet, RTMPose, SigLIP, MobileCLIP,
   MobileNet families, Places365, NIMA, CLIP-IQA/TOPIQ, SmolVLM2, Gemma 3 270M; runtime facts
   (NNAPI deprecated in Android 15, MediaPipe GPU requires OpenGL ES 3.1+, GPU delegate unavailable on the
   emulator, MediaPipe Tasks minSdk 24).
4. **Licensing** — the code-licence vs weight-licence vs commercial-use distinction, with a per-asset
   register and a redistribution checklist.

Three findings changed the design:
**(a)** the BlazePose model card explicitly puts *multiple people in an image* out of scope and states the
model tracks only one person — so multi-person needs a different pipeline later;
**(b)** several popular aesthetic/IQA models (CLIP-IQA, TOPIQ/IQA-PyTorch) are **non-commercial**, and
Ultralytics YOLO is **AGPL-3.0** — both are unusable in a closed-source APK;
**(c)** peer-reviewed evidence does **not** support rule-of-thirds compliance as a quality measure
(and a preference study found people preferred centered subjects), so composition advice must stay
advisory and evidence-aware.

---

## Photography Knowledge Base

* `docs/photography-rules.md` — rules as *measurements plus policy*, with a 17-field schema, threshold
  evidence classes (`GEOMETRIC`, `LITERATURE`, `PRACTITIONER`, `CALIBRATION_REQUIRED`, `DEVICE_TUNED`)
  and a confidence model with class caps.
* `docs/photography-errors.md` — four-axis taxonomy (error class × detectability × actor × severity),
  a master error table (framing/background/pose/lighting/timing), a severity→behaviour policy, and an
  explicit list of **rejected "errors"** with reasons.
* `docs/pose-taxonomy.md` — 16 solo poses with structured geometry (body/hips/legs/shoulders/arms/hands/
  head/feet/camera/shot types/detectability/typical mistakes), 10 shared craft foundations with cultural
  caveats, two-person relational features, and group structures for 3–6+.
* `docs/glossary.md` — terminology (EN/VI) so identifiers and translations stay consistent.

**Key stance:** photography principles are *not* universal laws. Only objective, measurable defects are
treated as errors; conventions are suggestions; taste is never scored.

---

## Deterministic Rules

Framing: subject presence, subject size (shot-type bands), headroom (via a circle-fit head-top estimator),
edge margins, joint crops, extreme off-centering, camera roll from the IMU.
Shot type estimation (headshot → full body) from bbox/face geometry.
Pose: match score, arm–torso gap, locked joints, foot merging, shoulder squareness, symmetry,
weight distribution, hand visibility, foreshortening (2D/3D ratio).
Lighting (relative only): face underexposure, face clipping, highlight clipping, shadow crushing,
low contrast, backlight.
Readiness: subject stability, camera stability, duration gates.

## ML-Assisted Rules

Head pose pitch/yaw and gaze (Face Landmarker transformation matrix + iris), head-top estimation
(hybrid), foreshortening (world landmarks), backlit subject (needs a subject mask), and the whole pose
match (geometry over ML landmarks). Everything ML-assisted is confidence-capped (≤ 0.85) and may not
block capture unless it also fails an objective criterion.

## Subjective Rules (never scored)

Visual balance, body language "openness", expression quality, attractiveness, fashion appropriateness,
"story", colour grading, and which of two photos is better. Aesthetic scoring is rejected for the MVP
(no licence-clean model plus product risk).

---

## Pose Taxonomy

Solo: weight-shift/S-curve, relaxed A-pose, hands-in-pockets, hands-on-hips, arms crossed, leaning on a
wall, seated (chair edge, ground/steps), walking mid-stride, look-back-over-shoulder, crouching, hands near
face, environmental portrait, close-up/headshot variants, pointing gesture, lying/reclining.
Two-person: couples, friends/siblings, parent + child, height-difference strategies — specified as
**relational features** (spacing, overlap, head stagger, depth offset, interaction anchors, mutual
orientation) which are measurable in principle but not implemented in the MVP.
Groups: triangle, staggered, layered, V, rows, height-balanced — with group checks (head-line variance,
spacing variance, occlusion, frame coverage).

## Pose Representation

Normalized geometry, never raster images: JSON templates in **SUBJECT space** (hip-mid origin, y down,
unit = torso length), anatomical landmark ids, per-landmark weights and `required` flags, component
weights, joint angles with tolerances floored at the measurement-noise level, relative unit vectors,
body-orientation expectations, framing recommendations, mirror policy, intent flags (to suppress
context-dependent warnings), and provenance (`SEED_UNVALIDATED` → `VALIDATED` after Phase 6 calibration).
Four seed templates are authored in `specs/poses/`.

## Pose Matching Strategy

usability mask → SUBJECT normalization → handedness selection → 2D Kabsch/Procrustes with translation +
rotation only, **clamped to ±25°** (so a lying body cannot be "aligned" into a standing template) →
component scoring (Head/Torso/arms/legs) using joint angles, limb directions, body orientation and
normalized positions → coverage-weighted aggregation where components with `coverage < 0.35` are excluded
rather than scored zero → hard gate (`visibleFraction ≥ 0.5`, torso + one more component, all in-frame
`required` landmarks usable) else `POSE_UNVERIFIABLE`. Bands: MATCHED ≥ 0.85, CLOSE 0.70–0.85,
DIFFERENT 0.45–0.70, FAR (**all `CALIBRATION_REQUIRED`**). Body-proportion robustness comes from using
directions/angles as the primary terms and torso-length self-normalization for positions.

## Composition Engine Strategy

`FrameAnalysis` → shot-type estimator → framing rules → advisory composition rules → lighting rules →
post-processor (hysteresis, severity policy, confidence caps, sort) → `RuleResult`s with measurement,
target range, abstention reason, semantic action, actor, threshold status and persistence.
**No user-visible text in the engine**; a `CompositionSummary` band is display-only (never a score out of 10).
Every **rule** number lives in one generated `Thresholds` object sourced from `specs/rules/mvp-rules.json`;
the few non-rule engine constants (estimator cut-offs, dwell/cooldown) are specified in their engine docs
and join the same object as they are implemented.

## Guidance Strategy

One primary instruction at a time (optional secondary only for a different actor at ≥ 0.8× score), scored
by `priority × severity × confidence × persistence × mode × conflict`. Priorities: readiness 100,
lighting 80, framing 70, pose 60, advisory composition 40. Dwell 900 ms, cooldown 2.5 s, repeat cooldown
4 s, flip guard 1.5 s. A curated **resolution-compatibility table** merges instructions that share one
physical fix (e.g. "move closer" + "more headroom") and sequences ones that conflict instead of showing
both. Actor modes: `PHOTOGRAPHER_MODE` and `SELF_MODE` (camera instructions are re-expressed as subject
actions, or suppressed). Three layers of temporal stability: One Euro filtering, threshold hysteresis +
N-of-M voting, guidance dwell. Readiness is a duration-gated state machine
(NOT_READY → NEAR_READY → READY → COUNTDOWN → CAPTURED) with required/optional/blocking criteria per shot
type; **auto-capture implemented but OFF by default**.

---

## Local Model Recommendations

| Need | Decision | Licence | Phase |
| --- | --- | --- | --- |
| Body pose | **MediaPipe Pose Landmarker** (lite on LOW, full on MEDIUM/HIGH) | Apache-2.0 (code + weights) | 2 |
| Face | **MediaPipe Face Landmarker** (landmarks + head-pose matrix; blendshapes off) | Apache-2.0 | 2 |
| Hands | MediaPipe Hand Landmarker, **on demand** | Apache-2.0 | 5+ |
| Scene | SigLIP image encoder + curated prompt tags | Apache-2.0 | 7 |
| Aesthetic | none in MVP; NIMA (Apache-2.0) only as a labelled optional feature | Apache-2.0 | 9+ |
| VLM / LLM | none | — | experimental |
| Multi-person | RTMPose-s/m (Apache-2.0) **or** detector + per-person BlazePose ROI | Apache-2.0 | 8 |

MVP asset budget: **< 15 MB** of models; MediaPipe Tasks needs minSdk 24, GPU requires OpenGL ES 3.1+ and
does not work on the emulator; NNAPI is deprecated and will not be used.

## Models Rejected / Deferred

**Rejected:** Ultralytics YOLOv8/v11 (AGPL-3.0), CLIP-IQA and TOPIQ/IQA-PyTorch (non-commercial licences),
MobileCLIP weights (Apple AMLR/ASCL terms unverified — MIT code does not cover weights), Qwen-VL (custom
licence + size), ViTPose/OpenPose (weight/size for real-time), on-device LLM for guidance (nondeterministic,
unnecessary, licence obligations).
**Deferred:** SmolVLM2 (Apache-2.0) and Gemma 3 270M (Gemma Terms) to experimental; Places365 to "reject for
v1" on size/licence/maintenance; DINOv2 as optional.

## Licensing Findings

* Distinguish **code licence ≠ weight licence ≠ commercial usability** — the MobileCLIP case is the
  document's cautionary example.
* `docs/model-licenses.md` is a **gate**: `OK` / `TEST` / `REVIEW` / `REJECT` per asset, for frameworks,
  perception models, encoders, aesthetics, large models and datasets.
* Redistribution checklist: NOTICE/licence texts bundled, an in-app licence screen, CC-BY attribution where
  applicable, the Play data-safety declaration (camera processed on-device, nothing collected), and **no**
  non-commercial or AGPL component in any build that leaves the developer's machine — not even a test APK.
* Open items marked `UNKNOWN`: MobileCLIP weight terms, MoveNet checkpoint licence, CLIP checkpoint licence,
  moondream2 release licences, and the **repository's own licence** (owner's decision).

## Performance Strategy

Targets: preview ≥ 30 FPS untouched by analysis; guidance latency p95 ≤ 250 ms; pose 15 FPS on MEDIUM /
10 FPS on LOW; memory ≤ 250 MB (LOW); APK ≤ 40 MB; ≤ 12 % battery per 10-minute session.
Device tiers LOW/MEDIUM/HIGH with a first-launch micro-benchmark (vendor specs do not predict MediaPipe
performance). Cadence: pose 15–30 FPS, face 5–15 FPS adaptive, lighting 2 FPS from the Y plane only,
scene 0.5–2 FPS, hands on demand. An 8-step **degradation ladder** (scene → hands → face cadence →
pose model size → pose cadence → analysis resolution → lighting → UI update rate) with hysteresis on
recovery. Measurement protocol is mandatory (Perfetto/Macrobenchmark, device-only numbers, thermal and
memory recorded) — **a performance claim without the table is a hope, not a claim.**

## Multi-Person Strategy

The v1 detector is single-person by model-card definition. The architecture therefore carries
`subjects: List<Subject>` from day one, every engine is written per-subject, and templates carry
`peopleCount`, but multi-person inference is **Phase 8**: either a licence-clean person detector plus
per-person ROI crops through BlazePose, or RTMPose-s/m via ONNX Runtime Mobile (Apache-2.0, 18 MB
quantized). Running BlazePose with `numPoses > 1` is explicitly rejected as the primary answer.

## Architecture Decisions

13 ADRs recorded in `docs/architecture.md` §12: offline-only core with no `INTERNET` permission;
Kotlin/Compose/CameraX minSdk 24; MediaPipe Tasks as the v1 perception runtime; deterministic rules with no
LLM; geometry pose templates; one canonical `FrameAnalysis` + a five-space coordinate contract;
one primary instruction; multi-person deferred but not designed out; message IDs with Vietnamese-first
strings; no fighting the camera's AE; no identity features ever; pure engines with device-independent tests;
a licence gate before any model ships.
Module graph: pure `core:*` (model/geometry/photography/pose/guidance/light), swappable `perception:*`,
`feature:*` UI, `benchmark`, dev-only `tools:*` — with `core:*` forbidden from depending on Android.

## MVP Definition

Phases 1–5 = CameraX foundation → pose + face perception → dashed pose guide → pose matching + corrections
→ composition/guidance/readiness with **manual capture** (auto-capture off).
Explicitly out: VLM, LLM, aesthetic scoring, scene understanding, group posing, cloud anything,
face recognition.

## Risks

1. Full-body pose quality at 3–4 m (model-card out-of-scope distance) — Phase 2 experiment decides
   resolution and messaging.
2. Nearly every user-visible threshold is `CALIBRATION_REQUIRED`; false positives are the top trust risk
   (classified P1 in the test plan).
3. Single-person perception until Phase 8.
4. Overlay alignment/mirroring across devices (mitigated by the coordinate contract; proven only on device).
5. Thermal/battery behaviour over continuous sessions.
6. Licence contamination if the gate is ignored.
7. Head-top estimate accuracy (no skull landmark) affects headroom rules.
8. Product ambiguity: who holds the phone — changes guidance semantics.

## Open Questions

Who holds the phone (mode); auto-capture default; portrait-lock vs landscape; minimum supported tier;
first pose-library size; repository licence; language scope (vi only vs vi+en); applicationId/display name.
Full list with proposed defaults in `docs/architecture.md` §14 and `docs/phase-status.md`.

## Files Created / Updated

`README.md`, `AGENTS.md`, and under `docs/`: `architecture.md`, `photography-rules.md`,
`photography-errors.md`, `pose-system.md`, `pose-taxonomy.md`, `composition-engine.md`,
`guidance-engine.md`, `lighting-engine.md`, `scene-understanding.md`, `ai-models.md`,
`model-licenses.md`, `performance-strategy.md`, `test-plan.md`, `roadmap.md`, `phase-status.md`,
`phase-0-report.md`, `research-sources.md`, `glossary.md`, `tasks/phase-1-camerax-foundation.md`.
Under `specs/`: `README.md`, six JSON Schemas, `rules/mvp-rules.json` (30 authored rules), four seed pose
templates, `fixtures/README.md`.

## Tasks Prepared for Codex Local

`docs/tasks/phase-1-camerax-foundation.md` — prepared and **explicitly not started**: project skeleton with
the module graph and dependency rules, CameraX 3-use-case session with documented fallbacks, `FrameRouter`
with cadence control and stage-latency histograms, the coordinate contract (unit tests first), minimal
`FrameAnalysis` with the luma grid, `CapabilityReport` v1, and a mandatory dev/perf screen — with
acceptance gates and a reporting template.

## Ready for Phase 1

**YES — conditional on human approval of Phase 0.**

The design is complete enough to implement without inventing architecture on the fly: modules and
dependency rules are fixed, coordinate spaces are specified, data contracts are machine-readable, the rule
set and its threshold statuses are enumerated, the pose template format and matcher are specified with
robustness tests, guidance arbitration and temporal stability have explicit algorithms and acceptance tests,
model choices are licence-verified, and the performance budget with degradation behaviour is written down.

What is **not** ready, and must not be claimed: any statement about real-device behaviour, accuracy,
thermal behaviour or user experience. Those are Phase 1/2 measurements and the phase status will record
them as they arrive.

**Phase 1 must not start until the owner answers the review checklist in `docs/phase-status.md`.**
