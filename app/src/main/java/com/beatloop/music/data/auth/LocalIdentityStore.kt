package com.beatloop.music.data.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class IdentityMode {
    GOOGLE,
    ANONYMOUS,
    LOCAL
}

@Singleton
class LocalIdentityStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOrCreateLocalId(): String {
        val existing = prefs.getString(KEY_LOCAL_USER_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_LOCAL_USER_ID, newId).apply()
        return newId
    }

    fun getLocalIdOrNull(): String? = prefs.getString(KEY_LOCAL_USER_ID, null)

    fun setIdentityMode(mode: IdentityMode) {
        prefs.edit().putString(KEY_IDENTITY_MODE, mode.name).apply()
    }

    fun getIdentityMode(): IdentityMode {
        val stored = prefs.getString(KEY_IDENTITY_MODE, IdentityMode.LOCAL.name)
        return runCatching { IdentityMode.valueOf(stored.orEmpty()) }
            .getOrDefault(IdentityMode.LOCAL)
    }

    fun resetIdentity(): String {
        val newId = UUID.randomUUID().toString()
        prefs.edit()
            .putString(KEY_LOCAL_USER_ID, newId)
            .putString(KEY_IDENTITY_MODE, IdentityMode.LOCAL.name)
            .apply()
        return newId
    }

    companion object {
        private const val PREFS_NAME = "beatloop_identity"
        private const val KEY_LOCAL_USER_ID = "local_user_id"
        private const val KEY_IDENTITY_MODE = "identity_mode"
    }
}
