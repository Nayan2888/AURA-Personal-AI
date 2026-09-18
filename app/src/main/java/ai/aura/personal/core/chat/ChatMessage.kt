package ai.aura.personal.core.chat

/**
 * Immutable message in an AURA conversation.
 * IDs and timestamps are supplied by the caller so this model remains deterministic and testable.
 */
data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val createdAtEpochMs: Long
) {
    init {
        require(id.isNotBlank()) { "Message id must not be blank" }
        require(content.isNotBlank()) { "Message content must not be blank" }
        require(createdAtEpochMs >= 0L) { "Message timestamp must not be negative" }
    }

    enum class Role {
        USER,
        ASSISTANT,
        SYSTEM
    }
}
