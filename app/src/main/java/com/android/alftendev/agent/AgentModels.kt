package com.android.alftendev.agent

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AgentRole { USER, ASSISTANT }

data class AgentMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: AgentRole,
    val content: String,
    val reasoning: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("role", role.name)
        put("content", content)
        put("reasoning", reasoning)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject) = AgentMessage(
            id = json.optString("id", UUID.randomUUID().toString()),
            role = AgentRole.valueOf(json.optString("role", AgentRole.USER.name)),
            content = json.optString("content"),
            reasoning = json.optString("reasoning").takeIf { it.isNotBlank() },
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }
}

data class AgentSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新会话",
    val messages: List<AgentMessage> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("updatedAt", updatedAt)
        put("messages", JSONArray(messages.map(AgentMessage::toJson)))
    }

    companion object {
        fun fromJson(json: JSONObject) = AgentSession(
            id = json.optString("id", UUID.randomUUID().toString()),
            title = json.optString("title", "新会话"),
            messages = json.optJSONArray("messages")?.let { messages ->
                List(messages.length()) { AgentMessage.fromJson(messages.getJSONObject(it)) }
            } ?: emptyList(),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}
