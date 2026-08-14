package com.android.alftendev.activities.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.android.alftendev.MyApplication
import com.android.alftendev.MyApplication.Companion.sharedPrefName
import com.android.alftendev.R
import com.android.alftendev.activities.adaptersactivity.BlackWhiteListActivity
import com.android.alftendev.activities.otheractivity.ImportActivity
import com.android.alftendev.activities.onboarding.OnboardingActivity
import com.android.alftendev.services.NotificationListenerKeepAlive
import com.android.alftendev.ui.theme.NotificationListenerTheme
import com.android.alftendev.utils.AuthUtils.askAuth
import com.android.alftendev.utils.ImportExport
import com.android.alftendev.utils.MySharedPref
import com.android.alftendev.utils.MySharedPref.RECORD_NOTIFICATIONS_ENABLED
import com.android.alftendev.utils.UiUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiUtils.themeValueToTheme(application, MySharedPref.getThemeOptions()))
        super.onCreate(savedInstanceState)
        askAuth(this)
        setContent { NotificationListenerTheme { NotificationSettingsContent() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationSettingsContent(showNavigation: Boolean = true) {
        val activity = LocalContext.current as AppCompatActivity
        var recordingEnabled by remember {
            mutableStateOf(
            activity.getSharedPreferences(sharedPrefName, android.content.Context.MODE_PRIVATE)
                .getBoolean(RECORD_NOTIFICATIONS_ENABLED, false)
            )
        }
        var exportDialog by remember { mutableStateOf(false) }
        var exportPassword by remember { mutableStateOf("") }
        var exporting by remember { mutableStateOf(false) }
        var exportResult by remember { mutableStateOf<Pair<String, String>?>(null) }
        var exportError by remember { mutableStateOf<String?>(null) }
        val clipboard = LocalClipboardManager.current
        if (exportDialog) {
            AlertDialog(
                onDismissRequest = { if (!exporting) exportDialog = false },
                title = { Text(stringResource(R.string.zip_file_password)) },
                text = { OutlinedTextField(exportPassword, { exportPassword = it }, label = { Text(stringResource(R.string.enter_zip_password)) }, visualTransformation = PasswordVisualTransformation()) },
                confirmButton = { Button(enabled = !exporting, onClick = {
                    if (exportPassword.isNotBlank() && !isPasswordValid(exportPassword)) {
                        exportError = activity.getString(R.string.password_invalid_alert)
                    } else {
                        exportDialog = false; exporting = true
                        MyApplication.executor.execute {
                            val result = ImportExport.exportDbZipEncrypted(activity, exportPassword)
                            activity.runOnUiThread { exporting = false; if (result.first == "error") exportError = result.second else exportResult = result }
                        }
                    }
                }) { Text(stringResource(R.string.create_zip)) } },
                dismissButton = { androidx.compose.material3.TextButton(enabled = !exporting, onClick = { exportDialog = false }) { Text(stringResource(R.string.cancel)) } }
            )
        }
        if (exporting) AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.export)) }, text = { CircularProgressIndicator() }, confirmButton = {})
        exportResult?.let { result ->
            AlertDialog(onDismissRequest = { File(result.first).delete(); exportResult = null }, title = { Text(stringResource(R.string.zip_password)) }, text = { Text(stringResource(R.string.password_should_copy) + "\n" + result.second) }, confirmButton = { Button(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(result.second)); File(result.first).delete(); exportResult = null }) { Text(stringResource(R.string.copy_string)) } }, dismissButton = { androidx.compose.material3.TextButton(onClick = { File(result.first).delete(); exportResult = null }) { Text(stringResource(R.string.cancel)) } })
        }
        exportError?.let { details ->
            AlertDialog(onDismissRequest = { exportError = null }, title = { Text(stringResource(R.string.operation_failed)) }, text = { Text(details) }, confirmButton = { Button(onClick = { exportError = null }) { Text(stringResource(android.R.string.ok)) } }, dismissButton = { androidx.compose.material3.TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(details)); exportError = null }) { Text(stringResource(R.string.copy_error)) } })
        }
        Scaffold(
            topBar = { if (showNavigation) TopAppBar(title = { Text(stringResource(R.string.settings)) }) },
            bottomBar = { if (showNavigation) HomeSettingsNavigation(activity) }
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
                                activity.getSharedPreferences(sharedPrefName, android.content.Context.MODE_PRIVATE).edit { putBoolean(RECORD_NOTIFICATIONS_ENABLED, enabled) }
                                NotificationListenerKeepAlive.scheduleHealthCheckIfActive(activity)
                            })
                        }
                    )
                }
                item { ActionItem(R.string.onboarding) { activity.startActivity(Intent(activity, OnboardingActivity::class.java)) } }
                item { ActionItem(R.string.permissions) { activity.startActivity(Intent(activity, PermissionsActivity::class.java)) } }
                item { SettingsCategory(R.string.notification_collection_apps) }
                item { ActionItem(R.string.notification_collection_apps, stringResource(R.string.notification_collection_apps_summary)) { activity.startActivity(Intent(activity, BlackWhiteListActivity::class.java)) } }
                item { SettingsCategory(R.string.export_import) }
                item { ActionItem(R.string.export, stringResource(R.string.exports_the_notification_database_to_a_password_encrypted_file)) { exportDialog = true } }
                item { ActionItem(R.string.import_string, stringResource(R.string.import_notification_database_password_generated_during_export_required)) { activity.startActivity(Intent(activity, ImportActivity::class.java)) } }
                item { SettingsCategory(R.string.notification_agent) }
                item { ActionItem(R.string.agent_service_settings, stringResource(R.string.agent_service_settings_summary)) { activity.startActivity(Intent(activity, AgentSettingsActivity::class.java)) } }
                item { SettingsCategory(R.string.info) }
                item { ActionItem(R.string.about, stringResource(R.string.about_summary)) { activity.startActivity(Intent(activity, AboutActivity::class.java)) } }
            }
        }
    }

    private fun isPasswordValid(password: String): Boolean = password.length >= 8 && password.any(Char::isDigit)

    @Composable
    private fun SettingsCategory(title: Int) {
        Text(
            stringResource(title),
            Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall
        )
    }

    @Composable
    private fun ActionItem(title: Int, summary: String? = null, onClick: () -> Unit) {
        ListItem(
            modifier = Modifier.clickable(onClick = onClick),
            headlineContent = { Text(stringResource(title)) },
            supportingContent = summary?.let { { Text(it) } }
        )
    }

    @Composable
    private fun HomeSettingsNavigation(activity: AppCompatActivity) {
        Column {
            HorizontalDivider()
            TextButton({ activity.startActivity(Intent(activity, AllNotificationsActivity::class.java)) }, Modifier.padding(8.dp)) {
                Text(stringResource(R.string.notification_history))
            }
        }
}
