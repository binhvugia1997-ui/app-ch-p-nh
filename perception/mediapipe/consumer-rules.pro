# Preserve the JNI/reflection ABI of the pinned official SDK, including generated containers.
# This is packaging configuration, not an SDK source change or telemetry workaround.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.common.flogger.backend.system.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
# Official Tasks Core omits these optional classic-Graph profiler/template protos.
# Neither API is called by the Pose/Face integration; see phase-2-sdk-audit.md.
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate
