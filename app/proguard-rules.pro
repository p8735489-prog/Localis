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

# TorService keep rule removed: tor-android AAR is no longer a dependency.
# TorManager uses reflection and degrades to "unavailable" when the class is absent.
-dontwarn org.torproject.**


# LocalAISearch optimization rules
# Keep JNI entry points and Android components stable
-keep class com.localaisearch.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn kotlinx.serialization.**
-dontwarn okhttp3.**
