package com.android.alftendev.agent

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.android.alftendev.R

class AgentSessionMenuAdapter(
    context: Context,
    sessions: List<AgentSession>,
    private val activeId: String
) : ArrayAdapter<AgentSession>(context, 0, sessions) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_agent_session, parent, false)
        val session = getItem(position) ?: return view
        (view as TextView).text = session.title
        view.isActivated = session.id == activeId
        return view
    }
}
