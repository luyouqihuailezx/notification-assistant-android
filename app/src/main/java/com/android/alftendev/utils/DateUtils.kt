package com.android.alftendev.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    fun dateFormatter(date: Date): String {
        val formatter = SimpleDateFormat("dd/M/yyyy HH:mm:ss", Locale.getDefault())
        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(date)
    }

    fun dateFormatterCompact(date: Date, now: Date = Date()): String {
        val elapsedMillis = now.time - date.time
        val today = Calendar.getInstance().apply { time = now }
        val target = Calendar.getInstance().apply { time = date }
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.CHINA)

        if (elapsedMillis in 0..59_999) return "刚刚"
        if (elapsedMillis in 60_000..3_599_999) {
            return "${elapsedMillis / 60_000}分钟前"
        }
        if (today.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            today.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        ) {
            return "今天${timeFormatter.format(date)}"
        }

        val daysAgo = ((today.timeInMillis - target.timeInMillis) /
                (24 * 60 * 60 * 1000)).toInt()
        if (daysAgo in 1..6) {
            val weekdays = arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")
            return "${weekdays[target.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY]} ${
                timeFormatter.format(date)
            }"
        }

        val dateFormatter = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA)
        return dateFormatter.format(date)
    }

    fun dateFormatterOnlyDayMonthYear(date: Date): String {
        val formatter = SimpleDateFormat("dd/M/yyyy", Locale.getDefault())
        formatter.timeZone = TimeZone.getDefault()
        return formatter.format(date)
    }

    fun areDatesEqual(date1: Date?, date2: Date): Boolean {
        if (date1 == null) {
            return false
        }

        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }

        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }
}
