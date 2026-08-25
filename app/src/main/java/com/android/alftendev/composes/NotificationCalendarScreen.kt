package com.android.alftendev.composes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.alftendev.MyApplication
import com.android.alftendev.utils.DBUtils
import com.android.alftendev.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class CalendarMode(val label: String) { MONTH("月"), WEEK("周"), DAY("日"), LIST("列表") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCalendarScreen(onOpen: (Long) -> Unit) {
    var anchor by remember { mutableStateOf(Date()) }
    var mode by remember { mutableStateOf(CalendarMode.MONTH) }
    var app by remember { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf<List<String>>(emptyList()) }
    var events by remember { mutableStateOf<List<DBUtils.CalendarEvent>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val refresh: () -> Unit = {
        loading = true
        MyApplication.executor.execute {
            try {
                val calendar = Calendar.getInstance().apply { time = anchor }
                val start = when (mode) {
                    CalendarMode.MONTH -> calendar.apply { set(Calendar.DAY_OF_MONTH, 1) }.time
                    CalendarMode.WEEK -> calendar.apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek) }.time
                    CalendarMode.DAY -> calendar.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.time
                    CalendarMode.LIST -> Date(anchor.time - 90L * 86400000L)
                }
                val end = when (mode) {
                    CalendarMode.MONTH -> Calendar.getInstance().apply { time = start; add(Calendar.MONTH, 1) }.time
                    CalendarMode.WEEK -> Date(start.time + 7L * 86400000L)
                    CalendarMode.DAY -> Date(start.time + 86400000L)
                    CalendarMode.LIST -> Date(anchor.time + 90L * 86400000L)
                }
                val loaded = DBUtils.calendarEvents(start, end, app)
                val available = DBUtils.availableDateApps()
                (MyApplication.application as android.app.Application).mainExecutor.execute {
                    events = loaded; apps = available; error = null; loading = false
                }
            } catch (t: Throwable) {
                val details = "加载日历失败：${t.localizedMessage}\n\n完整错误信息：${t.stackTraceToString()}"
                DBUtils.LOGGER.log(details)
                (MyApplication.application as android.app.Application).mainExecutor.execute { error = details; loading = false }
            }
        }
    }
    LaunchedEffect(anchor, mode, app) { refresh() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(SimpleDateFormat("yyyy年M月", Locale.CHINA).format(anchor)) }, actions = {
            TextButton(onClick = { anchor = Date() }) { Text("今天") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = { anchor = shift(anchor, mode, -1) }) { Text("‹") }
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) { CalendarMode.entries.forEachIndexed { index, item -> SegmentedButton(selected = item == mode, onClick = { mode = item }, shape = SegmentedButtonDefaults.itemShape(index, CalendarMode.entries.size), label = { Text(item.label) }) } }
                IconButton(onClick = { anchor = shift(anchor, mode, 1) }) { Text("›") }
            }
            if (apps.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded, { expanded = !expanded }, Modifier.padding(12.dp).fillMaxWidth()) {
                    OutlinedTextField(app ?: "全部应用", {}, readOnly = true, label = { Text("应用") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        DropdownMenuItem({ Text("全部应用") }, { app = null; expanded = false })
                        apps.forEach { name -> DropdownMenuItem({ Text(name) }, { app = name; expanded = false }) }
                    }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (mode) {
                CalendarMode.MONTH -> MonthGrid(anchor, events, onOpen)
                CalendarMode.WEEK, CalendarMode.DAY -> GroupedEvents(events, onOpen)
                CalendarMode.LIST -> EventList(events, onOpen)
            }
        }
    }
    error?.let { details -> CalendarErrorDialog(details) { error = null } }
}

@Composable
private fun CalendarErrorDialog(details: String, dismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(onDismissRequest = dismiss, title = { Text("日历加载失败") }, text = { Text(details, maxLines = 8, overflow = TextOverflow.Ellipsis) }, confirmButton = { Button(onClick = dismiss) { Text("关闭") } }, dismissButton = { TextButton(onClick = { (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("错误详情", details)) }) { Text("复制完整错误信息") } })
}

private fun shift(date: Date, mode: CalendarMode, amount: Int): Date = Calendar.getInstance().apply { time = date; add(if (mode == CalendarMode.MONTH) Calendar.MONTH else if (mode == CalendarMode.WEEK) Calendar.WEEK_OF_YEAR else Calendar.DAY_OF_YEAR, amount) }.time

@Composable private fun MonthGrid(anchor: Date, events: List<DBUtils.CalendarEvent>, onOpen: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { time = anchor; set(Calendar.DAY_OF_MONTH, 1) }
    val days = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    LazyColumn(Modifier.fillMaxSize().padding(8.dp)) { item { Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.weight(1f).padding(4.dp), style = MaterialTheme.typography.labelMedium) } } }; items((1..days).chunked(7)) { week -> Row(Modifier.fillMaxWidth()) { week.forEach { day -> val dayEvents = events.filter { Calendar.getInstance().apply { time = it.reference.resolvedDate ?: Date(0) }.get(Calendar.DAY_OF_MONTH) == day }; Column(Modifier.weight(1f).padding(2.dp).clickable { dayEvents.firstOrNull()?.let { onOpen(it.notificationId) } }) { Text(day.toString(), style = MaterialTheme.typography.labelMedium); dayEvents.take(2).forEach { Text("• ${it.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) } } } } } }
}

@Composable private fun GroupedEvents(events: List<DBUtils.CalendarEvent>, onOpen: (Long) -> Unit) { EventList(events.groupBy { SimpleDateFormat("M月d日", Locale.CHINA).format(it.reference.resolvedDate ?: Date()) }.flatMap { it.value }, onOpen) }

@Composable private fun EventList(events: List<DBUtils.CalendarEvent>, onOpen: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(events, key = { it.referenceId }) { event -> Card(Modifier.fillMaxWidth().clickable { onOpen(event.notificationId) }) { ListItem(headlineContent = { Text(event.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text("${event.app} · 提及：${event.reference.originalText}", maxLines = 1, overflow = TextOverflow.Ellipsis) }, overlineContent = { Text(event.reference.resolvedDate?.let(DateUtils::dateFormatterCompact) ?: "时间未明确") }) } } }
}
