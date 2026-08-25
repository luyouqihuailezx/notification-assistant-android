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

    @Test fun resolvesRelativeDateAndMarkedClock() {
        val references = MessageDateReferenceParser.referencesIn("明天六点开会", format.parse("2026-08-13 09:00")!!, TimeZone.getTimeZone("UTC"))
        assertTrue(references.any { it.year == 2026 && it.month == 8 && it.day == 14 && it.hour == 6 })
    }

    @Test fun rejectsUnmarkedNumbersAsClock() {
        val references = MessageDateReferenceParser.referencesIn("今天一口气从26星打到44", format.parse("2026-08-13 09:00")!!, TimeZone.getTimeZone("UTC"))
        assertTrue(references.any { it.originalText == "今天" && it.hour == null })
        assertFalse(references.any { it.hour != null })
    }

    @Test fun resolvesEnglishDateAndTime() {
        val references = MessageDateReferenceParser.referencesIn("August 21, 2026 at 8pm", format.parse("2026-08-13 09:00")!!, TimeZone.getTimeZone("UTC"))
        assertTrue(references.any { it.month == 8 && it.day == 21 && it.hour == 20 && it.precision == MessageDatePrecision.HOUR })
    }

    @Test fun protectsVersionNumbersAndSuppressesWeekdayWhenExplicitDateExists() {
        val references = MessageDateReferenceParser.referencesIn("版本 1.0.2，3.25日下周三开会", format.parse("2026-08-13 09:00")!!, TimeZone.getTimeZone("UTC"))
        assertTrue(references.any { it.month == 3 && it.day == 25 })
        assertFalse(references.any { it.ruleId == "weekday" })
    }
}
