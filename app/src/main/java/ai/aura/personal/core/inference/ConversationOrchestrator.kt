package ai.aura.personal.core.inference

import ai.aura.personal.core.chat.ChatMessage
import ai.aura.personal.core.research.ResearchContextFormatter
import ai.aura.personal.core.research.ResearchProvider
import ai.aura.personal.core.research.ResearchTrigger
import java.io.File

/**
 * Coordinates a user turn with the configured assistant engine.
 * It never fabricates a response when the engine is unavailable.
 */
class ConversationOrchestrator(
    private val engine: AssistantEngine,
    private val researchProvider: ResearchProvider? = null
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

        val provider = researchProvider
        if (provider == null || !ResearchTrigger.shouldResearch(userMessage.content, output)) {
            return ChatMessage(
                id = "assistant-" + userMessage.id,
                role = ChatMessage.Role.ASSISTANT,
                content = output,
                createdAtEpochMs = System.currentTimeMillis()
            )
        }

        val research = runCatching {
            provider.search(userMessage.content, ResearchProvider.DEFAULT_MAX_RESULTS)
        }.getOrNull()

        if (research == null || research.sources.isEmpty()) {
            return ChatMessage(
                id = "assistant-" + userMessage.id,
                role = ChatMessage.Role.ASSISTANT,
                content = output,
                createdAtEpochMs = System.currentTimeMillis()
            )
        }

        val evidence = ResearchContextFormatter.format(research.sources)
        val groundedOutput = engine.generate(
            history = history + ChatMessage(
                id = "research-context-" + userMessage.id,
                role = ChatMessage.Role.SYSTEM,
                content = evidence,
                createdAtEpochMs = System.currentTimeMillis()
            ),
            userInput = userMessage.content,
            loraAdapterFile = loraAdapterFile
        )

        val sources = research.sources.joinToString(
            separator = "\n"
        ) { source ->
            "- " + source.title + ": " + source.url
        }

        return ChatMessage(
            id = "assistant-" + userMessage.id,
            role = ChatMessage.Role.ASSISTANT,
            content = groundedOutput + "\n\nSources:\n" + sources,
            createdAtEpochMs = System.currentTimeMillis()
        )
    }
}
