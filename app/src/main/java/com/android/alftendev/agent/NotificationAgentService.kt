package com.android.alftendev.agent

import com.android.alftendev.models.Notifications
import com.android.alftendev.utils.DBUtils
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.regex.Pattern

class NotificationAgentService(private val settings: AgentProviderSettings) {
    sealed interface VerificationResult {
        data class Success(val modelCount: Int) : VerificationResult
        data class Failure(val error: Exception) : VerificationResult
    }
    data class Answer(val content: String, val reasoning: String?)

    fun answer(messages: List<AgentMessage>): Answer {
        val configuration = settings.configuration()
        require(configuration.token.isNotBlank()) { "尚未配置 API Token，请在智能体设置中填写后重试。" }
        val conversation = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            messages.forEach { message ->
                put(JSONObject().put("role", if (message.role == AgentRole.USER) "user" else "assistant")
                    .put("content", message.content))
            }
        }

        while (true) {
            val response = post(configuration, conversation)
            val choice = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val toolCalls = choice.optJSONArray("tool_calls")
            if (toolCalls == null || toolCalls.length() == 0) {
                return Answer(
                    content = choice.optString("content").ifBlank { "模型没有返回可显示的回答。" },
                    reasoning = choice.optString("reasoning_content").takeIf { it.isNotBlank() }
                )
            }

            conversation.put(choice)
            // 不限制工具调用轮数；用户取消请求或服务端错误将中断循环。
            for (index in 0 until toolCalls.length()) {
                val toolCall = toolCalls.getJSONObject(index)
                val function = toolCall.getJSONObject("function")
                val result = runCatching {
                    require(function.getString("name") == SEARCH_TOOL) {
                        "不支持的工具：${function.getString("name")}"
                    }
                    searchNotifications(JSONObject(function.getString("arguments"))).toString()
                }.getOrElse { error ->
                    JSONObject().put("error", error.message ?: error.stackTraceToString()).toString()
                }
                conversation.put(JSONObject()
                    .put("role", "tool")
                    .put("tool_call_id", toolCall.getString("id"))
                    .put("content", result))
            }
        }
    }

    private fun post(configuration: AgentConfiguration, messages: JSONArray): JSONObject {
        val payload = JSONObject()
            .put("model", configuration.model)
            .put("messages", messages)
            .put("tool_choice", "auto")
            .put("tools", JSONArray().put(SEARCH_TOOL_SCHEMA))
        val connection = (URL("${configuration.baseUrl}/chat/completions").openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${configuration.token}")
            connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("智能体服务请求失败（HTTP $status）：$body")
            }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    fun verify(): VerificationResult {
        val configuration = settings.configuration()
        return try {
            val models = request(configuration, "GET", "models", null)
            val data = models.optJSONArray("data") ?: error("模型列表响应缺少 data 字段")
            request(configuration, "POST", "chat/completions", JSONObject()
                .put("model", configuration.model)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "hello"))))
            VerificationResult.Success(data.length())
        } catch (error: Exception) {
            VerificationResult.Failure(error)
        }
    }

    private fun request(configuration: AgentConfiguration, method: String, path: String, payload: JSONObject?): JSONObject {
        val connection = (URL(endpoint(configuration.baseUrl, path)).openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            connection.readTimeout = 90_000
            connection.doOutput = payload != null
            connection.setRequestProperty("Content-Type", "application/json")
            if (configuration.token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer ${configuration.token}")
            payload?.let { connection.outputStream.bufferedWriter().use { writer -> writer.write(it.toString()) } }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) error("智能体服务请求失败（HTTP $status）：$body")
            JSONObject(body)
        } finally { connection.disconnect() }
    }

    private fun searchNotifications(arguments: JSONObject): JSONArray {
        val query = arguments.optString("query").trim()
        val app = arguments.optString("app").trim()
        val title = arguments.optString("title").trim()
        val body = arguments.optString("body").trim()
        val regex = arguments.optString("regex").trim()
        val from = arguments.optLong("from", Long.MIN_VALUE)
        val to = arguments.optLong("to", Long.MAX_VALUE)
        require(from <= to) { "查询时间范围无效：from 必须早于或等于 to" }
        val keyword = listOf(query, title, body).firstOrNull { it.isNotBlank() }.orEmpty()
        val notifications = if (keyword.isBlank()) {
            DBUtils.advancedNotificationSearchWithoutText(app, false)
        } else {
            DBUtils.advancedNotificationSearch(keyword, app, false)
        }
        val pattern = regex.takeIf { it.isNotBlank() }?.let {
            try {
                Pattern.compile(it, Pattern.CASE_INSENSITIVE)
            } catch (error: Exception) {
                throw IllegalArgumentException("regex 无效：${error.message}", error)
            }
        }
        return JSONArray(notifications.asSequence()
            .filter { it.time.time in from..to }
            .filter { notification ->
                pattern == null || pattern.matcher(notification.title + "\n" + notification.text).find()
            }
            .take(50)
            .map { it.toAgentJson() }
            .toList())
    }

    private fun Notifications.toAgentJson(): JSONObject = JSONObject().apply {
        put("app", packageName.target.name ?: packageName.target.pkg)
        put("package", packageName.target.pkg)
        put("title", title)
        put("body", text)
        put("time", time.time)
        put("isoTime", Instant.ofEpochMilli(time.time).toString())
    }

    companion object {
        fun endpoint(baseUrl: String, path: String): String = "${baseUrl.trim().trimEnd('/')}/${path.trimStart('/')}"
        private const val SEARCH_TOOL = "search_notifications"
        private const val SYSTEM_PROMPT = """你是通知历史智能体。仅可通过 search_notifications 查找本机通知；不得声称看到未查询到的数据。不要使用语义搜索。工具可以反复调用，直到获得可靠答案。回答使用简洁 Markdown。"""
        private val SEARCH_TOOL_SCHEMA = JSONObject("""{
          "type":"function",
          "function":{
            "name":"search_notifications",
            "description":"按关键词、应用、标题、正文、正则和时间范围查询本机通知历史。",
            "parameters":{"type":"object","properties":{
              "query":{"type":"string"},"app":{"type":"string"},"title":{"type":"string"},
              "body":{"type":"string"},"regex":{"type":"string"},"from":{"type":"integer"},
              "to":{"type":"integer"}
            }}
          }
        }""")
    }
}
