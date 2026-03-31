package com.beatloop.music.data.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.beatloop.music.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localIdentityStore: LocalIdentityStore
) {
    private fun ensureFirebaseInitialized(): Boolean {
        return runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseApp.getApps(context).isNotEmpty()
        }.getOrDefault(false)
    }

    private fun authOrNull(): FirebaseAuth? {
        if (!ensureFirebaseInitialized()) return null
        return runCatching { Firebase.auth }.getOrNull()
    }

    suspend fun loginWithGoogle(activity: Activity): Result<String> = withContext(Dispatchers.Main.immediate) {
        val auth = authOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Firebase is not configured"))
        val hostActivity = activity as? ComponentActivity
            ?: return@withContext Result.failure(
                IllegalStateException("Google sign-in requires a ComponentActivity host")
            )

        val webClientId = runCatching {
            context.getString(R.string.default_web_client_id)
        }.getOrNull().orEmpty()

        if (webClientId.isBlank() || webClientId == "null") {
            return@withContext Result.failure(
                IllegalStateException(
                    "Google sign-in is not configured. Ensure app/google-services.json matches package com.beatloop.music and includes OAuth clients."
                )
            )
        }

        return@withContext runCatching {
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            val googleSignInClient = GoogleSignIn.getClient(hostActivity, options)
            val account = launchGoogleSignIn(hostActivity, googleSignInClient.signInIntent)
            val idToken = account.idToken
                ?: throw IllegalStateException("Google sign-in did not return an ID token")
            val credential = GoogleAuthProvider.getCredential(idToken, null)

            val authResult = auth.signInWithCredential(credential).await()

            val uid = authResult.user?.uid
                ?: throw IllegalStateException("Google sign-in did not return a user")
            localIdentityStore.setIdentityMode(IdentityMode.GOOGLE)
            uid
        }.recoverCatching { error ->
            if (
                error.message?.contains("package certificate hash", ignoreCase = true) == true ||
                error.message?.contains("SHA", ignoreCase = true) == true
            ) {
                throw IllegalStateException(
                    "Google sign-in configuration mismatch. Add your debug/release SHA-1 and SHA-256 for package com.beatloop.music in Firebase Project Settings, then download a fresh app/google-services.json.",
                    error
                )
            }
            throw error
        }
    }

    private suspend fun launchGoogleSignIn(
        activity: ComponentActivity,
        signInIntent: Intent
    ): GoogleSignInAccount = suspendCancellableCoroutine { continuation ->
        val requestKey = "google_sign_in_${System.currentTimeMillis()}"
        lateinit var launcher: ActivityResultLauncher<Intent>
        launcher = activity.activityResultRegistry.register(
            requestKey,
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            runCatching {
                GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
            }.onSuccess { account ->
                if (continuation.isActive) {
                    continuation.resume(account)
                }
            }.onFailure { error ->
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
            launcher.unregister()
        }

        continuation.invokeOnCancellation {
            launcher.unregister()
        }
        launcher.launch(signInIntent)
    }

    suspend fun loginWithGoogle(): Result<String> {
        return Result.failure(
            IllegalStateException("Interactive Google sign-in requires an Activity context")
        )
    }

    suspend fun loginAnonymously(): Result<String> = withContext(Dispatchers.IO) {
        val auth = authOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Firebase is not configured"))

        return@withContext runCatching {
            val authResult = auth.signInAnonymously().await()
            val uid = authResult.user?.uid
                ?: throw IllegalStateException("Anonymous sign-in did not return a user")
            localIdentityStore.setIdentityMode(IdentityMode.ANONYMOUS)
            uid
        }
    }

    suspend fun ensureSession(): String {
        val auth = authOrNull()
        val firebaseUser = auth?.currentUser
        if (firebaseUser != null) {
            localIdentityStore.setIdentityMode(
                if (firebaseUser.isAnonymous) IdentityMode.ANONYMOUS else IdentityMode.GOOGLE
            )
            return firebaseUser.uid
        }

        val anonymousResult = loginAnonymously().getOrNull()
        if (!anonymousResult.isNullOrBlank()) {
            return anonymousResult
        }

        localIdentityStore.setIdentityMode(IdentityMode.LOCAL)
        return localIdentityStore.getOrCreateLocalId()
    }

    fun getCurrentFirebaseUid(): String? = authOrNull()?.currentUser?.uid

    fun hasCloudUser(): Boolean = authOrNull()?.currentUser != null

    fun isAnonymousSession(): Boolean = authOrNull()?.currentUser?.isAnonymous == true

    fun getCurrentUserId(): String {
        val firebaseUid = authOrNull()?.currentUser?.uid
        if (!firebaseUid.isNullOrBlank()) {
            localIdentityStore.setIdentityMode(
                if (isAnonymousSession()) IdentityMode.ANONYMOUS else IdentityMode.GOOGLE
            )
            return firebaseUid
        }

        localIdentityStore.setIdentityMode(IdentityMode.LOCAL)
        return localIdentityStore.getOrCreateLocalId()
    }

    fun getIdentityMode(): IdentityMode {
        val currentUser = authOrNull()?.currentUser
        if (currentUser != null) {
            return if (currentUser.isAnonymous) IdentityMode.ANONYMOUS else IdentityMode.GOOGLE
        }
        return localIdentityStore.getIdentityMode()
    }

    suspend fun signOutAndResetIdentity(): Unit = withContext(Dispatchers.IO) {
        val auth = authOrNull()
        if (auth != null) {
            runCatching {
                val user = auth.currentUser
                if (user?.isAnonymous == true) {
                    user.delete().await()
                }
            }
            auth.signOut()
        }
        localIdentityStore.resetIdentity()
        Unit
    }
}
