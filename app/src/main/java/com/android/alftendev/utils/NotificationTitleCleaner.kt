package com.android.alftendev.utils

object NotificationTitleCleaner {
    private val unreadCountSuffix = Regex(
        """\s*(?:[（(]\d+条(?:新消息)?[）)]|\[\d+条(?:新消息)?]|\d+条新消息|\d+条)\s*$"""
    )

    fun removeUnreadCountSuffix(title: String): String = title.replace(unreadCountSuffix, "").trim()
}
