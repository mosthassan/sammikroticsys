package com.example.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.crashlyticsDataStore by preferencesDataStore(name = "crashlytics_settings")

/**
 * Privacy-first Crashlytics integration for SamMikrotik.
 * - Collection is strictly disabled by default (at manifest and runtime levels).
 * - Enabled ONLY after explicit user consent in Settings.
 * - ZERO personally identifiable information (PII) is ever recorded (no names, phones, IPs, or credentials).
 */
object CrashReportingManager {

    private val KEY_CRASHLYTICS_ENABLED = booleanPreferencesKey("crashlytics_user_consent_enabled")

    fun isConsentGrantedFlow(context: Context): Flow<Boolean> {
        return context.crashlyticsDataStore.data.map { preferences ->
            preferences[KEY_CRASHLYTICS_ENABLED] ?: false
        }
    }

    suspend fun setConsent(context: Context, granted: Boolean) {
        context.crashlyticsDataStore.edit { preferences ->
            preferences[KEY_CRASHLYTICS_ENABLED] = granted
        }
        applyConsentState(granted)
    }

    fun applyConsentState(granted: Boolean) {
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(granted)
        } catch (_: Exception) {
            // Handled gracefully in environments without Google Play Services or during unit tests
        }
    }

    fun logNonFatal(tag: String, message: String, throwable: Throwable? = null) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("event_tag", tag)
            crashlytics.log("[$tag] $message")
            if (throwable != null) {
                crashlytics.recordException(throwable)
            }
        } catch (_: Exception) {
            // Ignored if collection is disabled
        }
    }
}
