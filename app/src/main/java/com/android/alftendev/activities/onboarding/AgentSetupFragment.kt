package com.android.alftendev.activities.onboarding

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.android.alftendev.R
import com.android.alftendev.agent.AgentProviderSettings
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class AgentSetupFragment : Fragment() {
    private lateinit var baseUrl: TextInputEditText
    private lateinit var model: TextInputEditText
    private lateinit var token: TextInputEditText
    private var useDeepSeek = true
    private val settings by lazy { AgentProviderSettings(requireContext()) }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val configuration = settings.configuration()
        useDeepSeek = configuration.useDeepSeek
        val scroll = ScrollView(requireContext())
        val body = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 8, 8, 24) }
        body.addView(TextInputLayout(requireContext()).apply { hint = getString(R.string.agent_api_token); token = TextInputEditText(context).apply { setText(configuration.token); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }; addView(token) }, params())
        val switch = SwitchMaterial(requireContext()).apply { text = getString(R.string.agent_use_deepseek); isChecked = useDeepSeek }
        body.addView(switch, params())
        val custom = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; isVisible = !useDeepSeek }
        baseUrl = TextInputEditText(requireContext()).apply { setText(configuration.baseUrl) }
        model = TextInputEditText(requireContext()).apply { setText(configuration.model) }
        custom.addView(input(R.string.agent_base_url, baseUrl), params())
        custom.addView(input(R.string.agent_model, model), params())
        body.addView(custom, params())
        body.addView(button(R.string.agent_get_token) { (activity as OnboardingActivity).openUrl(AgentProviderSettings.DEEPSEEK_API_KEYS_URL) }, params())
        body.addView(button(R.string.agent_verify) { save(); (activity as OnboardingActivity).verifyAgent(settings) }, params())
        switch.setOnCheckedChangeListener { _, checked -> useDeepSeek = checked; custom.isVisible = !checked }
        scroll.addView(body, android.widget.FrameLayout.LayoutParams(-1, -2))
        return scroll
    }

    fun save() {
        settings.save(useDeepSeek, if (useDeepSeek) AgentProviderSettings.DEFAULT_BASE_URL else baseUrl.text.toString(), if (useDeepSeek) AgentProviderSettings.DEFAULT_MODEL else model.text.toString(), token.text.toString())
    }

    private fun input(label: Int, input: TextInputEditText) = TextInputLayout(requireContext()).apply { hint = getString(label); addView(input) }
    private fun button(text: Int, action: () -> Unit) = Button(requireContext()).apply { setText(text); setOnClickListener { try { action() } catch (error: Exception) { (activity as OnboardingActivity).showError(error) } } }
    private fun params() = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
}
