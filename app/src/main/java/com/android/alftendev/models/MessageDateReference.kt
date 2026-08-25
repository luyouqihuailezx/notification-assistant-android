package com.android.alftendev.models

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern
import kotlin.math.abs

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

@Entity
data class MessageDateReferenceEntity(
    @Id var entityId: Long = 0,
    var notificationId: Long = 0,
    var originalText: String = "",
    var year: Long? = null,
    var month: Long? = null,
    var day: Long? = null,
    var hour: Long? = null,
    var minute: Long? = null,
    var second: Long? = null,
    var weekday: Long? = null,
    var precision: String = MessageDatePrecision.DAY.name,
    var resolvedTimestamp: Long? = null,
    var candidateRank: Int = 0,
    var weight: Double = 1.0,
    var ruleId: String = "",
    var indexVersion: Int = 1
)

enum class MessageDatePrecision { YEAR, MONTH, WEEK, DAY, HOUR, MINUTE, SECOND }

data class MessageDateReference(
    val originalText: String,
    val year: Int?, val month: Int?, val day: Int?,
    val hour: Int?, val minute: Int?, val second: Int?, val weekday: Int?,
    val precision: MessageDatePrecision, val resolvedDate: Date?,
    val candidateRank: Int, val weight: Double, val ruleId: String
)

data class MessageDateSearchFilter(
    val originalText: String? = null, val year: Int? = null, val month: Int? = null,
    val day: Int? = null, val hour: Int? = null, val minute: Int? = null,
    val second: Int? = null, val weekday: Int? = null,
    val from: Date? = null, val to: Date? = null
)

object MessageDateReferenceParser {
    private data class Clock(val hour: Int?, val minute: Int? = null, val second: Int? = null)
    private data class Match(val text: String, val groups: List<String?>, val start: Int, val end: Int)
    private val absolute = Pattern.compile("(?<!\\d)(\\d{4})[年/-](\\d{1,2})[月/-](\\d{1,2})[日号]?(?:[ T,，]+(\\d{1,2})(?:[:点时](\\d{1,2}))?(?:[:分](\\d{1,2}))?)?")
    private val monthDay = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*月\\s*(\\d{1,2})[日号]?(?:[ T,，]*(\\d{1,2})(?:[:点时](\\d{1,2}))?(?:[:分](\\d{1,2}))?)?")
    private val dotted = Pattern.compile("(?<!\\d)(\\d{1,2})\\.(\\d{1,2})日")
    private val english = Pattern.compile("(?i)\\b(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?(?:\\s+(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?)?")
    private val weekdays = mapOf("日" to 1, "天" to 1, "一" to 2, "二" to 3, "三" to 4, "四" to 5, "五" to 6, "六" to 7,
        "sunday" to 1, "sun" to 1, "monday" to 2, "mon" to 2, "tuesday" to 3, "tue" to 3, "tues" to 3,
        "wednesday" to 4, "wed" to 4, "thursday" to 5, "thu" to 5, "thur" to 5, "thurs" to 5,
        "friday" to 6, "fri" to 6, "saturday" to 7, "sat" to 7)
    private val months = mapOf("january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "mar" to 3, "april" to 4, "apr" to 4, "may" to 5, "june" to 6, "jun" to 6, "july" to 7, "jul" to 7, "august" to 8, "aug" to 8, "september" to 9, "sep" to 9, "sept" to 9, "october" to 10, "oct" to 10, "november" to 11, "nov" to 11, "december" to 12, "dec" to 12)

    fun referencesIn(text: String, referenceDate: Date, timeZone: TimeZone = TimeZone.getDefault()): List<MessageDateReference> {
        val source = text.trim(); if (source.isEmpty()) return emptyList()
        val calendar = Calendar.getInstance(timeZone)
        val result = mutableListOf<MessageDateReference>()
        fun n(value: String?) = value?.toIntOrNull()
        for (m in matches(absolute, source)) result += absoluteCandidates(m.text, n(m.groups[0]), n(m.groups[1]), n(m.groups[2]), clock(n(m.groups[3]), n(m.groups[4]), n(m.groups[5])), true, referenceDate, calendar, "absolute_numeric")
        for (m in matches(monthDay, source)) result += absoluteCandidates(m.text, null, n(m.groups[0]), n(m.groups[1]), clock(n(m.groups[2]), n(m.groups[3]), n(m.groups[4])), false, referenceDate, calendar, "month_day")
        for (m in matches(dotted, source)) result += absoluteCandidates(m.text, null, n(m.groups[0]), n(m.groups[1]), Clock(null), false, referenceDate, calendar, "month_day_dotted")
        for (m in matches(english, source)) {
            val month = months[m.groups[0]?.lowercase(Locale.US)] ?: continue
            var c = clock(n(m.groups[3]), n(m.groups[4]), null)
            val marker = m.groups[5]?.lowercase(Locale.US)
            if (marker == "pm" && (c.hour ?: 0) < 12) c = c.copy(hour = c.hour!! + 12)
            if (marker == "am" && c.hour == 12) c = c.copy(hour = 0)
            result += absoluteCandidates(m.text, n(m.groups[2]), month, n(m.groups[1]), c, m.groups[2] != null, referenceDate, calendar, "absolute_english")
        }
        val relativeDays = mapOf("大前天" to -3, "前天" to -2, "昨天" to -1, "昨日" to -1, "今天" to 0, "今日" to 0, "明天" to 1, "明日" to 1, "后天" to 2, "后日" to 2, "大后天" to 3, "tomorrow" to 1, "yesterday" to -1, "today" to 0)
        relativeDays.forEach { (token, offset) -> ranges(token, source).forEach { range ->
            val base = Calendar.getInstance(calendar.timeZone).apply { time = referenceDate; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_YEAR, offset) }.time
            result += reference(source.substring(range.first, range.second), base, nearbyClock(source, range.second), calendar, 0, 1.0, "relative_day")
        } }
        val relativeWeeks = mapOf("上上周" to -2, "上周" to -1, "上星期" to -1, "本周" to 0, "这周" to 0, "本星期" to 0, "下周" to 1, "下星期" to 1, "下下周" to 2, "last week" to -1, "this week" to 0, "next week" to 1)
        relativeWeeks.forEach { (token, offset) -> ranges(token, source).forEach { range ->
            if (source.drop(range.second).firstOrNull()?.let { "一二三四五六日天".contains(it) } == true) return@forEach
            val c = Calendar.getInstance(calendar.timeZone).apply { time = referenceDate; firstDayOfWeek = Calendar.MONDAY; set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); add(Calendar.WEEK_OF_YEAR, offset) }
            result += weekReference(source.substring(range.first, range.second), c.time, 0, 1.0, "relative_week")
        } }
        val weekdayPattern = Pattern.compile("(?i)(上(?:个|周|星期)?|下(?:个|周|星期)?|本(?:周|星期)?|这(?:周|星期)?|next\\s+|last\\s+|this\\s+)?(星期[一二三四五六日天]|周[一二三四五六日天]|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|Mon|Tue|Tues|Wed|Thu|Thur|Thurs|Fri|Sat|Sun)")
        for (m in matches(weekdayPattern, source)) {
            val value = m.groups[1]?.lowercase(Locale.US)?.replace("星期", "")?.replace("周", "") ?: m.groups[1]
            val weekday = weekdays[value ?: ""] ?: continue
            weekdayCandidates(weekday, m.groups[0]?.lowercase(Locale.US).orEmpty(), referenceDate, calendar).forEachIndexed { rank, date -> result += reference(m.text, date, nearbyClock(source, m.end), calendar, rank, if (rank == 0) 1.0 else .75, "weekday") }
        }
        val duration = Pattern.compile("(?i)(\\d+)\\s*(秒钟?|分钟?|小时|天|周|weeks?|days?|hours?|minutes?)\\s*(前|后|ago|later|from now)")
        for (m in matches(duration, source)) {
            val amount = n(m.groups[0]) ?: continue; val unit = m.groups[1]?.lowercase(Locale.US).orEmpty(); val sign = if (m.groups[2] in listOf("前", "ago")) -1 else 1
            val field = when (unit) { "秒", "秒钟" -> Calendar.SECOND; "分", "分钟", "minute", "minutes" -> Calendar.MINUTE; "小时", "hour", "hours" -> Calendar.HOUR_OF_DAY; "周", "week", "weeks" -> Calendar.WEEK_OF_YEAR; else -> Calendar.DAY_OF_YEAR }
            result += reference(m.text, Calendar.getInstance(calendar.timeZone).apply { time = referenceDate; add(field, sign * amount) }.time, null, calendar, 0, 1.0, "relative_duration")
        }
        val explicit = result.filter { it.ruleId.startsWith("absolute") || it.ruleId.startsWith("month_day") }
        return result.filterNot { it.ruleId == "weekday" && explicit.isNotEmpty() }.distinctBy { "${it.originalText}|${it.resolvedDate?.time}|${it.hour}|${it.minute}|${it.second}" }
    }

    private fun matches(pattern: Pattern, text: String) = pattern.matcher(text).let { matcher -> buildList { while (matcher.find()) add(Match(matcher.group(), (1..matcher.groupCount()).map { matcher.group(it) }, matcher.start(), matcher.end())) } }
    private fun ranges(token: String, text: String): List<Pair<Int, Int>> { val out = mutableListOf<Pair<Int, Int>>(); var start = 0; while (true) { val i = text.indexOf(token, start, ignoreCase = true); if (i < 0) return out; out += i to i + token.length; start = i + token.length } }
    private fun clock(hour: Int?, minute: Int?, second: Int?) = Clock(hour, minute, second)
    private fun absoluteCandidates(text: String, year: Int?, month: Int?, day: Int?, clock: Clock, fixed: Boolean, reference: Date, c: Calendar, rule: String): List<MessageDateReference> {
        if (month == null || day == null) return emptyList(); val currentYear = c.get(Calendar.YEAR); val years = if (fixed) listOf(year ?: currentYear) else listOf(currentYear - 1, currentYear, currentYear + 1)
        val dates = years.mapNotNull { date(it, month, day, clock, c) }.sortedBy { abs(it.time - reference.time) }.let { if (fixed) it else it.take(1) }
        return dates.mapIndexed { rank, d -> reference(text, d, clock, c, rank, if (rank == 0) 1.0 else .6, rule) }
    }
    private fun date(year: Int, month: Int, day: Int, clock: Clock, c: Calendar): Date? = Calendar.getInstance(c.timeZone).apply { clear(); set(year, month - 1, day, clock.hour ?: 0, clock.minute ?: 0, clock.second ?: 0) }.takeIf { it.get(Calendar.MONTH) == month - 1 && it.get(Calendar.DAY_OF_MONTH) == day }?.time
    private fun reference(text: String, date: Date?, clock: Clock?, c: Calendar, rank: Int, weight: Double, rule: String): MessageDateReference { if (date == null) return MessageDateReference(text, null, null, null, null, null, null, null, MessageDatePrecision.DAY, null, rank, weight, rule); val x = Calendar.getInstance(c.timeZone).apply { time = date; if (clock?.hour != null) { set(Calendar.HOUR_OF_DAY, clock.hour!!); set(Calendar.MINUTE, clock.minute ?: 0); set(Calendar.SECOND, clock.second ?: 0) } }; val precision = when { clock?.second != null -> MessageDatePrecision.SECOND; clock?.minute != null -> MessageDatePrecision.MINUTE; clock?.hour != null -> MessageDatePrecision.HOUR; else -> MessageDatePrecision.DAY }; return MessageDateReference(text, x.get(Calendar.YEAR), x.get(Calendar.MONTH) + 1, x.get(Calendar.DAY_OF_MONTH), clock?.hour, clock?.minute, clock?.second, x.get(Calendar.DAY_OF_WEEK), precision, x.time, rank, weight, rule) }
    private fun weekReference(text: String, date: Date, rank: Int, weight: Double, rule: String) = reference(text, date, null, Calendar.getInstance(), rank, weight, rule).copy(precision = MessageDatePrecision.WEEK)
    private fun nearbyClock(text: String, start: Int): Clock? { val fragment = text.substring(start, minOf(text.length, start + 16)); val normalized = fragment.map { mapOf('零' to '0', '〇' to '0', '一' to '1', '二' to '2', '两' to '2', '三' to '3', '四' to '4', '五' to '5', '六' to '6', '七' to '7', '八' to '8', '九' to '9').getOrDefault(it, it) }.joinToString(""); val match = Regex("(?i)(上午|早上|下午|晚上|中午|at\\s+)?(\\d{1,2})(?:[:点时](\\d{1,2})?)?(?:[:分](\\d{1,2}))?(?:\\s*(am|pm))?").find(normalized) ?: return null; val full = match.value.lowercase(Locale.US); if (!(full.contains(":" ) || full.contains("点") || full.contains("时") || full.contains("am") || full.contains("pm") || full.contains("上午") || full.contains("早上") || full.contains("下午") || full.contains("晚上") || full.contains("中午"))) return null; var hour = match.groupValues[2].toIntOrNull() ?: return null; if ((full.contains("下午") || full.contains("晚上") || full.contains("pm")) && hour < 12) hour += 12; if (full.contains("中午") && hour < 11) hour += 12; val minute = match.groupValues[3].toIntOrNull(); val second = match.groupValues[4].toIntOrNull(); return if (hour in 0..23 && (minute == null || minute in 0..59) && (second == null || second in 0..59)) Clock(hour, minute, second) else null }
    private fun weekdayCandidates(day: Int, qualifier: String, reference: Date, c: Calendar): List<Date> { val base = Calendar.getInstance(c.timeZone).apply { time = reference }; val current = base.get(Calendar.DAY_OF_WEEK); val delta = (day - current + 7) % 7; val thisWeek = Calendar.getInstance(c.timeZone).apply { time = reference; add(Calendar.DAY_OF_YEAR, delta) }; if (qualifier.contains("上") || qualifier.contains("last")) thisWeek.add(Calendar.DAY_OF_YEAR, -7); else if (qualifier.contains("下") || qualifier.contains("next")) thisWeek.add(Calendar.DAY_OF_YEAR, 7); return listOf(thisWeek.time) }
}
