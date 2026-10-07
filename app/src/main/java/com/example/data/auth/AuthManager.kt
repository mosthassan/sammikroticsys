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
import androidx.credentials.exceptions.NoCredentialException
import com.example.util.GoogleServicesConfigHelper
import com.example.util.findActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Production Native Google Credential Manager & Firebase Authentication Engine.
 * Features automatic fallback to legacy GoogleSignInClient when CredentialManager
 * returns NoCredentialException or GetCredentialException.
 */
class AuthManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sammikrotik_auth_prefs", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<UserSession?>(null)
    val currentUser: StateFlow<UserSession?> = _currentUser.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val credentialManager: CredentialManager by lazy { CredentialManager.create(context) }

    init {
        ensureFirebaseInitialized()
        loadPersistedSession()
    }

    private fun ensureFirebaseInitialized() {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId("sam-mikrotic")
                    .setApplicationId("com.samtecai.sammikrotic")
                    .setApiKey("AIzaSyAPnh9iJyZQdrhF2sYtzq2CKFdibKvK9J4")
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
        } catch (e: Exception) {
            Log.w("AuthManager", "FirebaseApp initialization fallback: ${e.message}")
        }
    }

    private fun loadPersistedSession() {
        val fbUser = try { FirebaseAuth.getInstance().currentUser } catch (e: Exception) { null }
        if (fbUser != null && !fbUser.email.isNullOrBlank()) {
            val session = UserSession(
                email = fbUser.email!!,
                displayName = fbUser.displayName ?: fbUser.email!!.substringBefore("@"),
                photoUrl = fbUser.photoUrl?.toString(),
                uid = fbUser.uid,
                isSignedIn = true
            )
            _currentUser.value = session
            saveSession(session)
            return
        }

        val savedEmail = prefs.getString("user_email", null)
        val savedUid = prefs.getString("user_uid", null)
        if (!savedEmail.isNullOrBlank() && !savedUid.isNullOrBlank()) {
            val name = prefs.getString("user_name", "") ?: ""
            val photo = prefs.getString("user_photo", null)
            _currentUser.value = UserSession(
                email = savedEmail,
                displayName = if (name.isNotBlank()) name else savedEmail.substringBefore("@"),
                photoUrl = photo,
                uid = savedUid,
                isSignedIn = true
            )
        } else {
            _currentUser.value = null
        }
    }

    /**
     * One-Tap Sign In via Android Credential Manager & Google ID.
     * When NoCredentialException or GetCredentialException occurs, automatically launches
     * GoogleSignInClient fallback intent.
     */
    suspend fun signInWithGoogleCredentialManager(
        activityOrContext: Context,
        serverClientId: String? = null
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _authError.value = null

        val webClientId = serverClientId?.takeIf { it.isNotBlank() }
            ?: GoogleServicesConfigHelper.getWebClientId(context)

        val activity = activityOrContext.findActivity() ?: (activityOrContext as? Activity)
        val targetContext = activity ?: activityOrContext

        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false) // Show ALL Google accounts on device
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false) // Displays native account chooser
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result: GetCredentialResponse = credentialManager.getCredential(
                context = targetContext,
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

                // Exchange Google idToken with Firebase Auth
                val firebaseAuth = FirebaseAuth.getInstance()
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                val firebaseUser = authResult.user
                val uid = firebaseUser?.uid ?: idToken.hashCode().toString()

                val session = UserSession(
                    email = email,
                    displayName = displayName,
                    photoUrl = photoUrl,
                    uid = uid,
                    isSignedIn = true
                )
                saveSession(session)
                _currentUser.value = session
                _isLoading.value = false
                Result.success(session)
            } else {
                val err = "نوع الاعتماد غير مدعوم من Google"
                _authError.value = err
                _isLoading.value = false
                Result.failure(IllegalStateException(err))
            }
        } catch (e: GetCredentialCancellationException) {
            _isLoading.value = false
            _authError.value = "تم إلغاء اختيار الحساب"
            Result.failure(e)
        } catch (e: NoCredentialException) {
            Log.w("AuthManager", "NoCredentialException: ${e.message}. Launching standard GoogleSignIn fallback.")
            return@withContext launchGoogleSignInFallback(activity, webClientId, e)
        } catch (e: GetCredentialException) {
            Log.w("AuthManager", "GetCredentialException: ${e.message}. Launching standard GoogleSignIn fallback.")
            return@withContext launchGoogleSignInFallback(activity, webClientId, e)
        } catch (e: Exception) {
            _isLoading.value = false
            val msg = e.localizedMessage ?: "فشل تسجيل الدخول باستخدام حساب Google"
            _authError.value = msg
            Result.failure(e)
        }
    }

    private suspend fun launchGoogleSignInFallback(
        activity: Activity?,
        webClientId: String,
        originalException: Exception
    ): Result<UserSession> {
        if (activity != null) {
            try {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build()
                val client = GoogleSignIn.getClient(activity, gso)
                withContext(Dispatchers.Main) {
                    activity.startActivityForResult(client.signInIntent, RC_GOOGLE_SIGN_IN)
                }
                _isLoading.value = false
                val notice = "جاري فتح نافذة اختيار حساب Google البديلة..."
                _authError.value = notice
                return Result.failure(Exception(notice))
            } catch (fallbackEx: Exception) {
                Log.e("AuthManager", "GoogleSignIn fallback failed: ${fallbackEx.message}")
            }
        }
        _isLoading.value = false
        val msg = "لا تتوفر حسابات Google متوافقة (تأكد من مطابقة SHA-1 في Firebase Console أو استخدم زر الدخول بالبريد أدناه)"
        _authError.value = msg
        return Result.failure(Exception(msg))
    }

    fun saveSessionDirectly(session: UserSession) {
        saveSession(session)
        _currentUser.value = session
        _authError.value = null
    }

    /**
     * Direct Sign-in method for explicit email/manual entry.
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
            uid = cleanEmail.replace(".", "_").replace("@", "_at_"),
            isSignedIn = true
        )
        saveSession(session)
        _currentUser.value = session
        _authError.value = null
        return session
    }

    /**
     * Sign out current user and clear credential manager state.
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

    companion object {
        const val RC_GOOGLE_SIGN_IN = 9001
    }
}
