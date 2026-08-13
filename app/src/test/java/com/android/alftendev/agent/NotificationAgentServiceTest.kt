package com.android.alftendev.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationAgentServiceTest {
    @Test fun endpointDoesNotForceV1() {
        assertEquals("https://example.test/models", NotificationAgentService.endpoint("https://example.test", "/models"))
        assertEquals("https://example.test/v1/chat/completions", NotificationAgentService.endpoint("https://example.test/v1/", "chat/completions"))
    }
}
