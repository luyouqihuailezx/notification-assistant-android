package com.android.alftendev.activities

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.alftendev.MyApplication.Companion.authSuccess
import com.android.alftendev.MyApplication.Companion.executor
import com.android.alftendev.R
import com.android.alftendev.activities.home.AllNotificationsActivity
import com.android.alftendev.activities.migration.LegacyDbMigrationActivity
import com.android.alftendev.activities.onboarding.OnboardingActivity
import com.android.alftendev.activities.onboarding.OnboardingSettings
import com.android.alftendev.ui.theme.NotificationListenerTheme
import com.android.alftendev.utils.AuthUtils.haveToAskAuth
import com.android.alftendev.utils.LegacyDbMigrationSettings
import com.android.alftendev.utils.UiUtils.showToast

class MainActivity : AppCompatActivity() {
    private var needsRetry by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (LegacyDbMigrationSettings.needsMigration(this)) { startActivity(Intent(this, LegacyDbMigrationActivity::class.java)); finish(); return }
        if (!OnboardingSettings(this).completed) { startActivity(Intent(this, OnboardingActivity::class.java)); finish(); return }
        enableEdgeToEdge()
        setContent {
            NotificationListenerTheme {
                Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                    if (needsRetry) Button(onClick = ::authenticate) { Text(getString(R.string.reauth)) }
                    else Button(onClick = ::openHistory) { Text(getString(R.string.continuee)) }
                }
            }
        }
        if (haveToAskAuth()) authenticate() else openHistory()
    }

    private fun openHistory() { startActivity(Intent(this, AllNotificationsActivity::class.java)); finish() }

    private fun authenticate() {
        if (!haveToAskAuth()) { openHistory(); return }
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { needsRetry = true; showToast(getString(R.string.authErr), this@MainActivity) }
            override fun onAuthenticationFailed() { needsRetry = true; showToast(getString(R.string.authFail), this@MainActivity) }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { authSuccess.set(true); showToast(getString(R.string.authSuccess), this@MainActivity); openHistory() }
        })
        val info = PromptInfo.Builder().setTitle(getString(R.string.biometric_title)).setConfirmationRequired(false).apply {
            val manager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            if (manager.isDeviceSecure) @Suppress("DEPRECATION") setDeviceCredentialAllowed(true) else setNegativeButtonText(getString(R.string.cancel))
        }.build()
        prompt.authenticate(info)
    }
}
