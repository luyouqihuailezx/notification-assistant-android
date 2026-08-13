package com.android.alftendev.activities.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.alftendev.R
import com.android.alftendev.utils.PermissionUtils
import com.android.alftendev.utils.UiUtils

class PermissionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiUtils.themeValueToTheme(application, com.android.alftendev.utils.MySharedPref.getThemeOptions()))
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.permissions)
        setContentView(R.layout.activity_permissions)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.permissions_settings, PermissionsFragment())
                .commit()
        }
    }

    class PermissionsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.permissions_preferences, rootKey)
        }

        override fun onResume() {
            super.onResume()
            bindPermissions()
        }

        private fun bindPermissions() {
            findPreference<Preference>("notification_listener_access")?.apply {
                summary = permissionSummary(PermissionUtils.isNotificationServiceEnabled(requireContext()))
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    PermissionUtils.openNotificationListenerSettings(requireContext())
                    true
                }
            }
            findPreference<Preference>("post_notifications")?.apply {
                summary = permissionSummary(PermissionUtils.isNotificationPostPermissionEnabled(requireContext()))
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    PermissionUtils.checkPostNotificationPermission(requireActivity())
                    true
                }
            }
            findPreference<Preference>("exact_alarm_access")?.apply {
                summary = permissionSummary(PermissionUtils.isExactAlarmPermissionEnabled(requireContext()))
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    PermissionUtils.openExactAlarmSettings(requireContext())
                    true
                }
            }
            findPreference<Preference>("battery_optimization_access")?.apply {
                summary = permissionSummary(PermissionUtils.isIgnoringBatteryOptimizations(requireContext()))
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    PermissionUtils.requestIgnoreBatteryOptimizations(requireContext())
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
