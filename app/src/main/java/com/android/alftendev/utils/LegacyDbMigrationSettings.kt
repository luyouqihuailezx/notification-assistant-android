package com.android.alftendev.utils

import android.content.Context
import java.io.File

object LegacyDbMigrationSettings {
    private const val PREFS_NAME = "legacy_db_migration_v1"
    private const val COMPLETED = "completed"

    fun isCompleted(context: Context): Boolean = preferences(context).getBoolean(COMPLETED, false)

    fun markCompleted(context: Context) {
        preferences(context).edit().putBoolean(COMPLETED, true).apply()
    }

    fun needsMigration(context: Context): Boolean {
        if (isCompleted(context)) return false

        val filesDir = context.filesDir
        val legacyConfig = File(filesDir, "notification_config").exists()
        val oldRules = context.getSharedPreferences("NotInfo", Context.MODE_PRIVATE)
            .contains("dynamic_ai_rules")
        val objectBox = File(filesDir, "objectbox")
        return objectBox.exists() && (legacyConfig || oldRules)
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
