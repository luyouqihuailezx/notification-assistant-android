package com.android.alftendev.agent

import com.android.alftendev.models.Notifications
import com.android.alftendev.models.MessageDateReferenceParser
import com.android.alftendev.models.MessageDateSearchFilter
import com.android.alftendev.utils.DBUtils
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.time.Instant
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

class NotificationAgentService(private val settings: AgentProviderSettings) {
    class CancelledException : Exception("智能体请求已取消")

    sealed interface VerificationResult {
        data class Success(val modelCount: Int) : VerificationResult
        data class Failure(val error: Exception) : VerificationResult
    }

    data class Answer(
        val content: String,
        val reasoning: String?,
        val sources: List<AgentSource> = emptyList()
    )

    fun answer(
        messages: List<AgentMessage>,
        cancelled: AtomicBoolean = AtomicBoolean(false),
        activeConnection: AtomicReference<HttpURLConnection?> = AtomicReference(null)
    ): Answer {
        val configuration = settings.configuration()
        require(configuration.token.isNotBlank()) { "尚未配置 API Token，请在智能体设置中填写后重试。" }
        val conversation = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            messages.forEach { message ->
                put(JSONObject().put("role", if (message.role == AgentRole.USER) "user" else "assistant")
                    .put("content", message.content))
            }
        }
        val collectedSources = linkedMapOf<Long, AgentSource>()

        while (true) {
            ensureActive(cancelled)
            val response = post(configuration, conversation, cancelled, activeConnection)
            ensureActive(cancelled)
            val choice = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val toolCalls = choice.optJSONArray("tool_calls")
            if (toolCalls == null || toolCalls.length() == 0) {
                return Answer(
                    content = choice.optString("content").ifBlank { "模型没有返回可显示的回答。" },
                    reasoning = choice.optString("reasoning_content").takeIf { it.isNotBlank() },
                    sources = collectedSources.values.toList()
                )
            }

            conversation.put(choice)
            for (index in 0 until toolCalls.length()) {
                ensureActive(cancelled)
                val toolCall = toolCalls.getJSONObject(index)
                val function = toolCall.getJSONObject("function")
                val result = runCatching {
                    require(function.getString("name") == SEARCH_TOOL) {
                        "不支持的工具：${function.getString("name")}"
                    }
                    searchNotifications(JSONObject(function.getString("arguments")), collectedSources).toString()
                }.getOrElse { error ->
                    if (error is CancelledException) throw error
                    JSONObject().put("error", error.message ?: error.stackTraceToString()).toString()
                }
                conversation.put(JSONObject()
                    .put("role", "tool")
                    .put("tool_call_id", toolCall.getString("id"))
                    .put("content", result))
            }
        }
    }

    private fun ensureActive(cancelled: AtomicBoolean) {
        if (cancelled.get()) throw CancelledException()
    }

    private fun post(
        configuration: AgentConfiguration,
        messages: JSONArray,
        cancelled: AtomicBoolean,
        activeConnection: AtomicReference<HttpURLConnection?>
    ): JSONObject {
        ensureActive(cancelled)
        val payload = JSONObject()
            .put("model", configuration.model)
            .put("messages", messages)
            .put("tool_choice", "auto")
            .put("tools", JSONArray().put(SEARCH_TOOL_SCHEMA))
        val connection = (URL("${configuration.baseUrl}/chat/completions").openConnection() as HttpURLConnection)
        activeConnection.set(connection)
        return try {
            ensureActive(cancelled)
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${configuration.token}")
            connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }
            ensureActive(cancelled)
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            ensureActive(cancelled)
            if (status !in 200..299) {
                throw IllegalStateException("智能体服务请求失败（HTTP $status）：$body")
            }
            JSONObject(body)
        } catch (error: Exception) {
            if (cancelled.get() || error is CancelledException) throw CancelledException()
            if (error is SocketTimeoutException) {
                Log.e("NotificationAgent", "智能体网络请求超时：${configuration.baseUrl}/chat/completions", error)
            }
            throw error
        } finally {
            activeConnection.compareAndSet(connection, null)
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

    private fun searchNotifications(
        arguments: JSONObject,
        collectedSources: MutableMap<Long, AgentSource>
    ): JSONObject {
        val query = arguments.optString("query").trim()
        val app = arguments.optString("app").trim()
        val title = arguments.optString("title").trim()
        val body = arguments.optString("body").trim()
        val regex = arguments.optString("regex").trim()
        val warnings = mutableListOf<String>()
        val from = parseBoundary(arguments.opt("from"), "from", false, warnings) ?: Long.MIN_VALUE
        val to = parseBoundary(arguments.opt("to"), "to", true, warnings) ?: Long.MAX_VALUE
        require(from <= to) { "查询时间范围无效：from 必须早于或等于 to" }
        val dateFilter = parseContentDate(arguments.optString("date").trim(), warnings)
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
        val dateIDs = dateFilter?.let(DBUtils::matchingNotificationIds)
        val matched = notifications.asSequence()
            .filter { it.time.time in from..to }
            .filter { notification ->
                dateIDs == null || notification.entityId in dateIDs ||
                    MessageDateReferenceParser.referencesIn(contentForDate(notification), notification.time).any { reference ->
                        dateFilterMatches(reference, dateFilter)
                    }
            }
            .filter { notification ->
                pattern == null || pattern.matcher(notification.title + "\n" + notification.text).find()
            }
            .take(50)
            .toList()
        matched.forEach { notification ->
            collectedSources.putIfAbsent(notification.entityId, notification.toAgentSource())
        }
        val records = matched.map { it.toAgentJson() }
        val status = when {
            records.isEmpty() -> "not_found"
            records.size == 1 -> "single"
            else -> "multiple"
        }
        return JSONObject()
            .put("records", JSONArray(records))
            .put("count", records.size)
            .put("status", status)
            .put("warnings", JSONArray(warnings))
            .put(
                "hint",
                when (status) {
                    "not_found" -> "在已收集的通知中未找到相关记录。请明确告知用户检索边界，不要断言现实中不存在该事项。"
                    "multiple" -> "找到多条候选通知。请分别列出可区分的候选及其来源，不要任意选定其中一条作为唯一答案。"
                    else -> "找到唯一相关通知，回答时可引用其 notificationId。"
                }
            )
    }

    private fun contentForDate(notification: Notifications): String = listOf(notification.title, notification.text, notification.bigText, notification.conversationTitle, notification.infoText, notification.peopleList, notification.titleBig).filterNotNull().joinToString("\n")

    private fun dateFilterMatches(reference: com.android.alftendev.models.MessageDateReference, filter: MessageDateSearchFilter): Boolean =
        (filter.year == null || reference.year == filter.year) &&
            (filter.month == null || reference.month == filter.month) &&
            (filter.day == null || reference.day == filter.day) &&
            (filter.hour == null || reference.hour == filter.hour) &&
            (filter.minute == null || reference.minute == filter.minute) &&
            (filter.second == null || reference.second == filter.second) &&
            (filter.weekday == null || reference.weekday == filter.weekday)

    private fun parseContentDate(value: String, warnings: MutableList<String>): MessageDateSearchFilter? {
        if (value.isBlank()) return null
        val reference = MessageDateReferenceParser.referencesIn(value, Date())
            .sortedWith(
                compareByDescending<com.android.alftendev.models.MessageDateReference> { it.weight }
                    .thenByDescending { it.originalText.length }
                    .thenBy { it.candidateRank }
            )
            .firstOrNull()
        if (reference == null) {
            val warning = "搜索工具参数警告：无法解析 date=$value，已忽略内容日期条件。"
            warnings += warning
            return null
        }
        return MessageDateSearchFilter(
            year = reference.year,
            month = reference.month,
            day = reference.day,
            hour = reference.hour,
            minute = reference.minute,
            second = reference.second,
            weekday = reference.weekday
        )
    }

    private fun parseBoundary(value: Any?, field: String, endOfDay: Boolean, warnings: MutableList<String>): Long? {
        if (value == null || value == JSONObject.NULL) return null
        val raw = value.toString().trim()
        if (raw.isBlank()) return null
        raw.toLongOrNull()?.let { return it }
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm", "yyyy-MM-dd", "yyyy年M月d日 H:mm", "yyyy年M月d日"
        )
        val parsed = formats.asSequence().mapNotNull { format ->
            runCatching {
                SimpleDateFormat(format, Locale.CHINA).apply {
                    timeZone = TimeZone.getDefault(); isLenient = false
                }.parse(raw)
            }.getOrNull()
        }.firstOrNull()
        val date = parsed ?: MessageDateReferenceParser.referencesIn(raw, Date())
            .sortedWith(
                compareByDescending<com.android.alftendev.models.MessageDateReference> { it.weight }
                    .thenByDescending { it.originalText.length }
                    .thenBy { it.candidateRank }
            )
            .firstOrNull()?.resolvedDate
        if (date == null) {
            val warning = "搜索工具参数警告：无法解析 $field=$raw，已忽略该时间条件。"
            warnings += warning
            return null
        }
        val dateOnly = raw.matches(Regex("\\d{4}-\\d{1,2}-\\d{1,2}")) || raw.matches(Regex("\\d{4}年\\d{1,2}月\\d{1,2}日"))
        if (!endOfDay || !dateOnly) return date.time
        return Calendar.getInstance().apply { time = date; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
    }

    private fun Notifications.toAgentSource(): AgentSource = AgentSource(
        notificationId = entityId,
        app = packageName.target.name ?: packageName.target.pkg,
        title = title,
        body = (bigText?.trim().takeUnless { it.isNullOrBlank() } ?: text).orEmpty(),
        time = time.time
    )

    private fun Notifications.toAgentJson(): JSONObject = JSONObject().apply {
        put("notificationId", entityId)
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
        private const val SYSTEM_PROMPT = """你是通知历史智能体。仅可通过 search_notifications 查找本机通知；不得声称看到未查询到的数据。不要使用语义搜索。工具可以反复调用，直到获得可靠答案。需要查找通知内容中提到的日程时使用 date 参数，例如“明天六点”“下周五”“Friday 8pm”；需要查找通知发生在某段时间时使用 from 和 to。

回答规则：
1. 使用简洁 Markdown。
2. 工具返回 status=not_found 时，明确写“在已收集的通知中未找到相关记录”，不要断言现实中不存在该事项。
3. 工具返回 status=multiple 时，分别列出可区分的候选，并引用各自的 notificationId；不要任意把其中一条当成唯一答案。
4. 工具返回 status=single 或有明确唯一答案时，简要回答后可引用 notificationId。
5. 引用通知时使用工具返回的 notificationId，不要编造。界面会展示可点击的来源卡片。"""
        private val SEARCH_TOOL_SCHEMA = JSONObject("""{
          "type":"function",
          "function":{
            "name":"search_notifications",
            "description":"按关键词、应用、标题、正文、正则、通知发生时间范围和通知内容中提到的自然语言日期查询本机通知历史；可用于查找指定时间段相关日程。",
            "parameters":{"type":"object","properties":{
              "query":{"type":"string"},"app":{"type":"string"},"title":{"type":"string"},
              "body":{"type":"string"},"regex":{"type":"string"},"from":{"type":"string","description":"通知发生的起始时间，支持 ISO8601、yyyy-MM-dd、本地日期时间或 Unix 毫秒"},
              "to":{"type":"string","description":"通知发生的结束时间，支持 ISO8601、yyyy-MM-dd、本地日期时间或 Unix 毫秒"},
              "date":{"type":"string","description":"通知内容中提到的日期或日程时间，例如昨天、明天六点、下周五、Friday 8pm"}
            }}
          }
        }""")
    }
}
