package com.android.alftendev.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.alftendev.MyApplication
import com.android.alftendev.composes.utils.MyTheme
import java.util.concurrent.atomic.AtomicBoolean

fun ComposeView.bindNotificationAgent() {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent { MyTheme { NotificationAgentScreen() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationAgentScreen() {
    val context = LocalContext.current
    val store = remember { AgentSessionStore(context) }
    val service = remember { NotificationAgentService(AgentSettings(context)) }
    var sessions by remember { mutableStateOf(store.restore().ifEmpty { listOf(AgentSession()) }) }
    var activeId by remember { mutableStateOf(sessions.first().id) }
    var input by remember { mutableStateOf("") }
    var sessionsOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    val cancelled = remember { AtomicBoolean(false) }
    val active = sessions.firstOrNull { it.id == activeId } ?: sessions.first()

    fun persist(updated: List<AgentSession>) {
        sessions = updated.sortedByDescending { it.updatedAt }
        store.persist(sessions)
    }
    fun updateActive(transform: (AgentSession) -> AgentSession) {
        persist(sessions.map { if (it.id == activeId) transform(it) else it })
    }
    fun createSession() {
        val session = AgentSession()
        persist(listOf(session) + sessions)
        activeId = session.id
        sessionsOpen = false
    }
    fun send() {
        val question = input.trim()
        if (question.isBlank() || running) return
        val userMessage = AgentMessage(role = AgentRole.USER, content = question)
        val conversation = active.messages + userMessage
        input = ""
        updateActive {
            it.copy(
                title = if (it.messages.isEmpty()) question.take(30) else it.title,
                messages = conversation,
                updatedAt = System.currentTimeMillis()
            )
        }
        running = true
        cancelled.set(false)
        MyApplication.executor.execute {
            runCatching { service.answer(conversation) }
                .onSuccess { answer ->
                    if (!cancelled.get()) {
                        (context as? android.app.Activity)?.runOnUiThread {
                            updateActive {
                                it.copy(
                                    messages = it.messages + AgentMessage(
                                        role = AgentRole.ASSISTANT,
                                        content = answer.content,
                                        reasoning = answer.reasoning
                                    ),
                                    updatedAt = System.currentTimeMillis()
                                )
                            }
                            running = false
                        }
                    }
                }
                .onFailure { throwable ->
                    Log.e("NotificationAgent", "智能体调用失败", throwable)
                    if (!cancelled.get()) {
                        (context as? android.app.Activity)?.runOnUiThread {
                            error = throwable.stackTraceToString()
                            running = false
                        }
                    }
                }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (sessionsOpen) {
            SessionList(
                sessions = sessions,
                activeId = activeId,
                onSelect = { activeId = it; sessionsOpen = false },
                onDelete = { id ->
                    if (sessions.size > 1) {
                        val remaining = sessions.filterNot { it.id == id }
                        persist(remaining)
                        if (activeId == id) activeId = remaining.first().id
                    }
                }
            )
            HorizontalDivider()
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (active.messages.isEmpty()) {
                item {
                    Text(
                        "输入问题，智能体会在本机通知历史中查找相关内容。",
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            items(active.messages, key = AgentMessage::id) { AgentBubble(it) }
            if (running) item { LoadingReply { cancelled.set(true); running = false } }
        }
        Composer(
            input = input,
            running = running,
            sessionsOpen = sessionsOpen,
            onChange = { input = it },
            onSend = ::send,
            onSessionsToggle = { sessionsOpen = !sessionsOpen },
            onNewSession = ::createSession
        )
    }
    error?.let { ErrorDialog(it, onDismiss = { error = null }) }
}

@Composable
private fun SessionList(sessions: List<AgentSession>, activeId: String, onSelect: (String) -> Unit, onDelete: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(sessions, key = AgentSession::id) { session ->
            val selected = session.id == activeId
            Card(
                onClick = { onSelect(session.id) },
                colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(session.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { onDelete(session.id) }) { Text("删除") }
                }
            }
        }
    }
}

@Composable
private fun AgentBubble(message: AgentMessage) {
    var expanded by remember { mutableStateOf(false) }
    val isUser = message.role == AgentRole.USER
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.82f else 1f),
            colors = CardDefaults.cardColors(containerColor = if (isUser) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(12.dp)) {
                if (!isUser && !message.reasoning.isNullOrBlank()) {
                    AssistChip(onClick = { expanded = !expanded }, label = { Text(if (expanded) "收起过程" else "查看过程") })
                    if (expanded) Text(message.reasoning, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                MarkdownText(message.content)
            }
        }
    }
}

@Composable
private fun LoadingReply(onStop: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text("正在查找通知…", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onStop) { Text("停止") }
    }
}

@Composable
private fun Composer(
    input: String,
    running: Boolean,
    sessionsOpen: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onSessionsToggle: () -> Unit,
    onNewSession: () -> Unit
) {
    Surface(
        Modifier.fillMaxWidth().padding(8.dp),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(4.dp)) {
            Row {
                TextButton(onClick = onSessionsToggle) { Text(if (sessionsOpen) "收起会话" else "会话") }
                TextButton(onClick = onNewSession) { Text("新建会话") }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onChange,
                    modifier = Modifier.weight(1f),
                    enabled = !running,
                    placeholder = { Text("询问通知历史") },
                    maxLines = 4
                )
                Spacer(Modifier.width(6.dp))
                Button(onClick = onSend, enabled = input.isNotBlank() && !running) { Text("发送") }
            }
        }
    }
}

@Composable
private fun MarkdownText(markdown: String) {
    markdown.split("```").forEachIndexed { index, block ->
        if (index % 2 == 0) Text(markdownAnnotated(block))
        else {
            val code = block.substringAfter('\n', block)
            Surface(Modifier.fillMaxWidth().padding(vertical = 6.dp), color = MaterialTheme.colorScheme.surface) {
                Text(code, Modifier.padding(10.dp).horizontalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun markdownAnnotated(markdown: String): AnnotatedString = buildAnnotatedString {
    val lines = markdown.lines()
    lines.forEachIndexed { index, line ->
        val normalized = line.removePrefix("# ").removePrefix("## ").removePrefix("- ").removePrefix("* ")
        if (line.startsWith("#")) pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
        append(normalized)
        if (line.startsWith("#")) pop()
        if (index < lines.lastIndex) append('\n')
    }
}

@Composable
private fun ErrorDialog(details: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("智能体无法完成请求") },
        text = { Text(details, maxLines = 8, overflow = TextOverflow.Ellipsis) },
        confirmButton = { TextButton(onClick = { copy(context, details) }) { Text("复制完整错误信息") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("智能体错误详情", text))
}
