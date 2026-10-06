# Phase 2 SDK privacy audit — 2026-10-06

## Blocker

The approved MediaPipe architecture remains the intended inference path. However, official
`com.google.mediapipe:tasks-core:0.10.32` declares `transport-api:3.0.0`,
`transport-backend-cct:3.1.0` and `transport-runtime:3.1.0`. These resolve Google DataTransport and
Firebase encoding libraries. This is not a telemetry-free SDK graph.

The downloaded official AAR was inspected with JDK javap, not inferred from dependency names:

- `TasksStatsLoggerFactory.create` calls `TasksStatsProtoLogger.create`.
- The proto logger constructor creates `RemoteLoggingClient`.
- The remote client initializes `TransportRuntime`, uses `CCTDestination.INSTANCE`, and calls
  `Transport.send` for log events.

Checks of official tasks-core POMs for 0.10.0, 0.10.9, 0.10.14, 0.10.21 and 1.0.0 also show these
transport dependencies. Bytecode inspection of 0.10.14 and 1.0.0 confirms the proto logger path.
No claim is made about every historical release. Sources:
[0.10.32 POM](https://dl.google.com/dl/android/maven2/com/google/mediapipe/tasks-core/0.10.32/tasks-core-0.10.32.pom),
[1.0.0 POM](https://dl.google.com/dl/android/maven2/com/google/mediapipe/tasks-core/1.0.0/tasks-core-1.0.0.pom),
[upstream TaskRunner](https://github.com/google-ai-edge/mediapipe/blob/v0.10.32/mediapipe/tasks/java/com/google/mediapipe/tasks/core/TaskRunner.java).

AGENTS.md rule 11 requires explicit owner approval for an SDK with a network component, while the
Phase 2 instruction prohibits network/analytics SDKs. Adding manifest removal alone would not remove
the SDK's logging/storage/jobs, and excluding transport alone would leave live class references.
Neither workaround is adopted. No network inference, camera upload or telemetry was executed.

## Safe checkpoint

The shipping Gradle graph retains the Phase 1 camera app and dependency inventory; no MediaPipe,
DataTransport, Firebase or model assets are added to its APK. Pure Phase 2 data contracts, confidence,
One Euro filtering, assembly, freshness, scheduling and diagnostic shot classification are independently
testable. Attempted adapters, downloaded weights and altered camera/UI wiring are preserved only under
ignored `device-evidence/phase2/blocked-integration/` and are not compiled or committed as production.
Local audit POM/AAR/bytecode evidence and logs stay under ignored `device-evidence/phase2/`.

## Required decision

Approve preparing an audited telemetry-free source-modified Tasks artifact (with pinned provenance,
modified-source notices, removed logger/network components and initialization tests), or obtain an
approved architecture brief for another compliant runtime. No SDK modification or alternate runtime is
silently substituted. This is a privacy/architecture blocker, not a model-weight license rejection.
Physical Phase 2 checks remain pending because no ADB device is connected.
