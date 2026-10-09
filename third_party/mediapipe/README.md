# Pinned official MediaPipe integration

This is third-party software, not an AI Photographer project license.

The production dependency is official `com.google.mediapipe:tasks-vision:0.10.32`
(SHA256 `d6e69475707d07a24478e9ff00c437c48c9a834ffb04d4e6e0ba907defa87777`). Its
mandatory Maven `tasks-core` edge is excluded and replaced by the **unmodified upstream**
Core AAR built from v0.10.32 commit `8317ba78778738ba90a521e7e4580a2ba0129c81`.
Upstream's default generated factory uses `TasksStatsDummyLogger`; no fake replacement,
Java/C++ edit, binary patch or custom fork is maintained.

The Core AAR is intentionally vendored under `third_party/maven/` so builds never require
an expiring CI artifact or runtime download. SHA256:
`f05d8c4432613342fa15d93914d0d7079381b941f4e1cd069dee9f41aa5c365f`.
The adjacent project-authored POM only supplies local Maven coordinates and Apache-2.0
license metadata; it does not change SDK code. Required Java libraries are explicit in
the version catalog. Gradle resolves Guava to the existing 33.3.1-android version.

Rebuild with `tools/build-mediapipe-upstream.sh <new source dir> <output dir> core` or the
pinned `.github/workflows/mediapipe-upstream-audit.yml`. Build tools: Bazel 7.4.1, JDK 21,
Clang 16, Android SDK 36/build-tools 35.0.0, NDK r28b. The only source checkout change is
upstream-prescribed WORKSPACE Android repository environment configuration. No source
files are changed. `ENABLE_TASKS_USAGE_LOGGING=0` remains disabled. Core contains Java
classes/resources only; JNI remains the unchanged official Vision artifact.

Successful build: [Actions run 37476494319](https://github.com/binhvugia1997-ui/app-ch-p-nh/actions/runs/37476494319).
Rebuilt archive timestamps may differ; a new binary requires hash review, the bounded
`verify-mediapipe-upstream.py` scan, complete dependency/manifest audit and Pose/Face
IMAGE/LIVE_STREAM device tests before replacement. Do not upgrade Core and Vision independently.

Models are unchanged official version-1 float16 local `.task` assets. Exact URLs/hashes,
Apache-2.0 model cards and redistribution status are in `docs/model-licenses.md` §8.
Upstream MediaPipe, protobuf and annotation license texts are bundled through the existing
offline APK notice generator. See `docs/phase-2-sdk-audit.md` for evidence and its limits.
