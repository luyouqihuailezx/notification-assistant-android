package com.android.alftendev.activities.onboarding

import android.content.*
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.alftendev.MyApplication
import com.android.alftendev.R
import com.android.alftendev.activities.home.AllNotificationsActivity
import com.android.alftendev.agent.*
import com.android.alftendev.ui.theme.NotificationListenerTheme
import com.android.alftendev.utils.MySharedPref
import com.android.alftendev.utils.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
class OnboardingActivity : AppCompatActivity() {
    private var step by mutableIntStateOf(0)
    private val provider by lazy { AgentProviderSettings(this) }
    private val onboarding by lazy { OnboardingSettings(this) }
    private var error by mutableStateOf<String?>(null)
    private var message by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) { setTheme(com.android.alftendev.utils.UiUtils.themeValueToTheme(this, MySharedPref.getThemeOptions())); super.onCreate(savedInstanceState); step = savedInstanceState?.getInt(KEY_STEP) ?: 0; setContent { NotificationListenerTheme { OnboardingScreen() } } }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt(KEY_STEP, step); super.onSaveInstanceState(outState) }
    @Composable private fun OnboardingScreen() {
        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.onboarding)) }, navigationIcon = { TextButton(onClick = { if (step == 0) finish() else step-- }) { Text("‹") } }) }) { padding ->
            Column(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.onboarding_step_counter, step + 1, 3)); LinearProgressIndicator({ (step + 1) / 3f }, Modifier.fillMaxWidth())
                when (step) { 0 -> AgentSetupFragment(); 1 -> PermissionsSetupFragment(); else -> AppSelectionSetupFragment() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button(onClick = ::nextStep) { Text(stringResource(if (step == 2) R.string.finish else R.string.next_step)) } }
            }
        }
        error?.let { ErrorDialog(it) { error = null } }; message?.let { AlertDialog(onDismissRequest = { message = null }, text = { Text(it) }, confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(android.R.string.ok)) } }) }
    }
    private fun nextStep() { try { when (step) { 0 -> step++; 1 -> if (isReady(this)) step++ else message = getString(R.string.required_permissions_missing); 2 -> { onboarding.completed = true; startActivity(Intent(this, AllNotificationsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)); finish() } } } catch (e: Exception) { error = e.stackTraceToString() } }
    fun verifyAgent(configuration: AgentProviderSettings) { MyApplication.executor.execute { try { when (val result = NotificationAgentService(configuration).verify()) { is NotificationAgentService.VerificationResult.Success -> runOnUiThread { message = getString(R.string.verification_success) }; is NotificationAgentService.VerificationResult.Failure -> runOnUiThread { error = result.error.stackTraceToString() } } } catch (e: Exception) { runOnUiThread { error = e.stackTraceToString() } } } }
    fun openUrl(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { error = e.stackTraceToString() } }
    fun showError(cause: Throwable) { error = cause.stackTraceToString() }
    @Composable private fun ErrorDialog(details: String, clear: () -> Unit) { AlertDialog(onDismissRequest = clear, title = { Text(stringResource(R.string.operation_failed)) }, text = { Text(details) }, confirmButton = { TextButton(onClick = clear) { Text(stringResource(android.R.string.ok)) } }, dismissButton = { TextButton(onClick = { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.error_details), details)); clear() }) { Text(stringResource(R.string.copy_error)) } }) }
    companion object { private const val KEY_STEP = "onboarding_step" }
}
