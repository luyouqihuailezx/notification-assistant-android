package com.android.alftendev.utils

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.android.alftendev.R

object PermissionUtils {
    fun isNotificationServiceEnabled(context: Context): Boolean {
        return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.split(":", ",")
            ?.any { it.startsWith(context.packageName + "/") } == true
    }

    fun askNotificationServicePermission(context: Context) {
        if (!isNotificationServiceEnabled(context)) {
            UiUtils.showToast(
                context.getString(R.string.explain_not_permission),
                context as AppCompatActivity
            )

            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }

            context.startActivity(intent)
        }
    }

    fun openNotificationListenerSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    @Suppress("DEPRECATION")
    fun canReadInstalledApps(context: Context): Boolean {
        return try {
            context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA).isNotEmpty()
        } catch (e: SecurityException) {
            CustomLog("permission-utils").log("Unable to read installed apps: ${e.stackTraceToString()}")
            false
        }
    }

    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            CustomLog("permission-utils").log("Unable to open app details settings: ${e.stackTraceToString()}")
            UiUtils.showToast(context.getString(R.string.open_settings_failed), context as AppCompatActivity)
        }
    }

    fun isNotificationPostPermissionEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.areNotificationsEnabled()
        }
    }

    fun checkPostNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationPostPermissionEnabled(
                context
            )
        ) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }
    }
}