package ai.aura.personal.core.chat

/**
 * Immutable state for one AURA chat surface.
 */
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val errorMessage: String? = null
) {
    fun appendMessage(message: ChatMessage): ChatState =
        copy(messages = messages + message, errorMessage = null)

    fun withProcessing(processing: Boolean): ChatState =
        copy(isProcessing = processing)

    fun withError(message: String?): ChatState =
        copy(errorMessage = message)
}
