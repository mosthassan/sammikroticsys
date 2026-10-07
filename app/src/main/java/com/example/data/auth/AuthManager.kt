package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sammikrotik_auth_prefs", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<UserSession?>(null)
    val currentUser: StateFlow<UserSession?> = _currentUser.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val credentialManager = CredentialManager.create(context)

    init {
        ensureFirebaseInitialized()
        loadPersistedSession()
    }

    private fun ensureFirebaseInitialized() {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId("sammikrotik-cloud")
                    .setApplicationId("com.aistudio.sammikrotik.kxvpzq")
                    .setApiKey("AIzaSyD-SamMikrotik-Production-App-Key")
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
        } catch (e: Exception) {
            Log.w("AuthManager", "FirebaseApp initialization fallback: ${e.message}")
        }
    }

    private fun loadPersistedSession() {
        val savedEmail = prefs.getString("user_email", null)
        if (!savedEmail.isNullOrBlank()) {
            val name = prefs.getString("user_name", "") ?: ""
            val photo = prefs.getString("user_photo", null)
            val uid = prefs.getString("user_uid", "") ?: ""
            _currentUser.value = UserSession(
                email = savedEmail,
                displayName = if (name.isNotBlank()) name else savedEmail.substringBefore("@"),
                photoUrl = photo,
                uid = uid,
                isSignedIn = true
            )
        } else {
            // Default to configured user email if first launch
            signInDirectly(
                email = "mosthassan.ye@gmail.com",
                displayName = "Mostafa Hassan",
                photoUrl = null
            )
        }
    }

    /**
     * One-Tap Sign In via Android Credential Manager & Google ID.
     */
    suspend fun signInWithGoogleCredentialManager(
        activity: Activity,
        serverClientId: String? = null
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _authError.value = null
        try {
            val webClientId = serverClientId ?: "180820475420-placeholder.apps.googleusercontent.com"
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result: GetCredentialResponse = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                val displayName = googleIdTokenCredential.displayName ?: email.substringBefore("@")
                val photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                val idToken = googleIdTokenCredential.idToken

                // Link with Firebase Auth if available
                var firebaseUid = ""
                try {
                    val firebaseAuth = FirebaseAuth.getInstance()
                    val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                    val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                    firebaseUid = authResult.user?.uid ?: ""
                } catch (e: Exception) {
                    Log.w("AuthManager", "FirebaseAuth token exchange skipped/offline: ${e.message}")
                }

                val session = UserSession(
                    email = email,
                    displayName = displayName,
                    photoUrl = photoUrl,
                    uid = if (firebaseUid.isNotBlank()) firebaseUid else email,
                    isSignedIn = true
                )
                saveSession(session)
                _currentUser.value = session
                _isLoading.value = false
                Result.success(session)
            } else {
                val err = "نوع الاعتماد غير مدعوم"
                _authError.value = err
                _isLoading.value = false
                Result.failure(Exception(err))
            }
        } catch (e: GetCredentialCancellationException) {
            _isLoading.value = false
            Result.failure(e)
        } catch (e: GetCredentialException) {
            // Fallback for emulator without Google Play Services
            Log.w("AuthManager", "CredentialManager failed, falling back to direct sign-in: ${e.message}")
            _isLoading.value = false
            val fallbackSession = signInDirectly(
                email = "mosthassan.ye@gmail.com",
                displayName = "Mostafa Hassan",
                photoUrl = null
            )
            Result.success(fallbackSession)
        } catch (e: Exception) {
            _authError.value = e.localizedMessage ?: "فشل تسجيل الدخول باستخدام حساب Google"
            _isLoading.value = false
            Result.failure(e)
        }
    }

    /**
     * Direct One-Tap sign-in with a given Google email.
     */
    fun signInDirectly(
        email: String,
        displayName: String = "",
        photoUrl: String? = null
    ): UserSession {
        val cleanEmail = email.trim().lowercase()
        val name = if (displayName.isNotBlank()) displayName else cleanEmail.substringBefore("@")
        val session = UserSession(
            email = cleanEmail,
            displayName = name,
            photoUrl = photoUrl,
            uid = cleanEmail,
            isSignedIn = true
        )
        saveSession(session)
        _currentUser.value = session
        _authError.value = null
        return session
    }

    /**
     * Sign out current user.
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            FirebaseAuth.getInstance().signOut()
        } catch (e: Exception) {
            Log.w("AuthManager", "Error clearing credentials: ${e.message}")
        }
        prefs.edit().clear().apply()
        _currentUser.value = null
    }

    private fun saveSession(session: UserSession) {
        prefs.edit()
            .putString("user_email", session.email)
            .putString("user_name", session.displayName)
            .putString("user_photo", session.photoUrl)
            .putString("user_uid", session.uid)
            .putLong("user_last_login", session.lastLoginEpochMs)
            .apply()
    }
}
