package ai.aura.personal.core.evaluation

import ai.aura.personal.core.chat.ChatMessage
import ai.aura.personal.core.inference.AssistantEngine
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelEvaluationEngineTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun evaluatesBaseAndCandidateWithTheSameExamples() = runBlocking {
        val adapter = temporaryFolder.newFile("candidate.adapter").apply {
            writeText("adapter")
        }
        val engine = object : AssistantEngine {
            override suspend fun initialize() = Unit

            override suspend fun generate(
                history: List<ChatMessage>,
                userInput: String,
                loraAdapterFile: File?
            ): String = if (loraAdapterFile == null) {
                "base answer"
            } else {
                "candidate answer"
            }

            override fun isInitialized(): Boolean = true

            override fun close() = Unit
        }

        val result = ModelEvaluationEngine(
            assistantEngine = engine,
            metric = TokenEditDistanceMetric()
        ).evaluate(
            examples = listOf(
                EvaluationExample(
                    id = "example-1",
                    input = "question",
                    expectedOutput = "candidate answer"
                )
            ),
            candidateAdapterFile = adapter
        )

        assertEquals(1, result.evaluatedExampleCount)
        // "base answer" vs "candidate answer" differs by one of two tokens, so\n        // normalized token edit distance is 0.5.\n        assertEquals(0.5, result.baseMeanError, 0.0)
        assertEquals(0.0, result.candidateMeanError, 0.0)
    }
}
