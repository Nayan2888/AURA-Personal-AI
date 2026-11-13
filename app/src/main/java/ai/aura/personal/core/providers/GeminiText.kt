package ai.aura.personal.core.providers

import ai.aura.personal.core.chat.ChatMessage
import java.io.BufferedReader
import org.json.JSONArray
import org.json.JSONObject

internal object GeminiText {
    private val modelId = Regex("[a-zA-Z0-9][a-zA-Z0-9._-]*")

    fun generationPath(model: String, streaming: Boolean = false): String {
        val id = model.removePrefix("models/")
        require(modelId.matches(id)) { "Select a valid Gemini model ID in Settings." }
        return if (streaming) "/models/$id:streamGenerateContent?alt=sse" else "/models/$id:generateContent"
    }

    fun chatModels(response: JSONObject): List<String> {
        val models = response.optJSONArray("models") ?: JSONArray()
        return models.objects().filter { model ->
            val methods = model.optJSONArray("supportedGenerationMethods") ?: JSONArray()
            val id = model.optString("name").removePrefix("models/")
            (0 until methods.length()).any { methods.optString(it) == "generateContent" } &&
                modelId.matches(id) &&
                listOf("image", "tts", "audio", "live", "robotics", "computer-use", "veo").none {
                    it in id.lowercase()
                }
        }.map { it.getString("name").removePrefix("models/") }.toList()
    }

    fun body(history: List<ChatMessage>, prompt: String): JSONObject {
        val contents = JSONArray()
        val instructions = mutableListOf<String>()
        for (message in history) {
            when (message.role) {
                ChatMessage.Role.SYSTEM -> instructions.add(message.content)
                ChatMessage.Role.USER -> contents.put(turn("user", message.content))
                ChatMessage.Role.ASSISTANT -> contents.put(turn("model", message.content))
            }
        }
        contents.put(turn("user", prompt))
        return JSONObject().put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", 4096))
            .apply {
                if (instructions.isNotEmpty()) put("systemInstruction", JSONObject()
                    .put("parts", parts(instructions.joinToString("\n"))))
            }
    }

    fun visibleText(response: JSONObject): String {
        val candidate = response.optJSONArray("candidates")?.optJSONObject(0)
        val parts = candidate?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        return parts.objects().filter { !it.optBoolean("thought") && it.opt("text") is String }
            .joinToString("") { it.getString("text") }
    }

    fun reply(response: JSONObject): String {
        val answer = visibleText(response).trim()
        if (answer.isNotEmpty()) return answer
        val candidate = response.optJSONArray("candidates")?.optJSONObject(0)

        val reason = response.optJSONObject("promptFeedback")?.optString("blockReason")
            .orEmpty().ifBlank { candidate?.optString("finishReason").orEmpty() }
        error(when (reason) {
            "MAX_TOKENS" -> "Gemini reached its token limit before returning text. Try a shorter prompt or a different model."
            "SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "RECITATION", "SPII" ->
                "Gemini blocked this response ($reason). Revise your message."
            else -> "Gemini returned no text" + (reason.takeIf { it.matches(Regex("[A-Z_]{1,40}")) }?.let { " ($it)" } ?: "") + "."
        })
    }

    fun readStream(reader: BufferedReader, onText: (String) -> Unit): String {
        val answer = StringBuilder()
        val event = StringBuilder()
        var last = JSONObject()
        var finished = false
        fun consume() {
            if (event.isEmpty()) return
            last = JSONObject(event.toString())
            event.clear()
            val candidate = last.optJSONArray("candidates")?.optJSONObject(0)
            val reason = candidate?.optString("finishReason").orEmpty()
            if (reason.isNotBlank() && reason != "FINISH_REASON_UNSPECIFIED") finished = true
            val piece = visibleText(last)
            if (piece.isNotEmpty()) {
                check(answer.length + piece.length <= 2_000_000) { "Gemini reply exceeded the app limit." }
                answer.append(piece)
                onText(answer.toString())
            }
        }
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) consume()
            else if (line.startsWith("data:")) {
                if (event.isNotEmpty()) event.append('\n')
                event.append(line.removePrefix("data:").trimStart())
                check(event.length <= 2_000_000) { "Gemini event exceeded the app limit." }
            }
        }
        consume()
        if (answer.isBlank()) return reply(last)
        check(finished) {
            "Gemini closed the stream before confirming completion. Retry to get a complete reply."
        }
        return answer.toString().trim()
    }

    private fun parts(text: String) = JSONArray().put(JSONObject().put("text", text))
    private fun turn(role: String, text: String) = JSONObject().put("role", role).put("parts", parts(text))
    private fun JSONArray.objects() = (0 until length()).asSequence().mapNotNull { optJSONObject(it) }
}
