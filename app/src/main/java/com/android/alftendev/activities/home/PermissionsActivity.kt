package com.android.alftendev.activities.home

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.android.alftendev.R
import com.android.alftendev.ui.theme.NotificationListenerTheme
import com.android.alftendev.utils.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
class PermissionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { NotificationListenerTheme { PermissionsScreen() } } }
    @Composable private fun PermissionsScreen() {
        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.permissions)) }) }) { padding ->
            PermissionManagementContent(Modifier.padding(padding).padding(horizontal = 16.dp))
        }
    }
}

@Composable
internal fun PermissionManagementContent(modifier: Modifier = Modifier) {
        val activity = LocalContext.current as AppCompatActivity
        var refresh by remember { mutableIntStateOf(0) }
        val required = listOf(
            R.string.notification_listener_access to Pair({ PermissionUtils.isNotificationServiceEnabled(activity) }, { PermissionUtils.openNotificationListenerSettings(activity) }),
            R.string.app_list_access to Pair({ PermissionUtils.canReadInstalledApps(activity) }, { PermissionUtils.openAppDetailsSettings(activity) })
        )
        val recommended = listOf(
            R.string.post_notifications_permission to Pair({ PermissionUtils.isNotificationPostPermissionEnabled(activity) }, { PermissionUtils.checkPostNotificationPermission(activity) }),
            R.string.exact_alarm_access to Pair({ PermissionUtils.isExactAlarmPermissionEnabled(activity) }, { PermissionUtils.openExactAlarmSettings(activity) }),
            R.string.battery_optimization_access to Pair({ PermissionUtils.isIgnoringBatteryOptimizations(activity) }, { PermissionUtils.requestIgnoreBatteryOptimizations(activity) })
        )
        DisposableEffect(activity) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++ }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }
        LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { PermissionGroupTitle(R.string.required_permissions) }
            items(required.size) { PermissionItem(required[it], refresh) }
            item { PermissionGroupTitle(R.string.recommended_permissions) }
            items(recommended.size) { PermissionItem(recommended[it], refresh) }
        }
}

@Composable
private fun PermissionGroupTitle(title: Int) = Text(
    stringResource(title),
    color = MaterialTheme.colorScheme.primary,
    style = MaterialTheme.typography.titleSmall,
    modifier = Modifier.padding(top = 16.dp, start = 8.dp)
)

@Composable
private fun PermissionItem(item: Pair<Int, Pair<() -> Boolean, () -> Unit>>, refresh: Int) {
    val granted = item.second.first()
    ListItem(
        headlineContent = { Text(stringResource(item.first)) },
        supportingContent = { Text(stringResource(if (granted) R.string.enabled else R.string.disabled)) },
        modifier = Modifier.fillMaxWidth(),
        trailingContent = { TextButton({ item.second.second() }) { Text(stringResource(R.string.go_to_settings)) } }
    )
}
