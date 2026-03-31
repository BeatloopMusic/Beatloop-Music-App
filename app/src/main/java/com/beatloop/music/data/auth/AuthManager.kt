package com.beatloop.music.data.auth

import android.app.Activity
import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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

        return@withContext runCatching {
            val provider = OAuthProvider.newBuilder("google.com")
                .setScopes(listOf("email", "profile"))
                .build()

            val authResult = auth.pendingAuthResult?.await()
                ?: auth.startActivityForSignInWithProvider(activity, provider).await()

            val uid = authResult.user?.uid
                ?: throw IllegalStateException("Google sign-in did not return a user")
            localIdentityStore.setIdentityMode(IdentityMode.GOOGLE)
            uid
        }
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
