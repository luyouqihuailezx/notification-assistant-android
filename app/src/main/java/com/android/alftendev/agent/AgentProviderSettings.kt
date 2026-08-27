package com.android.alftendev.agent

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class AgentConfiguration(
    val useDeepSeek: Boolean,
    val baseUrl: String,
    val model: String,
    val token: String
)

class AgentProviderSettings(context: Context) {
    private val appContext = context.applicationContext
    private val publicPreferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val encryptedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            SECRET_PREFERENCES,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun configuration(): AgentConfiguration = AgentConfiguration(
        useDeepSeek = publicPreferences.getBoolean(USE_DEEPSEEK, true),
        baseUrl = publicPreferences.getString(BASE_URL, DEFAULT_BASE_URL)!!.trim().trimEnd('/'),
        model = publicPreferences.getString(MODEL, DEFAULT_MODEL)!!.trim(),
        token = encryptedPreferences.getString(TOKEN, "")!!.trim()
    )

    fun save(useDeepSeek: Boolean, baseUrl: String, model: String, token: String) {
        val normalizedBaseUrl = validateBaseUrl(baseUrl)
        require(model.isNotBlank()) { "模型名称不能为空" }
        check(publicPreferences.edit()
            .putBoolean(USE_DEEPSEEK, useDeepSeek)
            .putString(BASE_URL, normalizedBaseUrl)
            .putString(MODEL, model.trim())
            .commit()) { "保存智能体服务设置失败" }
        check(encryptedPreferences.edit().putString(TOKEN, token.trim()).commit()) {
            "保存智能体 API Token 失败"
        }
    }

    private fun validateBaseUrl(value: String): String {
        val normalized = value.trim().trimEnd('/')
        val uri = Uri.parse(normalized)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) {
            "服务地址必须是包含主机名的 HTTPS 地址"
        }
        require(uri.userInfo.isNullOrBlank()) { "服务地址不能包含用户名或密码" }
        return normalized
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-v4-flash"
        const val DEEPSEEK_API_KEYS_URL = "https://platform.deepseek.com/api_keys"
        private const val PREFERENCES = "notification_agent_settings_v2"
        private const val SECRET_PREFERENCES = "notification_agent_secrets_v2"
        private const val USE_DEEPSEEK = "use_deepseek"
        private const val BASE_URL = "base_url"
        private const val MODEL = "model"
        private const val TOKEN = "token"
    }
}
