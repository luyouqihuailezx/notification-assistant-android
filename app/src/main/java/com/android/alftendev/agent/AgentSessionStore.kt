package com.android.alftendev.agent

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray

class AgentSessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val encryptedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            ENCRYPTED_PREFERENCES,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    private val legacyPreferences = appContext.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)

    fun restore(): List<AgentSession> = runCatching {
        val encrypted = encryptedPreferences.getString(SESSIONS_KEY, null)
        val serialized = encrypted ?: legacyPreferences.getString(SESSIONS_KEY, "[]")
        val sessions = JSONArray(serialized).let { array ->
            List(array.length()) { AgentSession.fromJson(array.getJSONObject(it)) }
        }
        if (encrypted == null && sessions.isNotEmpty()) persist(sessions)
        sessions
    }.getOrElse { error ->
        Log.e(TAG, "恢复智能体会话失败", error)
        emptyList()
    }

    fun persist(sessions: List<AgentSession>) {
        val serialized = JSONArray(sessions.map(AgentSession::toJson)).toString()
        check(encryptedPreferences.edit().putString(SESSIONS_KEY, serialized).commit()) {
            "保存智能体会话失败：加密存储写入未完成"
        }
        legacyPreferences.edit().remove(SESSIONS_KEY).apply()
    }

    companion object {
        private const val TAG = "AgentSessionStore"
        private const val LEGACY_PREFERENCES = "notification_agent_sessions"
        private const val ENCRYPTED_PREFERENCES = "notification_agent_sessions_v2"
        private const val SESSIONS_KEY = "sessions_v1"
    }
}
