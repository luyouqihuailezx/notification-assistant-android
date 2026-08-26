package com.android.alftendev.models

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
    // 解析规则变化时递增，启动归档数据库会自动为历史通知重建日期索引。
    const val INDEX_VERSION = 2

    private data class Clock(val hour: Int? = null, val minute: Int? = null, val second: Int? = null)
    private data class Match(val text: String, val groups: List<String?>, val start: Int, val end: Int)

    private val absolute = Pattern.compile("(?<!\\d)(\\d{4})[年/-](\\d{1,2})[月/-](\\d{1,2})[日号]?(?:[ T,，]+(\\d{1,2})(?:[:点时](\\d{1,2}))?(?:[:分](\\d{1,2}))?)?")
    private val monthDay = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*月\\s*(\\d{1,2})[日号]?(?:[ T,，]*(\\d{1,2})(?:[:点时](\\d{1,2}))?(?:[:分](\\d{1,2}))?)?")
    private val dotted = Pattern.compile("(?<!\\d)(\\d{1,2})\\.(\\d{1,2})日")
    private val english = Pattern.compile("(?i)\\b(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?(?:\\s+(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?)?")
    private val relativeMonth = Pattern.compile("(上上|上|本|这|下|下下)个?月\\s*(?:(最后(?:一个)?|最末)(?:的)?\\s*)?(星期[一二三四五六日天]|周[一二三四五六日天]|\\d{1,2}[日号])")
    private val bareDay = Pattern.compile("(?<![\\d月])\\d{1,2}\\s*[日号]")
    private val periodClock = Pattern.compile("(?i)(凌晨|早上|早晨|清晨|早|上午|中午|下午|傍晚|晚上|晚)(\\d{1,2}|[零〇一二两三四五六七八九])(?:[:点时](\\d{1,2}|[零〇一二两三四五六七八九]))?")
    private val weekdayPattern = Pattern.compile("(?i)(上(?:个|周|星期)?|下(?:个|周|星期)?|本(?:周|星期)?|这(?:周|星期)?|next\\s+|last\\s+|this\\s+)?(星期[一二三四五六日天]|周[一二三四五六日天]|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|Mon|Tue|Tues|Wed|Thu|Thur|Thurs|Fri|Sat|Sun)")
    private val standaloneClock = Pattern.compile("(?<![\\d零〇一二两三四五六七八九])(\\d{1,2}|[零〇一二两三四五六七八九])(?:[:点时](\\d{1,2}|[零〇一二两三四五六七八九])?)?(?:分)?(?![\\d零〇一二两三四五六七八九])")
    private val duration = Pattern.compile("(?i)(\\d+|[零〇一二两三四五六七八九十百千万亿]+)\\s*个?\\s*(秒钟?|分钟?|小时|天|周|weeks?|days?|hours?|minutes?)\\s*(前|后|以后|之后|ago|later|from now)")
    private val nearbyClockPattern = Pattern.compile("(?:凌晨|早上|早晨|清晨|上午|中午|下午|傍晚|晚上|早|晚|(?i:at\\s+))?(\\d{1,2})(?:[:点时](\\d{1,2})?)?(?:[:分](\\d{1,2})?)?(?:\\s*(?i:am|pm))?")
    private val chineseDigits = mapOf('零' to 0, '〇' to 0, '一' to 1, '两' to 2, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)

    private val weekdays = mapOf(
        "星期日" to 1, "星期天" to 1, "周日" to 1, "周天" to 1,
        "星期一" to 2, "周一" to 2, "星期二" to 3, "周二" to 3, "星期三" to 4, "周三" to 4,
        "星期四" to 5, "周四" to 5, "星期五" to 6, "周五" to 6, "星期六" to 7, "周六" to 7,
        "sunday" to 1, "sun" to 1, "monday" to 2, "mon" to 2, "tuesday" to 3, "tue" to 3, "tues" to 3,
        "wednesday" to 4, "wed" to 4, "thursday" to 5, "thu" to 5, "thur" to 5, "thurs" to 5,
        "friday" to 6, "fri" to 6, "saturday" to 7, "sat" to 7
    )
    private val months = mapOf(
        "january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4, "may" to 5, "june" to 6, "jun" to 6, "july" to 7, "jul" to 7,
        "august" to 8, "aug" to 8, "september" to 9, "sep" to 9, "sept" to 9,
        "october" to 10, "oct" to 10, "november" to 11, "nov" to 11, "december" to 12, "dec" to 12
    )
    private val relativeDays = listOf(
        Triple("大前天", -3, null), Triple("前天", -2, null), Triple("昨天", -1, null), Triple("昨日", -1, null),
        Triple("今天", 0, null), Triple("今日", 0, null), Triple("明天", 1, null), Triple("明日", 1, null),
        Triple("后天", 2, null), Triple("后日", 2, null), Triple("大后天", 3, null),
        Triple("今早", 0, Clock(9)), Triple("明早", 1, Clock(9)), Triple("今晚", 0, Clock(19)), Triple("明晚", 1, Clock(19)),
        Triple("day after tomorrow", 2, null), Triple("tomorrow", 1, null), Triple("yesterday", -1, null), Triple("today", 0, null)
    )
    private val relativeWeeks = listOf(
        "上上周" to -2, "上周" to -1, "上星期" to -1, "本周" to 0, "这周" to 0, "本星期" to 0,
        "下周" to 1, "下星期" to 1, "下下周" to 2, "last week" to -1, "this week" to 0, "next week" to 1
    )
    private val standalonePeriods = listOf(
        "凌晨" to 0, "早上" to 9, "早晨" to 9, "清晨" to 9, "早" to 9, "上午" to 9,
        "中午" to 12, "下午" to 15, "傍晚" to 17, "晚上" to 19, "晚" to 19
    )
    private val defaultPeriods = listOf(
        "凌晨" to 0, "早上" to 9, "早晨" to 9, "清晨" to 9, "上午" to 9, "中午" to 12, "下午" to 15, "傍晚" to 17, "晚上" to 19
    )

    fun referencesIn(text: String, referenceDate: Date, timeZone: TimeZone = TimeZone.getDefault()): List<MessageDateReference> {
        val source = text.trim()
        if (source.isEmpty()) return emptyList()
        val result = mutableListOf<MessageDateReference>()
        fun n(value: String?) = parseClockNumber(value)

        for (m in matches(absolute, source)) {
            result += absoluteCandidates(m.text, n(m.groups[0]), n(m.groups[1]), n(m.groups[2]), clock(n(m.groups[3]), n(m.groups[4]), n(m.groups[5])), true, referenceDate, timeZone, "absolute_numeric")
        }
        for (m in matches(monthDay, source)) {
            result += absoluteCandidates(m.text, null, n(m.groups[0]), n(m.groups[1]), clock(n(m.groups[2]), n(m.groups[3]), n(m.groups[4])), false, referenceDate, timeZone, "month_day")
        }
        for (m in matches(dotted, source)) {
            result += absoluteCandidates(m.text, null, n(m.groups[0]), n(m.groups[1]), Clock(), false, referenceDate, timeZone, "month_day_dotted")
        }
        for (m in matches(english, source)) {
            val month = months[m.groups[0]?.lowercase(Locale.US)] ?: continue
            var time = clock(n(m.groups[3]), n(m.groups[4]), null)
            val marker = m.groups[5]?.lowercase(Locale.US)
            val hour = time.hour
            if (marker == "pm" && hour != null && hour < 12) time = time.copy(hour = hour + 12)
            if (marker == "am" && hour == 12) time = time.copy(hour = 0)
            result += absoluteCandidates(m.text, n(m.groups[2]), month, n(m.groups[1]), time, m.groups[2] != null, referenceDate, timeZone, "absolute_english")
        }

        val relativeMonthRanges = mutableListOf<IntRange>()
        for (m in matches(relativeMonth, source)) {
            val qualifier = m.groups[0] ?: continue
            val target = m.groups[2] ?: continue
            relativeMonthRanges += m.start until m.end
            val offset = relativeMonthOffset(qualifier)
            val monthDate = add(referenceDate, Calendar.MONTH, offset, timeZone)
            val time = nearbyClock(source, m.end)
            val weekday = weekdayValue(target)
            if (weekday != null) {
                val last = m.groups[1] != null
                monthWeekdayCandidates(weekday, monthDate, referenceDate, timeZone, last).forEachIndexed { rank, date ->
                    result += reference(source.substring(m.start, m.end), date, time, timeZone, rank, if (rank == 0) 1.0 else 0.75, "relative_month_weekday")
                }
            } else {
                val day = relativeDayNumber(target) ?: continue
                val parts = calendar(timeZone, monthDate)
                buildDate(parts.get(Calendar.YEAR), parts.get(Calendar.MONTH) + 1, day, time ?: Clock(), timeZone)?.let { date ->
                    result += reference(source.substring(m.start, m.end), date, time, timeZone, 0, 1.0, "relative_month_day")
                }
            }
        }

        for (m in matches(bareDay, source)) {
            val day = relativeDayNumber(m.text) ?: continue
            val time = nearbyClock(source, m.end)
            nearestMonthDayCandidates(day, referenceDate, timeZone, time ?: Clock()).forEachIndexed { rank, date ->
                result += reference(m.text, date, time, timeZone, rank, if (rank == 0) 1.0 else 0.75, "relative_day_number")
            }
        }

        for ((token, offset, defaultTime) in relativeDays) {
            for (range in ranges(token, source)) {
                val end = range.first + token.length
                val base = startOfDay(add(referenceDate, Calendar.DAY_OF_YEAR, offset, timeZone), timeZone)
                result += reference(source.substring(range.first, end), base, nearbyClock(source, end) ?: defaultTime, timeZone, 0, 1.0, "relative_day")
            }
        }

        val explicitDateRanges = explicitDateRanges(source)
        val periodClockRanges = matches(periodClock, source).map { it.start until it.end }
        for (m in matches(periodClock, source)) {
            val hour = n(m.groups[1]) ?: continue
            val fullRange = m.start until m.end
            if (explicitDateRanges.any { rangesOverlap(it, fullRange) }) continue
            val period = m.groups[0].orEmpty()
            val time = adjustedPeriodClock(period, hour, n(m.groups[2]))
            nearestDayCandidates(time.hour ?: hour, time.minute, referenceDate, timeZone).forEachIndexed { rank, date ->
                result += reference(m.text, date, time, timeZone, rank, if (rank == 0) 1.0 else 0.75, "period_clock")
            }
        }

        for ((period, hour) in standalonePeriods) {
            for (range in ranges(period, source)) {
                val periodRange = range.first until (range.first + period.length)
                if (explicitDateRanges.any { rangesOverlap(it, periodRange) }) continue
                if (periodClockRanges.any { rangesOverlap(it, periodRange) }) continue
                if (hasDateAnchorImmediatelyBefore(range.first, source)) continue
                nearestDayCandidates(hour, null, referenceDate, timeZone).forEachIndexed { rank, date ->
                    result += reference(period, date, Clock(hour), timeZone, rank, if (rank == 0) 1.0 else 0.75, "standalone_period")
                }
            }
        }
        for (m in matches(standaloneClock, source)) {
            val hour = n(m.groups[0]) ?: continue
            if (hour !in 0..23) continue
            if (!(m.text.contains("点") || m.text.contains("时") || m.text.contains(":"))) continue
            val minute = n(m.groups[1])
            if (minute != null && minute !in 0..59) continue
            if (hasDateAnchorImmediatelyBefore(m.start, source)) continue
            nearestDayCandidates(hour, minute, referenceDate, timeZone).forEachIndexed { rank, date ->
                result += reference(m.text, date, Clock(hour, minute), timeZone, rank, if (rank == 0) 1.0 else 0.75, "standalone_clock")
            }
        }

        for ((token, offset) in relativeWeeks) {
            for (range in ranges(token, source)) {
                val suffix = source.drop(range.last + 1).firstOrNull()
                if (suffix != null && "一二三四五六日天".contains(suffix)) continue
                val date = add(isoWeekStart(referenceDate, timeZone), Calendar.WEEK_OF_YEAR, offset, timeZone)
                result += weekReference(source.substring(range.first, range.last + 1), date, timeZone, 0, 1.0, "relative_week")
            }
        }

        for (m in matches(weekdayPattern, source)) {
            val weekday = weekdayValue(m.groups[1]) ?: continue
            val qualifier = m.groups[0]?.lowercase(Locale.US).orEmpty()
            val weekdayRange = m.start until m.end
            if (relativeMonthRanges.any { rangesOverlap(it, weekdayRange) }) continue
            weekdayCandidates(weekday, qualifier, referenceDate, timeZone).forEachIndexed { rank, date ->
                result += reference(m.text, date, nearbyClock(source, m.end), timeZone, rank, if (rank == 0) 1.0 else 0.75, "weekday")
            }
        }

        for (m in matches(duration, source)) {
            val amount = amount(m.groups[0]) ?: continue
            val unit = m.groups[1]?.lowercase(Locale.US).orEmpty()
            val direction = m.groups[2]?.lowercase(Locale.US).orEmpty()
            val sign = if (direction == "前" || direction == "ago") -1 else 1
            val (field, precision) = when (unit) {
                "秒", "秒钟" -> Calendar.SECOND to MessageDatePrecision.SECOND
                "分", "分钟", "minute", "minutes" -> Calendar.MINUTE to MessageDatePrecision.MINUTE
                "小时", "hour", "hours" -> Calendar.HOUR_OF_DAY to MessageDatePrecision.HOUR
                "周", "week", "weeks" -> Calendar.WEEK_OF_YEAR to MessageDatePrecision.HOUR
                else -> Calendar.DAY_OF_YEAR to MessageDatePrecision.HOUR
            }
            result += relativeDurationReference(m.text, add(referenceDate, field, sign * amount, timeZone), precision, timeZone)
        }
        return normalize(result)
    }

    private fun explicitDateRanges(text: String): List<IntRange> {
        val patterns = listOf(
            Pattern.compile("大前天|前天|昨天|昨日|今天|今日|明天|明日|后天|后日|大后天|今早|明早|今晚|明晚|昨晚"),
            Pattern.compile("上上周|上周|上星期|本周|这周|本星期|下周|下星期|下下周"),
            Pattern.compile("(?:上|下|本|这)?(?:周|星期)[一二三四五六日天]"),
            Pattern.compile("\\d{4}[年/-]\\d{1,2}[月/-]\\d{1,2}[日号]?|\\d{1,2}\\s*月\\s*\\d{1,2}[日号]?|\\d{1,2}\\.\\d{1,2}日")
        )
        return patterns.flatMap { matches(it, text).map { match -> match.start until match.end } }
    }

    private fun nearestDayCandidates(hour: Int, minute: Int?, referenceDate: Date, timeZone: TimeZone): List<Date> {
        val start = startOfDay(referenceDate, timeZone)
        val candidates = listOf(-1, 0, 1).mapNotNull { offset ->
            val day = add(start, Calendar.DAY_OF_YEAR, offset, timeZone)
            atTime(day, hour, minute ?: 0, timeZone)
        }.sortedBy { abs(it.time - referenceDate.time) }
        val current = candidates.firstOrNull { isSameDay(it, referenceDate, timeZone) } ?: return emptyList()
        val nearest = candidates.firstOrNull() ?: return emptyList()
        return listOf(nearest, current)
    }

    private fun hasDateAnchorImmediatelyBefore(start: Int, text: String): Boolean {
        val prefix = text.substring(maxOf(0, start - 4), start)
        return listOf("今天", "今日", "明天", "明日", "昨天", "昨日", "星期", "周").any { prefix.contains(it) }
    }

    private fun rangesOverlap(lhs: IntRange, rhs: IntRange) = lhs.first < rhs.last + 1 && rhs.first < lhs.last + 1

    private fun normalize(references: List<MessageDateReference>): List<MessageDateReference> {
        val explicitIds = setOf("absolute_numeric", "month_day", "month_day_dotted", "absolute_english", "relative_month_day", "relative_month_weekday")
        val explicit = references.filter { it.ruleId in explicitIds }
        val explicitDates = explicit.mapNotNull { it.resolvedDate?.time }.toSet()
        val filtered = references.filter { reference ->
            val timestamp = reference.resolvedDate?.time
            if (reference.ruleId in setOf("month_day", "month_day_dotted", "relative_day_number") &&
                timestamp != null && timestamp in explicitDates && explicit.none { it.originalText == reference.originalText }
            ) {
                return@filter false
            }
            if (reference.ruleId == "weekday" && explicit.isNotEmpty()) return@filter false
            true
        }
        return deduplicate(filtered)
    }

    private fun relativeMonthOffset(qualifier: String) = when (qualifier) {
        "上上" -> -2; "上" -> -1; "下" -> 1; "下下" -> 2; else -> 0
    }

    private fun relativeDayNumber(text: String) = text.filter { it.isDigit() }.toIntOrNull()

    private fun nearestMonthDayCandidates(day: Int, referenceDate: Date, timeZone: TimeZone, time: Clock): List<Date> {
        return (-1..1).mapNotNull { offset ->
            val monthDate = add(referenceDate, Calendar.MONTH, offset, timeZone)
            val parts = calendar(timeZone, monthDate)
            buildDate(parts.get(Calendar.YEAR), parts.get(Calendar.MONTH) + 1, day, time, timeZone)
        }.sortedBy { abs(it.time - referenceDate.time) }.take(1)
    }

    private fun monthWeekdayCandidates(weekday: Int, monthDate: Date, referenceDate: Date, timeZone: TimeZone, last: Boolean): List<Date> {
        val start = calendar(timeZone, monthDate).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val daysInMonth = start.getActualMaximum(Calendar.DAY_OF_MONTH)
        val days = (0 until daysInMonth).map { add(start.time, Calendar.DAY_OF_YEAR, it, timeZone) }
            .filter { calendar(timeZone, it).get(Calendar.DAY_OF_WEEK) == weekday }
        if (last) return listOfNotNull(days.lastOrNull())
        return days.sortedBy { abs(it.time - referenceDate.time) }.take(1)
    }

    private fun isoWeekStart(date: Date, timeZone: TimeZone): Date {
        val cal = calendar(timeZone, date)
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun weekReference(text: String, date: Date, timeZone: TimeZone, rank: Int, weight: Double, ruleId: String): MessageDateReference {
        val parts = calendar(timeZone, date)
        return MessageDateReference(
            originalText = text,
            year = parts.get(Calendar.YEAR),
            month = parts.get(Calendar.MONTH) + 1,
            day = parts.get(Calendar.DAY_OF_MONTH),
            hour = null, minute = null, second = null, weekday = 2,
            precision = MessageDatePrecision.WEEK,
            resolvedDate = date,
            candidateRank = rank, weight = weight, ruleId = ruleId
        )
    }

    private fun absoluteCandidates(
        text: String, year: Int?, month: Int?, day: Int?, time: Clock, fixed: Boolean,
        referenceDate: Date, timeZone: TimeZone, ruleId: String
    ): List<MessageDateReference> {
        if (month == null || day == null) return emptyList()
        val currentYear = calendar(timeZone, referenceDate).get(Calendar.YEAR)
        val years = if (fixed) listOf(year ?: currentYear) else listOf(currentYear - 1, currentYear, currentYear + 1)
        val dates = years.mapNotNull { buildDate(it, month, day, time, timeZone) }.sortedBy { abs(it.time - referenceDate.time) }
        val selected = if (fixed) dates else dates.take(1)
        return selected.mapIndexed { rank, date ->
            reference(text, date, time, timeZone, rank, if (rank == 0) 1.0 else maxOf(0.4, 0.8 - rank * 0.2), ruleId)
        }
    }

    private fun weekdayCandidates(weekday: Int, qualifier: String, referenceDate: Date, timeZone: TimeZone): List<Date> {
        val start = startOfDay(referenceDate, timeZone)
        val current = calendar(timeZone, start).get(Calendar.DAY_OF_WEEK)
        val forward = (weekday - current + 7) % 7
        val thisWeek = add(start, Calendar.DAY_OF_YEAR, forward, timeZone)
        if (qualifier.contains("上") || qualifier.contains("last")) return listOf(add(thisWeek, Calendar.DAY_OF_YEAR, -7, timeZone))
        if (qualifier.contains("下") || qualifier.contains("next")) return listOf(add(thisWeek, Calendar.DAY_OF_YEAR, 7, timeZone))
        if (qualifier.contains("本") || qualifier.contains("这") || qualifier.contains("this")) return listOf(thisWeek)
        val previous = add(thisWeek, Calendar.DAY_OF_YEAR, -7, timeZone)
        val following = add(thisWeek, Calendar.DAY_OF_YEAR, 7, timeZone)
        val nearest = listOf(previous, thisWeek, following).sortedBy { abs(it.time - referenceDate.time) }
        return if (nearest[0] == thisWeek) listOf(thisWeek) else listOf(nearest[0], thisWeek)
    }

    private fun reference(text: String, date: Date?, time: Clock?, timeZone: TimeZone, rank: Int, weight: Double, ruleId: String): MessageDateReference {
        if (date == null) {
            return MessageDateReference(text, null, null, null, null, null, null, null, MessageDatePrecision.DAY, null, rank, weight, ruleId)
        }
        val parts = calendar(timeZone, date)
        if (time?.hour != null) {
            parts.set(Calendar.HOUR_OF_DAY, time.hour)
            parts.set(Calendar.MINUTE, time.minute ?: 0)
            parts.set(Calendar.SECOND, time.second ?: 0)
            parts.set(Calendar.MILLISECOND, 0)
        }
        val precision = when {
            time?.second != null -> MessageDatePrecision.SECOND
            time?.minute != null -> MessageDatePrecision.MINUTE
            time?.hour != null -> MessageDatePrecision.HOUR
            else -> MessageDatePrecision.DAY
        }
        return MessageDateReference(
            originalText = text,
            year = parts.get(Calendar.YEAR),
            month = parts.get(Calendar.MONTH) + 1,
            day = parts.get(Calendar.DAY_OF_MONTH),
            hour = time?.hour,
            minute = time?.minute,
            second = time?.second,
            weekday = parts.get(Calendar.DAY_OF_WEEK),
            precision = precision,
            resolvedDate = parts.time,
            candidateRank = rank,
            weight = weight,
            ruleId = ruleId
        )
    }

    private fun relativeDurationReference(text: String, date: Date?, precision: MessageDatePrecision, timeZone: TimeZone): MessageDateReference {
        if (date == null) {
            return MessageDateReference(text, null, null, null, null, null, null, null, precision, null, 0, 1.0, "relative_duration")
        }
        val parts = calendar(timeZone, date)
        return MessageDateReference(
            originalText = text,
            year = parts.get(Calendar.YEAR),
            month = parts.get(Calendar.MONTH) + 1,
            day = parts.get(Calendar.DAY_OF_MONTH),
            hour = parts.get(Calendar.HOUR_OF_DAY),
            minute = parts.get(Calendar.MINUTE),
            second = parts.get(Calendar.SECOND),
            weekday = parts.get(Calendar.DAY_OF_WEEK),
            precision = precision,
            resolvedDate = date,
            candidateRank = 0,
            weight = 1.0,
            ruleId = "relative_duration"
        )
    }

    private fun nearbyClock(text: String, start: Int): Clock? {
        if (start >= text.length) return null
        val fragment = text.substring(start, minOf(text.length, start + 16))
        val normalized = buildString {
            for (character in fragment) {
                val digit = chineseDigits[character]
                if (digit != null) append(digit) else append(character)
            }
        }
        val match = matches(nearbyClockPattern, normalized).firstOrNull()
        if (match != null) {
            var value = clock(parseClockNumber(match.groups[0]), parseClockNumber(match.groups[1]), parseClockNumber(match.groups[2]))
            val prefix = match.text.lowercase(Locale.US)
            val markers = listOf(":", "点", "时", "am", "pm", "凌晨", "早上", "早晨", "清晨", "上午", "中午", "下午", "傍晚", "晚上", "早", "晚")
            val hour = value.hour
            if (markers.any { prefix.contains(it) } && hour != null && hour in 0..23 &&
                (value.minute == null || value.minute in 0..59) &&
                (value.second == null || value.second in 0..59)
            ) {
                val period = listOf("凌晨", "早上", "早晨", "清晨", "上午", "中午", "下午", "傍晚", "晚上", "早", "晚").firstOrNull { prefix.contains(it) }.orEmpty()
                if (period.isNotEmpty()) value = adjustedPeriodClock(period, hour, value.minute)
                if (prefix.contains("pm") && hour < 12) value = value.copy(hour = hour + 12)
                return value
            }
        }
        for ((period, hour) in defaultPeriods) {
            if (fragment.startsWith(period) || normalized.startsWith(period)) return Clock(hour)
        }
        return null
    }

    private fun parseClockNumber(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        value.toIntOrNull()?.let { return it }
        if (value.length == 1) return chineseDigits[value[0]]
        return null
    }

    private fun adjustedPeriodClock(period: String, hour: Int, minute: Int?): Clock {
        var adjusted = hour
        if (period in listOf("下午", "傍晚", "晚上", "晚") && adjusted < 12) adjusted += 12
        if (period == "中午" && adjusted < 11) adjusted += 12
        if (period == "凌晨" && adjusted == 12) adjusted = 0
        return Clock(adjusted, minute)
    }

    private fun buildDate(year: Int, month: Int, day: Int, time: Clock, timeZone: TimeZone): Date? {
        val cal = Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month - 1, day, time.hour ?: 0, time.minute ?: 0, time.second ?: 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.takeIf { it.get(Calendar.MONTH) == month - 1 && it.get(Calendar.DAY_OF_MONTH) == day }?.time
    }

    private fun deduplicate(values: List<MessageDateReference>): List<MessageDateReference> {
        val keys = mutableSetOf<String>()
        return values.sortedWith(compareBy<MessageDateReference> { it.candidateRank }.thenByDescending { it.weight }.thenBy { it.originalText })
            .filter { keys.add("${it.resolvedDate?.time ?: -1}") }
    }

    private fun matches(pattern: Pattern, text: String): List<Match> {
        val matcher = pattern.matcher(text)
        return buildList {
            while (matcher.find()) {
                add(Match(matcher.group(), (1..matcher.groupCount()).map { matcher.group(it) }, matcher.start(), matcher.end()))
            }
        }
    }

    private fun ranges(token: String, text: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = 0
        val source = text.lowercase(Locale.US)
        val needle = token.lowercase(Locale.US)
        while (true) {
            val index = source.indexOf(needle, start)
            if (index < 0) return out
            out.add(index until (index + token.length))
            start = index + token.length
        }
    }

    private fun clock(hour: Int?, minute: Int?, second: Int?) = Clock(hour, minute, second)
    private fun amount(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        value.toIntOrNull()?.let { return it }
        return chineseNumber(value)
    }

    private fun chineseNumber(value: String): Int? {
        val digits = mapOf('零' to 0, '〇' to 0, '一' to 1, '两' to 2, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        var total = 0
        var section = 0
        var current = 0
        for (character in value) {
            val digit = digits[character]
            if (digit != null) {
                current = digit
            } else if (character == '十' || character == '百' || character == '千') {
                val multiplier = if (character == '十') 10 else if (character == '百') 100 else 1000
                section += (if (current == 0) 1 else current) * multiplier
                current = 0
            } else if (character == '万' || character == '亿') {
                val multiplier = if (character == '万') 10_000 else 100_000_000
                total += (section + current) * multiplier
                section = 0
                current = 0
            } else {
                return null
            }
        }
        val result = total + section + current
        return result.takeIf { it > 0 }
    }

    private fun weekdayValue(value: String?): Int? {
        val token = value?.lowercase(Locale.US) ?: return null
        return weekdays[token] ?: weekdays[token.replace("星期", "").replace("周", "")]
    }

    private fun calendar(timeZone: TimeZone, date: Date) = Calendar.getInstance(timeZone).apply { time = date }

    private fun startOfDay(date: Date, timeZone: TimeZone) = calendar(timeZone, date).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private fun add(date: Date, field: Int, amount: Int, timeZone: TimeZone) = calendar(timeZone, date).apply { add(field, amount) }.time

    private fun atTime(day: Date, hour: Int, minute: Int, timeZone: TimeZone) = calendar(timeZone, day).apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private fun isSameDay(lhs: Date, rhs: Date, timeZone: TimeZone): Boolean {
        val left = calendar(timeZone, lhs)
        val right = calendar(timeZone, rhs)
        return left.get(Calendar.YEAR) == right.get(Calendar.YEAR) && left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR)
    }
}
