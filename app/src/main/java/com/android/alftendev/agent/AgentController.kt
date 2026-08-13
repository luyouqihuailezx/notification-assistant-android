package com.android.alftendev.agent

import android.content.Context

class AgentController(context: Context) {
    private val store = AgentSessionStore(context)
    private val listeners = mutableListOf<() -> Unit>()

    var sessions: List<AgentSession> = store.restore().ifEmpty { listOf(AgentSession()) }
        private set
    var activeId: String = sessions.first().id
        private set

    val active: AgentSession
        get() = sessions.firstOrNull { it.id == activeId } ?: sessions.first()

    fun observe(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners.remove(listener) }
    }

    fun createSession() {
        val session = AgentSession()
        activeId = session.id
        persist(listOf(session) + sessions)
    }

    fun switchTo(id: String) {
        if (sessions.none { it.id == id } || activeId == id) return
        activeId = id
        notifyChanged()
    }

    fun deleteSession(id: String) {
        val remaining = sessions.filterNot { it.id == id }
        if (remaining.isEmpty()) {
            val session = AgentSession()
            activeId = session.id
            persist(listOf(session))
            return
        }
        if (activeId == id) {
            activeId = remaining.first().id
        }
        persist(remaining)
    }

    fun updateActive(transform: (AgentSession) -> AgentSession) {
        persist(sessions.map { if (it.id == activeId) transform(it) else it })
    }

    private fun persist(updated: List<AgentSession>) {
        sessions = updated.sortedByDescending { it.updatedAt }
        store.persist(sessions)
        notifyChanged()
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it() }
    }
}
