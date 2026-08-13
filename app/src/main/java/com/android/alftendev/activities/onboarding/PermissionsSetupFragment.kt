package com.android.alftendev.activities.onboarding

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.alftendev.R
import com.android.alftendev.utils.PermissionUtils

class PermissionsSetupFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) { setPreferencesFromResource(R.xml.permissions_preferences, rootKey) }
    override fun onResume() { super.onResume(); bindPermissions(); (activity as? OnboardingActivity)?.refreshPermissionStep(isReady(requireContext())) }
    private fun bindPermissions() {
        bind("notification_listener_access", PermissionUtils.isNotificationServiceEnabled(requireContext())) { PermissionUtils.openNotificationListenerSettings(requireContext()) }
        bind("app_list_access", PermissionUtils.canReadInstalledApps(requireContext())) { PermissionUtils.openAppDetailsSettings(requireContext()) }
        bind("post_notifications", PermissionUtils.isNotificationPostPermissionEnabled(requireContext())) { PermissionUtils.checkPostNotificationPermission(requireActivity()) }
        bind("exact_alarm_access", PermissionUtils.isExactAlarmPermissionEnabled(requireContext())) { PermissionUtils.openExactAlarmSettings(requireContext()) }
        bind("battery_optimization_access", PermissionUtils.isIgnoringBatteryOptimizations(requireContext())) { PermissionUtils.requestIgnoreBatteryOptimizations(requireContext()) }
    }
    private fun bind(key: String, granted: Boolean, action: () -> Unit) { findPreference<Preference>(key)?.apply { summary = "${getString(if (granted) R.string.enabled else R.string.disabled)} · ${getString(R.string.go_to_settings)}"; onPreferenceClickListener = Preference.OnPreferenceClickListener { action(); true } } }
    companion object { fun isReady(context: android.content.Context) = PermissionUtils.isNotificationServiceEnabled(context) && PermissionUtils.canReadInstalledApps(context) }
}
