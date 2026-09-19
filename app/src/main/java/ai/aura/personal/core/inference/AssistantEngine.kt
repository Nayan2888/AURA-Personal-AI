package ai.aura.personal.core.inference

import ai.aura.personal.core.chat.ChatMessage

/**
 * Provider-neutral contract for AURA's assistant model.
 * Implementations must perform real inference or report an explicit unavailable/error state.
 */
interface AssistantEngine : AutoCloseable {
    suspend fun initialize()

    suspend fun generate(
        history: List<ChatMessage>,
        userInput: String
    ): String

    fun isInitialized(): Boolean

    override fun close()
}
