package com.android.alftendev.activities.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.android.alftendev.R
import com.android.alftendev.agent.AgentProviderSettings

@Composable
fun AgentSetupFragment() {
    val activity = androidx.compose.ui.platform.LocalContext.current as OnboardingActivity
    val settings = remember { AgentProviderSettings(activity) }; val initial = remember { settings.configuration() }
    var deepSeek by remember { mutableStateOf(initial.useDeepSeek) }; var token by remember { mutableStateOf(initial.token) }; var baseUrl by remember { mutableStateOf(initial.baseUrl) }; var model by remember { mutableStateOf(initial.model) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(token, { token = it }, label = { Text(stringResource(R.string.agent_api_token)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(R.string.agent_use_deepseek)); Switch(deepSeek, { deepSeek = it }) }
        if (!deepSeek) { OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text(stringResource(R.string.agent_base_url)) }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(model, { model = it }, label = { Text(stringResource(R.string.agent_model)) }, modifier = Modifier.fillMaxWidth()) }
        OutlinedButton(onClick = { activity.openUrl(AgentProviderSettings.DEEPSEEK_API_KEYS_URL) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.agent_get_token)) }
        Button(onClick = { settings.save(deepSeek, if (deepSeek) AgentProviderSettings.DEFAULT_BASE_URL else baseUrl, if (deepSeek) AgentProviderSettings.DEFAULT_MODEL else model, token); activity.verifyAgent(settings) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.agent_verify)) }
    }
}
