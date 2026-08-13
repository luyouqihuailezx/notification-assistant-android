package com.android.alftendev.activities.home

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.alftendev.BuildConfig
import com.android.alftendev.R
import com.android.alftendev.utils.UiUtils
import com.android.alftendev.utils.Utils

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiUtils.themeValueToTheme(application, com.android.alftendev.utils.MySharedPref.getThemeOptions()))
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.about)
        setContentView(R.layout.activity_about)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.about_settings, AboutFragment())
                .commit()
        }
    }

    class AboutFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.about_preferences, rootKey)
            findPreference<Preference>("version")?.title =
                "${getString(R.string.version)} ${BuildConfig.VERSION_NAME}"
            findPreference<Preference>("rate_app")?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                Utils.openPlayStore(requireContext(), BuildConfig.APPLICATION_ID); true
            }
            findPreference<Preference>("github")?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                Utils.openLink(requireContext(), "https://github.com/Alfio010/notification-listener-android"); true
            }
            findPreference<Preference>("privacy_policy")?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                Utils.openLink(requireContext(), "https://alfio010.github.io/"); true
            }
            bindLicense("app_license", "file:///android_asset/gnu_license.html")
            bindLicense("licenses", "file:///android_asset/open_source_licenses.html")
            bindLicense("icon_licenses", "file:///android_asset/material_icon_license.html")
            bindLicense("mp_chart_license", "file:///android_asset/apache_license.html")
        }

        private fun bindLicense(key: String, path: String) {
            findPreference<Preference>(key)?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                startActivity(Intent(requireContext(), WebViewActivity::class.java).putExtra("filePath", path))
                true
            }
        }
    }
}
