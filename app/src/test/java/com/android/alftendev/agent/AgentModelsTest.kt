package com.android.alftendev.agent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AgentModelsTest {
    @Test
    fun sessionRoundTripPreservesMessagesAndReasoning() {
        val original = AgentSession(
            title = "查看快递通知",
            messages = listOf(
                AgentMessage(role = AgentRole.USER, content = "查一下快递"),
                AgentMessage(role = AgentRole.ASSISTANT, content = "找到一条", reasoning = "先按关键词查询")
            )
        )

        val restored = AgentSession.fromJson(JSONObject(original.toJson().toString()))

        assertEquals(original.title, restored.title)
        assertEquals(2, restored.messages.size)
        assertEquals("先按关键词查询", restored.messages.last().reasoning)
        assertFalse(restored.messages.last().content.isBlank())
    }
}
