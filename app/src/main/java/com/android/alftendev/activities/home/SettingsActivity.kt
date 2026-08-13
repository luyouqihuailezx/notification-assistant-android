package com.android.alftendev.activities.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.android.alftendev.BuildConfig
import com.android.alftendev.MyApplication
import com.android.alftendev.MyApplication.Companion.sharedPrefName
import com.android.alftendev.R
import com.android.alftendev.activities.adaptersactivity.BlackWhiteListActivity
import com.android.alftendev.activities.otheractivity.ImportActivity
import com.android.alftendev.utils.AuthUtils.askAuth
import com.android.alftendev.utils.DialogUtils.askPasswordForZipDialog
import com.android.alftendev.utils.MySharedPref.RECORD_NOTIFICATIONS_ENABLED
import com.android.alftendev.utils.PermissionUtils
import com.android.alftendev.utils.UiUtils
import com.android.alftendev.utils.UiUtils.setTheme
import com.android.alftendev.utils.UiUtils.uiDefaultSettings
import com.google.android.material.bottomnavigation.BottomNavigationView

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiUtils.themeValueToTheme(application, com.android.alftendev.utils.MySharedPref.getThemeOptions()))
        super.onCreate(savedInstanceState)
        askAuth(this)
        setContentView(R.layout.settings_activity)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        uiDefaultSettings(this)
        findViewById<BottomNavigationView>(R.id.bottomNavigation).apply {
            selectedItemId = R.id.nav_settings
            setOnItemSelectedListener { item ->
                if (item.itemId == R.id.nav_history) {
                    startActivity(Intent(this@SettingsActivity, AllNotificationsActivity::class.java))
                }
                true
            }
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            bindServiceSwitch()
            bindPermissions()
            findPreference<Preference>("open_collection_apps")?.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    startActivity(Intent(requireContext(), BlackWhiteListActivity::class.java))
                    true
                }
            findPreference<Preference>("export_db")?.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    askPasswordForZipDialog(requireContext(), activity)
                    true
                }
            findPreference<Preference>("import_db")?.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    startActivity(Intent(requireContext(), ImportActivity::class.java))
                    true
                }
            findPreference<Preference>("open_about")?.onPreferenceClickListener =
                Preference.OnPreferenceClickListener {
                    startActivity(Intent(requireContext(), AboutActivity::class.java))
                    true
                }
        }

        override fun onResume() {
            super.onResume()
            bindPermissions()
        }

        private fun bindServiceSwitch() {
            findPreference<SwitchPreferenceCompat>(RECORD_NOTIFICATIONS_ENABLED)?.apply {
                isChecked = requireContext().getSharedPreferences(sharedPrefName, MODE_PRIVATE)
                    .getBoolean(RECORD_NOTIFICATIONS_ENABLED, false)
                setOnPreferenceChangeListener { _, value ->
                    requireContext().getSharedPreferences(sharedPrefName, MODE_PRIVATE).edit {
                        putBoolean(RECORD_NOTIFICATIONS_ENABLED, value as Boolean)
                    }
                    true
                }
            }
        }

        private fun bindPermissions() {
            findPreference<Preference>("notification_listener_access")?.apply {
                summary = permissionSummary(PermissionUtils.isNotificationServiceEnabled(requireContext()))
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    PermissionUtils.openNotificationListenerSettings(requireContext())
                    true
                }
            }
            findPreference<Preference>("app_list_access")?.apply {
                summary = permissionSummary(PermissionUtils.canReadInstalledApps(requireContext()))
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    PermissionUtils.openAppDetailsSettings(requireContext())
                    true
                }
            }
        }

        private fun permissionSummary(granted: Boolean): String =
            "${getString(if (granted) R.string.enabled else R.string.disabled)} · ${getString(R.string.go_to_settings)}"
    }
}
