package com.android.alftendev.activities.home

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.android.alftendev.R
import com.android.alftendev.agent.AgentSettings
import com.android.alftendev.utils.MySharedPref
import com.android.alftendev.utils.UiUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AgentSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiUtils.themeValueToTheme(application, MySharedPref.getThemeOptions()))
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.agent_service_settings)
        setContentView(R.layout.activity_agent_settings)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.agent_settings, AgentSettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class AgentSettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.agent_preferences, rootKey)
            val settings = AgentSettings(requireContext())
            val configuration = settings.configuration()

            findPreference<EditTextPreference>("agent_base_url")?.apply {
                text = configuration.baseUrl
                setOnPreferenceChangeListener { _, value ->
                    save(settings, baseUrl = value as String)
                }
            }
            findPreference<EditTextPreference>("agent_model")?.apply {
                text = configuration.model
                setOnPreferenceChangeListener { _, value ->
                    save(settings, model = value as String)
                }
            }
            findPreference<EditTextPreference>("agent_token")?.apply {
                text = configuration.token
                summary = tokenSummary(configuration.token)
                setOnBindEditTextListener { field: EditText ->
                    field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                setOnPreferenceChangeListener { preference, value ->
                    val saved = save(settings, token = value as String)
                    if (saved) preference.summary = tokenSummary(value)
                    saved
                }
            }
        }

        private fun tokenSummary(token: String): String {
            return getString(if (token.isBlank()) R.string.agent_token_unset else R.string.agent_token_set)
        }

        private fun save(
            settings: AgentSettings,
            baseUrl: String = settings.configuration().baseUrl,
            model: String = settings.configuration().model,
            token: String = settings.configuration().token
        ): Boolean {
            return runCatching { settings.save(baseUrl, model, token) }
                .onFailure { error ->
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.agent_settings_save_failed)
                        .setMessage(error.stackTraceToString())
                        .setPositiveButton(R.string.copy_error) { _, _ ->
                            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    getString(R.string.error_details),
                                    error.stackTraceToString()
                                )
                            )
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
                .isSuccess
        }
    }
}
