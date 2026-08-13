package com.android.alftendev.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.android.alftendev.MyApplication
import java.util.concurrent.atomic.AtomicBoolean

fun ComposeView.bindNotificationAgent() {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent { MaterialTheme { NotificationAgentScreen() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationAgentScreen() {
    val context = LocalContext.current
    val store = remember { AgentSessionStore(context) }
    val settings = remember { AgentSettings(context) }
    val service = remember { NotificationAgentService(settings) }
    var sessions by remember { mutableStateOf(store.restore().ifEmpty { listOf(AgentSession()) }) }
    var activeId by remember { mutableStateOf(sessions.first().id) }
    var input by remember { mutableStateOf("") }
    var showSessions by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
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
    fun send() {
        val question = input.trim()
        if (question.isBlank() || running) return
        input = ""
        val user = AgentMessage(role = AgentRole.USER, content = question)
        updateActive {
            it.copy(
                title = if (it.messages.isEmpty()) question.take(30) else it.title,
                messages = it.messages + user,
                updatedAt = System.currentTimeMillis()
            )
        }
        running = true
        cancelled.set(false)
        val conversation = active.messages + user
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
                    (context as? android.app.Activity)?.runOnUiThread {
                        error = throwable.stackTraceToString()
                        running = false
                    }
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知智能体") },
                navigationIcon = {
                    IconButton(onClick = { showSessions = !showSessions }) {
                        Text(if (showSessions) "返回" else "会话")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val session = AgentSession()
                        persist(listOf(session) + sessions)
                        activeId = session.id
                    }) { Text("新建") }
                    IconButton(onClick = { showSettings = true }) {
                        Text("设置")
                    }
                }
            )
        }
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (showSessions) {
                SessionList(
                    sessions = sessions,
                    activeId = activeId,
                    onSelect = { activeId = it; showSessions = false },
                    onDelete = { id ->
                        if (sessions.size > 1) {
                            val remaining = sessions.filterNot { it.id == id }
                            persist(remaining)
                            if (activeId == id) activeId = remaining.first().id
                        }
                    }
                )
            }
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(active.messages, key = AgentMessage::id) { message ->
                        AgentBubble(message)
                    }
                    if (running) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("正在查找通知并生成回答…")
                                TextButton(onClick = { cancelled.set(true); running = false }) { Text("停止") }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        enabled = !running,
                        placeholder = { Text("询问通知历史…") },
                        maxLines = 5
                    )
                    IconButton(onClick = ::send, enabled = input.isNotBlank() && !running) {
                        Text("发送")
                    }
                }
            }
        }
    }
    if (showSettings) AgentSettingsDialog(settings, onDismiss = { showSettings = false }, onError = { error = it })
    error?.let { details ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("智能体错误") },
            text = { Text(details, maxLines = 8, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = { copy(context, details) }) { Text("复制完整错误信息") }
            },
            dismissButton = { TextButton(onClick = { error = null }) { Text("关闭") } }
        )
    }
}

@Composable
private fun SessionList(sessions: List<AgentSession>, activeId: String, onSelect: (String) -> Unit, onDelete: (String) -> Unit) {
    LazyColumn(Modifier.width(180.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(sessions, key = AgentSession::id) { session ->
            Card(onClick = { onSelect(session.id) }) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(session.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (session.id == activeId) FontWeight.Bold else FontWeight.Normal)
                    IconButton(onClick = { onDelete(session.id) }) { Text("删除") }
                }
            }
        }
    }
}

@Composable
private fun AgentBubble(message: AgentMessage) {
    var expanded by remember { mutableStateOf(false) }
    val isUser = message.role == AgentRole.USER
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(Modifier.fillMaxWidth(if (isUser) 0.8f else 1f)) {
            Column(Modifier.padding(12.dp)) {
                if (!isUser && !message.reasoning.isNullOrBlank()) {
                    AssistChip(onClick = { expanded = !expanded }, label = { Text(if (expanded) "收起过程" else "展开思考过程") })
                    if (expanded) {
                        Text(message.reasoning, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                MarkdownText(message.content)
            }
        }
    }
}

@Composable
private fun MarkdownText(markdown: String) {
    val blocks = markdown.split("```")
    blocks.forEachIndexed { index, block ->
        if (index % 2 == 0) {
            Text(markdownAnnotated(block))
        } else {
            val code = block.substringAfter('\n', block)
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)
                .horizontalScroll(rememberScrollState())) {
                Text(code, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun markdownAnnotated(markdown: String): AnnotatedString = buildAnnotatedString {
    markdown.lines().forEachIndexed { index, line ->
        val normalized = line.removePrefix("# ").removePrefix("## ").removePrefix("- ").removePrefix("* ")
        if (line.startsWith("#")) pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
        append(normalized)
        if (line.startsWith("#")) pop()
        if (index < markdown.lines().lastIndex) append('\n')
    }
}

@Composable
private fun AgentSettingsDialog(settings: AgentSettings, onDismiss: () -> Unit, onError: (String) -> Unit) {
    val initial = remember { settings.configuration() }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var model by remember { mutableStateOf(initial.model) }
    var token by remember { mutableStateOf(initial.token) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("智能体设置") },
        text = {
            Column {
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("服务地址") })
                OutlinedTextField(model, { model = it }, label = { Text("模型") })
                OutlinedTextField(token, { token = it }, label = { Text("API Token") })
            }
        },
        confirmButton = {
            Button(onClick = {
                runCatching { settings.save(baseUrl, model, token) }
                    .onSuccess { onDismiss() }
                    .onFailure { onError(it.stackTraceToString()) }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("智能体错误详情", text))
}
