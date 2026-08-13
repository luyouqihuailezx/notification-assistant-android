package com.android.alftendev.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.android.alftendev.utils.CustomLog

class NotificationListenerHealthCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CustomLog("not-listener-health-check").log("Received listener health-check alarm.")
        NotificationListenerKeepAlive.requestRebindIfActive(context)
        NotificationListenerKeepAlive.scheduleHealthCheckIfActive(context)
    }
}
