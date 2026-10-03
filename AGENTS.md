# AGENTS.md — shared working rules

This file is the contract between the **GitHub Agent** (research / architecture / specification / review)
and the **Codex Local Agent** (Android implementation on the developer's machine). Both agents, and any
future human contributor, must follow it.

---

## PROJECT

**AI Photographer** — an offline, on-device, real-time AI photography assistant for Android.

Pipeline concept:

```
CAMERA → PERCEPTION → PHOTOGRAPHY ANALYSIS → POSE + COMPOSITION DECISION
       → LIVE GUIDANCE → READY → CAPTURE
```

---

## SOURCE OF TRUTH

**This GitHub repository.** Not chat history, not a local scratch file, not a whiteboard.

* Specifications live in `docs/` and `specs/`.
* The handoff state lives in `docs/phase-status.md` and must be updated at the end of every work session
  by whichever agent did the work.
* If reality diverges from the docs, update the doc in the same session.

---

## ROLE SPLIT

### GITHUB AGENT

* Research (photography craft, vision models, licensing, performance evidence)
* Architecture and module boundaries
* Specifications, schemas, algorithms, data structures
* Test criteria and acceptance gates
* Documentation
* Reviewing implementation against specification
* Risk identification, requirement-conflict identification
* Preparing implementation task briefs for Codex Local

### CODEX LOCAL

* Kotlin production code, Android Studio project, Gradle
* CameraX implementation, Compose UI/overlay
* Android SDK, ADB, emulator, physical devices
* Debugging, profiling, build verification, APK generation
* **Device verification and reporting measured numbers**

Neither agent may do the other's job silently. If Codex Local needs an architecture change, it must be
written down in `docs/` (or raised as an issue) — not just implemented.

---

## CORE POLICY (applies to every decision)

* **Local-first** — perception and analysis run on the device.
* **Free-first** — no paid API, no paid inference provider, no mandatory subscription, in the core loop.
* **Offline-capable** — the MVP must work in airplane mode. No `INTERNET` permission in the MVP manifest.
* **Privacy-first** — camera frames do not leave the device; no identity recognition; no upload of
  photos or landmarks.
* **Deterministic-first** — photography decisions come from measurable quantities, not vibes.
* **No invented numbers** — every threshold is either traced to a source or tagged `CALIBRATION_REQUIRED`.
* **No fake measurements** — an unmeasured performance number is a `[TGT]` hypothesis, never a claim; only
  a recorded device measurement is a `[GATE]` (`performance-strategy.md` §0).
* **Public ≠ open source** — the repository is public and has no open-source licence; that is a recorded
  owner decision, not permission to reuse. Do not add a licence, do not treat the code as reusable, and
  keep the third-party licence audit (`model-licenses.md`) up to date.

---

## DEVELOPMENT RULES

1. **Work phase by phase. Do not skip phases.** A phase starts only when `docs/phase-status.md` records
   the previous phase as accepted.
2. **Do not start Phase 1 until Phase 0 is approved by the human owner.**
3. **Do not claim device success without device verification.** "Builds on my machine" is not evidence.
   Report device model, Android version, measured latency/FPS, and thermal state.
4. **Document architecture changes** in `docs/architecture.md` (new ADR entry) before or with the change.
5. **Engine code is pure and testable.** Analysis engines (`core-photography`, `core-pose`,
   `core-guidance`) must not touch Android APIs, the camera, the UI, or strings.
6. **No user-visible strings in analysis/engine code.** Emit message IDs + parameters
   (e.g. `guidance.pose.arm_far_from_torso.left`), resolve them in the UI/resource layer.
   Default locale is Vietnamese (`vi`), English (`en`) as fallback.
7. **No secret knowledge in code comments.** Rationale goes in `docs/`.
8. **Keep MVP and non-MVP separated.** Anything tagged `NEXT`, `LATER`, `EXPERIMENTAL` in
   `docs/roadmap.md` must not leak into an MVP phase.
9. **Licence gate:** a model or dataset may ship only if `docs/model-licenses.md` records its
   redistribution status as `OK` and the required notice files are in the APK.
10. **Performance claims are hypotheses.** Anything in `performance-strategy.md` marked
    `CALIBRATION_REQUIRED` is a hypothesis until measured on a device.
11. **Privacy gate:** a change that adds a network call, an SDK with a network component, or any
    identity/recognition feature must be explicitly approved by the human owner and documented.
12. **Spec validation is part of the build.** Run `python3 specs/validation/validate_specs.py` after touching
    anything under `specs/` or a rule/message reference in `docs/`. It checks the cross-file chains that JSON
    Schema cannot (rule → action → message key → text, conflict references, template contracts, doc
    references). Green is required before a phase is called done.
13. **Single sources of truth.** Usability/confidence math lives in `pose-system.md` §4.1.1; instruction ids
    in `specs/schemas/guidance-instruction.schema.json`; message keys and vi/en text in
    `specs/i18n/messages.json`; rule ids, thresholds and priorities in `specs/rules/mvp-rules.json`.
    Never re-derive a value in a second document — link to it.
14. **Modules are created when needed.** A new Gradle module requires a stated boundary reason in the phase
    brief (`architecture.md` §2.1); "we will need it later" is not a reason.

---

## DEFINITION OF DONE (per task brief)

A task is done when:

* Code compiles and the app runs on a real device (device model + Android version recorded).
* The behaviour matches the task brief and the referenced `specs/` schemas.
* Unit tests for the new pure logic exist and pass.
* Measured numbers (latency, FPS, memory) are recorded in `docs/phase-status.md` when relevant.
* `docs/phase-status.md` is updated (status, files, decisions, risks, next tasks).

---

## COMMUNICATION TEMPLATE (Codex Local → GitHub Agent)

```
PHASE: <n>
STATUS: <done | blocked | partial>
FILES: <paths>
DEVICES TESTED: <model, Android version, build type>
MEASURED: <latency/FPS/memory/thermal numbers, or "not measured">
DEVIATIONS FROM SPEC: <none | description + reason>
BLOCKERS: <none | description>
QUESTIONS: <numbered list>
NEXT PROPOSED: <task>
```

---

## ESCALATION

Stop and ask the human owner when:

* A requirement conflicts with the local-first / free-first policy.
* A licence is unclear or missing for a model you want to ship.
* A measurement contradicts a documented hypothesis badly enough to change the architecture.
* A task requires a decision that changes user-visible product behaviour (e.g. who holds the phone,
  whether auto-capture is on by default).
