# ===================================================================
# ENTERPRISE R8 / PROGUARD AGGRESSIVE OPTIMIZATION CONFIGURATION
# Optimized for:
# - Maximum Code Shrinking Percentage
# - Advanced DEX Bytecode Optimization
# - Multi-pass Inlining & Class Merging
# ===================================================================

# 1. Multi-pass Optimization (Repeats dead-code elimination 5 times for max shrinking)
-optimizationpasses 5

# 2. DEX Code Flattening & Repackaging (Consolidates classes to root package to minimize DEX string pool & size)
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-repackageclasses ''

# 3. Aggressive Stripping of Debug & Logging Bytecode from Release DEX
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int println(...);
}

# 4. Strip Unused Kotlin Intrinsics and Assertions
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(...);
    public static void checkNotNull(...);
    public static void checkExpressionValueIsNotNull(...);
}

# 5. Clean Bytecode Metadata & Debug Tables (Retain line numbers for crash analysis)
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-dontnote kotlin.**
-dontnote kotlinx.**
-dontwarn kotlin.reflect.**
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontnote **

# 6. Compose Runtime & Compiler Keep Rules
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
    androidx.compose.runtime.RecomposeScopeImpl *;
}

# 7. Room Database Persistence
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase$Callback
-dontwarn androidx.room.paging.**

# 8. Media3 / ExoPlayer & Video Streaming Engine
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# 9. OkHttp, Okio & Retrofit Network Layer
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# 10. Kotlin Coroutines Optimization
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# 11. Hilt & Android Injection Components
-keep class * extends dagger.hilt.internal.GeneratedComponentManager
-keep class * implements dagger.hilt.internal.GeneratedComponentManager
-keep,allowobfuscation class * extends androidx.lifecycle.ViewModel
