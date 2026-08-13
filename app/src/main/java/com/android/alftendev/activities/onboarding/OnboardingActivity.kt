package com.android.alftendev.activities.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.activities.home.AllNotificationsActivity
import com.android.alftendev.agent.AgentProviderSettings
import com.android.alftendev.agent.NotificationAgentService
import com.android.alftendev.utils.UiUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.appbar.MaterialToolbar

class OnboardingActivity : AppCompatActivity() {
    private lateinit var content: FrameLayout
    private lateinit var back: MaterialButton
    private lateinit var next: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var stepLabel: TextView
    private var step = 0
    private val provider by lazy { AgentProviderSettings(this) }
    private val onboarding by lazy { OnboardingSettings(this) }
    private var agentFragment: AgentSetupFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiUtils.themeValueToTheme(this, com.android.alftendev.utils.MySharedPref.getThemeOptions()))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        val toolbar = findViewById<MaterialToolbar>(R.id.onboardingToolbar)
        toolbar.title = getString(R.string.onboarding)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        content = findViewById(R.id.onboardingContent)
        back = findViewById(R.id.onboardingBack)
        next = findViewById(R.id.onboardingNext)
        progress = findViewById(R.id.onboardingProgress)
        stepLabel = findViewById(R.id.stepLabel)
        back.setOnClickListener { showStep(step - 1) }
        next.setOnClickListener { nextStep() }
        showStep(savedInstanceState?.getInt(KEY_STEP) ?: 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_STEP, step)
        super.onSaveInstanceState(outState)
    }

    private fun showStep(value: Int) {
        step = value.coerceIn(0, 2)
        content.removeAllViews()
        progress.max = 3
        progress.progress = step + 1
        stepLabel.text = getString(R.string.onboarding_step_counter, step + 1, 3)
        back.isVisible = step > 0
        next.text = getString(if (step == 2) R.string.finish else R.string.next_step)
        when (step) {
            0 -> {
                agentFragment = AgentSetupFragment()
                supportFragmentManager.beginTransaction().replace(R.id.onboardingContent, agentFragment!!).commitNow()
            }
            1 -> supportFragmentManager.beginTransaction().replace(R.id.onboardingContent, PermissionsSetupFragment()).commitNow()
            2 -> supportFragmentManager.beginTransaction().replace(R.id.onboardingContent, AppSelectionSetupFragment()).commitNow()
        }
        next.isEnabled = step != 1 || PermissionsSetupFragment.isReady(this)
    }

    fun refreshPermissionStep(ready: Boolean) {
        if (step == 1) next.isEnabled = ready
    }

    private fun nextStep() {
        try {
            when (step) {
                0 -> {
                    agentFragment?.save()
                    showStep(1)
                }
                1 -> if (PermissionsSetupFragment.isReady(this)) showStep(2)
                2 -> complete()
            }
        } catch (error: Exception) { showError(error) }
    }

    private fun complete() {
        onboarding.completed = true
        startActivity(Intent(this, AllNotificationsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    fun verifyAgent(configuration: AgentProviderSettings) {
        MyApplication.executor.execute {
            val result = NotificationAgentService(configuration).verify()
            runOnUiThread {
                when (result) {
                    is NotificationAgentService.VerificationResult.Success -> showMessage(getString(R.string.verification_success))
                    is NotificationAgentService.VerificationResult.Failure -> showError(result.error)
                }
            }
        }
    }

    private fun showMessage(message: String) = MaterialAlertDialogBuilder(this).setMessage(message).setPositiveButton(android.R.string.ok, null).show()

    fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (error: Exception) { showError(error) }
    }

    fun showError(error: Exception) {
        val details = error.stackTraceToString()
        MaterialAlertDialogBuilder(this).setTitle(R.string.operation_failed).setMessage(details)
            .setNeutralButton(R.string.copy_error) { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.error_details), details))
            }.setPositiveButton(android.R.string.ok, null).show()
    }

    companion object { private const val KEY_STEP = "onboarding_step" }
}
