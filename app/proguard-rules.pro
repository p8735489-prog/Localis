# Keep native JNI bridge classes
-keep class com.localaisearch.data.llm.** { *; }

# Keep serializable models
-keepclassmembers class com.localaisearch.data.model.** {
    *;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep every JNI native method name/signature stable for llama.cpp.
-keepclasseswithmembers class * {
    native <methods>;
}

# JNI invokes this callback method by name from C++; keep it stable in release/R8.
-keep class com.localaisearch.data.llm.LlamaBridge$TokenCallback { *; }
-keepclassmembers class com.localaisearch.data.llm.LlamaBridge$TokenCallback {
    fun onToken(java.lang.String);
}

# TorService is referenced from AndroidManifest and loaded by the embedded AAR.
-keep class org.torproject.jni.TorService { *; }
-dontwarn org.torproject.jni.**
