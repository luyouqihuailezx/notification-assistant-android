package com.android.alftendev.activities.onboarding

import android.content.Intent
import android.graphics.Color
import android.text.InputType
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.activities.MainActivity
import com.android.alftendev.activities.adaptersactivity.BlackWhiteListActivity
import com.android.alftendev.activities.home.AllNotificationsActivity
import com.android.alftendev.agent.AgentProviderSettings
import com.android.alftendev.agent.NotificationAgentService
import com.android.alftendev.models.PackageName
import com.android.alftendev.utils.DBUtils
import com.android.alftendev.utils.PermissionUtils
import com.android.alftendev.utils.UiUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.switchmaterial.SwitchMaterial

class OnboardingActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private lateinit var back: Button
    private lateinit var next: Button
    private var step = 0
    private val onboarding by lazy { OnboardingSettings(this) }
    private val provider by lazy { AgentProviderSettings(this) }
    private var useDeepSeek = true
    private lateinit var baseUrl: TextInputEditText
    private lateinit var model: TextInputEditText
    private lateinit var token: TextInputEditText
    private lateinit var permissionStatus: TextView
    private lateinit var heading: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiUtils.themeValueToTheme(this, com.android.alftendev.utils.MySharedPref.getThemeOptions()))
        super.onCreate(savedInstanceState)
        title = getString(R.string.onboarding)
        setContentView(buildRoot())
        showStep(0)
    }

    private fun buildRoot(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(32, 24, 32, 16)
        heading = TextView(context).apply { textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
        addView(heading, LinearLayout.LayoutParams(-1, 56))
        content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(context).apply { gravity = Gravity.BOTTOM }
        back = Button(context).apply { text = getString(R.string.back); setOnClickListener { showStep(step - 1) } }
        next = Button(context).apply { text = getString(R.string.next_step); setOnClickListener { nextStep() } }
        actions.addView(back, LinearLayout.LayoutParams(0, 56, 1f))
        actions.addView(next, LinearLayout.LayoutParams(0, 56, 1f))
        addView(actions)
    }

    private fun showStep(value: Int) {
        step = value.coerceIn(0, 2)
        content.removeAllViews()
        heading.text = listOf(R.string.onboarding_step_agent, R.string.onboarding_step_permissions, R.string.onboarding_step_apps)[step].let(::getString)
        back.isVisible = step > 0
        when (step) { 0 -> showAgent(); 1 -> showPermissions(); 2 -> showApps() }
        next.text = getString(if (step == 2) R.string.finish else R.string.next_step)
    }

    private fun showAgent() {
        val configuration = provider.configuration()
        useDeepSeek = configuration.useDeepSeek
        val switch = SwitchMaterial(this).apply { text = getString(R.string.agent_use_deepseek); isChecked = useDeepSeek }
        token = TextInputEditText(this).apply { hint = getString(R.string.agent_api_token); setText(configuration.token); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        baseUrl = TextInputEditText(this).apply { hint = getString(R.string.agent_base_url); setText(configuration.baseUrl) }
        model = TextInputEditText(this).apply { hint = getString(R.string.agent_model); setText(configuration.model) }
        val custom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; isVisible = !useDeepSeek }
        custom.addView(baseUrl); custom.addView(model)
        switch.setOnCheckedChangeListener { _, checked -> useDeepSeek = checked; custom.isVisible = !checked }
        content.addView(switch); content.addView(token); content.addView(custom)
        content.addView(Button(this).apply { text = getString(R.string.agent_get_token); setOnClickListener { openUrl(AgentProviderSettings.DEEPSEEK_API_KEYS_URL) } })
        content.addView(Button(this).apply { text = getString(R.string.agent_verify); setOnClickListener { verifyAgent() } })
    }

    private fun verifyAgent() {
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
        permissionStatus = TextView(this).apply { textSize = 18f }
        content.addView(permissionStatus)
        content.addView(Button(this).apply { text = getString(R.string.notification_listener_access); setOnClickListener { PermissionUtils.openNotificationListenerSettings(this@OnboardingActivity) } })
        content.addView(Button(this).apply { text = getString(R.string.app_list_access); setOnClickListener { PermissionUtils.openAppDetailsSettings(this@OnboardingActivity) } })
        refreshPermissions()
    }

    override fun onResume() { super.onResume(); if (::permissionStatus.isInitialized && step == 1) refreshPermissions() }
    private fun refreshPermissions() { val ok = PermissionUtils.isNotificationServiceEnabled(this) && PermissionUtils.canReadInstalledApps(this); permissionStatus.text = if (ok) getString(R.string.enabled) else getString(R.string.required_permissions_missing); next.isEnabled = ok }

    private fun showApps() {
        content.addView(TextView(this).apply { text = getString(R.string.notification_collection_apps_summary); textSize = 16f })
        content.addView(Button(this).apply { text = getString(R.string.notification_collection_apps); setOnClickListener { startActivity(Intent(this@OnboardingActivity, BlackWhiteListActivity::class.java)) } })
        initializeDefaults()
    }

    private fun initializeDefaults() {
        if (onboarding.defaultsInitialized) return
        MyApplication.executor.execute {
            DBUtils.addAllInstalledPackageNames()
            listOf("com.tencent.mobileqq", "com.tencent.mm").forEach { pkg ->
                DBUtils.getPackageName(pkg)?.let { entity -> entity.isWhiteList = true; entity.isBlackList = false; MyApplication.packageNames.put(entity) }
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
    private fun complete() { onboarding.completed = true; startActivity(Intent(this, AllNotificationsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)); finish() }
    private fun openUrl(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (error: Exception) { showError(error) } }
    private fun showMessage(message: String) = MaterialAlertDialogBuilder(this).setMessage(message).setPositiveButton(android.R.string.ok, null).show()
    private fun showError(error: Exception) = MaterialAlertDialogBuilder(this).setTitle(R.string.operation_failed).setMessage(error.stackTraceToString()).setNeutralButton(R.string.copy_error) { _, _ -> android.content.ClipboardManager::class.java.cast(getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.error_details), error.stackTraceToString())) }.setPositiveButton(android.R.string.ok, null).show()
}
