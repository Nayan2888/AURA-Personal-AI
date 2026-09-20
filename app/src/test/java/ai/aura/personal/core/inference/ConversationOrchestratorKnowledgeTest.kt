package ai.aura.personal.core.inference

import ai.aura.personal.core.chat.ChatMessage
import ai.aura.personal.core.experience.LearningConsentStore
import ai.aura.personal.core.knowledge.LearnedKnowledgeStore
import ai.aura.personal.core.research.ResearchProvider
import ai.aura.personal.core.research.ResearchResponse
import ai.aura.personal.core.research.ResearchSource
import android.content.Context
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class ConversationOrchestratorKnowledgeTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun researchedEvidenceIsRetainedAndReusedOfflineWhenLearningIsEnabled() {
        runBlocking {
            val context = org.robolectric.RuntimeEnvironment.getApplication()
            val consent = LearningConsentStore(context)
            consent.setGranted(true)
            val store = LearnedKnowledgeStore(temporaryFolder.root)
            val provider = object : ResearchProvider {
                override suspend fun search(
                    query: String,
                    maxResults: Int
                ): ResearchResponse = ResearchResponse(
                    query = query,
                    sources = listOf(
                        ResearchSource(
                            title = "Mars reference",
                            url = "https://example.org/mars",
                            excerpt = "Mars is the fourth planet from the Sun.",
                            provider = "Test"
                        )
                    )
                )
            }

            val firstEngine = RecordingEngine(listOf("I don't know.", "Mars answer"))
            ConversationOrchestrator(
                engine = firstEngine,
                researchProvider = provider,
                learnedKnowledgeStore = store,
                learningConsentStore = consent
            ).respond(
                history = emptyList(),
                userMessage = user("Tell me the current Mars facts.", "user-1")
            )

            assertEquals(1, store.list().size)

            val secondEngine = RecordingEngine(listOf("Offline Mars answer"))
            ConversationOrchestrator(
                engine = secondEngine,
                learnedKnowledgeStore = store,
                learningConsentStore = consent
            ).respond(
                history = emptyList(),
                userMessage = user("Tell me about Mars.", "user-2")
            )

            assertTrue(
                secondEngine.histories.single().any {
                    it.role == ChatMessage.Role.SYSTEM &&
                        it.content.contains("Mars is the fourth planet from the Sun.")
                }
            )
        }
    }

    @Test
    fun learnedKnowledgeIsNotInjectedWhenLearningConsentIsDisabled() {
        runBlocking {
            val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
            val consent = LearningConsentStore(context)
            consent.setGranted(false)
            val store = LearnedKnowledgeStore(temporaryFolder.root)
            store.remember(
                query = "Mars",
                sources = listOf(
                    ResearchSource(
                        title = "Mars",
                        url = "https://example.org/mars",
                        excerpt = "Mars evidence.",
                        provider = "Test"
                    )
                ),
                consentGranted = true,
                learnedAtEpochMs = 1L
            )

            val engine = RecordingEngine(listOf("Local answer"))
            ConversationOrchestrator(
                engine = engine,
                learnedKnowledgeStore = store,
                learningConsentStore = consent
            ).respond(
                history = emptyList(),
                userMessage = user("Tell me about Mars.", "user-3")
            )

            assertTrue(
                engine.histories.single().none {
                    it.role == ChatMessage.Role.SYSTEM &&
                        it.content.contains("Mars evidence.")
                }
            )
        }
    }

    private fun user(content: String, id: String): ChatMessage =
        ChatMessage(
            id = id,
            role = ChatMessage.Role.USER,
            content = content,
            createdAtEpochMs = 1L
        )

    private class RecordingEngine(
        private val outputs: List<String>
    ) : AssistantEngine {
        var callIndex = 0
        val histories = mutableListOf<List<ChatMessage>>()

        override suspend fun initialize() = Unit

        override suspend fun generate(
            history: List<ChatMessage>,
            userInput: String,
            loraAdapterFile: File?
        ): String {
            histories += history
            return outputs[callIndex++]
        }

        override fun isInitialized(): Boolean = true

        override fun close() = Unit
    }
}
