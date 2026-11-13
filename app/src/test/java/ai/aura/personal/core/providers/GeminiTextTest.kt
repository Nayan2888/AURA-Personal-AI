package ai.aura.personal.core.providers

import ai.aura.personal.core.chat.ChatMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiTextTest {
    @Test
    fun request_keeps_roles_and_normalizes_existing_model_ids() {
        val history = listOf(
            ChatMessage("1", ChatMessage.Role.SYSTEM, "Reply in Hindi", 1),
            ChatMessage("2", ChatMessage.Role.USER, "नमस्ते", 2),
            ChatMessage("3", ChatMessage.Role.ASSISTANT, "नमस्कार", 3)
        )
        val body = GeminiText.body(history, "कैसे हो?")
        val turns = body.getJSONArray("contents")
        assertEquals("/models/gemini-2.5-flash:generateContent", GeminiText.generationPath("models/gemini-2.5-flash"))
        assertEquals(listOf("user", "model", "user"), (0 until turns.length()).map { turns.getJSONObject(it).getString("role") })
        assertEquals("Reply in Hindi", body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        assertThrows(IllegalArgumentException::class.java) { GeminiText.generationPath("models/example/../path") }
    }

    @Test
    fun catalog_excludes_nonchat_model_types() {
        val body = JSONObject("""
            {"models":[
              {"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]},
              {"name":"models/gemini-2.5-flash-preview-tts","supportedGenerationMethods":["generateContent"]},
              {"name":"models/gemini-embedding-001","supportedGenerationMethods":["embedContent"]}
            ]}
        """.trimIndent())
        assertEquals(listOf("gemini-2.5-flash"), GeminiText.chatModels(body))
    }

    @Test
    fun reply_keeps_visible_text_and_rejects_blocked_or_thought_only_responses() {
        val reply = JSONObject("""
            {"candidates":[{"content":{"parts":[{"thought":true,"text":"internal"},
            {"text":"नमस्ते"},{"text":" दोस्त"}]},"finishReason":"STOP"}]}
        """.trimIndent())
        assertEquals("नमस्ते दोस्त", GeminiText.reply(reply))
        val blocked = assertThrows(IllegalStateException::class.java) {
            GeminiText.reply(JSONObject("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        }
        assertTrue(blocked.message.orEmpty().contains("SAFETY"))
        val truncated = assertThrows(IllegalStateException::class.java) {
            GeminiText.reply(JSONObject("""{"candidates":[{"content":{"parts":[{"thought":true,"text":"internal"}]},"finishReason":"MAX_TOKENS"}]}"""))
        }
        assertTrue(truncated.message.orEmpty().contains("token limit"))
    }
    @Test
    fun stream_displays_text_in_order_and_rejects_truncated_reply() {
        val first = """data: {"candidates":[{"content":{"parts":[{"text":"नमस्ते"}]}}]}"""
        val second = """data: {"candidates":[{"content":{"parts":[{"text":" दोस्त"}]},"finishReason":"STOP"}]}"""
        val usage = """data: {"usageMetadata":{"totalTokenCount":20}}"""
        val updates = mutableListOf<String>()
        assertEquals("नमस्ते दोस्त", GeminiText.readStream(
            listOf(first, second, usage).joinToString("\n\n").reader().buffered(), updates::add
        ))
        assertEquals(listOf("नमस्ते", "नमस्ते दोस्त"), updates)
        assertThrows(IllegalStateException::class.java) {
            GeminiText.readStream(first.reader().buffered()) {}
        }
    }
}
