package com.android.alftendev.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import com.android.alftendev.utils.CustomLog
import com.android.alftendev.utils.MySharedPref
import com.android.alftendev.utils.PermissionUtils

object NotificationListenerKeepAlive {
    private const val HEALTH_CHECK_INTERVAL_MS = 30 * 60 * 1000L
    private const val HEALTH_CHECK_REQUEST_CODE = 1001
    private val logger = CustomLog("not-listener-keep-alive")

    fun isCollectionActive(context: Context): Boolean =
        MySharedPref.getIsRecordNotificationsEnabled() &&
            PermissionUtils.isNotificationServiceEnabled(context)

    fun scheduleHealthCheckIfActive(context: Context) {
        if (!isCollectionActive(context)) {
            cancelHealthCheck(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMillis = System.currentTimeMillis() + HEALTH_CHECK_INTERVAL_MS
        val pendingIntent = healthCheckPendingIntent(context)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                logger.log("Exact alarms are unavailable; scheduled an inexact listener health check.")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (exception: SecurityException) {
            logger.log("Unable to schedule notification listener health check: ${exception.stackTraceToString()}")
        }
    }

    fun cancelHealthCheck(context: Context) {
        try {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .cancel(healthCheckPendingIntent(context))
        } catch (exception: SecurityException) {
            logger.log("Unable to cancel notification listener health check: ${exception.stackTraceToString()}")
        }
    }

    fun requestRebindIfActive(context: Context) {
        if (!isCollectionActive(context)) {
            cancelHealthCheck(context)
            return
        }

        try {
            NotificationListenerService.requestRebind(
                ComponentName(context, NotificationListenerServiceImpl::class.java)
            )
            logger.log("Requested notification listener rebind.")
        } catch (exception: RuntimeException) {
            logger.log("Unable to request notification listener rebind: ${exception.stackTraceToString()}")
        }
    }

    private fun healthCheckPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            HEALTH_CHECK_REQUEST_CODE,
            Intent(context, NotificationListenerHealthCheckReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
