package com.android.alftendev.utils

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.android.alftendev.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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

            openSettings(context, intent)
        }
    }

    fun openNotificationListenerSettings(context: Context) {
        openSettings(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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
        openSettings(
            context,
            Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
            )
        )
    }

    fun openAppNotificationSettings(context: Context) {
        openSettings(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }

    fun isExactAlarmPermissionEnabled(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            openSettings(
                context,
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)

    fun requestIgnoreBatteryOptimizations(context: Context) {
        openSettings(
            context,
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }

    private fun openSettings(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            CustomLog("permission-utils").log("Unable to open app details settings: ${e.stackTraceToString()}")
            showSettingsError(context, e)
        }
    }

    private fun showSettingsError(context: Context, exception: Exception) {
        val details = exception.stackTraceToString()
        if (context is AppCompatActivity) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.open_settings_failed)
                .setMessage(details)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.copy_error) { _, _ ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(context.getString(R.string.error_details), details)
                    )
                }
                .show()
        } else {
            CustomLog("permission-utils").log("Cannot show settings error dialog: $details")
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