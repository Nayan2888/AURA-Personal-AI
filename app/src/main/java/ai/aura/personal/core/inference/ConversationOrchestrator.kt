package ai.aura.personal.core.inference

import ai.aura.personal.core.chat.ChatMessage
import ai.aura.personal.core.experience.LearningConsentStore
import ai.aura.personal.core.knowledge.LearnedKnowledgeStore
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
    private val researchProvider: ResearchProvider? = null,
    private val learnedKnowledgeStore: LearnedKnowledgeStore? = null,
    private val learningConsentStore: LearningConsentStore? = null
) {
    suspend fun respond(
        history: List<ChatMessage>,
        userMessage: ChatMessage,
        loraAdapterFile: File? = null
    ): ChatMessage {
        require(userMessage.role == ChatMessage.Role.USER) {
            "ConversationOrchestrator requires a USER message."
        }

        val learningEnabled = learningConsentStore?.isGranted() ?: true
        val learnedSources = if (learningEnabled) {
            learnedKnowledgeStore?.search(userMessage.content).orEmpty()
        } else {
            emptyList()
        }

        val localHistory = if (learnedSources.isEmpty()) {
            history
        } else {
            history + ChatMessage(
                id = "learned-knowledge-" + userMessage.id,
                role = ChatMessage.Role.SYSTEM,
                content = ResearchContextFormatter.format(
                    learnedSources.map { entry ->
                        ai.aura.personal.core.research.ResearchSource(
                            title = entry.title,
                            url = entry.url,
                            excerpt = entry.excerpt,
                            provider = entry.provider
                        )
                    }
                ),
                createdAtEpochMs = System.currentTimeMillis()
            )
        }

        val output = engine.generate(
            history = localHistory,
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

        val research = try {
            provider.search(userMessage.content, ResearchProvider.DEFAULT_MAX_RESULTS)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

        if (research == null || research.sources.isEmpty()) {
            return ChatMessage(
                id = "assistant-" + userMessage.id,
                role = ChatMessage.Role.ASSISTANT,
                content = output,
                createdAtEpochMs = System.currentTimeMillis()
            )
        }

        val evidence = ResearchContextFormatter.format(research.sources)
        if (learningConsentStore?.isGranted() == true) {
            runCatching {
                learnedKnowledgeStore?.remember(
                    query = userMessage.content,
                    sources = research.sources,
                    consentGranted = true,
                    learnedAtEpochMs = System.currentTimeMillis()
                )
            }
        }

        val groundedOutput = engine.generate(
            history = localHistory + ChatMessage(
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
