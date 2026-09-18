package ai.aura.personal.core.chat

/**
 * Immutable container for one AURA conversation session.
 * State transitions return a new session instead of mutating existing data.
 */
data class ChatSession(
    val id: String,
    val state: ChatState = ChatState()
) {
    init {
        require(id.isNotBlank()) { "Session id must not be blank" }
    }

    fun appendMessage(message: ChatMessage): ChatSession =
        copy(state = state.appendMessage(message))

    fun withProcessing(processing: Boolean): ChatSession =
        copy(state = state.withProcessing(processing))

    fun withError(message: String?): ChatSession =
        copy(state = state.withError(message))
}
