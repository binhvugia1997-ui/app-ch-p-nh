#!/usr/bin/env bash
# Build the official, unmodified Tasks sources with upstream's default dummy logger.
# Linux/macOS with Android SDK, NDK r28b, JDK 21 and Bazel 7.4.1 required.
# Output is audit evidence until its bytecode/native payload and runtime are verified.
set -euo pipefail
source_dir="${1:?source directory required}"
output_dir="${2:?output directory required}"
mode="${3:-full}"
test "$mode" = full || test "$mode" = core
source_commit=8317ba78778738ba90a521e7e4580a2ba0129c81
test ! -e "$source_dir"
mkdir -p "$output_dir"
git init "$source_dir"
git -C "$source_dir" remote add origin https://github.com/google-ai-edge/mediapipe.git
git -C "$source_dir" fetch --depth=1 origin "$source_commit"
git -C "$source_dir" checkout --detach FETCH_HEAD
test "$(git -C "$source_dir" rev-parse HEAD)" = "$source_commit"
cd "$source_dir"
# These repository declarations are the configuration prescribed by upstream's
# setup_android_sdk_and_ndk.sh. No Java/C++ sources or logger factory are changed.
cat >> WORKSPACE <<EOF
android_sdk_repository(name = "androidsdk", path = "${ANDROID_HOME:?}", api_level = 36, build_tools_version = "35.0.0")
android_ndk_repository(name = "androidndk", api_level = 21, path = "${ANDROID_NDK_HOME:?}")
bind(name = "android/crosstool", actual = "@androidndk//:toolchain")
EOF
git diff -- WORKSPACE > "$output_dir/environment-configuration.patch"
test "$(git diff --name-only | tr '\n' ' ')" = "WORKSPACE "
targets=(//mediapipe/tasks/java/com/google/mediapipe/tasks/core:tasks_core.aar)
if [ "$mode" = full ]; then
  targets+=(//mediapipe/tasks/java/com/google/mediapipe/tasks/vision:tasks_vision)
fi
bazel build -c opt --config=android --cpu=arm64-v8a --fat_apk_cpu=arm64-v8a,x86_64 \
  --jobs=2 --local_ram_resources=8192 \
  --define=ENABLE_TASKS_USAGE_LOGGING=0 --define=EXCLUDE_OPENCV_SO_LIB=1 \
  "${targets[@]}" \
  2>&1 | tee "$output_dir/build.log"
cp bazel-bin/mediapipe/tasks/java/com/google/mediapipe/tasks/core/tasks_core.aar "$output_dir/"
if [ "$mode" = full ]; then
  cp bazel-bin/mediapipe/tasks/java/com/google/mediapipe/tasks/vision/tasks_vision.aar "$output_dir/"
fi
cp LICENSE "$output_dir/MediaPipe-LICENSE.txt"
printf '%s\n' "$source_commit" > "$output_dir/source-commit.txt"
bazel cquery 'deps(//mediapipe/tasks/java/com/google/mediapipe/tasks/core:tasks_core.aar)' \
  --config=android --cpu=arm64-v8a --fat_apk_cpu=arm64-v8a,x86_64 --define=ENABLE_TASKS_USAGE_LOGGING=0 --define=EXCLUDE_OPENCV_SO_LIB=1 \
  > "$output_dir/core-build-graph.txt"
sha256sum "$output_dir"/*.aar > "$output_dir/SHA256SUMS"

# Preserve license texts of the native runtime source dependencies resolved by
# upstream. This conservative notice set also covers the unchanged Vision JNI.
# Build tools/JDK/Python/NDK are deliberately not described as shipped SDK code.
external="$(bazel info output_base)/external"
mkdir -p "$output_dir/native-notices"
for repo in FP16 FXdiv XNNPACK cpuinfo eigen eigen_archive farmhash_archive fft2d flatbuffers gemmlowp pthreadpool ruy zlib com_google_absl com_google_protobuf com_github_glog_glog_no_gflags org_tensorflow; do
  if [ -d "$external/$repo" ]; then
    find -L "$external/$repo" -maxdepth 1 -type f \( -iname '*license*' -o -iname '*copying*' -o -iname 'notice*' \) -print0 |
      while IFS= read -r -d '' file; do cp "$file" "$output_dir/native-notices/${repo}-$(basename "$file")"; done
  fi
done
