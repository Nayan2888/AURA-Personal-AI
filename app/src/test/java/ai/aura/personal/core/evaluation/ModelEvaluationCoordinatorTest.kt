package ai.aura.personal.core.evaluation

import ai.aura.personal.core.chat.ChatMessage
import ai.aura.personal.core.inference.AssistantEngine
import ai.aura.personal.core.security.ArtifactDigest
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelEvaluationCoordinatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun evaluatesAndPersistsImmutableEvidence() {
        runBlocking {
            val root = temporaryFolder.newFolder("evaluation-coordinator")
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

            val coordinator = ModelEvaluationCoordinator(
                evaluationEngine = ModelEvaluationEngine(
                    assistantEngine = engine,
                    metric = TokenEditDistanceMetric()
                ),
                reportStore = EvaluationReportStore(root),
                nowEpochMs = { 1234L }
            )

            val report = coordinator.evaluateAndPersist(
                reportId = "eval-1",
                baseVersionId = "base-1",
                candidateVersionId = "candidate-1",
                examples = listOf(
                    EvaluationExample(
                        id = "example-1",
                        input = "question",
                        expectedOutput = "candidate answer"
                    )
                ),
                candidateAdapterFile = adapter,
                safetyChecksPassed = true,
                compatibilityChecksPassed = true
            )

            assertEquals("eval-1", report.id)
            assertEquals(ArtifactDigest.sha256(adapter), report.candidateAdapterSha256)
            assertEquals(0.5, report.baseMeanError, 0.0)
            assertEquals(0.0, report.candidateMeanError, 0.0)
            assertEquals(1234L, report.completedAtEpochMs)

            val persisted = EvaluationReportStore(root).get("eval-1")
            assertNotNull(persisted)
            assertEquals(report, persisted)
        }
    }

    @Test
    fun blankReportIdIsRejected() {
        runBlocking {
            val root = temporaryFolder.newFolder("evaluation-coordinator-invalid")
            val adapter = temporaryFolder.newFile("candidate.adapter").apply {
                writeText("adapter")
            }
            val engine = object : AssistantEngine {
                override suspend fun initialize() = Unit

                override suspend fun generate(
                    history: List<ChatMessage>,
                    userInput: String,
                    loraAdapterFile: File?
                ): String = "answer"

                override fun isInitialized(): Boolean = true

                override fun close() = Unit
            }

            var rejected = false
            try {
                ModelEvaluationCoordinator(
                    ModelEvaluationEngine(engine),
                    EvaluationReportStore(root),
                    nowEpochMs = { 1234L }
                ).evaluateAndPersist(
                    reportId = "",
                    baseVersionId = "base-1",
                    candidateVersionId = "candidate-1",
                    examples = listOf(
                        EvaluationExample(
                            id = "example-1",
                            input = "question",
                            expectedOutput = "answer"
                        )
                    ),
                    candidateAdapterFile = adapter,
                    safetyChecksPassed = true,
                    compatibilityChecksPassed = true
                )
            } catch (_: IllegalArgumentException) {
                rejected = true
            }

            assertTrue("Blank report id must be rejected", rejected)
        }
    }

    @Test
    fun adapterMutationDuringEvaluationIsRejected() {
        runBlocking {
            val root = temporaryFolder.newFolder("evaluation-coordinator-mutating")
            val adapter = temporaryFolder.newFile("candidate.adapter").apply {
                writeText("adapter")
            }
            val engine = object : AssistantEngine {
                override suspend fun initialize() = Unit

                override suspend fun generate(
                    history: List<ChatMessage>,
                    userInput: String,
                    loraAdapterFile: File?
                ): String {
                    if (loraAdapterFile != null) {
                        loraAdapterFile.writeText("tampered")
                    }
                    return if (loraAdapterFile == null) "base answer" else "candidate answer"
                }

                override fun isInitialized(): Boolean = true
                override fun close() = Unit
            }

            val store = EvaluationReportStore(root)
            val coordinator = ModelEvaluationCoordinator(
                evaluationEngine = ModelEvaluationEngine(engine),
                reportStore = store,
                nowEpochMs = { 1234L }
            )

            val error = runCatching {
                coordinator.evaluateAndPersist(
                    reportId = "eval-mutated",
                    baseVersionId = "base-1",
                    candidateVersionId = "candidate-1",
                    examples = listOf(
                        EvaluationExample(
                            id = "example-1",
                            input = "question",
                            expectedOutput = "candidate answer"
                        )
                    ),
                    candidateAdapterFile = adapter,
                    safetyChecksPassed = true,
                    compatibilityChecksPassed = true
                )
            }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertEquals(null, store.get("eval-mutated"))
        }
    }
}
