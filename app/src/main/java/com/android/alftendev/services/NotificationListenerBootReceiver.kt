package com.android.alftendev.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.android.alftendev.utils.CustomLog

class NotificationListenerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        CustomLog("not-listener-boot").log("Device boot completed; restoring listener health checks.")
        NotificationListenerKeepAlive.requestRebindIfActive(context)
        NotificationListenerKeepAlive.scheduleHealthCheckIfActive(context)
    }
}
