package ai.aura.personal.core.inference

import ai.aura.personal.core.chat.ChatMessage
import java.io.File

/**
 * Coordinates a user turn with the configured assistant engine.
 * It never fabricates a response when the engine is unavailable.
 */
class ConversationOrchestrator(
    private val engine: AssistantEngine
) {
    suspend fun respond(
        history: List<ChatMessage>,
        userMessage: ChatMessage,
        loraAdapterFile: File? = null
    ): ChatMessage {
        require(userMessage.role == ChatMessage.Role.USER) {
            "ConversationOrchestrator requires a USER message."
        }

        val output = engine.generate(
            history = history,
            userInput = userMessage.content,
            loraAdapterFile = loraAdapterFile
        )

        return ChatMessage(
            id = "assistant-${userMessage.id}",
            role = ChatMessage.Role.ASSISTANT,
            content = output,
            createdAtEpochMs = System.currentTimeMillis()
        )
    }
}
