package com.android.alftendev.agent

import android.content.Context

/** Compatibility wrapper for existing callers. */
class AgentSettings(context: Context) {
    private val delegate = AgentProviderSettings(context)
    fun configuration() = delegate.configuration()
    fun save(baseUrl: String, model: String, token: String) = delegate.save(delegate.configuration().useDeepSeek, baseUrl, model, token)
}
