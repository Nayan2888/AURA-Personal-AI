package ai.aura.personal.core.inference

import ai.aura.personal.core.chat.ChatMessage
import ai.aura.personal.core.research.ResearchProvider
import ai.aura.personal.core.research.ResearchResponse
import ai.aura.personal.core.research.ResearchSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConversationOrchestratorResearchTest {

    @Test
    fun uncertainLocalAnswerIsRewrittenUsingResearchEvidence() = runBlocking {
        val engine = FakeEngine()
        val provider = object : ResearchProvider {
            override suspend fun search(
                query: String,
                maxResults: Int
            ): ResearchResponse = ResearchResponse(
                query = query,
                sources = listOf(
                    ResearchSource(
                        title = "Authoritative article",
                        url = "https://example.org/article",
                        excerpt = "Grounded factual evidence.",
                        provider = "Test"
                    )
                )
            )
        }

        val result = ConversationOrchestrator(
            engine = engine,
            researchProvider = provider
        ).respond(
            history = emptyList(),
            userMessage = ChatMessage(
                id = "user-1",
                role = ChatMessage.Role.USER,
                content = "Tell me about this unknown topic.",
                createdAtEpochMs = 1L
            )
        )

        assertEquals("researched answer", result.content.substringBefore("

Sources:"))
        assertEquals(2, engine.callCount)
        assertTrue(engine.secondHistory.any { it.role == ChatMessage.Role.SYSTEM })
        assertTrue(engine.secondHistory.any { it.content.contains("UNTRUSTED RESEARCH DATA") })
        assertTrue(result.content.contains("https://example.org/article"))
    }

    @Test
    fun researchFailureFallsBackToTheRealLocalAnswer() = runBlocking {
        val engine = object : AssistantEngine {
            override suspend fun initialize() = Unit

            override suspend fun generate(
                history: List<ChatMessage>,
                userInput: String,
                loraAdapterFile: File?
            ): String = "I don't know."

            override fun isInitialized(): Boolean = true

            override fun close() = Unit
        }

        val provider = object : ResearchProvider {
            override suspend fun search(
                query: String,
                maxResults: Int
            ): ResearchResponse {
                throw IllegalStateException("research unavailable")
            }
        }

        val result = ConversationOrchestrator(
            engine = engine,
            researchProvider = provider
        ).respond(
            history = emptyList(),
            userMessage = ChatMessage(
                id = "user-2",
                role = ChatMessage.Role.USER,
                content = "Who is this?",
                createdAtEpochMs = 1L
            )
        )

        assertEquals("I don't know.", result.content)
    }

    private class FakeEngine : AssistantEngine {
        var callCount = 0
        var secondHistory: List<ChatMessage> = emptyList()

        override suspend fun initialize() = Unit

        override suspend fun generate(
            history: List<ChatMessage>,
            userInput: String,
            loraAdapterFile: File?
        ): String {
            callCount += 1
            if (callCount == 2) {
                secondHistory = history
                return "researched answer"
            }
            return "I don't know."
        }

        override fun isInitialized(): Boolean = true

        override fun close() = Unit
    }
}
