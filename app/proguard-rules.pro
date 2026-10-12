# Proguard / R8 Optimization & Obfuscation Rules for SamMikrotik

# Preserve line numbers and source files for readable stack traces
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Room Database rules
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * implements androidx.room.migration.Migration { *; }

# Moshi and JSON serialization
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keep class com.squareup.moshi.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }

# Data & Backup models (crucial for JSON serialization/deserialization)
-keep class com.example.data.local.entity.** { *; }
-keep class com.example.data.sync.** { *; }
-keep class com.example.domain.usecase.** { *; }
-keep class com.example.core.model.** { *; }
-keep class com.example.data.network.** { *; }
-keep class com.example.util.** { *; }

# Firebase & Play Services
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
