package ai.aura.personal.core.providers

import ai.aura.personal.core.chat.ChatMessage
import java.net.SocketTimeoutException
import java.io.BufferedReader
import java.net.UnknownHostException
import java.net.URLEncoder
import javax.net.ssl.SSLException
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject

internal class ProviderClient(private val store: ProviderStore) {
    fun listModels(profile: ProviderProfile): List<String> {
        val gemini = usesNativeGemini(profile)
        val ids = linkedSetOf<String>()
        var path = if (gemini) "/models?pageSize=100" else "/models"
        repeat(30) {
            val page = request(profile, path)
            if (gemini) {
                ids.addAll(GeminiText.chatModels(page))
                val token = page.optString("nextPageToken")
                if (token.isBlank()) return ids.toList()
                path = "/models?pageSize=100&pageToken=" + URLEncoder.encode(token, "UTF-8")
            } else {
                val models = page.getJSONArray("data")
                for (index in 0 until models.length()) ids.add(models.getJSONObject(index).getString("id"))
                if (profile.protocol != ProviderProtocol.CLAUDE || !page.optBoolean("has_more")) return ids.toList()
                path = "/models?after_id=" + URLEncoder.encode(page.getString("last_id"), "UTF-8")
            }
        }
        error("Provider returned too many model pages. Enter the model ID manually.")
    }

    fun generate(profile: ProviderProfile, history: List<ChatMessage>, prompt: String, onText: ((String) -> Unit)? = null): String {
        require(profile.model.isNotBlank()) { "Select a provider model first." }
        if (usesNativeGemini(profile)) {
            val path = GeminiText.generationPath(profile.model, streaming = onText != null)
            val body = GeminiText.body(history, prompt)
            return if (onText == null) GeminiText.reply(request(profile, path, body))
                else exchange(profile, path, body, streaming = true) { GeminiText.readStream(it, onText) }
        }
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

    // Exact official endpoint also recognizes existing Gemini profiles without re-entering their keys.
    private fun usesNativeGemini(profile: ProviderProfile): Boolean =
        profile.protocol == ProviderProtocol.OPENAI && profile.endpoint().toString() == ProviderPreset.GEMINI.baseUrl

    private fun request(profile: ProviderProfile, path: String, body: JSONObject? = null): JSONObject =
        exchange(profile, path, body) { reader ->
            val result = StringBuilder()
            val buffer = CharArray(4096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                check(result.length + count <= 2_000_000) { "Provider response exceeded the app limit." }
                result.append(buffer, 0, count)
            }
            JSONObject(result.toString())
        }

    private fun <T> exchange(
        profile: ProviderProfile, path: String, body: JSONObject?, streaming: Boolean = false,
        decode: (BufferedReader) -> T
    ): T {
        val gemini = usesNativeGemini(profile)
        val base = if (gemini) "https://generativelanguage.googleapis.com/v1beta" else profile.endpoint().toString()
        val connection = java.net.URI(base + path).toURL().openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = if (body == null) 30_000 else 90_000
            connection.setRequestProperty("Accept", if (streaming) "text/event-stream" else "application/json")
            val key = store.key(profile)
            if (gemini) {
                connection.setRequestProperty("x-goog-api-key", key)
            } else if (profile.protocol == ProviderProtocol.CLAUDE) {
                connection.setRequestProperty("x-api-key", key)
                connection.setRequestProperty("anthropic-version", "2023-06-01")
            } else connection.setRequestProperty("Authorization", "Bearer $key")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val payload = body.toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
            }
            val status = connection.responseCode
            check(status in 200..299) {
                when (status) {
                    401, 403 -> "Provider HTTP $status. Check your key and model permissions."
                    400 -> "Provider HTTP 400. Check that the selected model supports text chat and your account can access it."
                    429 -> "Provider HTTP 429. Check rate limits and account quota."
                    404 -> "Provider HTTP 404. Check the endpoint and model ID."
                    500, 502, 503, 504 -> "Provider HTTP $status. The service could not complete the request. Try again later."
                    in 300..399 -> "Provider redirected the request. Update the endpoint before sending your key again."
                    else -> "Provider returned HTTP $status."
                }
            }
            if (streaming) check(connection.contentType.orEmpty().startsWith("text/event-stream", ignoreCase = true)) {
                "Gemini returned an unexpected streaming response."
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use(decode)
        } catch (_: org.json.JSONException) {
            error("${profile.name} returned an unreadable response. Check the selected endpoint and model.")
        } catch (_: SocketTimeoutException) {
            error(if (body == null) "${profile.name} timed out while listing models. Check your connection and try again."
                else "${profile.name} did not return a reply in time. Retry this message or select a faster chat model in Settings.")
        } catch (_: UnknownHostException) {
            error("Could not reach ${profile.name}. Check your internet connection and endpoint.")
        } catch (_: SSLException) {
            error("Secure connection to ${profile.name} failed. Check your connection and endpoint.")
        } finally {
            connection.disconnect()
        }
    }
}
