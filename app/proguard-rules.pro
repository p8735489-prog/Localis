# Keep native JNI bridge classes
-keep class com.localaisearch.data.llm.** { *; }

# Keep serializable models
-keepclassmembers class com.localaisearch.data.model.** {
    *;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
