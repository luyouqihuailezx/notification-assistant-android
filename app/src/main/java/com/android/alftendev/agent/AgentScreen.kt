package com.android.alftendev.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.composes.utils.Typography
import com.android.alftendev.utils.MySharedPref
import java.util.concurrent.atomic.AtomicBoolean

fun ComposeView.bindNotificationAgent(controller: AgentController) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent { AgentContentTheme { NotificationAgentScreen(controller) } }
}

@Composable
private fun AgentContentTheme(content: @Composable () -> Unit) {
    val dark = when (MySharedPref.getThemeOptions()) {
        2 -> true
        3 -> false
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) {
        darkColorScheme(
            primary = colorResource(R.color.blue_dark),
            onPrimary = Color.White,
            secondary = colorResource(R.color.blue),
            secondaryContainer = colorResource(R.color.background_dark_cardview),
            background = colorResource(R.color.background_dark),
            onBackground = colorResource(R.color.text_color_white),
            surface = colorResource(R.color.background_dark),
            onSurface = colorResource(R.color.text_color_white),
            surfaceVariant = colorResource(R.color.background_dark_cardview)
        )
    } else {
        lightColorScheme(
            primary = colorResource(R.color.blue_dark),
            onPrimary = Color.White,
            secondary = colorResource(R.color.blue),
            secondaryContainer = colorResource(R.color.cyan_light),
            background = colorResource(R.color.background),
            onBackground = colorResource(R.color.text_color_black),
            surface = colorResource(R.color.background),
            onSurface = colorResource(R.color.text_color_black),
            surfaceVariant = colorResource(R.color.background_light_cardview)
        )
    }
    MaterialTheme(colorScheme = colors, typography = Typography, content = content)
}

@Composable
private fun NotificationAgentScreen(controller: AgentController) {
    val context = LocalContext.current
    val service = remember { NotificationAgentService(AgentSettings(context)) }
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
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                enabled = !running,
                placeholder = { Text("询问通知历史") },
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = ::send, enabled = input.isNotBlank() && !running) { Text("发送") }
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
    markdown.split("```").forEachIndexed { index, block ->
        if (index % 2 == 0) Text(markdownAnnotated(block))
        else {
            val code = block.substringAfter('\n', block)
            Surface(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    code,
                    Modifier.padding(10.dp).horizontalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace
                )
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
