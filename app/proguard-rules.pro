# Add project specific ProGuard rules here.
# By default these rules are used by the release build (minification disabled in
# this deliverable for out-of-the-box reproducibility).

# Keep Kotlin coroutines metadata
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Media3 / ExoPlayer
-keep class com.google.android.exoplayer2.** { *; }
