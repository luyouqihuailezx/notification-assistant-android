package com.android.alftendev.activities.otheractivity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.ui.theme.NotificationListenerTheme
import com.android.alftendev.utils.CustomLog
import com.android.alftendev.utils.ImportExport
import com.android.alftendev.utils.ImportExport.copyUriToFile
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
class ImportActivity : AppCompatActivity() {
    private lateinit var picker: ActivityResultLauncher<Intent>
    private var password = ""
    private var showError by mutableStateOf<String?>(null)
    private var working by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) { showError = getString(R.string.file_selection_failed, result.resultCode); return@registerForActivityResult }
            val uri = result.data?.data ?: run { showError = getString(R.string.file_not_found); return@registerForActivityResult }
            val zip = copyUriToFile(this, uri)
            if (zip == null || !zip.exists()) { showError = getString(R.string.zip_file_unavailable); return@registerForActivityResult }
            working = true
            MyApplication.executor.execute {
                val result = try { ImportExport.importZipDecryptAndPrintStreaming(zip, password) }
                finally { zip.delete() }
                runOnUiThread { working = false; if (result.success) finish() else showError = result.errorDetails.orEmpty() }
            }
        }
        setContent { NotificationListenerTheme { ImportScreen(::selectFile, working, showError) { showError = null } } }
    }

    private fun selectFile(value: String): Unit {
        if (value.isBlank()) { showError = getString(R.string.enter_zip_password); return }
        password = value
        picker.launch(Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/zip"; addCategory(Intent.CATEGORY_OPENABLE)
        }, getString(R.string.select_the_zip)))
    }

    @Composable private fun ImportScreen(onSelect: (String) -> Unit, loading: Boolean, error: String?, clear: () -> Unit) {
        var value by remember { mutableStateOf("") }
        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.import_string)) }) }) { padding ->
            Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.import_notification_database_password_generated_during_export_required))
                OutlinedTextField(value, { value = it }, label = { Text(stringResource(R.string.zip_password)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { onSelect(value) }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_the_zip)) }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        ErrorDialog(error, clear)
    }

    @Composable private fun ErrorDialog(details: String?, clear: () -> Unit) {
        if (details == null) return
        AlertDialog(onDismissRequest = clear, title = { Text(stringResource(R.string.import_failed)) }, text = { Text(details.ifBlank { stringResource(R.string.operation_failed) }) },
            confirmButton = { TextButton(onClick = clear) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { copy(details); clear() }) { Text(stringResource(R.string.copy_error)) } })
    }
    private fun copy(details: String) { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.error_details), details)) }
    companion object { val LOGGER = CustomLog("noti-importer-activity") }
}
