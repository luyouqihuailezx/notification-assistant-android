package com.android.alftendev.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun NotificationAgentScreen(controller: AgentController) {
    val context = LocalContext.current
    val service = remember { NotificationAgentService(AgentProviderSettings(context)) }
    var revision by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    val cancelled = remember { AtomicBoolean(false) }
    val listState = rememberLazyListState()
    DisposableEffect(controller) {
        val unsubscribe = controller.observe { revision++ }
        onDispose { unsubscribe() }
    }
    @Suppress("UNUSED_EXPRESSION")
    revision
    val active = controller.active

    fun send() {
        val question = input.trim()
        if (question.isBlank() || running) return
        val userMessage = AgentMessage(role = AgentRole.USER, content = question)
        val conversation = active.messages + userMessage
        input = ""
        controller.updateActive {
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
                            controller.updateActive {
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

    LaunchedEffect(active.messages.size, running, revision) {
        val lastIndex = active.messages.lastIndex + if (running) 1 else 0
        if (lastIndex >= 0) listState.scrollToItem(lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(active.messages, key = AgentMessage::id) { AgentBubble(it) }
            if (running) item { LoadingReply { cancelled.set(true); running = false } }
        }
        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("询问通知历史") },
                    maxLines = 5,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { send() })
                )
                Spacer(Modifier.width(8.dp))
                if (running) {
                    FilledTonalButton(onClick = { cancelled.set(true); running = false }) {
                        Text("停止")
                    }
                } else {
                    Button(onClick = ::send, enabled = input.isNotBlank()) { Text("发送") }
                }
            }
        }
    }
    error?.let { ErrorDialog(it, onDismiss = { error = null }) }
}

@Composable
private fun AgentBubble(message: AgentMessage) {
    var expanded by remember { mutableStateOf(false) }
    val isUser = message.role == AgentRole.USER
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.82f else 1f),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(12.dp)) {
                if (!isUser && !message.reasoning.isNullOrBlank()) {
                    AssistChip(
                        onClick = { expanded = !expanded },
                        label = { Text(if (expanded) "收起过程" else "查看过程") }
                    )
                    if (expanded) {
                        Text(
                            message.reasoning,
                            Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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
        Text("正在查找通知…", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        TextButton(onClick = onStop) { Text("停止") }
    }
}

@Composable
private fun MarkdownText(markdown: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var inCodeBlock = false
        val codeLines = mutableListOf<String>()

        @Composable
        fun flushCodeBlock() {
            if (codeLines.isEmpty()) return
            Surface(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    codeLines.joinToString("\n"),
                    Modifier.padding(10.dp).horizontalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace
                )
            }
            codeLines.clear()
        }

        markdown.lines().forEach { line ->
            if (line.startsWith("```")) {
                if (inCodeBlock) flushCodeBlock()
                inCodeBlock = !inCodeBlock
                return@forEach
            }
            if (inCodeBlock) {
                codeLines += line
                return@forEach
            }

            when {
                line.matches(Regex("^\\s{0,3}([-*_])(?:\\s*\\1){2,}\\s*$")) -> {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                line.startsWith("> ") -> Text(
                    markdownAnnotated(line.removePrefix("> ")),
                    Modifier.padding(start = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                line.startsWith("### ") -> MarkdownHeading(line.removePrefix("### "), MaterialTheme.typography.titleMedium)
                line.startsWith("## ") -> MarkdownHeading(line.removePrefix("## "), MaterialTheme.typography.titleLarge)
                line.startsWith("# ") -> MarkdownHeading(line.removePrefix("# "), MaterialTheme.typography.headlineSmall)
                line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> MarkdownListItem("•", line.drop(2))
                line.matches(Regex("^\\d+[.)]\\s+.*$")) -> {
                    val markerEnd = line.indexOfFirst { it == '.' || it == ')' } + 1
                    MarkdownListItem(line.take(markerEnd), line.drop(markerEnd).trimStart())
                }
                else -> Text(markdownAnnotated(line), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (inCodeBlock) flushCodeBlock()
    }
}

@Composable
private fun MarkdownHeading(content: String, style: androidx.compose.ui.text.TextStyle) {
    Text(markdownAnnotated(content), style = style, fontWeight = FontWeight.Bold)
}

@Composable
private fun MarkdownListItem(marker: String, content: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(marker, Modifier.width(24.dp), fontWeight = FontWeight.Bold)
        Text(markdownAnnotated(content), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun markdownAnnotated(markdown: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < markdown.length) {
        val marker = listOf("**", "__", "~~", "`", "*", "_")
            .firstOrNull { markdown.startsWith(it, index) }
        if (marker == null) {
            append(markdown[index++])
            continue
        }
        val closingIndex = markdown.indexOf(marker, index + marker.length)
        if (closingIndex == -1) {
            append(markdown[index++])
            continue
        }
        val content = markdown.substring(index + marker.length, closingIndex)
        val style = when (marker) {
            "**", "__" -> SpanStyle(fontWeight = FontWeight.Bold)
            "~~" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            "`" -> SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = Color(0x22000000)
            )
            else -> SpanStyle(fontStyle = FontStyle.Italic)
        }
        pushStyle(style)
        append(content)
        pop()
        index = closingIndex + marker.length
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
