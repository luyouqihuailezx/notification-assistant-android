package com.android.alftendev.composes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.alftendev.MyApplication
import com.android.alftendev.utils.DBUtils
import com.android.alftendev.utils.DateUtils
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private enum class CalendarMode(val label: String) {
    MONTH("月"), WEEK("周"), DAY("日"), LIST("列表")
}

private val AppPalette = listOf(
    Color(0xFF007AFF),
    Color(0xFF34C759),
    Color(0xFFFF9500),
    Color(0xFFFF2D55),
    Color(0xFFAF52DE),
    Color(0xFF5AC8FA)
)

private val SnappyTween = tween<IntOffset>(220)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCalendarScreen(modifier: Modifier = Modifier, onOpen: (Long) -> Unit) {
    var anchor by remember { mutableStateOf(Date()) }
    var mode by remember { mutableStateOf(CalendarMode.MONTH) }
    var app by remember { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf<List<String>>(emptyList()) }
    var events by remember { mutableStateOf<List<DBUtils.CalendarEvent>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var navigationDirection by remember { mutableIntStateOf(1) }
    var dayTransitionIncludesIndex by remember { mutableStateOf(false) }

    val refresh: () -> Unit = {
        loading = true
        val currentAnchor = anchor
        val currentMode = mode
        val currentApp = app
        MyApplication.executor.execute {
            try {
                val start = rangeStart(currentAnchor, currentMode)
                val end = rangeEnd(currentAnchor, currentMode, start)
                val loaded = DBUtils.calendarEvents(start, end, currentApp)
                (MyApplication.application as android.app.Application).mainExecutor.execute {
                    events = loaded
                    error = null
                    loading = false
                }
                try {
                    val available = DBUtils.availableDateApps()
                    (MyApplication.application as android.app.Application).mainExecutor.execute {
                        apps = available
                    }
                } catch (appError: Throwable) {
                    val details = "加载应用列表失败：${appError.localizedMessage}\n\n完整错误信息：${appError.stackTraceToString()}"
                    DBUtils.LOGGER.log(details)
                    (MyApplication.application as android.app.Application).mainExecutor.execute { error = details }
                }
            } catch (t: Throwable) {
                val details = "加载日历失败：${t.localizedMessage}\n\n完整错误信息：${t.stackTraceToString()}"
                DBUtils.LOGGER.log(details)
                (MyApplication.application as android.app.Application).mainExecutor.execute {
                    error = details
                    loading = false
                }
            }
        }
    }
    LaunchedEffect(anchor, mode, app) { refresh() }
    LaunchedEffect(Unit) {
        delay(2000)
        refresh()
    }

    val eventsByDay = remember(events) {
        events.groupBy { event ->
            event.reference.resolvedDate?.let(::dayKey) ?: Long.MIN_VALUE
        }
    }
    val eventsOnDay: (Date) -> List<DBUtils.CalendarEvent> = { day ->
        eventsByDay[dayKey(day)].orEmpty()
    }

    fun moveAnchor(amount: Int) {
        navigationDirection = amount
        if (mode == CalendarMode.DAY) {
            val candidate = addCalendarField(anchor, Calendar.DAY_OF_YEAR, amount)
            dayTransitionIncludesIndex = startOfWeek(anchor).time != startOfWeek(candidate).time
            anchor = candidate
        } else {
            anchor = shift(anchor, mode, amount)
        }
    }

    fun moveWeek(amount: Int) {
        navigationDirection = amount
        dayTransitionIncludesIndex = true
        anchor = addCalendarField(anchor, Calendar.WEEK_OF_YEAR, amount)
    }

    Column(modifier.fillMaxSize()) {
        CalendarHeader(
            title = titleFor(anchor, mode),
            mode = mode,
            app = app,
            apps = apps,
            onToday = { anchor = Date() },
            onMove = { moveAnchor(it) },
            onMode = { mode = it },
            onApp = { app = it }
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!loading && events.isEmpty()) {
            Text(
                "没有相关通知",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        Box(Modifier.weight(1f, fill = true).fillMaxWidth().clipToBounds()) {
            when (mode) {
                CalendarMode.DAY -> DayView(
                    anchor = anchor,
                    eventsOnDay = eventsOnDay,
                    navigationDirection = navigationDirection,
                    dayTransitionIncludesIndex = dayTransitionIncludesIndex,
                    onSelectDay = { day ->
                        dayTransitionIncludesIndex = false
                        anchor = day
                    },
                    onMoveDay = { moveAnchor(it) },
                    onMoveWeek = { moveWeek(it) },
                    onOpen = onOpen
                )
                else -> AnimatedContent(
                    targetState = PeriodSnapshot(mode, periodAnchor(mode, anchor)),
                    transitionSpec = { directionalTransition(navigationDirection) },
                    label = "calendar-period",
                    modifier = Modifier.fillMaxSize()
                ) { snapshot ->
                    when (snapshot.mode) {
                        CalendarMode.MONTH -> MonthGrid(
                            anchor = snapshot.anchor,
                            events = events,
                            eventsOnDay = eventsOnDay,
                            onSelectDay = { day ->
                                dayTransitionIncludesIndex = false
                                anchor = day
                                mode = CalendarMode.DAY
                            },
                            onShift = { moveAnchor(it) }
                        )
                        CalendarMode.WEEK -> WeekView(
                            anchor = snapshot.anchor,
                            eventsOnDay = eventsOnDay,
                            onShift = { moveAnchor(it) },
                            onOpen = onOpen
                        )
                        CalendarMode.LIST -> EventList(events, onOpen)
                        CalendarMode.DAY -> Unit
                    }
                }
            }
        }
    }
    error?.let { details -> CalendarErrorDialog(details) { error = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarHeader(
    title: String,
    mode: CalendarMode,
    app: String?,
    apps: List<String>,
    onToday: () -> Unit,
    onMove: (Int) -> Unit,
    onMode: (CalendarMode) -> Unit,
    onApp: (String?) -> Unit
) {
    var appMenu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onMove(-1) },
                modifier = Modifier.semantics { contentDescription = "上一个时间段" }
            ) {
                Text("‹", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
            OutlinedButton(
                onClick = onToday,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) { Text("今天") }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = { onMove(1) },
                modifier = Modifier.semantics { contentDescription = "下一个时间段" }
            ) {
                Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CalendarMode.entries.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = item == mode,
                    onClick = { onMode(item) },
                    shape = SegmentedButtonDefaults.itemShape(index, CalendarMode.entries.size),
                    label = { Text(item.label) }
                )
            }
        }
        if (apps.isNotEmpty()) {
            Box {
                TextButton(onClick = { appMenu = true }) {
                    Text(app ?: "全部应用")
                    Text(" ▾")
                }
                DropdownMenu(expanded = appMenu, onDismissRequest = { appMenu = false }) {
                    DropdownMenuItem({ Text("全部应用") }, { onApp(null); appMenu = false })
                    apps.forEach { name ->
                        DropdownMenuItem({ Text(name) }, { onApp(name); appMenu = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    anchor: Date,
    events: List<DBUtils.CalendarEvent>,
    eventsOnDay: (Date) -> List<DBUtils.CalendarEvent>,
    onSelectDay: (Date) -> Unit,
    onShift: (Int) -> Unit
) {
    val cells = remember(anchor) { monthCells(anchor) }
    val weekdays = remember { weekdayLabels() }
    Column(
        Modifier
            .fillMaxWidth()
            .periodSwipe(onShift)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            weekdays.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f)) {
                        if (day != null) {
                            DayCell(day, eventsOnDay(day), onSelectDay)
                        } else {
                            Spacer(Modifier.fillMaxWidth().height(72.dp))
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (events.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DayCell(day: Date, events: List<DBUtils.CalendarEvent>, onSelectDay: (Date) -> Unit) {
    val isToday = DateUtils.areDatesEqual(day, Date())
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier
            .alpha(if (events.isEmpty()) 0.42f else 1f)
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), shape)
            .then(
                if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            )
            .clickable { onSelectDay(day) }
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                dayOfMonth(day).toString(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            if (events.isNotEmpty()) {
                Text(
                    events.size.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        events.take(3).forEach { event -> EventBadge(event) }
    }
}

@Composable
private fun EventBadge(event: DBUtils.CalendarEvent) {
    val color = colorForApp(event.app)
    Text(
        event.title,
        fontSize = 9.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp)
    )
}

@Composable
private fun WeekView(
    anchor: Date,
    eventsOnDay: (Date) -> List<DBUtils.CalendarEvent>,
    onShift: (Int) -> Unit,
    onOpen: (Long) -> Unit
) {
    val days = remember(anchor) { daysInWeek(anchor) }
    Column(
        Modifier
            .fillMaxSize()
            .periodSwipe(onShift)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        days.forEach { day ->
            WeekDayRow(day, eventsOnDay(day), onOpen)
        }
    }
}

@Composable
private fun WeekDayRow(day: Date, events: List<DBUtils.CalendarEvent>, onOpen: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(wideDayTitle(day), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.weight(1f))
            Text(
                "${events.size} 条",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (events.isEmpty()) {
            Text("没有相关通知", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                events.forEach { event -> EventCard(event, onOpen) }
            }
        }
    }
}

@Composable
private fun EventCard(event: DBUtils.CalendarEvent, onOpen: (Long) -> Unit) {
    val color = colorForApp(event.app)
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .width(190.dp)
            .height(96.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, color.copy(alpha = 0.35f), shape)
            .clickable { onOpen(event.notificationId) }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Text(eventTimeLabel(event), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
        }
        Text(
            event.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${event.app} · 提及：${event.reference.originalText}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DayView(
    anchor: Date,
    eventsOnDay: (Date) -> List<DBUtils.CalendarEvent>,
    navigationDirection: Int,
    dayTransitionIncludesIndex: Boolean,
    onSelectDay: (Date) -> Unit,
    onMoveDay: (Int) -> Unit,
    onMoveWeek: (Int) -> Unit,
    onOpen: (Long) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.clipToBounds()) {
            AnimatedContent(
                targetState = startOfWeek(anchor).time,
                transitionSpec = {
                    if (dayTransitionIncludesIndex) directionalTransition(navigationDirection)
                    else EnterTransition.None togetherWith ExitTransition.None
                },
                label = "day-week-index"
            ) { weekStart ->
                WeekIndex(Date(weekStart), anchor, eventsOnDay, onSelectDay, onMoveWeek)
            }
        }
        Box(Modifier.weight(1f).clipToBounds().periodSwipe(onMoveDay)) {
            AnimatedContent(
                targetState = startOfDay(anchor).time,
                transitionSpec = {
                    if (dayTransitionIncludesIndex) EnterTransition.None togetherWith ExitTransition.None
                    else directionalTransition(navigationDirection)
                },
                label = "day-timeline",
                modifier = Modifier.fillMaxSize()
            ) { dayStart ->
                val day = Date(dayStart)
                DayTimeline(day, eventsOnDay(day), onOpen)
            }
        }
    }
}

@Composable
private fun WeekIndex(
    weekAnchor: Date,
    selected: Date,
    eventsOnDay: (Date) -> List<DBUtils.CalendarEvent>,
    onSelectDay: (Date) -> Unit,
    onMoveWeek: (Int) -> Unit
) {
    val days = remember(weekAnchor) { daysInWeek(weekAnchor) }
    Row(
        Modifier.fillMaxWidth().periodSwipe(onMoveWeek),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        days.forEach { day ->
            val isSelected = DateUtils.areDatesEqual(day, selected)
            val count = eventsOnDay(day).size
            val shape = RoundedCornerShape(8.dp)
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 62.dp)
                    .clip(shape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        shape
                    )
                    .clickable { onSelectDay(day) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Text(
                    shortWeekday(day),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    maxLines = 1
                )
                Text(
                    "${monthOf(day)}/${dayOfMonth(day)}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = contentColor,
                    maxLines = 1
                )
                Text(
                    "$count 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DayTimeline(day: Date, events: List<DBUtils.CalendarEvent>, onOpen: (Long) -> Unit) {
    val hours = remember(events, day) { visibleHours(events) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 2.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(wideDayTitle(day), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
        if (events.isEmpty()) {
            Text(
                "没有相关通知",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            hours.forEach { hour ->
                val hourEvents = events.filter { event ->
                    event.reference.resolvedDate?.let { hourOf(it) == hour } ?: false
                }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        String.format(Locale.CHINA, "%02d:00", hour),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(42.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                        hourEvents.forEach { event -> DayEventRow(event, onOpen) }
                    }
                }
            }
        }
        Spacer(Modifier.height(180.dp))
    }
}

@Composable
private fun DayEventRow(event: DBUtils.CalendarEvent, onOpen: (Long) -> Unit) {
    val color = colorForApp(event.app)
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(event.notificationId) }.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .background(color, CircleShape)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                eventTimeLabel(event),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                event.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (event.body.isNotBlank()) {
                Text(
                    event.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "${event.app} · 提及：${event.reference.originalText}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EventList(events: List<DBUtils.CalendarEvent>, onOpen: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(events, key = { it.referenceId }) { event ->
            EventRow(event, onOpen)
        }
    }
}

@Composable
private fun EventRow(event: DBUtils.CalendarEvent, onOpen: (Long) -> Unit) {
    val color = colorForApp(event.app)
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(event.notificationId) }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .background(color, CircleShape)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                eventTimeLabel(event),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                event.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${event.app} · 提及：${event.reference.originalText}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CalendarErrorDialog(details: String, dismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("日历加载失败") },
        text = { Text(details, maxLines = 8, overflow = TextOverflow.Ellipsis) },
        confirmButton = { Button(onClick = dismiss) { Text("关闭") } },
        dismissButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("错误详情", details))
            }) { Text("复制完整错误信息") }
        }
    )
}

private fun Modifier.periodSwipe(onShift: (Int) -> Unit): Modifier = composed {
    val current = rememberUpdatedState(onShift)
    pointerInput(Unit) {
        val threshold = 40.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = down.position
            var last = start
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull() ?: break
                last = change.position
                if (!change.pressed) break
            }
            val dx = last.x - start.x
            val dy = last.y - start.y
            if (abs(dx) > abs(dy) && abs(dx) > threshold) {
                current.value(if (dx < 0) 1 else -1)
            }
        }
    }
}

private fun directionalTransition(direction: Int) = if (direction > 0) {
    slideInHorizontally(SnappyTween) { it } togetherWith slideOutHorizontally(SnappyTween) { -it }
} else {
    slideInHorizontally(SnappyTween) { -it } togetherWith slideOutHorizontally(SnappyTween) { it }
}

private fun titleFor(date: Date, mode: CalendarMode): String = when (mode) {
    CalendarMode.MONTH -> SimpleDateFormat("yyyy年M月", Locale.CHINA).format(date)
    CalendarMode.WEEK -> "第 ${weekOfYear(date)} 周"
    CalendarMode.DAY -> SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(date)
    CalendarMode.LIST -> "相关通知"
}

private data class PeriodSnapshot(val mode: CalendarMode, val anchor: Date)

private fun periodAnchor(mode: CalendarMode, anchor: Date): Date = when (mode) {
    CalendarMode.MONTH -> startOfMonth(anchor)
    CalendarMode.WEEK -> startOfWeek(anchor)
    CalendarMode.DAY -> startOfDay(anchor)
    CalendarMode.LIST -> startOfDay(anchor)
}

private fun rangeStart(anchor: Date, mode: CalendarMode): Date = when (mode) {
    CalendarMode.MONTH -> startOfMonth(anchor)
    CalendarMode.WEEK, CalendarMode.DAY -> startOfWeek(anchor)
    CalendarMode.LIST -> addCalendarField(anchor, Calendar.MONTH, -3)
}

private fun rangeEnd(anchor: Date, mode: CalendarMode, start: Date): Date = when (mode) {
    CalendarMode.MONTH -> addCalendarField(start, Calendar.MONTH, 1)
    CalendarMode.WEEK, CalendarMode.DAY -> Date(start.time + 7L * 86_400_000L)
    CalendarMode.LIST -> addCalendarField(anchor, Calendar.MONTH, 3)
}

private fun shift(date: Date, mode: CalendarMode, amount: Int): Date {
    val field = when (mode) {
        CalendarMode.MONTH -> Calendar.MONTH
        CalendarMode.WEEK -> Calendar.WEEK_OF_YEAR
        CalendarMode.DAY, CalendarMode.LIST -> Calendar.DAY_OF_YEAR
    }
    return addCalendarField(date, field, amount)
}

private fun addCalendarField(date: Date, field: Int, amount: Int): Date =
    Calendar.getInstance().apply { time = date; add(field, amount) }.time

private fun startOfDay(date: Date): Date = Calendar.getInstance().apply {
    time = date
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.time

private fun startOfMonth(date: Date): Date = Calendar.getInstance().apply {
    time = startOfDay(date)
    set(Calendar.DAY_OF_MONTH, 1)
}.time

private fun startOfWeek(date: Date): Date {
    val cal = Calendar.getInstance().apply { time = startOfDay(date) }
    val diff = (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
    cal.add(Calendar.DAY_OF_YEAR, -diff)
    return cal.time
}

private fun daysInWeek(date: Date): List<Date> {
    val start = startOfWeek(date)
    return (0 until 7).map { addCalendarField(start, Calendar.DAY_OF_YEAR, it) }
}

private fun monthCells(anchor: Date): List<Date?> {
    val first = startOfMonth(anchor)
    val cal = Calendar.getInstance().apply { time = first }
    val leading = (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    return List(leading) { null } + (0 until daysInMonth).map { addCalendarField(first, Calendar.DAY_OF_YEAR, it) }
}

private fun weekdayLabels(): List<String> {
    val cal = Calendar.getInstance()
    val first = cal.firstDayOfWeek
    return (0 until 7).map { offset ->
        val day = ((first - 1 + offset) % 7) + 1
        cal.set(Calendar.DAY_OF_WEEK, day)
        cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.CHINA) ?: ""
    }
}

private fun weekOfYear(date: Date): Int = Calendar.getInstance().apply { time = date }.get(Calendar.WEEK_OF_YEAR)

private fun dayOfMonth(date: Date): Int = Calendar.getInstance().apply { time = date }.get(Calendar.DAY_OF_MONTH)

private fun monthOf(date: Date): Int = Calendar.getInstance().apply { time = date }.get(Calendar.MONTH) + 1

private fun hourOf(date: Date): Int = Calendar.getInstance().apply { time = date }.get(Calendar.HOUR_OF_DAY)

private fun wideDayTitle(date: Date): String = SimpleDateFormat("EEEE M月d日", Locale.CHINA).format(date)

private fun shortWeekday(date: Date): String = SimpleDateFormat("EEE", Locale.CHINA).format(date)

private fun eventTimeLabel(event: DBUtils.CalendarEvent): String =
    event.reference.resolvedDate?.let { SimpleDateFormat("HH:mm", Locale.CHINA).format(it) } ?: "时间未明确"

private fun visibleHours(events: List<DBUtils.CalendarEvent>): List<Int> =
    events.mapNotNull { it.reference.resolvedDate?.let(::hourOf) }.toSortedSet().toList()

private fun dayKey(date: Date): Long {
    val cal = Calendar.getInstance().apply { time = date }
    return cal.get(Calendar.YEAR) * 10_000L + (cal.get(Calendar.MONTH) + 1) * 100L + cal.get(Calendar.DAY_OF_MONTH)
}

private fun colorForApp(app: String): Color {
    var hash = 0L
    for (ch in app) hash = hash * 31L + ch.code
    return AppPalette[hash.mod(6L).toInt()]
}
