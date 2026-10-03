# AI Photographer (app-chụp-ảnh)

An Android application that behaves like a **real-time AI photography assistant**: it watches the live
camera, understands the person in frame and the composition, and tells the photographer (and the subject)
what to change — then optionally captures at the right moment.

> **Status: PHASE 0 — RESEARCH + ARCHITECTURE (complete, awaiting review).**
> No Android production code exists yet. Phase 1 has **not** started. See
> [`docs/phase-status.md`](docs/phase-status.md).

---

## Core policy (non-negotiable)

| Policy | Meaning |
| --- | --- |
| **Local-first** | All core perception, analysis and guidance run on the device. |
| **Offline-first** | The MVP declares **no `INTERNET` permission**. No cloud AI, ever, for the core loop. |
| **Free-first** | API cost = $0, cloud inference cost = $0, mandatory subscription = $0. |
| **Privacy-first** | Camera frames and photos never leave the device. No face recognition, no identity features. |
| **Deterministic-first** | Photography decisions come from measurable geometry and image statistics; ML is used only for *perception* (where is the body, where is the face). |
| **Honest engineering** | Nothing is claimed to work on a device until it has been verified on a device. |

---

## Repository map

```
docs/                     Research, architecture and specifications (GitHub Agent output)
  architecture.md         Module graph, threading, data contracts, ADRs
  photography-rules.md    Rule database with measurement math + threshold policy
  photography-errors.md   Error taxonomy and detectability classification
  pose-system.md          Pose representation, matching, corrections, dashed guide
  pose-taxonomy.md        Solo / two-person / group posing knowledge base
  composition-engine.md   Deterministic composition engine specification
  guidance-engine.md      Guidance model, conflict arbitration, temporal stability, readiness
  lighting-engine.md      What can and cannot be inferred from a camera frame
  scene-understanding.md  Deferred scene/context module design
  ai-models.md            Model candidates with evaluation format
  model-licenses.md       License map: code vs weights vs commercial usability
  performance-strategy.md Device tiers, inference cadence, thermal/battery budget
  test-plan.md            Unit / instrumented / device test criteria and acceptance gates
  roadmap.md              Phase 1..12 roadmap
  phase-status.md         Live handoff document between GitHub Agent and Codex Local
  phase-0-report.md       Phase 0 final report
  research-sources.md     Consolidated bibliography
  glossary.md             Shared terminology (EN/VI where relevant)
  tasks/                  Prepared implementation task briefs (DO NOT START until approved)
specs/                    Machine-readable specifications for the implementer
  schemas/                JSON Schemas (rule set, rule, pose template, frame analysis, rule result,
                          guidance instruction, message catalog)
  rules/                  Rule database as data: 30 rules with threshold status, priority and phase
  poses/                  Seed pose templates as normalized landmark geometry (SEED_UNVALIDATED)
  fixtures/               Test fixture format + the "no photographs of people" rule
  i18n/                   Localization contract: instruction -> action -> message key -> vi/en text
  validation/             Cross-file validator (run it before claiming a phase done)
```

## Validate the specs

```bash
pip install jsonschema        # only needed for the structural checks
python3 specs/validation/validate_specs.py
```

It fails on any broken cross-file reference: a rule suggesting an instruction that does not exist, an
instruction pointing at a message key with no text, a conflict pointing at an unknown rule, a template
breaking the mirror contract, or a document mentioning a rule/key/path that is not in the specs.

## Product decisions taken (2026-10-03)

Photographer mode is the MVP default (tripod/self-shooting later); auto-capture is OFF by default;
the UX is portrait-first while the architecture stays landscape-capable; MEDIUM is the primary target
tier, LOW degrades gracefully, HIGH may enable extra analysis; the pose library target is 20 solo poses;
Vietnamese is the primary language with English authored in parallel; the repository is public and has
**no** open-source licence (public visibility is not permission to reuse).

---



## Who does what

* **GitHub Agent (research / architecture / review)** — owns everything under `docs/` and `specs/`.
* **Codex Local (implementation)** — owns Kotlin, Compose, CameraX, Gradle, ADB, device testing.

Working rules for both are in [`AGENTS.md`](AGENTS.md).

---

## Reading order (for a new contributor or agent)

1. [`AGENTS.md`](AGENTS.md) — how to work in this repo
2. [`docs/phase-status.md`](docs/phase-status.md) — what is done, what is blocked, what is next
3. [`docs/architecture.md`](docs/architecture.md) — how the app is put together
4. [`docs/roadmap.md`](docs/roadmap.md) — where we are going
5. Then the engine docs relevant to your task.

---

## MVP at a glance (Phase 1–5)

```
CameraX preview + analysis
      ↓
MediaPipe Pose Landmarker (1 person)  +  Face Landmarker
      ↓
Deterministic measurement layer (framing, headroom, subject size, orientation, stability)
      ↓
Dashed pose guide overlay (vector, driven by normalized pose templates)
      ↓
Pose matching + semantic pose corrections
      ↓
Basic composition guidance + one primary instruction at a time
      ↓
Manual capture (auto-capture readiness logic implemented but OFF by default)
```

Explicitly **out of MVP**: VLM, LLM, aesthetic scoring, scene understanding, full group posing,
cloud anything, face recognition.
