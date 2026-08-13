package com.android.alftendev.activities.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.activities.adaptersactivity.BlackWhiteListActivity
import com.android.alftendev.activities.home.AllNotificationsActivity
import com.android.alftendev.agent.AgentProviderSettings
import com.android.alftendev.agent.NotificationAgentService
import com.android.alftendev.utils.DBUtils
import com.android.alftendev.utils.PermissionUtils
import com.android.alftendev.utils.UiUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class OnboardingActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private lateinit var back: Button
    private lateinit var next: Button
    private lateinit var permissionStatus: TextView
    private lateinit var heading: TextView
    private var step = 0
    private var useDeepSeek = true
    private val onboarding by lazy { OnboardingSettings(this) }
    private val provider by lazy { AgentProviderSettings(this) }
    private lateinit var baseUrl: TextInputEditText
    private lateinit var model: TextInputEditText
    private lateinit var token: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiUtils.themeValueToTheme(this, com.android.alftendev.utils.MySharedPref.getThemeOptions()))
        super.onCreate(savedInstanceState)
        title = getString(R.string.onboarding)
        setContentView(buildRoot())
        showStep(savedInstanceState?.getInt(KEY_STEP) ?: 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_STEP, step)
        super.onSaveInstanceState(outState)
    }

    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }
        heading = TextView(this).apply {
            textSize = 21f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(heading, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(8))
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(12))
        }
        scroll.addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        back = Button(this).apply {
            text = getString(R.string.back)
            setOnClickListener { showStep(step - 1) }
        }
        next = Button(this).apply {
            text = getString(R.string.next_step)
            setOnClickListener { nextStep() }
        }
        actions.addView(back, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(8) })
        actions.addView(next, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(actions, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun showStep(value: Int) {
        step = value.coerceIn(0, 2)
        content.removeAllViews()
        heading.text = getString(listOf(R.string.onboarding_step_agent, R.string.onboarding_step_permissions, R.string.onboarding_step_apps)[step])
        back.isVisible = step > 0
        next.isEnabled = true
        when (step) {
            0 -> showAgent()
            1 -> showPermissions()
            2 -> showApps()
        }
        next.text = getString(if (step == 2) R.string.finish else R.string.next_step)
        content.post { content.parent?.requestLayout() }
    }

    private fun showAgent() {
        val configuration = provider.configuration()
        useDeepSeek = configuration.useDeepSeek
        content.addView(description(getString(R.string.agent_service_settings_summary)))

        val switch = SwitchMaterial(this).apply {
            text = getString(R.string.agent_use_deepseek)
            isChecked = useDeepSeek
        }
        content.addView(switch, fieldParams())

        token = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(configuration.token)
        }
        content.addView(field(getString(R.string.agent_api_token), token), fieldParams())

        val custom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; isVisible = !useDeepSeek }
        baseUrl = TextInputEditText(this).apply { setText(configuration.baseUrl) }
        model = TextInputEditText(this).apply { setText(configuration.model) }
        custom.addView(field(getString(R.string.agent_base_url), baseUrl), fieldParams())
        custom.addView(field(getString(R.string.agent_model), model), fieldParams())
        content.addView(custom, fieldParams())

        switch.setOnCheckedChangeListener { _, checked ->
            useDeepSeek = checked
            custom.isVisible = !checked
        }
        content.addView(actionButton(R.string.agent_get_token) { openUrl(AgentProviderSettings.DEEPSEEK_API_KEYS_URL) }, fieldParams())
        content.addView(actionButton(R.string.agent_verify) { verifyAgent() }, fieldParams())
    }

    private fun verifyAgent() {
        try {
            saveAgent()
            MyApplication.executor.execute {
                val result = NotificationAgentService(provider).verify()
                runOnUiThread {
                    when (result) {
                        is NotificationAgentService.VerificationResult.Success -> showMessage(getString(R.string.verification_success))
                        is NotificationAgentService.VerificationResult.Failure -> showError(result.error)
                    }
                }
            }
        } catch (error: Exception) {
            showError(error)
        }
    }

    private fun saveAgent() {
        provider.save(
            useDeepSeek,
            if (useDeepSeek) AgentProviderSettings.DEFAULT_BASE_URL else baseUrl.text.toString(),
            if (useDeepSeek) AgentProviderSettings.DEFAULT_MODEL else model.text.toString(),
            token.text.toString()
        )
    }

    private fun showPermissions() {
        content.addView(description(getString(R.string.required_permissions)))
        permissionStatus = description("")
        content.addView(permissionStatus, fieldParams())
        content.addView(actionButton(R.string.notification_listener_access) {
            PermissionUtils.openNotificationListenerSettings(this)
        }, fieldParams())
        content.addView(actionButton(R.string.app_list_access) {
            PermissionUtils.openAppDetailsSettings(this)
        }, fieldParams())
        refreshPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (::permissionStatus.isInitialized && step == 1) refreshPermissions()
    }

    private fun refreshPermissions() {
        val ok = PermissionUtils.isNotificationServiceEnabled(this) && PermissionUtils.canReadInstalledApps(this)
        permissionStatus.text = if (ok) getString(R.string.enabled) else getString(R.string.required_permissions_missing)
        next.isEnabled = ok
    }

    private fun showApps() {
        content.addView(description(getString(R.string.notification_collection_apps_summary)))
        content.addView(actionButton(R.string.notification_collection_apps) {
            startActivity(Intent(this, BlackWhiteListActivity::class.java))
        }, fieldParams())
        initializeDefaults()
    }

    private fun initializeDefaults() {
        if (onboarding.defaultsInitialized) return
        MyApplication.executor.execute {
            DBUtils.addAllInstalledPackageNames()
            listOf("com.tencent.mobileqq", "com.tencent.mm").forEach { packageName ->
                DBUtils.getPackageName(packageName)?.let { entity ->
                    entity.isWhiteList = true
                    entity.isBlackList = false
                    MyApplication.packageNames.put(entity)
                }
            }
            onboarding.defaultsInitialized = true
        }
    }

    private fun nextStep() {
        try {
            if (step == 0) saveAgent()
            if (step < 2) showStep(step + 1) else complete()
        } catch (error: Exception) {
            showError(error)
        }
    }

    private fun complete() {
        onboarding.completed = true
        startActivity(Intent(this, AllNotificationsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (error: Exception) {
            showError(error)
        }
    }

    private fun field(label: String, input: TextInputEditText) = TextInputLayout(this).apply {
        hint = label
        addView(input, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun description(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        setPadding(dp(4), dp(6), dp(4), dp(10))
    }

    private fun actionButton(textRes: Int, action: () -> Unit) = Button(this).apply {
        text = getString(textRes)
        setOnClickListener { action() }
        minHeight = dp(48)
    }

    private fun fieldParams() = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(8)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showMessage(message: String) = MaterialAlertDialogBuilder(this)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok, null)
        .show()

    private fun showError(error: Exception) {
        val details = error.stackTraceToString()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.operation_failed)
            .setMessage(details)
            .setNeutralButton(R.string.copy_error) { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.error_details), details))
            }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    companion object {
        private const val KEY_STEP = "onboarding_step"
    }
}
