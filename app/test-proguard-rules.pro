# Preserve instrumentation discovery in the test APK. The target app remains shrunk.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep class com.aiphotographer.app.**Test { *; }
