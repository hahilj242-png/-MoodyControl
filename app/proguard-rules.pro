# OkHttp WebSocket
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# App classes
-keep class com.mycontrol.mdm.** { *; }
-keepclassmembers class com.mycontrol.mdm.** { *; }

# Android components
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.accessibilityservice.AccessibilityService
-keep class * extends android.app.Application

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Google Play Services Location
-keep class com.google.android.gms.location.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# General
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-flattenpackagehierarchy
-repackageclasses 'h'
-allowaccessmodification
