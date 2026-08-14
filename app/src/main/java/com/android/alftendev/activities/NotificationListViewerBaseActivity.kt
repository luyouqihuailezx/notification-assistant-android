package com.android.alftendev.activities

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.android.alftendev.MyApplication
import com.android.alftendev.MyApplication.Companion.authSuccess
import com.android.alftendev.MyApplication.Companion.sharedPrefName
import com.android.alftendev.R
import com.android.alftendev.activities.home.AgentSettingsActivity
import com.android.alftendev.activities.home.SettingsActivity
import com.android.alftendev.agent.AgentController
import com.android.alftendev.agent.NotificationAgentScreen
import com.android.alftendev.models.Notifications
import com.android.alftendev.models.getParsedNoti
import com.android.alftendev.services.NotificationListenerKeepAlive
import com.android.alftendev.ui.theme.NotificationListenerTheme
import com.android.alftendev.utils.AuthUtils.askAuth
import com.android.alftendev.utils.DBUtils
import com.android.alftendev.utils.DateUtils.dateFormatterCompact
import com.android.alftendev.utils.MySharedPref
import com.android.alftendev.utils.MySharedPref.RECORD_NOTIFICATIONS_ALREADY_ASKED
import com.android.alftendev.utils.MySharedPref.RECORD_NOTIFICATIONS_ENABLED
import com.android.alftendev.utils.MySharedPref.getIsRecordNotificationsAlreadyAsked
import com.android.alftendev.utils.MySharedPref.getIsRecordNotificationsEnabled
import com.android.alftendev.utils.PermissionUtils.askNotificationServicePermission
import com.android.alftendev.utils.PermissionUtils.isNotificationServiceEnabled
import com.android.alftendev.utils.Utils
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class NotificationListViewerBaseActivity : AppCompatActivity() {
    abstract fun getNotifications(): List<Notifications>
    abstract fun getNotificationsBySearch(filter: String): List<Notifications>

    @SuppressLint("ApplySharedPref")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(com.android.alftendev.utils.UiUtils.themeValueToTheme(this, MySharedPref.getThemeOptions()))
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        askAuth(this)
        setContent {
            NotificationListenerTheme {
                NotificationHost(
                    title = title?.toString().orEmpty(),
                    load = ::getNotifications,
                    search = ::getNotificationsBySearch,
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onOpenAgentSettings = { startActivity(Intent(this, AgentSettingsActivity::class.java)) }
                )
            }
        }
        onBackPressedDispatcher.addCallback(this) { finishAndRemoveTask() }
    }

    override fun onResume() {
        super.onResume()
        if (javaClass.simpleName == "AllNotificationsActivity" && !isNotificationServiceEnabled(this)) {
            Log.w("NotificationList", getString(R.string.explain_not_permission))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (javaClass.simpleName == "AllNotificationsActivity") authSuccess.set(false)
    }

}

private enum class HostTab { HISTORY, AGENT, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationHost(
    title: String,
    load: () -> List<Notifications>,
    search: (String) -> List<Notifications>,
    onOpenSettings: () -> Unit,
    onOpenAgentSettings: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as NotificationListViewerBaseActivity
    var tab by remember { mutableStateOf(HostTab.HISTORY) }
    var notifications by remember { mutableStateOf<List<Notifications>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var detail by remember { mutableStateOf<Notifications?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var permissionPrompt by remember { mutableStateOf(activity.javaClass.simpleName == "AllNotificationsActivity" && !getIsRecordNotificationsEnabled() && !getIsRecordNotificationsAlreadyAsked()) }
    var revision by remember { mutableIntStateOf(0) }
    val agentController = remember { AgentController(context) }

    fun refresh(filter: String = query) {
        loading = true
        MyApplication.executor.execute {
            runCatching { if (filter.isBlank()) load() else search(filter) }
                .onSuccess { result -> activity.runOnUiThread { notifications = result; loading = false; revision++ } }
                .onFailure { throwable ->
                    Log.e("NotificationList", "通知加载失败", throwable)
                    activity.runOnUiThread { error = throwable.stackTraceToString(); loading = false }
                }
        }
    }

    LaunchedEffect(Unit) { refresh("") }
    LaunchedEffect(query) {
        if (query.isNotEmpty()) {
            delay(300)
            refresh(query)
        } else refresh("")
    }
    DisposableEffect(tab) { onDispose { selected = emptySet() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (title.isBlank()) context.getString(R.string.notification_history) else title) },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(painterResource(R.drawable.baseline_delete_24), "删除")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                HostTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { tab = item },
                        icon = { Icon(painterResource(when (item) {
                            HostTab.HISTORY -> R.drawable.baseline_home_24
                            HostTab.AGENT -> R.drawable.baseline_chat_24
                            HostTab.SETTINGS -> R.drawable.baseline_settings_24
                        }), null) },
                        label = { Text(when (item) {
                            HostTab.HISTORY -> context.getString(R.string.notification_history)
                            HostTab.AGENT -> context.getString(R.string.notification_agent)
                            HostTab.SETTINGS -> context.getString(R.string.settings)
                        }) }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            HostTab.HISTORY -> NotificationList(
                modifier = Modifier.padding(padding), notifications = notifications, query = query,
                loading = loading, selected = selected, onQueryChange = { query = it },
                onRefresh = { refresh() }, onOpen = { detail = it },
                onToggle = { id -> selected = selected.toMutableSet().apply { if (!add(id)) remove(id) } },
                onLongPress = { id -> selected = selected + id }
            )
            HostTab.AGENT -> Box(Modifier.fillMaxSize().padding(padding)) { NotificationAgentScreen(agentController) }
            HostTab.SETTINGS -> ComposeSettingsPlaceholder(Modifier.padding(padding), onOpenSettings, onOpenAgentSettings)
        }
    }
    error?.let { details -> CopyableErrorDialog("通知加载失败", details) { error = null } }
    detail?.let { NotificationDetailDialog(it) { detail = null } }
    if (permissionPrompt) {
        AlertDialog(
            onDismissRequest = { permissionPrompt = false },
            title = { Text(context.getString(R.string.ask_not_permission_title)) },
            text = { Text(context.getString(R.string.ask_not_permission)) },
            confirmButton = { Button(onClick = {
                activity.getSharedPreferences(sharedPrefName, android.content.Context.MODE_PRIVATE).edit().putBoolean(RECORD_NOTIFICATIONS_ALREADY_ASKED, true).putBoolean(RECORD_NOTIFICATIONS_ENABLED, true).commit()
                NotificationListenerKeepAlive.scheduleHealthCheckIfActive(activity)
                askNotificationServicePermission(activity)
                permissionPrompt = false
            }) { Text(context.getString(R.string.yes)) } },
            dismissButton = { Button(onClick = {
                activity.getSharedPreferences(sharedPrefName, android.content.Context.MODE_PRIVATE).edit().putBoolean(RECORD_NOTIFICATIONS_ALREADY_ASKED, true).commit()
                permissionPrompt = false
            }) { Text(context.getString(R.string.no)) } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(context.getString(R.string.confirm_delete_noti_warning)) },
            text = { Text(context.getString(R.string.are_you_sure_you_want_to_delete_the_selected_items_they_cannot_be_recovered, selected.size.toString())) },
            confirmButton = { Button(onClick = {
                val deleting = selected
                confirmDelete = false
                selected = emptySet()
                MyApplication.executor.execute {
                    runCatching { DBUtils.deleteMultipleNotificationByIds(deleting.toList()) }
                        .onSuccess { refresh() }
                        .onFailure { throwable -> Log.e("NotificationList", "批量删除失败", throwable); activity.runOnUiThread { error = throwable.stackTraceToString() } }
                }
            }) { Text(context.getString(R.string.confirm)) } },
            dismissButton = { Button(onClick = { confirmDelete = false }) { Text(context.getString(R.string.cancel)) } }
        )
    }
}

@Composable
private fun NotificationList(
    modifier: Modifier, notifications: List<Notifications>, query: String, loading: Boolean, selected: Set<Long>,
    onQueryChange: (String) -> Unit, onRefresh: () -> Unit, onOpen: (Notifications) -> Unit,
    onToggle: (Long) -> Unit, onLongPress: (Long) -> Unit
) {
    Column(modifier.fillMaxSize()) {
        if (selected.isEmpty()) OutlinedTextField(query, onQueryChange, Modifier.fillMaxWidth().padding(12.dp), label = { Text("搜索通知") }, singleLine = true)
        else Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(selected.size == notifications.size && notifications.isNotEmpty(), { if (selected.size == notifications.size) notifications.forEach { if (it.entityId in selected) onToggle(it.entityId) } else notifications.forEach { if (it.entityId !in selected) onToggle(it.entityId) } })
            Text("已选择 ${selected.size} 项")
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notifications, key = Notifications::entityId) { notification ->
                    NotificationRow(notification, notification.entityId in selected, selected.isNotEmpty(), onOpen, onToggle, onLongPress)
                }
            }
            if (loading) Text("正在加载…", Modifier.align(Alignment.Center))
            FloatingActionButton(onClick = onRefresh, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Text("刷新") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationRow(notification: Notifications, isSelected: Boolean, selecting: Boolean, onOpen: (Notifications) -> Unit, onToggle: (Long) -> Unit, onLongPress: (Long) -> Unit) {
    val context = LocalContext.current
    val parsed = remember(notification.entityId) {
        getParsedNoti(notification.title, notification.text, notification.packageName.target.pkg, context)
    }
    val iconDrawable = remember(notification.entityId) { parsed.icon.loadDrawable(context) }
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = { if (selecting) onToggle(notification.entityId) else onOpen(notification) }, onLongClick = { onLongPress(notification.entityId) })) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) Checkbox(isSelected, { onToggle(notification.entityId) })
            Image(
                painter = rememberDrawablePainter(iconDrawable),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(parsed.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(parsed.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(dateFormatterCompact(notification.time), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ComposeSettingsPlaceholder(modifier: Modifier, onOpenSettings: () -> Unit, onOpenAgentSettings: () -> Unit) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)
        Text("设置页面正在迁移为 Compose。现有设置功能仍可直接打开。")
        Button(onClick = onOpenSettings) { Text("打开完整设置") }
        Button(onClick = onOpenAgentSettings) { Text("智能体服务设置") }
    }
}

@Composable
private fun NotificationDetailDialog(notification: Notifications, dismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = dismiss, title = { Text(notification.title) }, text = {
        SelectionContainer { Text(notification.bigText?.trim().takeUnless { it.isNullOrBlank() } ?: notification.text) }
    }, confirmButton = { Button(onClick = { Utils.openApp(notification.packageName.target.pkg, context); dismiss() }) { Text(context.getString(R.string.open_app)) } }, dismissButton = { Button(onClick = dismiss) { Text(context.getString(R.string.back)) } })
}

@Composable
fun CopyableErrorDialog(title: String, details: String, dismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(details, maxLines = 8, overflow = TextOverflow.Ellipsis) }, confirmButton = { Button(onClick = { (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("错误详情", details)) }) { Text("复制完整错误信息") } }, dismissButton = { Button(onClick = dismiss) { Text("关闭") } })
}
