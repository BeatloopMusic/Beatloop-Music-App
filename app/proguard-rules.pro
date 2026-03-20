# Add project specific ProGuard rules here.

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.beatloop.music.**$$serializer { *; }
-keepclassmembers class com.beatloop.music.** {
    *** Companion;
}
-keepclasseswithmembers class com.beatloop.music.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }

# Keep Media3
-keep class androidx.media3.** { *; }

# Keep Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coil
-dontwarn coil.**

# Ktor
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep data classes
-keep class com.beatloop.music.data.model.** { *; }
-keep class com.beatloop.music.innertube.models.** { *; }

# Suppress warnings for external dependencies with missing classes on Android
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn com.google.re2j.**
-dontwarn org.mozilla.javascript.**
-dontwarn org.jsoup.**

# Keep jsoup for HTML parsing
-keep class org.jsoup.** { *; }
-keeppackagenames org.jsoup

# Keep Mozilla Rhino ScriptEngine if used
-keep class org.mozilla.javascript.** { *; }
-keeppackagenames org.mozilla.javascript
