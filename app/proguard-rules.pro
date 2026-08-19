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

# Tor service - must not be obfuscated (Android Service loaded by reflection)
-keep class org.torproject.jni.** { *; }
-keep class info.guardianproject.** { *; }
-dontwarn org.torproject.**
-dontwarn info.guardianproject.**
