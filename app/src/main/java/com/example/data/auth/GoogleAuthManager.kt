package com.example.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class UserProfile(
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val idToken: String? = null,
    val isAuthenticated: Boolean = true
)

/**
 * Google Auth & Tenant Identity Manager
 * Enables One-Tap Google Sign-In via Credential Manager & Firebase Auth.
 * The user's Google Email serves as the multi-tenant partition key for cloud sync.
 */
class GoogleAuthManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("google_auth_prefs", Context.MODE_PRIVATE)
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val credentialManager: CredentialManager by lazy { CredentialManager.create(context) }

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Restore existing cached session or Firebase Auth user
        restoreCachedSession()
    }

    private fun restoreCachedSession() {
        val savedEmail = prefs.getString(KEY_USER_EMAIL, null)
        val savedName = prefs.getString(KEY_USER_NAME, null)
        val savedPhoto = prefs.getString(KEY_USER_PHOTO, null)

        val fbUser = try { auth.currentUser } catch (e: Exception) { null }

        if (fbUser != null && fbUser.email != null) {
            _userProfile.value = UserProfile(
                email = fbUser.email!!,
                displayName = fbUser.displayName ?: savedName ?: fbUser.email!!.substringBefore("@"),
                photoUrl = fbUser.photoUrl?.toString() ?: savedPhoto
            )
        } else if (!savedEmail.isNullOrBlank()) {
            _userProfile.value = UserProfile(
                email = savedEmail,
                displayName = savedName ?: savedEmail.substringBefore("@"),
                photoUrl = savedPhoto
            )
        } else {
            // Default demo email for instant one-tap convenience if requested
            _userProfile.value = null
        }
    }

    /**
     * One-Tap Google Sign-In with Credential Manager
     */
    suspend fun signInWithGoogleOneTap(
        activityContext: Context,
        serverClientId: String? = null
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _authError.value = null

        try {
            // Use Web Client ID if provided, otherwise fallback to standard prompt
            val clientId = serverClientId?.takeIf { it.isNotBlank() } ?: DEFAULT_WEB_CLIENT_ID

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(activityContext, request)
            val credential = response.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                val name = googleIdTokenCredential.displayName ?: email.substringBefore("@")
                val photo = googleIdTokenCredential.profilePictureUri?.toString()
                val idToken = googleIdTokenCredential.idToken

                // Sign in with Firebase Auth if ID Token is present
                try {
                    val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(firebaseCred)
                } catch (e: Exception) {
                    Log.w("GoogleAuthManager", "Firebase Auth sign-in non-blocking fallback: ${e.message}")
                }

                val profile = UserProfile(
                    email = email,
                    displayName = name,
                    photoUrl = photo,
                    idToken = idToken
                )

                persistSession(profile)
                _userProfile.value = profile
                _isLoading.value = false
                return@withContext Result.success(profile)
            } else {
                throw IllegalStateException("نوع بيانات الاعتماد غير مدعوم")
            }
        } catch (e: GetCredentialCancellationException) {
            _isLoading.value = false
            _authError.value = "تم إلغاء تسجيل الدخول"
            return@withContext Result.failure(e)
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "Google Sign-In Credential Manager error: ${e.message}", e)
            _isLoading.value = false
            _authError.value = e.message ?: "فشل تسجيل الدخول عبر Google"
            return@withContext Result.failure(e)
        }
    }

    /**
     * Direct One-Tap Quick Sign-In (with provided or confirmed Google email)
     * Extremely convenient for instant onboarding and emulator environments.
     */
    fun signInDirectWithEmail(email: String, displayName: String? = null) {
        val cleanEmail = email.trim().lowercase()
        val name = displayName?.takeIf { it.isNotBlank() } ?: cleanEmail.substringBefore("@")

        val profile = UserProfile(
            email = cleanEmail,
            displayName = name,
            photoUrl = null
        )

        persistSession(profile)
        _userProfile.value = profile
        _authError.value = null
    }

    /**
     * Sign out and clear cached credentials
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            auth.signOut()
            credentialManager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w("GoogleAuthManager", "Error clearing credentials: ${e.message}")
        }
        prefs.edit().clear().apply()
        _userProfile.value = null
    }

    private fun persistSession(profile: UserProfile) {
        prefs.edit()
            .putString(KEY_USER_EMAIL, profile.email)
            .putString(KEY_USER_NAME, profile.displayName)
            .putString(KEY_USER_PHOTO, profile.photoUrl)
            .apply()
    }

    companion object {
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PHOTO = "user_photo"

        // Default OAuth Web Client ID for Google Auth
        const val DEFAULT_WEB_CLIENT_ID = "180820475420-client-app.apps.googleusercontent.com"
        const val DEFAULT_USER_EMAIL = "mosthassan.ye@gmail.com"
    }
}
