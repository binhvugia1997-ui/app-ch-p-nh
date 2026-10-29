# Task Brief — Phase 2: Pose + face perception

STATUS: AUTHORIZED by the human owner on 2026-10-06 after Phase 1 acceptance and PR #2 merge.
Branch: `codex/phase-2-pose-face-detection`. No Phase 2 acceptance is implied.

Current checkpoint: implementation is complete for the approved Phase 2 modules and interfaces.
The autonomous robustness review adds bounded recovery, lifecycle/timestamp/freshness safeguards and
coordinate regressions (ADR-018). Reproducible native cache truncation during overlapping activity
recreation is addressed with official model-buffer loading (ADR-019), without modifying MediaPipe.
IMPLEMENTATION_COMPLETE and AUTOMATED_VALIDATION_COMPLETE. Human-subject gates are
PENDING_PHYSICAL_VERIFICATION;
owner acceptance is OWNER_ACCEPTANCE_PENDING. Deferred human tests are not implementation failure.
The single current solo session plan is [../phase-2-physical-verification.md](../phase-2-physical-verification.md).
Existing SDK/privacy/model/license results and recorded physical measurements remain preserved.
No source-code modification/fork, Premium UI implementation or Phase 3 work is underway.

## Scope and boundaries

Implement roadmap Phase 2 and architecture §§3–8. Create only the three modules justified by
architecture §2.1: `:perception:api` (pure interfaces, filtering/assembly), `:perception:mediapipe`
(Android Tasks adapters and bounded image ownership), and `:core:photography` (diagnostic shot-type
estimator only). No photography rule engine, guidance, templates, matching or Phase 3 work.
Confidence math lives once in core:model so all pure consumers can use pose-system §4.1.1 without
depending on perception. See ADR-016.

Use pinned official lite/full pose and face task bundles, numPoses/numFaces=1, LIVE_STREAM,
blendshapes off and facial transformation matrices on. Code/model redistribution must be OK in
model-licenses.md and notices must be packaged before an APK is built for distribution.

ANALYSIS inputs are crop-local, rotated once and never mirrored. Reuse Phase 1 plane conversion and
preview transform. Bound ownership to one RGB lease; reject new work while an accepted inference batch
is active. Reuse task image storage on the worker and retain it until callbacks complete.
Initialize, submit and close GPU tasks on their owning worker. Bridge callbacks through a bounded
channel/flow to immutable snapshots. No model or conversion on the UI thread.

Retain raw confidence, world coordinates with nonmetric caveat, explicit freshness and diagnostic
delegate/error state. One Euro applies to usable coordinates only; reset on loss, frame geometry change
and lifecycle restart. Single-person session IDs are not identity recognition. Only associate a face
whose bounds contain the pose nose; otherwise expose face diagnostic separately and abstain.

## Calibration and honest limits

Usability constants are normative in pose-system §4.1.1. One Euro seeds, freshness timeout, scheduling,
thermal/backlog tuning and visible-body shot classification remain CALIBRATION_REQUIRED. No sigmoid,
invented confidence or production hard errors. Shot type stays UNKNOWN where visibility cannot establish
the category; headshot/close-portrait thresholds and local-capture calibration remain pending.

Implementation seeds for review, not validated constants: One Euro minimum/derivative cutoff 1 Hz,
beta 0.007; face freshness 500 ms; static-motion diagnostic <0.003 normalized displacement per result
for 1 s. Scheduling overload/recovery follow performance-strategy §5 (3 s / 15 s); diagnostic pose
latency trigger uses twice the 20 ms upper design budget. They require device calibration, and the
pure scheduler is connected to production inference cadence/model selection. The final degradation
step requests 480p only when capture is not saving; device tuning remains pending.

No Phase 1 measurement is a Phase 2 baseline. Numerical reprojection accuracy remains unmeasured;
unsupported-device fallback remains hardware-unverified; SM-S918B evidence cannot be generalized.

## Validation and physical gates

Run unit tests for usability boundaries, filter behaviour, source ownership/backpressure, freshness,
face association and shot classification; builds for debug/release/profile, lint and schema validation.
Inspect runtime dependencies, manifests, notices/models, diff and ignored evidence before committing.

On a connected reference phone, verify Tasks initialization and failure state, both cameras/orientations/
aspects, mirroring, capture, lifecycle and visible pose/face tracking. Measure optimized-profile 60-second
pose/face/conversion rates and latencies, preview FrameTimeline, memory, and five-minute thermal run.
Compare 480p/720p full-body detection and human judgement at 3–4 m. If no phone is available, record
all physical gates NOT_MEASURED and leave Phase 2 partial. No acceptance or general device claims.
