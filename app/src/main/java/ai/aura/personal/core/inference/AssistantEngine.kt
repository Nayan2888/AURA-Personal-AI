package ai.aura.personal.core.inference

import ai.aura.personal.core.chat.ChatMessage
import java.io.File

/**
 * Provider-neutral contract for AURA's assistant model.
 * Implementations must perform real inference or report an explicit unavailable/error state.
 *
 * A LoRA adapter is optional and is supplied per generation so candidate adapters can be
 * evaluated without silently changing the active base model.
 */
interface AssistantEngine : AutoCloseable {
    suspend fun initialize()

    suspend fun generate(
        history: List<ChatMessage>,
        userInput: String,
        loraAdapterFile: File? = null
    ): String

    fun isInitialized(): Boolean

    override fun close()
}
