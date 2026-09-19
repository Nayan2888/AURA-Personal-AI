package ai.aura.personal.core.inference

import ai.aura.personal.core.chat.ChatMessage
import java.io.File

/**
 * Owns the currently selected local model runtime.
 * Switching models always closes the previous native engine first.
 */
class AssistantRuntimeManager : AutoCloseable {
    private var engine: LiteRtLmAssistantEngine? = null

    suspend fun load(modelFile: File) {
        close()
        val newEngine = LiteRtLmAssistantEngine(modelFile)
        try {
            newEngine.initialize()
            engine = newEngine
        } catch (error: Throwable) {
            newEngine.close()
            throw error
        }
    }

    fun isReady(): Boolean = engine?.isInitialized() == true

    suspend fun respond(
        history: List<ChatMessage>,
        userMessage: ChatMessage
    ): ChatMessage {
        val activeEngine = checkNotNull(engine) {
            "No local model is installed. Import a .litertlm model first."
        }
        return ConversationOrchestrator(activeEngine).respond(history, userMessage)
    }

    override fun close() {
        engine?.close()
        engine = null
    }
}
