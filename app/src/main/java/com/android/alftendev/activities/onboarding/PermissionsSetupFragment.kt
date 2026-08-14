package com.android.alftendev.activities.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.alftendev.R
import com.android.alftendev.utils.PermissionUtils

@Composable fun PermissionsSetupFragment() {
    val context = LocalContext.current
    data class PermissionRow(val label: Int, val granted: Boolean, val action: () -> Unit)
    val permissions = listOf(
        PermissionRow(R.string.notification_listener_access, PermissionUtils.isNotificationServiceEnabled(context)) { PermissionUtils.openNotificationListenerSettings(context) },
        PermissionRow(R.string.app_list_access, PermissionUtils.canReadInstalledApps(context)) { PermissionUtils.openAppDetailsSettings(context) },
        PermissionRow(R.string.post_notifications_permission, PermissionUtils.isNotificationPostPermissionEnabled(context)) { PermissionUtils.checkPostNotificationPermission(context) },
        PermissionRow(R.string.exact_alarm_access, PermissionUtils.isExactAlarmPermissionEnabled(context)) { PermissionUtils.openExactAlarmSettings(context) },
        PermissionRow(R.string.battery_optimization_access, PermissionUtils.isIgnoringBatteryOptimizations(context)) { PermissionUtils.requestIgnoreBatteryOptimizations(context) }
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { permissions.forEach { row -> ListItem(headlineContent = { Text(stringResource(row.label)) }, supportingContent = { Text(stringResource(if (row.granted) R.string.enabled else R.string.disabled)) }, trailingContent = { TextButton(onClick = row.action) { Text(stringResource(R.string.go_to_settings)) } }) } }
}
fun isReady(context: android.content.Context) = PermissionUtils.isNotificationServiceEnabled(context) && PermissionUtils.canReadInstalledApps(context)
