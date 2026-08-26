package com.android.alftendev

import com.android.alftendev.models.MessageDatePrecision
import com.android.alftendev.models.MessageDateReferenceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MessageDateReferenceTest {
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun indexVersionIsIncrementedForParserChanges() {
        assertEquals(3, MessageDateReferenceParser.INDEX_VERSION)
    }

    @Test fun resolvesRelativeDateAndMarkedClock() {
        val references = MessageDateReferenceParser.referencesIn("明天六点开会", format.parse("2026-08-13 09:00")!!, utc)
        assertTrue(
            "明天六点 -> ${references.joinToString { "${it.originalText}/${it.ruleId}/${it.day} ${it.hour}" }}",
            references.any { it.year == 2026 && it.month == 8 && it.day == 14 && it.hour == 6 }
        )
    }

    @Test fun rejectsUnmarkedNumbersAsClock() {
        val references = MessageDateReferenceParser.referencesIn("今天一口气从26星打到44", format.parse("2026-08-13 09:00")!!, utc)
        assertTrue(references.any { it.originalText == "今天" && it.hour == null })
        assertFalse(references.any { it.hour != null })
    }

    @Test fun resolvesEnglishDateAndTime() {
        val references = MessageDateReferenceParser.referencesIn("August 21, 2026 at 8pm", format.parse("2026-08-13 09:00")!!, utc)
        assertTrue(references.any { it.month == 8 && it.day == 21 && it.hour == 20 && it.precision == MessageDatePrecision.HOUR })
    }

    @Test fun protectsVersionNumbersAndSuppressesWeekdayWhenExplicitDateExists() {
        val references = MessageDateReferenceParser.referencesIn("版本 1.0.2，3.25日下周三开会", format.parse("2026-08-13 09:00")!!, utc)
        assertTrue(references.any { it.month == 3 && it.day == 25 })
        assertFalse(references.any { it.ruleId == "weekday" })
    }

    @Test fun resolvesPeriodsAndPeriodClocks() {
        val reference = format.parse("2026-08-26 02:20")!!
        val cases = listOf(
            Triple("明早开会", 27, 9),
            Triple("今天中午吃饭", 26, 12),
            Triple("今天下午开会", 26, 15),
            Triple("今天傍晚出发", 26, 17),
            Triple("今天晚上聚餐", 26, 19),
            Triple("今天凌晨值班", 26, 0),
            Triple("早八上课", 26, 8),
            Triple("晚八开会", 26, 20),
            Triple("星期五早上开会", 28, 9)
        )
        for ((text, day, hour) in cases) {
            val parsed = MessageDateReferenceParser.referencesIn(text, reference, utc)
            assertTrue(
                "未识别时段：$text -> ${parsed.joinToString { "${it.originalText}/${it.ruleId}/${it.day} ${it.hour}:${it.minute}" }}",
                parsed.any { it.day == day && it.hour == hour }
            )
        }
    }

    @Test fun resolvesArabicAndChineseRelativeDurations() {
        val reference = format.parse("2026-08-13 09:00")!!
        val cases = listOf(
            "一小时后" to 3600_000L,
            "五小时后" to 5 * 3600_000L,
            "两分钟后" to 120_000L,
            "5小时后" to 5 * 3600_000L,
            "2 分钟以后" to 120_000L,
            "三天之后" to 3 * 86400_000L,
            "十分钟前" to -600_000L
        )
        for ((text, offset) in cases) {
            val references = MessageDateReferenceParser.referencesIn(text, reference, utc)
            assertTrue("未识别相对时长：$text", references.any { candidate ->
                candidate.ruleId == "relative_duration" &&
                    kotlin.math.abs((candidate.resolvedDate?.time ?: Long.MAX_VALUE) - (reference.time + offset)) < 1
            })
        }
    }

    @Test fun relativeDurationPreservesNotificationClockTime() {
        val reference = format.parse("2026-08-26 02:20")!!
        val parsed = MessageDateReferenceParser.referencesIn("一个小时以后开会", reference, utc)
        assertTrue(parsed.any { candidate ->
            candidate.ruleId == "relative_duration" &&
                candidate.hour == 3 &&
                candidate.minute == 20 &&
                candidate.precision == MessageDatePrecision.HOUR
        })
    }

    @Test fun standalonePeriodKeepsNearestAndCurrentCandidates() {
        val noon = format.parse("2026-08-26 12:00")!!
        val evening = MessageDateReferenceParser.referencesIn("晚上", noon, utc)
        assertEquals(1, evening.filter { it.hour == 19 }.distinctBy { it.day }.size)
        assertTrue(evening.any { it.day == 26 && it.hour == 19 })
    }

    @Test fun explicitDatePreventsFuzzyPeriodGuess() {
        val reference = format.parse("2026-08-26 02:20")!!
        val parsed = MessageDateReferenceParser.referencesIn("明天晚上开会", reference, utc)
        assertTrue(
            "明天晚上 -> ${parsed.joinToString { "${it.originalText}/${it.ruleId}/${it.day} ${it.hour}" }}",
            parsed.any { it.day == 27 && it.hour == 19 && it.ruleId == "relative_day" }
        )
        assertFalse(parsed.any { it.ruleId == "standalone_period" && it.day == 26 })
    }

    @Test fun resolvesRelativeMonthAndBareDayNumber() {
        val reference = format.parse("2026-08-13 09:00")!!
        val bareDay = MessageDateReferenceParser.referencesIn("25号提交", reference, utc)
        assertTrue(bareDay.any { it.year == 2026 && it.month == 8 && it.day == 25 })

        val nextMonth = MessageDateReferenceParser.referencesIn("下个月25号上午10点", reference, utc)
        assertTrue(nextMonth.any { it.year == 2026 && it.month == 9 && it.day == 25 && it.hour == 10 })

        val previousMonth = MessageDateReferenceParser.referencesIn("上个月25号", reference, utc)
        assertTrue(previousMonth.any { it.year == 2026 && it.month == 7 && it.day == 25 })

        val nextMonthFriday = MessageDateReferenceParser.referencesIn("下个月星期五", reference, utc)
        assertTrue(nextMonthFriday.any { it.year == 2026 && it.month == 9 && it.day == 4 && it.weekday == 6 })
        assertFalse(nextMonthFriday.any { it.ruleId == "weekday" })
    }

    @Test fun resolvesRelativeWeekAndWeekdayClock() {
        val reference = format.parse("2026-08-13 09:00")!!
        val nextWeek = MessageDateReferenceParser.referencesIn("下周开会", reference, utc)
        assertTrue(nextWeek.any { it.precision == MessageDatePrecision.WEEK && it.weekday == 2 && it.year == 2026 && it.month == 8 && it.day == 17 })

        val nextFriday = MessageDateReferenceParser.referencesIn("下周五 8pm", reference, utc)
        assertTrue(nextFriday.any { it.weekday == 6 && it.hour == 20 && it.day == 21 })
    }

    @Test fun resolvesVagueSoonWordsAsOneHourAfterNotification() {
        val reference = format.parse("2026-08-13 09:00")!!
        val tokens = listOf("稍后", "马上", "待会", "过一会", "晚点", "稍晚", "待会儿", "一会儿", "立刻")
        for (token in tokens) {
            val parsed = MessageDateReferenceParser.referencesIn("${token}处理", reference, utc)
            assertTrue(
                "未识别近期用语：$token -> ${parsed.joinToString { "${it.originalText}/${it.ruleId}/${it.hour}:${it.minute}" }}",
                parsed.any { candidate ->
                    candidate.ruleId == "relative_soon" &&
                        candidate.originalText == token &&
                        candidate.hour == 10 &&
                        candidate.minute == 0 &&
                        candidate.precision == MessageDatePrecision.HOUR &&
                        kotlin.math.abs((candidate.resolvedDate?.time ?: Long.MAX_VALUE) - (reference.time + 3_600_000L)) < 1
                }
            )
        }
    }

    @Test fun vagueSoonDoesNotOverrideExplicitDateOrSplitEveningToken() {
        val reference = format.parse("2026-08-13 09:00")!!
        val tomorrow = MessageDateReferenceParser.referencesIn("明天马上开会", reference, utc)
        assertTrue(tomorrow.any { it.ruleId == "relative_day" && it.day == 14 })
        assertFalse(tomorrow.any { it.ruleId == "relative_soon" })

        val tonight = MessageDateReferenceParser.referencesIn("今晚晚点再聊", reference, utc)
        assertTrue(tonight.any { it.originalText == "今晚" })
        assertFalse(tonight.any { it.ruleId == "relative_soon" })

        val later = MessageDateReferenceParser.referencesIn("晚点处理", reference, utc)
        assertTrue(later.any { it.ruleId == "relative_soon" && it.originalText == "晚点" })
        assertFalse(later.any { it.ruleId == "standalone_period" && it.originalText == "晚" })
    }
}
