package ai.aura.personal.core.providers

import ai.aura.personal.core.chat.ChatMessage
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject

internal class ProviderClient(private val store: ProviderStore) {
    fun listModels(profile: ProviderProfile): List<String> {
        val ids = linkedSetOf<String>()
        var path = "/models"
        repeat(30) {
            val page = request(profile, path)
            val models = page.getJSONArray("data")
            for (index in 0 until models.length()) ids.add(models.getJSONObject(index).getString("id"))
            if (profile.protocol != ProviderProtocol.CLAUDE || !page.optBoolean("has_more")) return ids.toList()
            val cursor = URLEncoder.encode(page.getString("last_id"), "UTF-8")
            path = "/models?after_id=" + cursor
        }
        error("Provider returned too many model pages. Enter the model ID manually.")
    }

    fun generate(profile: ProviderProfile, history: List<ChatMessage>, prompt: String): String {
        require(profile.model.isNotBlank()) { "Select a provider model first." }
        val claude = profile.protocol == ProviderProtocol.CLAUDE
        val messages = JSONArray()
        val system = mutableListOf<String>()
        for (item in history) {
            if (claude && item.role == ChatMessage.Role.SYSTEM) system.add(item.content)
            else messages.put(JSONObject().put("role", item.role.name.lowercase()).put("content", item.content))
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt))
        val body = JSONObject().put("model", profile.model).put("messages", messages)
        if (claude) {
            body.put("max_tokens", 1024)
            if (system.isNotEmpty()) body.put("system", system.joinToString("\n"))
        }
        val response = request(profile, if (claude) "/messages" else "/chat/completions", body)
        val answer = if (claude) {
            val parts = response.getJSONArray("content")
            (0 until parts.length()).map { parts.getJSONObject(it) }
                .filter { it.optString("type") == "text" }.joinToString("\n") { it.getString("text") }
        } else {
            val message = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            if (message.isNull("content")) "" else message.optString("content")
        }
        return answer.trim().also { check(it.isNotEmpty()) { "Provider returned no text." } }
    }

    private fun request(profile: ProviderProfile, path: String, body: JSONObject? = null): JSONObject {
        val base = profile.endpoint().toString()
        val connection = java.net.URI(base + path).toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("Accept", "application/json")
            val key = store.key(profile)
            if (profile.protocol == ProviderProtocol.CLAUDE) {
                connection.setRequestProperty("x-api-key", key)
                connection.setRequestProperty("anthropic-version", "2023-06-01")
            } else connection.setRequestProperty("Authorization", "Bearer $key")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            check(status in 200..299) {
                when (status) {
                    401, 403 -> "Provider HTTP $status. Check your key and model permissions."
                    429 -> "Provider HTTP 429. Check rate limits and account quota."
                    404 -> "Provider HTTP 404. Check the endpoint and model ID."
                    in 300..399 -> "Provider redirected the request. Update the endpoint before sending your key again."
                    else -> "Provider returned HTTP $status."
                }
            }
            val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(4096)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    check(result.length + count <= 2_000_000) { "Provider response exceeded the app limit." }
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
            return try { JSONObject(raw) } catch (_: org.json.JSONException) {
                error("Provider returned invalid JSON.")
            }
        } finally {
            connection.disconnect()
        }
    }
}
