# Proguard / R8 Optimization & Obfuscation Rules for SamMikrotik (18 Keep Rules)

# Rule 1: Preserve line numbers and source files for readable stack traces & Crashlytics
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Rule 2: Room Database abstract implementations
-keep class * extends androidx.room.RoomDatabase { *; }

# Rule 3: Room Entities
-keep @androidx.room.Entity class * { *; }

# Rule 4: Room DAOs
-keep @androidx.room.Dao interface * { *; }

# Rule 5: Room Migrations
-keep class * implements androidx.room.migration.Migration { *; }

# Rule 6: Moshi JSON field annotations
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}

# Rule 7: Moshi Core
-keep class com.squareup.moshi.** { *; }

# Rule 8: Moshi Custom Adapters
-keep class * extends com.squareup.moshi.JsonAdapter { *; }

# Rule 9: Room Entity data models
-keep class com.example.data.local.entity.** { *; }

# Rule 10: Sync & Backup data models
-keep class com.example.data.sync.** { *; }

# Rule 11: Domain Use Cases & Results
-keep class com.example.domain.usecase.** { *; }

# Rule 12: Core Financial Models (Money, Currency, Rates)
-keep class com.example.core.model.** { *; }

# Rule 13: Core Ledger & Journal Drafting models
-keep class com.example.core.ledger.** { *; }

# Rule 14: Financial Statements & Reports Engine models
-keep class com.example.domain.report.** { *; }

# Rule 15: Network & API DTOs
-keep class com.example.data.network.** { *; }

# Rule 16: Utilities & Cryptography
-keep class com.example.util.** { *; }

# Rule 17: Firebase SDKs & Crashlytics
-keep class com.google.firebase.** { *; }

# Rule 18: Google Play Services & Identity
-keep class com.google.android.gms.** { *; }

# Coroutines runtime dispatchers
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Don't warn on optional platform dependencies in OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
