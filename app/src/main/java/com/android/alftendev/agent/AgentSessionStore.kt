package com.android.alftendev.agent

import android.content.Context
import org.json.JSONArray

class AgentSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun restore(): List<AgentSession> = runCatching {
        val array = JSONArray(preferences.getString(SESSIONS_KEY, "[]"))
        List(array.length()) { AgentSession.fromJson(array.getJSONObject(it)) }
    }.getOrElse { error ->
        android.util.Log.e(TAG, "恢复智能体会话失败", error)
        emptyList()
    }

    fun persist(sessions: List<AgentSession>) {
        val serialized = JSONArray(sessions.map(AgentSession::toJson)).toString()
        check(preferences.edit().putString(SESSIONS_KEY, serialized).commit()) {
            "保存智能体会话失败：SharedPreferences 写入未完成"
        }
    }

    companion object {
        private const val TAG = "AgentSessionStore"
        private const val PREFERENCES = "notification_agent_sessions"
        private const val SESSIONS_KEY = "sessions_v1"
    }
}
