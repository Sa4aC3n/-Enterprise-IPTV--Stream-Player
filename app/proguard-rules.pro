# ===================================================================
# ENTERPRISE R8 / PROGUARD AGGRESSIVE OPTIMIZATION CONFIGURATION
# ===================================================================

# 1. Multi-pass Optimization (Repeats dead-code elimination 5 times for max shrinking)
-optimizationpasses 5

# 2. DEX Code Flattening & Repackaging (Consolidates classes to minimize DEX size)
-allowaccessmodification
-repackageclasses ''

# 3. Strip Logging to reduce DEX bytecode size and protect endpoints
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# 4. Strip unused Kotlin metadata attributes
-dontnote kotlin.**
-dontnote kotlinx.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 5. Core Rules & Libraries (Room, Media3, OkHttp, Coroutines, Hilt)
# Room Persistence
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Hilt DI
-keep class * extends dagger.hilt.internal.GeneratedComponentManager
-keep class * implements dagger.hilt.internal.GeneratedComponentManager
