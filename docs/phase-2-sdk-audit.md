# Phase 2 SDK privacy audit — 2026-10-06

## Current outcome

The owner requested a deeper audit before any fork. The initial conclusion that a source-modified
SDK was required was premature. Stock Maven artifacts and the tested exclusions do not meet the
telemetry-free requirement, but **an unmodified official source build is being investigated**.
Upstream generates a dummy logger by default. No fork, logger patch or substitute model has been made.
The shipping app still contains no MediaPipe, DataTransport, Firebase or model assets.

## Exact Maven artifacts and full graph

Intended dependency: `com.google.mediapipe:tasks-vision:0.10.32` → `tasks-core:0.10.32`.
Core introduces these non-optional compile dependencies:

| Dependency | Direct/transitive purpose |
|---|---|
| `transport-api:3.0.0` | Logging transport interfaces |
| `transport-runtime:3.1.0` | Event persistence, scheduling and upload execution |
| `transport-backend-cct:3.1.0` | CCT HTTP backend |
| `firebase-encoders:17.0.0` | Transitive encoding interface |
| `firebase-encoders-proto:16.0.0` | Transitive protobuf encoding |
| `firebase-encoders-json:18.0.0` | Transitive JSON encoding |

The remaining SDK graph includes Guava 27.0.1-android and support/annotation artifacts, Flogger 0.6
and its system backend, protobuf-javalite 4.26.1, and AndroidX annotations (resolved against the
probe's AndroidX dependencies). Firebase encoders are not Firebase Analytics; telemetry is established
by the executable Tasks logging path below. `tools/mediapipe-privacy-probe` is an isolated Gradle
project, absent from production settings.gradle.kts. Its `exportAuditGraph` exports **all** resolved
runtime edges and component coordinates for both flavors. Evidence remains in ignored build/evidence
directories. Primary artifacts:
[vision POM](https://dl.google.com/dl/android/maven2/com/google/mediapipe/tasks-vision/0.10.32/tasks-vision-0.10.32.pom),
[core POM](https://dl.google.com/dl/android/maven2/com/google/mediapipe/tasks-core/0.10.32/tasks-core-0.10.32.pom).

## Initialization, collection and transmission

Official AAR bytecode, inspected with JDK javap:

1. Both landmarkers call `TaskRunner.create`.
2. `TasksStatsLoggerFactory.create` unconditionally creates `TasksStatsProtoLogger`.
3. Its constructor creates `RemoteLoggingClient`, which initializes `TransportRuntime`, selects
   `CCTDestination.INSTANCE`, and creates a `COREML_ON_DEVICE_SOLUTIONS` protobuf transport.
4. Session start/end and periodic invocation statistics call `Transport.send`.

The Java logger builds application ID/version, platform, task/mode, initialization/inference latency
and invocation/drop statistics. No image/landmark payload was identified in this Java logger; this is
not a comprehensive claim about every native binary or all releases. The
[upstream privacy notice](https://github.com/google-ai-edge/mediapipe#privacy-notice) documents on-device
input processing and reporting of API utilization/performance metrics.

Android's [INTERNET permission](https://developer.android.com/reference/android/Manifest.permission#INTERNET)
is required to open network sockets. Removing it prevents this app UID from opening network sockets;
it does **not** remove local telemetry collection, SQLite storage, jobs or HTTP code. No successful
transmission was observed or claimed. The probe grants no INTERNET permission; no cloud inference,
photos, camera input, API keys or cloud configuration are used. Black synthetic bitmaps are its only
inputs. No packet-capture-based universal network claim is made.

Runtime verification on existing `Medium_Phone_API_37.0`, x86_64 emulator / Android 17:

| Probe | Actual outcome |
|---|---|
| Stock 0.10.32 + manifest INTERNET removal | Pose and Face CPU initialization and black-image inference succeeded |
| Permission assertion | INTERNET denied in both target apps |
| Stock local telemetry assertion | SQLite event counts: 2 after pose, 3 after face |
| Exclude DataTransport and Firebase groups | APKs build; both landmarker creation paths throw `NoClassDefFoundError: TransportRuntime` |
| Instrumentation | 3 stock + 3 exclusion tests PASS; exclusion tests assert failure, **not** functioning inference |

No Samsung phone is connected. These are SDK/privacy experiments, not physical perception acceptance
or Phase 2 performance baselines.

## Alternatives in owner preference order

**A — Official Maven artifact:** `tools/audit-mediapipe-artifacts.py` downloaded metadata, POMs and
Core AARs for all 39 listed versions (alpha-1 through 1.0.0, including 0.10.33/0.10.35). The first four
alphas have no transport dependency. Alpha-4 Vision inspection shows no PoseLandmarker/FaceLandmarker
API, so it cannot implement the approved contract. All later Core POMs declare transport. Older
TaskRunner classes invoke the proto logger directly; newer releases use the unconditional factory.
No compatible telemetry-free Maven artifact was identified. This is bounded to the audited artifacts.

**B — Stock with narrow exclusions:** tested on 0.10.32 as above. POMs do not mark transport optional.
No official Tasks opt-out/exclusion contract was identified in the inspected API/docs. Gradle removes
graph edges, not mandatory bytecode references. Runtime failure rules out the tested exclusions;
retaining partial transport jars retains logging functionality. No replacement classes, reflection,
R8 assumptions or fabricated transports are adopted.

**C — Official unmodified source build:** a real candidate, not yet a validated integration.
Pinned v0.10.32 commit `8317ba78778738ba90a521e7e4580a2ba0129c81` includes a
[default dummy logger factory](https://github.com/google-ai-edge/mediapipe/blob/8317ba78778738ba90a521e7e4580a2ba0129c81/mediapipe/tasks/java/com/google/mediapipe/tasks/core/BUILD)
and official Core/Vision AAR targets. The logging define is described as internal-only and remains
disabled. `tools/build-mediapipe-upstream.sh` builds official targets without Java/C++ changes. Only
Android repository/environment declarations prescribed by upstream setup are appended to WORKSPACE
and retained as evidence. No fork is created. The Windows host lacks Linux/NDK/Bazel, so a read-only
GitHub Actions audit build uses upstream Linux prerequisites, Bazel 7.4.1, JDK 21 and NDK r28b.
Outputs are temporary audit artifacts, not automatically distributed/packaged dependencies. Full
bytecode/native graph, licensing/notices and Pose/Face runtime checks must pass before adoption.

**D — Modified source/fork:** not selected. Propose a minimal modification only if C is insufficient
and request the owner decision then. An upstream dummy logger must not be misreported as a fork.

## Reproduction and shipping boundary

Run the artifact script with JDK javap and an output directory under ignored device-evidence. For the
probe, `-PprobeAssets=<absolute assets directory>` points to audited version-1 local bundles from
model-licenses.md §8. The app never downloads models. Build/test both flavors using their
`assembleIncludedDebug` / `assembleExcludedDebug` and corresponding AndroidTest tasks. The source
workflow runs for its own script/workflow changes on Draft PR #3.

Stock telemetry-bearing dependencies/models exist only in the isolated probe and draft evidence.
Production runtime wiring remains held until C is demonstrated compliant. Phase 1 numerical
reprojection remains unmeasured, fallback hardware-unverified and SM-S918B results non-generalizable.
Phase 3, matching, guidance, recommendations and auto-capture remain outside this work.
