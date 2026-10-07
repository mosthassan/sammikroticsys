package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.util.GoogleServicesConfigHelper
import com.example.util.findActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class UserProfile(
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val idToken: String? = null,
    val uid: String = "",
    val isAuthenticated: Boolean = true
)

/**
 * Google Auth & Tenant Identity Manager
 * Enables One-Tap Google Sign-In via Credential Manager & Firebase Auth.
 * Triggers Google native account chooser bottom sheet with automatic legacy GoogleSignInClient fallback.
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
        restoreCachedSession()
    }

    private fun restoreCachedSession() {
        val fbUser = try { auth.currentUser } catch (e: Exception) { null }

        if (fbUser != null && !fbUser.email.isNullOrBlank()) {
            _userProfile.value = UserProfile(
                email = fbUser.email!!,
                displayName = fbUser.displayName ?: fbUser.email!!.substringBefore("@"),
                photoUrl = fbUser.photoUrl?.toString(),
                uid = fbUser.uid,
                isAuthenticated = true
            )
            return
        }

        val savedEmail = prefs.getString(KEY_USER_EMAIL, null)
        val savedName = prefs.getString(KEY_USER_NAME, null)
        val savedPhoto = prefs.getString(KEY_USER_PHOTO, null)
        val savedUid = prefs.getString(KEY_USER_UID, null)

        if (!savedEmail.isNullOrBlank()) {
            _userProfile.value = UserProfile(
                email = savedEmail,
                displayName = savedName ?: savedEmail.substringBefore("@"),
                photoUrl = savedPhoto,
                uid = savedUid ?: savedEmail.replace(".", "_").replace("@", "_at_"),
                isAuthenticated = true
            )
        } else {
            _userProfile.value = null
        }
    }

    /**
     * One-Tap Google Sign-In with Credential Manager.
     * Displays native account chooser bottom sheet.
     * If NoCredentialException or GetCredentialException occurs, automatically launches
     * standard GoogleSignInClient fallback intent.
     */
    suspend fun signInWithGoogleOneTap(
        activityContext: Context,
        serverClientId: String? = null
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _authError.value = null

        val clientId = serverClientId?.takeIf { it.isNotBlank() }
            ?: GoogleServicesConfigHelper.getWebClientId(context)

        val activity = activityContext.findActivity()
        val targetContext = activity ?: activityContext

        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false) // Show ALL Google accounts on device
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false) // Disables auto-select, shows native account chooser
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(targetContext, request)
            val credential = response.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                val name = googleIdTokenCredential.displayName ?: email.substringBefore("@")
                val photo = googleIdTokenCredential.profilePictureUri?.toString()
                val idToken = googleIdTokenCredential.idToken

                // Exchange Google idToken with Firebase Auth
                var firebaseUid = ""
                try {
                    val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
                    val authResult = auth.signInWithCredential(firebaseCred).await()
                    firebaseUid = authResult.user?.uid ?: ""
                } catch (e: Exception) {
                    Log.w("GoogleAuthManager", "Firebase Auth sign-in: ${e.message}")
                }

                val profile = UserProfile(
                    email = email,
                    displayName = name,
                    photoUrl = photo,
                    idToken = idToken,
                    uid = if (firebaseUid.isNotBlank()) firebaseUid else email.replace(".", "_").replace("@", "_at_"),
                    isAuthenticated = true
                )

                persistSession(profile)
                _userProfile.value = profile
                _isLoading.value = false
                Result.success(profile)
            } else {
                val err = "نوع بيانات الاعتماد غير مدعوم"
                _isLoading.value = false
                _authError.value = err
                Result.failure(IllegalStateException(err))
            }
        } catch (e: GetCredentialCancellationException) {
            _isLoading.value = false
            _authError.value = "تم إلغاء تسجيل الدخول"
            Result.failure(e)
        } catch (e: NoCredentialException) {
            Log.w("GoogleAuthManager", "NoCredentialException: ${e.message}. Launching legacy GoogleSignIn fallback.")
            return@withContext launchFallbackOrReport(activity, clientId, e)
        } catch (e: GetCredentialException) {
            Log.w("GoogleAuthManager", "GetCredentialException: ${e.message}. Launching legacy GoogleSignIn fallback.")
            return@withContext launchFallbackOrReport(activity, clientId, e)
        } catch (e: Exception) {
            Log.e("GoogleAuthManager", "Google Sign-In Credential Manager error: ${e.message}", e)
            _isLoading.value = false
            val err = e.localizedMessage ?: "فشل تسجيل الدخول عبر Google"
            _authError.value = err
            Result.failure(e)
        }
    }

    private suspend fun launchFallbackOrReport(
        activity: Activity?,
        clientId: String,
        originalException: Exception
    ): Result<UserProfile> {
        if (activity != null) {
            try {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(clientId)
                    .requestEmail()
                    .build()
                val client = GoogleSignIn.getClient(activity, gso)
                withContext(Dispatchers.Main) {
                    activity.startActivityForResult(client.signInIntent, AuthManager.RC_GOOGLE_SIGN_IN)
                }
                _isLoading.value = false
                val notice = "جاري فتح نافذة اختيار حساب Google البديلة..."
                _authError.value = notice
                return Result.failure(Exception(notice))
            } catch (ex: Exception) {
                Log.e("GoogleAuthManager", "Fallback launch failed: ${ex.message}", ex)
            }
        }
        _isLoading.value = false
        val msg = "لا تتوفر بيانات اعتماد Google متوافقة (تأكد من مطابقة SHA-1 في Firebase Console أو استخدم زر الدخول السريع بالبريد)"
        _authError.value = msg
        return Result.failure(Exception(msg))
    }

    /**
     * Direct sign-in with verified email (for tests / manual fallback).
     */
    fun signInDirectWithEmail(email: String, displayName: String? = null) {
        val cleanEmail = email.trim().lowercase()
        val name = displayName?.takeIf { it.isNotBlank() } ?: cleanEmail.substringBefore("@")
        val uid = cleanEmail.replace(".", "_").replace("@", "_at_")

        val profile = UserProfile(
            email = cleanEmail,
            displayName = name,
            photoUrl = null,
            uid = uid,
            isAuthenticated = true
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
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
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
            .putString(KEY_USER_UID, profile.uid)
            .apply()
    }

    companion object {
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PHOTO = "user_photo"
        private const val KEY_USER_UID = "user_uid"
    }
}
