package com.example

import android.app.Application
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.util.CrashReportingManager
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * SamMikrotik Production Application Class.
 * Handles graceful startup initialization, safe offline Firebase configuration,
 * background database pre-warming, and global unhandled exception trapping to eliminate
 * startup freezes and silent crashes in Android Studio.
 */
class SamMikrotikApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // 1. Safe FirebaseApp fallback initialization
        // Guarantees FirebaseApp is never null, preventing IllegalStateException when google-services.json is absent
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(packageName)
                    .setProjectId("sammikrotik-prod")
                    .setApiKey("AIzaSyDummyFallbackKeyForOfflineOperation")
                    .build()
                FirebaseApp.initializeApp(this, options)
            }
        } catch (e: Exception) {
            Log.w("SamMikrotikApp", "FirebaseApp fallback initialization: ${e.message}")
        }

        // 2. Pre-warm Room database asynchronously on background thread
        appScope.launch {
            try {
                AppDatabase.getInstance(this@SamMikrotikApp).openHelper.readableDatabase
            } catch (e: Exception) {
                Log.e("SamMikrotikApp", "Database warm-up error: ${e.message}", e)
            }
        }

        // 3. Catch and log any uncaught exceptions to prevent silent process termination
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("SamMikrotikApp", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            CrashReportingManager.logNonFatal("FATAL_APP_CRASH", throwable.message ?: "Unknown fatal error", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
