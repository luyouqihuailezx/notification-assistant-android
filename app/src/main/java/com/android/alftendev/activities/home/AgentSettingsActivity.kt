package com.android.alftendev.activities.home

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.alftendev.agent.AgentSettings
import com.android.alftendev.composes.utils.MyTheme
import com.android.alftendev.utils.MySharedPref
import com.android.alftendev.utils.UiUtils
import androidx.appcompat.app.AppCompatActivity

class AgentSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UiUtils.themeValueToTheme(this, MySharedPref.getThemeOptions()))
        super.onCreate(savedInstanceState)
        supportActionBar?.title = getString(com.android.alftendev.R.string.agent_service_settings)
        val agentSettings = AgentSettings(this)
        setContent {
            val initial = agentSettings.configuration()
            var baseUrl by remember { mutableStateOf(initial.baseUrl) }
            var model by remember { mutableStateOf(initial.model) }
            var token by remember { mutableStateOf(initial.token) }
            var error by remember { mutableStateOf<String?>(null) }
            MyTheme {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                        Text("配置兼容 OpenAI 的服务。API Token 会加密保存在此设备中。", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(24.dp))
                        OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("服务地址") }, singleLine = true)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("模型名称") }, singleLine = true)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("API Token") }, singleLine = true)
                        error?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = {
                            runCatching { agentSettings.save(baseUrl, model, token) }
                                .onSuccess { finish() }
                                .onFailure { error = it.message ?: it.stackTraceToString() }
                        }, modifier = Modifier.fillMaxWidth()) { Text("保存配置") }
                }
            }
        }
    }
}
