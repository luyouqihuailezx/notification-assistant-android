package com.android.alftendev.activities.migration

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.activities.onboarding.OnboardingActivity
import com.android.alftendev.utils.ImportExport
import com.android.alftendev.utils.LegacyDbMigrationSettings
import com.android.alftendev.utils.LegacyDbMigrator
import com.android.alftendev.utils.UiUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File

class LegacyDbMigrationActivity : AppCompatActivity() {
    private lateinit var description: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var next: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiUtils.themeValueToTheme(this, com.android.alftendev.utils.MySharedPref.getThemeOptions()))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legacy_db_migration)
        findViewById<MaterialToolbar>(R.id.legacyMigrationToolbar).setNavigationOnClickListener { finish() }
        description = findViewById(R.id.legacyMigrationDescription)
        progress = findViewById(R.id.legacyMigrationProgress)
        next = findViewById(R.id.legacyMigrationNext)
        findViewById<MaterialButton>(R.id.legacyMigrationExport).setOnClickListener { exportBackup() }
        next.setOnClickListener { confirmMigration() }
    }

    private fun exportBackup() {
        next.isEnabled = false
        progress.visibility = View.VISIBLE
        MyApplication.executor.execute {
            val result = ImportExport.exportDbZipEncrypted(this, "")
            runOnUiThread {
                progress.visibility = View.GONE
                if (result.first == "error") showError(result.second) else showExportSuccess(result)
            }
        }
    }

    private fun showExportSuccess(result: Pair<String, String>) {
        val zip = File(result.first)
        if (!zip.exists()) {
            showError(getString(R.string.legacy_migration_export_missing))
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.zip_password)
            .setMessage(getString(R.string.password_should_copy) + "\n${result.second}")
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.copy_string) { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.zip_password), result.second))
            }
            .setPositiveButton(R.string.legacy_migration_backup_saved) { _, _ ->
                next.isEnabled = true
                description.setText(R.string.legacy_migration_export_done)
                zip.delete()
            }
            .show()
    }

    private fun confirmMigration() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.legacy_migration_confirm_title)
            .setMessage(R.string.legacy_migration_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ -> migrate() }
            .show()
    }

    private fun migrate() {
        next.isEnabled = false
        findViewById<MaterialButton>(R.id.legacyMigrationExport).isEnabled = false
        progress.visibility = View.VISIBLE
        MyApplication.executor.execute {
            try {
                LegacyDbMigrator.migrate(this)
                LegacyDbMigrationSettings.markCompleted(this)
                runOnUiThread {
                    startActivity(android.content.Intent(this, OnboardingActivity::class.java))
                    finish()
                }
            } catch (error: Exception) {
                runOnUiThread { showError(error.stackTraceToString()); next.isEnabled = true }
            }
        }
    }

    private fun showError(details: String?) {
        val error = details ?: getString(R.string.operation_failed)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.operation_failed)
            .setMessage(error)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.copy_error) { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.error_details), error))
            }
            .show()
    }
}
