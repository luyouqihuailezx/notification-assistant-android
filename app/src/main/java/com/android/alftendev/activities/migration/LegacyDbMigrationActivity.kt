package com.android.alftendev.activities.migration

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.activities.onboarding.OnboardingActivity
import com.android.alftendev.ui.theme.NotificationListenerTheme
import com.android.alftendev.utils.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
class LegacyDbMigrationActivity : AppCompatActivity() {
    private var exporting by mutableStateOf(false); private var migrating by mutableStateOf(false)
    private var exportDone by mutableStateOf(false); private var error by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiUtils.themeValueToTheme(this, MySharedPref.getThemeOptions())); super.onCreate(savedInstanceState)
        setContent { NotificationListenerTheme { MigrationScreen() } }
    }
    @Composable private fun MigrationScreen() {
        var showConfirm by remember { mutableStateOf(false) }
        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.legacy_migration_title)) }, navigationIcon = { TextButton(onClick = ::finish) { Text("‹") } }) }) { padding ->
            Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(if (exportDone) R.string.legacy_migration_export_done else R.string.legacy_migration_export_message))
                OutlinedButton(onClick = ::exportBackup, enabled = !exporting && !migrating, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.legacy_migration_export)) }
                Button(onClick = { showConfirm = true }, enabled = exportDone && !migrating, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.next_step)) }
                if (exporting || migrating) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (showConfirm) AlertDialog(onDismissRequest = { showConfirm = false }, title = { Text(stringResource(R.string.legacy_migration_confirm_title)) }, text = { Text(stringResource(R.string.legacy_migration_confirm_message)) },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.cancel)) } }, confirmButton = { TextButton(onClick = { showConfirm = false; migrate() }) { Text(stringResource(R.string.confirm)) } })
        if (error != null) ErrorDialog(error!!, { error = null })
    }
    private fun exportBackup() { exporting = true; MyApplication.executor.execute { val result = ImportExport.exportFullAppDataZipEncrypted(this, ""); runOnUiThread { exporting = false; if (result.first == "error") error = result.second else { val zip = File(result.first); if (!zip.exists()) error = getString(R.string.legacy_migration_export_missing) else showPassword(result) } } } }
    private fun showPassword(result: Pair<String, String>) { androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.zip_password).setMessage(getString(R.string.password_should_copy) + "\n" + result.second).setNegativeButton(R.string.cancel, null).setNeutralButton(R.string.copy_string) { _, _ -> copy(result.second) }.setPositiveButton(R.string.legacy_migration_backup_saved) { _, _ -> exportDone = true; File(result.first).delete() }.show() }
    private fun migrate() { migrating = true; MyApplication.executor.execute { try { LegacyDbMigrator.migrate(this); LegacyDbMigrationSettings.markCompleted(this); runOnUiThread { startActivity(Intent(this, OnboardingActivity::class.java)); finish() } } catch (e: Exception) { runOnUiThread { migrating = false; error = e.stackTraceToString() } } } }
    @Composable private fun ErrorDialog(details: String, clear: () -> Unit) { AlertDialog(onDismissRequest = clear, title = { Text(stringResource(R.string.operation_failed)) }, text = { Text(details) }, confirmButton = { TextButton(onClick = clear) { Text(stringResource(android.R.string.ok)) } }, dismissButton = { TextButton(onClick = { copy(details); clear() }) { Text(stringResource(R.string.copy_error)) } }) }
    private fun copy(value: String) { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.error_details), value)) }
}
