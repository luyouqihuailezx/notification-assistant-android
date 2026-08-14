package com.android.alftendev.activities.home

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.android.alftendev.R
import com.android.alftendev.ui.theme.NotificationListenerTheme
import com.android.alftendev.utils.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
class PermissionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { NotificationListenerTheme { PermissionsScreen() } } }
    @Composable private fun PermissionsScreen() {
        var refresh by remember { mutableIntStateOf(0) }; val context = this
        val items = listOf(
            R.string.notification_listener_access to Pair({ PermissionUtils.isNotificationServiceEnabled(context) }, { PermissionUtils.openNotificationListenerSettings(context) }),
            R.string.app_list_access to Pair({ PermissionUtils.canReadInstalledApps(context) }, { PermissionUtils.openAppDetailsSettings(context) }),
            R.string.post_notifications_permission to Pair({ PermissionUtils.isNotificationPostPermissionEnabled(context) }, { PermissionUtils.checkPostNotificationPermission(this) }),
            R.string.exact_alarm_access to Pair({ PermissionUtils.isExactAlarmPermissionEnabled(context) }, { PermissionUtils.openExactAlarmSettings(context) }),
            R.string.battery_optimization_access to Pair({ PermissionUtils.isIgnoringBatteryOptimizations(context) }, { PermissionUtils.requestIgnoreBatteryOptimizations(context) })
        )
        DisposableEffect(Unit) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++ }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.permissions)) }) }) { padding -> LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { item { Text(stringResource(R.string.required_permissions), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }; items(items) { (title, state) -> val granted = state.first(); ListItem(headlineContent = { Text(stringResource(title)) }, supportingContent = { Text(stringResource(if (granted) R.string.enabled else R.string.disabled) + " · " + stringResource(R.string.go_to_settings)) }, modifier = Modifier.fillMaxWidth(), trailingContent = { TextButton({ state.second(); refresh++ }) { Text(stringResource(R.string.go_to_settings)) } }) } } }
    }
}
