package com.android.alftendev.activities.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.android.alftendev.MyApplication.Companion.executor
import com.android.alftendev.R
import com.android.alftendev.agent.AgentProviderSettings
import com.android.alftendev.agent.NotificationAgentService
import com.android.alftendev.ui.theme.NotificationListenerTheme
import com.android.alftendev.utils.MySharedPref
import com.android.alftendev.utils.UiUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
class AgentSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiUtils.themeValueToTheme(application, MySharedPref.getThemeOptions()))
        super.onCreate(savedInstanceState)
        setContent { NotificationListenerTheme { AgentSettingsScreen() } }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    @Composable
    private fun AgentSettingsScreen() {
        val settings = remember { AgentProviderSettings(this@AgentSettingsActivity) }
        val initial = remember { settings.configuration() }
        var useDeepSeek by remember { mutableStateOf(initial.useDeepSeek) }
        var baseUrl by remember { mutableStateOf(initial.baseUrl) }
        var model by remember { mutableStateOf(initial.model) }
        var token by remember { mutableStateOf(initial.token) }
        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.agent_service_settings)) }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.agent_use_deepseek))
                Switch(useDeepSeek, { useDeepSeek = it; save(settings, useDeepSeek, baseUrl, model, token) })
                if (useDeepSeek) TextButton({ openDeepSeekKeys() }) { Text(stringResource(R.string.agent_get_token)) }
                else {
                    OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text(stringResource(R.string.agent_base_url)) })
                    OutlinedTextField(model, { model = it }, label = { Text(stringResource(R.string.agent_model)) })
                }
                OutlinedTextField(token, { token = it }, label = { Text(stringResource(R.string.agent_api_token)) }, visualTransformation = PasswordVisualTransformation())
                Button({ save(settings, useDeepSeek, baseUrl, model, token); verify(settings) }) { Text(stringResource(R.string.agent_verify)) }
            }
        }
    }

    private fun openDeepSeekKeys() { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AgentProviderSettings.DEEPSEEK_API_KEYS_URL))) } catch (error: Exception) { showError(error) } }

    private fun save(settings: AgentProviderSettings, useDeepSeek: Boolean, baseUrl: String, model: String, token: String): Boolean = try { settings.save(useDeepSeek, baseUrl, model, token); true } catch (error: Exception) { showSaveError(error); false }

    private fun verify(settings: AgentProviderSettings) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { NotificationAgentService(settings).verify() }
            when (result) {
                is NotificationAgentService.VerificationResult.Success -> MaterialAlertDialogBuilder(this@AgentSettingsActivity).setTitle(R.string.verification_success).setMessage("获取到 ${result.modelCount} 个模型并完成 hello 请求。\nToken 为空时仍按相同流程验证。").setPositiveButton(android.R.string.ok, null).show()
                is NotificationAgentService.VerificationResult.Failure -> showError(result.error)
            }
        }
    }

    private fun showError(error: Exception) { showDetails(R.string.operation_failed, error.stackTraceToString(), R.string.copy_error) }

    private fun showSaveError(error: Exception) { showDetails(R.string.agent_settings_save_failed, error.stackTraceToString(), R.string.copy_error) }

    private fun showDetails(title: Int, details: String, copyLabel: Int) {
        MaterialAlertDialogBuilder(this).setTitle(title).setMessage(details).setNeutralButton(copyLabel) { _, _ ->
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(getString(R.string.error_details), details))
        }.setPositiveButton(android.R.string.ok, null).show()
    }
}
