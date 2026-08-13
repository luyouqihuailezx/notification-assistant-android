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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import java.util.concurrent.atomic.AtomicBoolean

fun ComposeView.bindNotificationAgent() {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        val colors = if (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        ) AgentDarkColors else AgentLightColors
        MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography) {
            NotificationAgentScreen()
        }
    }
}

private val AgentLightColors = lightColorScheme(
    primary = Color(0xFF006A64),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9EF2E9),
    onPrimaryContainer = Color(0xFF00201E),
    secondaryContainer = Color(0xFFD7E7E3),
    surface = Color(0xFFF8FAF8),
    surfaceVariant = Color(0xFFDCE5E2),
    background = Color(0xFFF8FAF8)
)

private val AgentDarkColors = darkColorScheme(
    primary = Color(0xFF83D6CE),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504B),
    onPrimaryContainer = Color(0xFF9EF2E9),
    secondaryContainer = Color(0xFF3C4A48),
    surface = Color(0xFF101413),
    surfaceVariant = Color(0xFF404947),
    background = Color(0xFF101413)
)

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
        input = ""
        val userMessage = AgentMessage(role = AgentRole.USER, content = question)
        val conversation = active.messages + userMessage
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("通知智能体", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("基于本机通知历史", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    TextButton(onClick = { sessionsOpen = !sessionsOpen }) {
                        Text(if (sessionsOpen) "收起会话" else "会话")
                    }
                    TextButton(onClick = ::createSession) { Text("新建") }
                }
            )
        }
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (sessionsOpen) {
                SessionRail(
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
                HorizontalDivider(Modifier.width(1.dp).height(1.dp))
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                if (active.messages.isEmpty()) {
                    WelcomeState(onPrompt = { input = it })
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(active.messages, key = AgentMessage::id) { AgentBubble(it) }
                        if (running) item { LoadingReply(onStop = { cancelled.set(true); running = false }) }
                    }
                }
                Composer(input = input, running = running, onChange = { input = it }, onSend = ::send)
            }
        }
    }
    error?.let { details -> ErrorDialog(details, onDismiss = { error = null }) }
}

@Composable
private fun WelcomeState(onPrompt: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
            Text("本机", Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        Text("从通知历史开始提问", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("智能体只会查询保存在此设备中的通知。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        FilledTonalButton(onClick = { onPrompt("我今天收到了哪些重要通知？") }) {
            Text("总结今天的重要通知")
        }
        FilledTonalButton(onClick = { onPrompt("查找最近的快递或外卖通知") }) {
            Text("查找快递与外卖")
        }
    }
}

@Composable
private fun SessionRail(sessions: List<AgentSession>, activeId: String, onSelect: (String) -> Unit, onDelete: (String) -> Unit) {
    LazyColumn(
        Modifier.width(248.dp).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("会话记录", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(sessions, key = AgentSession::id) { session ->
            val selected = session.id == activeId
            Card(
                onClick = { onSelect(session.id) },
                colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(session.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    TextButton(onClick = { onDelete(session.id) }) { Text("删除") }
                }
            }
        }
    }
}

@Composable
private fun AgentBubble(message: AgentMessage) {
    var reasoningExpanded by remember { mutableStateOf(false) }
    val user = message.role == AgentRole.USER
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Text(if (user) "你" else "智能体", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp))
        Card(
            modifier = Modifier.fillMaxWidth(if (user) 0.84f else 1f),
            colors = CardDefaults.cardColors(containerColor = if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.padding(14.dp)) {
                if (!user && !message.reasoning.isNullOrBlank()) {
                    AssistChip(onClick = { reasoningExpanded = !reasoningExpanded }, label = { Text(if (reasoningExpanded) "收起过程" else "查看思考过程") })
                    if (reasoningExpanded) Text(message.reasoning, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                MarkdownText(message.content)
            }
        }
    }
}

@Composable
private fun LoadingReply(onStop: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("正在查找通知…", Modifier.weight(1f))
            TextButton(onClick = onStop) { Text("停止") }
        }
    }
}

@Composable
private fun Composer(input: String, running: Boolean, onChange: (String) -> Unit, onSend: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, shape = MaterialTheme.shapes.extraLarge) {
        Row(Modifier.padding(start = 14.dp, top = 4.dp, end = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                enabled = !running,
                placeholder = { Text("询问通知历史") },
                maxLines = 4,
                singleLine = false
            )
            Spacer(Modifier.width(6.dp))
            Button(onClick = onSend, enabled = input.isNotBlank() && !running, shape = MaterialTheme.shapes.large) { Text("发送") }
        }
    }
}

@Composable
private fun MarkdownText(markdown: String) {
    markdown.split("```").forEachIndexed { index, block ->
        if (index % 2 == 0) Text(markdownAnnotated(block))
        else {
            val code = block.substringAfter('\n', block)
            Surface(Modifier.fillMaxWidth().padding(vertical = 6.dp), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Text(code, Modifier.padding(12.dp).horizontalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace)
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
