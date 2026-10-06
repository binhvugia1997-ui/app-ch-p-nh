# Phase 2 SDK privacy audit — 2026-10-06

## Current outcome

The owner requested a deeper audit before any fork. The initial conclusion that a source-modified
SDK was required was premature. Stock Maven artifacts and the tested exclusions do not meet the
telemetry-free requirement. **Option C is integrated: unmodified official upstream Core with its
default dummy logger, plus unchanged official Vision 0.10.32.**
Upstream generates a dummy logger by default. No fork, logger patch or substitute model has been made.
The production app contains the approved local models and audited MediaPipe combination; no
DataTransport/Firebase transport/encoder components. Physical Phase 2 verification remains pending.

## Exact Maven artifacts and full graph

Intended dependency: `com.google.mediapipe:tasks-vision:0.10.32` → `tasks-core:0.10.32`.
Core introduces these non-optional compile dependencies:

Audited AAR SHA256: Core `8265164d7c72f131b90497bfc06579242ca388d4481190c7fbd5aa797a1d4637`;
Vision `d6e69475707d07a24478e9ff00c437c48c9a834ffb04d4e6e0ba907defa87777`.

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
Backend CCT's manifest contributes INTERNET and ACCESS_NETWORK_STATE; Runtime contributes
ACCESS_NETWORK_STATE, job service, alarm receiver and backend discovery. The stock probe explicitly
removes INTERNET during merge; its merged manifest retains ACCESS_NETWORK_STATE. The excluded
probe has neither permission nor those DataTransport components.

Runtime verification on existing `Medium_Phone_API_37.0`, x86_64 emulator / Android 17:

| Probe | Actual outcome |
|---|---|
| Stock 0.10.32 + manifest INTERNET removal | Pose and Face CPU initialization and black-image inference succeeded |
| Permission assertion | INTERNET denied in both target apps |
| Stock local telemetry assertion | SQLite event counts: 2 after pose, 3 after face |
| Exclude DataTransport and Firebase groups | APKs build; both landmarker creation paths throw `NoClassDefFoundError: TransportRuntime` |
| Socket permission | Loopback-only socket creation fails with EPERM in both apps |
| Instrumentation | 4 stock + 4 exclusion tests PASS; exclusion tests assert failure, **not** functioning inference |

No Samsung phone is connected. These are SDK/privacy experiments, not physical perception acceptance
or Phase 2 performance baselines.

## Alternatives in owner preference order

**A — Official Maven artifact:** `tools/audit-mediapipe-artifacts.py` downloaded metadata, POMs and
Core AARs for all 39 listed versions (alpha-1 through 1.0.0, including 0.10.33/0.10.35). The first four
alphas have no transport dependency. All four Vision AARs lack PoseLandmarker/FaceLandmarker
APIs, so they cannot implement the approved contract. All later Core POMs declare transport. Older
TaskRunner classes invoke the proto logger directly; newer releases use the unconditional factory.
No compatible telemetry-free Maven artifact was identified. This is bounded to the audited artifacts.

**B — Stock with narrow exclusions:** tested on 0.10.32 as above. POMs do not mark transport optional.
No official Tasks opt-out/exclusion contract was identified in the inspected API/docs. Gradle removes
graph edges, not mandatory bytecode references. Runtime failure rules out the tested exclusions;
retaining partial transport jars retains logging functionality. No replacement classes, reflection,
R8 assumptions or fabricated transports are adopted.

**C — Official unmodified source build: SELECTED and integrated.**
Pinned v0.10.32 commit `8317ba78778738ba90a521e7e4580a2ba0129c81` includes a
[default dummy logger factory](https://github.com/google-ai-edge/mediapipe/blob/8317ba78778738ba90a521e7e4580a2ba0129c81/mediapipe/tasks/java/com/google/mediapipe/tasks/core/BUILD)
and official Core/Vision AAR targets. The logging define is described as internal-only and remains
disabled. `tools/build-mediapipe-upstream.sh` builds official targets without Java/C++ changes. Only
Android repository/environment declarations prescribed by upstream setup are appended to WORKSPACE
and retained as evidence. No fork is created. The Windows host lacks Linux/NDK/Bazel, so a read-only
GitHub Actions audit build uses upstream Linux prerequisites, Bazel 7.4.1, JDK 21 and NDK r28b.
The Core-only target built successfully in Actions run 37476494319. Its Java-only AAR uses upstream's
generated dummy factory. Keep the unchanged official Maven Vision/JNI artifact and exclude only its
stock tasks-core edge. The tested Core is intentionally vendored in a restricted local Maven repository;
exact provenance/hash/rebuild instructions are in `third_party/mediapipe/README.md`. No expiring CI
download is needed for app builds, and no runtime download exists. A full official native build was
interrupted to recover logs after over 7,000 of 7,677 actions; it was progressing, not shown impossible.
The smaller Core-only path avoids rebuilding unchanged native Vision code.
`tools/verify-mediapipe-upstream.py` checks known telemetry identifiers in Java/native AAR payloads,
the actual generated factory bytecode and ABI inventory. Its negative control correctly rejects the
stock Core AAR. No known native telemetry identifier was found in stock Vision by this bounded scan;
that is not a complete native call-path proof. All eight source-flavor tests actually ran and PASS:
four shared privacy/IMAGE checks, upstream factory class assertion, sequential Pose and Face
LIVE_STREAM callbacks, and actual 33-pose / 478-face landmarks plus a 16-element facial matrix from
official local fixture images. Blendshapes are disabled. Fixtures are ignored developer evidence,
not app/Git assets. No DataTransport class/database exists after these probes; INTERNET is denied
and the loopback socket fails with EPERM. Production adapter tests separately pass rapid stop/resume,
one-frame ownership and rear/front-like portrait/landscape geometry changes on the emulator.
These synthetic results do not verify real camera tracking, accuracy, mirroring or GPU performance.

**D — Modified source/fork:** not selected. Propose a minimal modification only if C is insufficient
and request the owner decision then. An upstream dummy logger must not be misreported as a fork.

## Reproduction and shipping boundary

Run the artifact script with JDK javap and an output directory under ignored device-evidence. For the
probe, `-PprobeAssets=<absolute assets directory>` points to audited version-1 local bundles from
model-licenses.md §8. The app never downloads models. Build/test both flavors using their
`assembleIncludedDebug` / `assembleExcludedDebug` and corresponding AndroidTest tasks. The source
workflow runs for its own script/workflow changes on Draft PR #3.

Stock telemetry-bearing Core dependencies exist only in the isolated audit probe. The production
graph replaces Core and contains no DataTransport/Firebase transport/encoder artifacts. Audited
model weights are now local production assets. Native third-party notices and final profile checks
are completed and reported separately in the session handoff. Phase 1 numerical
reprojection remains unmeasured, fallback hardware-unverified and SM-S918B results non-generalizable.
Phase 3, matching, guidance, recommendations and auto-capture remain outside this work.

## Shrinking and instrumentation configuration

Release/profile preserve the pinned SDK's JNI/reflection ABI and Flogger system backend. Protobuf
Lite message fields are retained: a real shrunk-runtime failure removed `Any.typeUrl_`, preventing
both task graphs from initializing. This is an SDK serialization requirement, not telemetry suppression.
The official Tasks Core target omits two optional classic-Graph proto types (CalculatorProfile and
CalculatorGraphTemplate). Only those exact unused types have `-dontwarn`; TaskRunner calls neither
template nor profiler APIs. This is separate from the privacy decision: the source dummy factory and
absence of transport classes were demonstrated before shrinking and do not rely on R8 deletion.

Profile instrumentation retains its test-facing model/API signatures plus shared Kotlin/AndroidX
tracing ABI; the MediaPipe adapter and application still run through R8. Release has no test-facing
keep rules. Profile footprint therefore must not be described as an exact release memory measurement.
Crosshair and dynamic ImageProxy proxy tests are debug-only. Connected app test tasks explicitly reject
zero-test reports, since a runner crash previously produced a misleading Gradle success with zero tests.
The earlier source-flavor directory was corrected to `androidTestUpstream`; the eight-test XML count
was checked after the corrected run. No unexecuted or zero-test run counts as a pass in the handoff.

Runtime watchdog (5 s) and recent busy-ratio window (1 s) are engineering diagnostic seeds,
CALIBRATION_REQUIRED, not measured accuracy/confidence thresholds. Intended cadence skips precede
busy admission; overload/recovery uses a recent window, not an irreversible cumulative ratio.

## Physical image-ownership repair (2026-10-07)

The first Samsung SM-S918B live run exposed `IllegalStateException: Can't call setPixels() on a
recycled bitmap`. Upstream `BitmapImageContainer.close()` recycles its bitmap when MPImage closes.
The adapter now uses official `ByteBufferImageBuilder` with RGB format and a task-owned reusable
direct buffer, held unchanged until the callback. Upstream `AndroidPacketCreator.createImage`
supports RGB byte buffers and `ByteBufferImageContainer.close()` is a no-op. This changes app-side
storage, not MediaPipe source or the audited privacy architecture. Repeated same-geometry frames
are now covered by the production adapter instrumentation regression. Failed physical inference
is excluded from success evidence; actual rerun results and remaining framing gates are in
phase-status.md.
