package com.android.alftendev.activities.otheractivity

import android.app.Dialog
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.utils.CustomLog
import com.android.alftendev.utils.ImportExport
import com.android.alftendev.utils.ImportExport.copyUriToFile
import com.android.alftendev.utils.UiUtils
import com.android.alftendev.utils.UiUtils.showLoadingDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ImportActivity : AppCompatActivity() {
    companion object {
        val LOGGER = CustomLog("noti-importer-activity")

        private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
        private var zipPassword = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_import)

        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                LOGGER.log("received resultCode ${result.resultCode}")
                if (result.resultCode == RESULT_OK) {
                    val uri = result.data?.data

                    if (uri == null) {
                        LOGGER.log("uri is null")
                        UiUtils.showToast(getString(R.string.file_not_found), this)
                        return@registerForActivityResult
                    }

                    val zipFile = copyUriToFile(this, uri)

                    if (zipFile != null) {
                        if (zipFile.exists()) {
                            if (zipPassword.isEmpty()) {
                                UiUtils.showToast(getString(R.string.enter_zip_password), this)
                                LOGGER.log("Zip password is empty")
                                return@registerForActivityResult
                            }

                            LOGGER.log("processing zip")
                            UiUtils.showToast(getString(R.string.importing_zip), this)

                            var loadingDialog: Dialog? = null

                            runOnUiThread {
                                loadingDialog = showLoadingDialog(this)
                            }

                            MyApplication.executor.execute {
                                val result = ImportExport.importZipDecryptAndPrintStreaming(
                                    zipFile,
                                    zipPassword
                                )

                                runOnUiThread {
                                    loadingDialog?.dismiss()

                                    if (result.success) {
                                        UiUtils.showToast(
                                            getString(R.string.import_completed),
                                            this
                                        )
                                    } else {
                                        showImportError(result.errorDetails.orEmpty())
                                    }
                                }
                            }
                        } else {
                            UiUtils.showToast(getString(R.string.zip_does_not_exist), this)
                            LOGGER.log(getString(R.string.zip_does_not_exist))
                        }
                    } else {
                        UiUtils.showToast(getString(R.string.zip_file_unavailable), this)
                        LOGGER.log("zip null")
                    }
                } else {
                    UiUtils.showToast(
                        getString(R.string.file_selection_failed, result.resultCode),
                        this
                    )
                }
            }

        val etZipPassword = findViewById<EditText>(R.id.etZipPassword)
        val bSelectZipFile = findViewById<MaterialButton>(R.id.bSelectZipFile)

        bSelectZipFile.setOnClickListener {
            if (etZipPassword.text.isNullOrEmpty()) {
                UiUtils.showToast(getString(R.string.enter_zip_password), this)
                return@setOnClickListener
            }

            zipPassword = etZipPassword.text.toString()

            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/zip"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            filePickerLauncher.launch(
                Intent.createChooser(
                    intent,
                    getString(R.string.select_the_zip)
                )
            )
        }
    }

    private fun showImportError(details: String) {
        LOGGER.log("Import failed: $details")
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.import_failed))
            .setMessage(details.ifBlank { getString(R.string.operation_failed) })
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.copy_error) { _, _ ->
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.error_details), details))
                UiUtils.showToast(getString(R.string.copy_string), this)
            }
            .show()
    }
}