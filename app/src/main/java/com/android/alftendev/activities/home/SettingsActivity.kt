package com.android.alftendev.activities.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.android.alftendev.MyApplication.Companion.sharedPrefName
import com.android.alftendev.R
import com.android.alftendev.activities.adaptersactivity.BlackWhiteListActivity
import com.android.alftendev.activities.otheractivity.ImportActivity
import com.android.alftendev.activities.onboarding.OnboardingActivity
import com.android.alftendev.services.NotificationListenerKeepAlive
import com.android.alftendev.ui.theme.NotificationListenerTheme
import com.android.alftendev.utils.AuthUtils.askAuth
import com.android.alftendev.utils.DialogUtils.askPasswordForZipDialog
import com.android.alftendev.utils.MySharedPref
import com.android.alftendev.utils.MySharedPref.RECORD_NOTIFICATIONS_ENABLED
import com.android.alftendev.utils.UiUtils

@OptIn(ExperimentalMaterial3Api::class)
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiUtils.themeValueToTheme(application, MySharedPref.getThemeOptions()))
        super.onCreate(savedInstanceState)
        askAuth(this)
        setContent { NotificationListenerTheme { SettingsScreen() } }
    }

    @Composable
    private fun SettingsScreen() {
        var recordingEnabled by remember {
            mutableStateOf(
            getSharedPreferences(sharedPrefName, MODE_PRIVATE).getBoolean(RECORD_NOTIFICATIONS_ENABLED, false)
            )
        }
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }) },
            bottomBar = { HomeSettingsNavigation() }
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item { SettingsCategory(R.string.app_name) }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.notification_assistant_service)) },
                        supportingContent = { Text(stringResource(R.string.notification_assistant_service_summary)) },
                        trailingContent = {
                            Switch(recordingEnabled, onCheckedChange = { enabled ->
                                recordingEnabled = enabled
                                getSharedPreferences(sharedPrefName, MODE_PRIVATE).edit { putBoolean(RECORD_NOTIFICATIONS_ENABLED, enabled) }
                                NotificationListenerKeepAlive.scheduleHealthCheckIfActive(this@SettingsActivity)
                            })
                        }
                    )
                }
                item { ActionItem(R.string.onboarding, R.string.onboarding) { startActivity(Intent(this@SettingsActivity, OnboardingActivity::class.java)) } }
                item { ActionItem(R.string.permissions) { startActivity(Intent(this@SettingsActivity, PermissionsActivity::class.java)) } }
                item { SettingsCategory(R.string.notification_collection_apps) }
                item { ActionItem(R.string.notification_collection_apps, R.string.notification_collection_apps_summary) { startActivity(Intent(this@SettingsActivity, BlackWhiteListActivity::class.java)) } }
                item { SettingsCategory(R.string.export_import) }
                item { ActionItem(R.string.export, R.string.exports_the_notification_database_to_a_password_encrypted_file) { askPasswordForZipDialog(this@SettingsActivity, this@SettingsActivity) } }
                item { ActionItem(R.string.import_string, R.string.import_notification_database_password_generated_during_export_required) { startActivity(Intent(this@SettingsActivity, ImportActivity::class.java)) } }
                item { SettingsCategory(R.string.notification_agent) }
                item { ActionItem(R.string.agent_service_settings, R.string.agent_service_settings_summary) { startActivity(Intent(this@SettingsActivity, AgentSettingsActivity::class.java)) } }
                item { SettingsCategory(R.string.info) }
                item { ActionItem(R.string.about, R.string.about_summary) { startActivity(Intent(this@SettingsActivity, AboutActivity::class.java)) } }
            }
        }
    }

    @Composable
    private fun SettingsCategory(title: Int) { Text(stringResource(title), Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)) }

    @Composable
    private fun ActionItem(title: Int, summary: Int? = null, onClick: () -> Unit) {
        ListItem(
            headlineContent = { Text(stringResource(title)) },
            supportingContent = summary?.let { { Text(stringResource(it)) } },
            trailingContent = { TextButton(onClick) { Text(stringResource(R.string.more_info)) } }
        )
    }

    @Composable
    private fun HomeSettingsNavigation() {
        Column {
            HorizontalDivider()
            TextButton({ startActivity(Intent(this@SettingsActivity, AllNotificationsActivity::class.java)) }, Modifier.padding(8.dp)) {
                Text(stringResource(R.string.notification_history))
            }
        }
    }
}
