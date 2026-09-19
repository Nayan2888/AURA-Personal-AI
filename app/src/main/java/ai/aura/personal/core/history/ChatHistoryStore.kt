package ai.aura.personal.core.history

import android.content.Context
import ai.aura.personal.core.chat.ChatMessage
import ai.aura.personal.core.chat.ChatSession
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, device-only persistence for AURA conversations.
 * Uses SharedPreferences for the first storage layer; no network is involved.
 */
class ChatHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun save(session: ChatSession, title: String) {
        val conversations = readConversations()
        val conversation = JSONObject().apply {
            put("id", session.id)
            put("title", title.trim().ifEmpty { "AURA Chat" })
            put("updatedAtEpochMs", System.currentTimeMillis())
            put("messages", JSONArray().apply {
                session.state.messages.forEach { message ->
                    put(JSONObject().apply {
                        put("id", message.id)
                        put("role", message.role.name)
                        put("content", message.content)
                        put("createdAtEpochMs", message.createdAtEpochMs)
                    })
                }
            })
        }

        var replaced = false
        for (index in 0 until conversations.length()) {
            if (conversations.getJSONObject(index).optString("id") == session.id) {
                conversations.put(index, conversation)
                replaced = true
                break
            }
        }
        if (!replaced) conversations.put(conversation)
        preferences.edit().putString(KEY_CONVERSATIONS, conversations.toString()).apply()
    }

    fun list(): List<HistorySummary> {
        val conversations = readConversations()
        return buildList {
            for (index in 0 until conversations.length()) {
                val item = conversations.getJSONObject(index)
                add(
                    HistorySummary(
                        id = item.optString("id"),
                        title = item.optString("title", "AURA Chat"),
                        messageCount = item.optJSONArray("messages")?.length() ?: 0,
                        updatedAtEpochMs = item.optLong("updatedAtEpochMs", 0L)
                    )
                )
            }
        }.sortedByDescending { it.updatedAtEpochMs }
    }

    fun load(sessionId: String): ChatSession? {
        val conversations = readConversations()
        for (index in 0 until conversations.length()) {
            val item = conversations.getJSONObject(index)
            if (item.optString("id") == sessionId) {
                val messages = item.optJSONArray("messages") ?: JSONArray()
                val session = ChatSession(sessionId)
                var restored = session
                for (messageIndex in 0 until messages.length()) {
                    val message = messages.getJSONObject(messageIndex)
                    restored = restored.appendMessage(
                        ChatMessage(
                            id = message.getString("id"),
                            role = ChatMessage.Role.valueOf(message.getString("role")),
                            content = message.getString("content"),
                            createdAtEpochMs = message.getLong("createdAtEpochMs")
                        )
                    )
                }
                return restored
            }
        }
        return null
    }

    fun rename(sessionId: String, title: String) {
        val conversations = readConversations()
        for (index in 0 until conversations.length()) {
            val item = conversations.getJSONObject(index)
            if (item.optString("id") == sessionId) {
                item.put("title", title.trim().ifEmpty { "AURA Chat" })
                break
            }
        }
        preferences.edit().putString(KEY_CONVERSATIONS, conversations.toString()).apply()
    }

    fun delete(sessionId: String) {
        val conversations = readConversations()
        for (index in conversations.length() - 1 downTo 0) {
            if (conversations.getJSONObject(index).optString("id") == sessionId) {
                conversations.remove(index)
            }
        }
        preferences.edit().putString(KEY_CONVERSATIONS, conversations.toString()).apply()
    }

    private fun readConversations(): JSONArray = runCatching {
        JSONArray(preferences.getString(KEY_CONVERSATIONS, "[]"))
    }.getOrElse { JSONArray() }

    data class HistorySummary(
        val id: String,
        val title: String,
        val messageCount: Int,
        val updatedAtEpochMs: Long
    )

    private companion object {
        const val PREFERENCES_NAME = "aura_chat_history"
        const val KEY_CONVERSATIONS = "conversations"
    }
}
