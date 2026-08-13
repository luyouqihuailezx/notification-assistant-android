package com.android.alftendev.agent

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class AgentConfiguration(
    val baseUrl: String,
    val model: String,
    val token: String
)

class AgentSettings(context: Context) {
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
        baseUrl = publicPreferences.getString(BASE_URL, DEFAULT_BASE_URL)!!.trim().trimEnd('/'),
        model = publicPreferences.getString(MODEL, DEFAULT_MODEL)!!.trim(),
        token = encryptedPreferences.getString(TOKEN, "")!!.trim()
    )

    fun save(baseUrl: String, model: String, token: String) {
        require(baseUrl.isNotBlank()) { "服务地址不能为空" }
        require(model.isNotBlank()) { "模型名称不能为空" }
        check(publicPreferences.edit()
            .putString(BASE_URL, baseUrl.trim().trimEnd('/'))
            .putString(MODEL, model.trim())
            .commit()) { "保存智能体服务设置失败" }
        check(encryptedPreferences.edit().putString(TOKEN, token.trim()).commit()) {
            "保存智能体 API Token 失败"
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-chat"
        private const val PREFERENCES = "notification_agent_settings"
        private const val SECRET_PREFERENCES = "notification_agent_secrets"
        private const val BASE_URL = "base_url"
        private const val MODEL = "model"
        private const val TOKEN = "token"
    }
}
