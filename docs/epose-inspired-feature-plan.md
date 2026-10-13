# Epose-inspired feature delivery plan (documentation only)

This is a delivery map for the requested pose-guidance experience, not a new architecture, phase authorization, or specification of new thresholds. The existing `docs/roadmap.md` controls phase boundaries; the linked engine documents and `specs/` control behavior, identifiers, geometry, and numbers. "Epose-inspired" describes product intent only. No third-party interface, image, pose artwork, template coordinates, text, model, or dataset is approved for copying.

**Current gate (2026-10-13):** Phase 0 and Phase 1 are accepted. Phase 2 implementation and automated validation are complete, but physical verification and owner acceptance are pending. Draft PR #3 remains unmerged. The last recorded SM-S918B Face-continuity state is a passed camera-off preflight and a requested, unanswered Ready; no new 30-second run is implied by this document. Phase 3 cannot begin until Phase 2 is accepted by the owner. Preserve the NORMAL-only preflight, stable camera-idle check, thermal rapid-rise/severe guards, one-person requirement, explicit Ready, single-run/no-retry limit, and Pose-off Face-only constraint for that pending Phase 2 test (`docs/phase-status.md`, `docs/phase-2-physical-verification.md`).

## Existing work and ownership

| Capability | Already specified or implemented | Planned owner; no duplicate work |
| --- | --- | --- |
| Camera preview, transform, manual JPEG capture | Phase 1 accepted; `docs/phase-1-verification.md` | Reuse the CameraX session and ANALYSIS-to-PREVIEW contract. Phase 5 integrates capture with guidance; it does not rebuild the camera. |
| Single-person Pose/Face landmarks, filtering, diagnostic overlay | Phase 2 implementation exists; physical gates remain partial (`docs/tasks/phase-2-pose-face-detection.md`) | Phase 3 consumes `FrameAnalysis`. Do not treat Phase 2 diagnostic skeleton or Face-only counters as a guide, matcher, or identity tracker. |
| Pose schema and five seed files (four solo seeds plus an explicit mirrored twin) | Phase 0 specified; `specs/schemas/pose-template.schema.json`, `specs/poses/`; seeds remain `SEED_UNVALIDATED` | Phase 3 loads/validates seeds for a developer guide. Phase 6 calibrates and promotes user-facing templates. |
| Guide geometry, matching, corrections, usability, mirror contract | Specified in `docs/pose-system.md`; matcher and corrections are not Phase 2 features | Phase 3 builds the dashed guide; Phase 4 builds scoring/corrections. Use the normative math and IDs rather than recopying them here. |
| Framing rules, candidate arbitration, readiness machine, vi/en message contracts | Specified in `docs/composition-engine.md`, `docs/guidance-engine.md`, `specs/rules/mvp-rules.json`, `specs/i18n/messages.json` | Phase 5 implements rules, camera guidance UI, readiness, and the default-off auto-capture setting. |
| 20-pose solo target and family allocation | Owner decision D5 and `docs/pose-taxonomy.md` section 6 | Phase 6 delivers the validated library and selection UI. Pair/group templates wait for Phase 8. |

## Phase assignment and dependencies

| Phase | Work in this feature set | Entry dependency and completion evidence |
| --- | --- | --- |
| 0 | Existing research, geometry/schema, rule taxonomy, localization contract, licence policy, owner decisions D1-D8. | Accepted; retain as source of truth, with no new Phase 0 deliverable. |
| 1 | Existing preview, transforms, manual capture and device baseline. | Accepted; reuse APIs and evidence. |
| 2 | Existing single-person perception, confidence/freshness and diagnostic alignment; finish its outstanding physical gates. | Owner acceptance is required before Phase 3. This plan adds no Phase 2 camera run or relaxed guard. |
| 3 | Load/validate seed templates; render the dashed guide with stable anchoring, front mirror, partial-body handling, and authoring preview. An active developer/test template may be selected without shipping a library browser. | Phase 2 accepted; `docs/pose-system.md` sections 3 and 6; roadmap G-P3 and L6 guide recognition. No match score or correction text. |
| 4 | Score a selected template; emit component bands, `UNVERIFIABLE`, and semantic correction candidates. | Phase 3 geometry and Phase 2 `FrameAnalysis`; `docs/pose-system.md` section 4-5 and `docs/test-plan.md` section 3.3. No composition/auto-capture. |
| 5 | Implement shot-aware framing/composition, photographer-mode camera-move advice, one-instruction UI, explanation, readiness and manual capture integration. Implement optional auto-capture behind a setting that is OFF by default. | Phase 4 correction candidates and Phase 1 capture; `docs/composition-engine.md`, `docs/guidance-engine.md`, roadmap Phase 5 gates. No pose-library browsing or recommendations. |
| 6 | Deliver local browsing, explicit pose choice, favourites/history, thumbnails, difficulty/shot-type metadata and the owner-approved 20 validated solo templates. | Phase 3 authoring and Phase 4 matcher calibration; `docs/pose-taxonomy.md` section 6 and `docs/pose-system.md` section 7. Only `VALIDATED` templates are user-facing. |
| 7 | Add calibrated lighting/scene work and optional shot-type/scene-tag ranking of the Phase 6 library; suggestions never silently replace the selected pose. | Phase 6 library and Phase 7 scene gates. No new pose assets from an external app. |
| 8 | Add a separately licensed multi-person pipeline and pair/group templates. | Do not claim Phase 2 BlazePose is a multi-person solution; stable tracking and real-couple L6 gate from the roadmap. |
| 9 | Integrate choose-intent, guide, capture, review, and the optional auto-capture countdown into one coherent experience; verify the armed flow. | Reuse Phase 5 readiness and default-off setting; do not implement a second readiness machine. |
| 10 | Tune performance, degradation, memory, battery and thermal behavior across device tiers. | Preserve measurement classifications and device limits in `docs/performance-strategy.md`; roadmap G-P5. |
| 11 | Refine guide and banner aesthetics, Vietnamese wording, onboarding, accessibility, haptics and failure states. | Reuse stable message IDs; L6 comprehension and accessibility gates. |
| 12 | Package and release only after the preceding gates, licences/notices, privacy and release-device checks pass. | `docs/model-licenses.md` section 3 and roadmap release gates; no unlicensed external assets. |

## Delivery slices and acceptance

### A. Pose selection and camera guidance UI

1. **Phase 3 guide surface:** consume the active template and Phase 2 landmarks in the existing coordinate contract. Draw template geometry in the guide overlay, including partial-body and handed/mirror cases. Keep model status and unknown landmarks distinct from a visually "wrong" pose. Test geometry in pure JVM fixtures, mirrored/front and both orientations on the eventual reference device, overlay rate G-P3, and L6 recognition. Do not expose seed templates as a validated consumer library.
2. **Phase 5 camera guidance:** turn calibrated framing results into photographer-action candidates, run them through the existing arbiter, then render only `GuidanceState.primary` plus an allowed secondary. The UI may show the selected pose, guide, score band, one actionable instruction, neutral low-confidence state, and a "why?" sheet; it must not compose its own advice or present an uncalibrated threshold as a hard error. Test contradictory actions, one-primary invariant, dwell, orientation/mirror direction, missing subject, poor quality, localization and human framing improvement against the roadmap/test-plan gates.
3. **Phase 6 selection:** build the user-facing local template browser and explicit selection flow; show only validated templates, their shot-type/difficulty metadata and geometry-derived thumbnails. Switching templates resets guide/matcher/readiness state so stale score or countdown cannot carry over. Test ordering, empty/invalid metadata, persisted local favourites/history, selection/reselection and accessibility. Phase 7 may rank suggestions; it must not auto-switch a pose. No Phase 5 library UI is pulled forward.

### B. Pose similarity scoring and correction feedback (Phase 4)

1. Consume a schema-valid Phase 3 template and Phase 2 usable landmarks. Normalize in SUBJECT space, apply the permitted mirror contract, rigid alignment and coverage gates, and return a score only when verifiable. `UNVERIFIABLE` carries no low score or blame. Score bands and match confidence use `docs/pose-system.md` section 4; all proposed cutoffs remain `CALIBRATION_REQUIRED` until human-labelled calibration.
2. Produce per-component bands and semantic correction candidates only for evaluated, sufficiently confident active-template components. Apply the existing enter/exit latch, moving-subject suppression and top-k policy; send IDs through the Phase 5 arbiter. Keep anatomical left/right correct on front preview. Test exact/scaled/proportion-changed/mirrored/occluded/noisy/wrong-pose fixtures, unknown-confidence abstention, correction churn, wrong-direction regressions and human match agreement as specified in `docs/test-plan.md` section 3.3 and roadmap Phase 4. Do not introduce a second scoring formula or message catalog.

### C. Ready state, countdown and optional auto-capture (Phases 5 and 9)

1. **Phase 5:** implement the single state machine in `docs/guidance-engine.md` section 7 with required versus advisory criteria, continuous hold, camera/subject stability and clear reasons. Manual shutter remains available, with warnings where appropriate; reaching READY never fires the shutter on the default setting. Auto-capture is OFF by default and must require an explicit opt-in setting. Keep capture through the existing Phase 1 use case and confirm a saved URI rather than inventing a second camera path.
2. **Phase 9:** integrate the already implemented state machine into the full session. If opted in, show a cancellable countdown and revalidate hard criteria during it. Template change, lost subject, low confidence, app backgrounding, camera switch, user cancellation, or thermal stop must cancel pending capture and prevent stale/repeated shots. Test scripted readiness timelines, one capture per arm/countdown, no capture after cancellation, default-off persistence, manual override, save success/failure, lifecycle and human early/missed/repeated-capture cases. Use the existing `CALIBRATION_REQUIRED` holds/cooldown in the guidance spec; do not create new timing claims here.

### D. Pose templates and composition library (Phases 3, 5-8)

1. **Phase 3:** keep the Phase 0 seed JSON and `_m1` mirror contract; validate schema, topology, required coverage, handedness and geometry preview. These are engineering seeds.
2. **Phase 5:** apply only the existing MVP framing/composition rules in `specs/rules/mvp-rules.json` and the abstention/threshold policy of `docs/composition-engine.md`; composition advice never becomes a hidden pose-library acceptance score or blocks readiness when advisory.
3. **Phase 6:** author and calibrate the exact 20-solo distribution already recorded in `docs/pose-taxonomy.md` section 6. Record per-template provenance, consented local human review, plausible wrong-pose comparisons, matcher calibration, mirror variants and shot-type fit before `VALIDATED` promotion. Test schema/cross-reference integrity, duplicate IDs, thumbnails, selection and L6 navigation. Phase 7 ranking and Phase 8 pair/group work remain separate.

## Licensing, privacy, and evidence gates

- Create original vector/geometry thumbnails and template coordinates. Do not copy Epose screenshots, UI artwork, text, pose photos, database entries or models. A public image or app listing does not grant redistribution rights. For any new external asset or dataset, record its exact version, code/weight/data licences, commercial and redistribution rights, attribution and `SHIP: OK` in `docs/model-licenses.md` before inclusion; otherwise do not ship it. Keep required notices in the APK.
- Keep inference, matching, favourites/history and photo evidence local; no paid API, cloud dependency, `INTERNET` permission, identity recognition or uploaded frames/landmarks. Human calibration captures require consent and remain local. Synthetic fixtures precede real-person evaluation.
- Each phase inherits its roadmap entry/exit and L1-L6 tests in `docs/test-plan.md`. Record actual devices and measured values; a proposed performance number is a target, never a device result. Preserve Phase 2 raw evidence and the thermal/camera-off gate described above. This document records planning only and authorizes no implementation or physical test.
