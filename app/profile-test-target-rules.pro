# AndroidJUnitRunner references this target-app dependency by its original class.
# Retain it for profile instrumentation; other application code remains optimized.
-keep class androidx.tracing.** { *; }
-keep class kotlin.** { *; }
# Stable test-facing DTO/interface signatures; the MediaPipe implementation is still optimized.
-keep class com.aiphotographer.model.** { *; }
-keep class com.aiphotographer.perception.* { *; }
-keep class com.aiphotographer.perception.mediapipe.MediaPipePipelineFactory { *; }
