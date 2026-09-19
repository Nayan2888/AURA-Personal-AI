package ai.aura.personal.core.evaluation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EvaluationReportStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun reportCanBeSavedAndReloaded() {
        val root = temporaryFolder.newFolder("evaluation")
        val report = EvaluationReport(
            id = "eval-1",
            baseVersionId = "base-1",
            candidateVersionId = "candidate-1",
            evaluatedExampleCount = 5,
            baseMeanLoss = 1.0,
            candidateMeanLoss = 0.8,
            safetyChecksPassed = true,
            compatibilityChecksPassed = true,
            completedAtEpochMs = 100L
        )

        EvaluationReportStore(root).save(report)
        val reloaded = EvaluationReportStore(root).get("eval-1")

        assertNotNull(reloaded)
        assertEquals(report, reloaded)
    }

    @Test
    fun reportsAreSortedByCompletionTimeThenId() {
        val root = temporaryFolder.newFolder("evaluation")
        val store = EvaluationReportStore(root)

        store.save(
            EvaluationReport(
                id = "eval-b",
                baseVersionId = "base",
                candidateVersionId = "candidate-b",
                evaluatedExampleCount = 1,
                baseMeanLoss = 1.0,
                candidateMeanLoss = 1.0,
                safetyChecksPassed = true,
                compatibilityChecksPassed = true,
                completedAtEpochMs = 20L
            )
        )
        store.save(
            EvaluationReport(
                id = "eval-a",
                baseVersionId = "base",
                candidateVersionId = "candidate-a",
                evaluatedExampleCount = 1,
                baseMeanLoss = 1.0,
                candidateMeanLoss = 1.0,
                safetyChecksPassed = true,
                compatibilityChecksPassed = true,
                completedAtEpochMs = 20L
            )
        )

        assertEquals(
            listOf("eval-a", "eval-b"),
            store.list().map { it.id }
        )
    }

    @Test
    fun duplicateReportIdsAreRejected() {
        val root = temporaryFolder.newFolder("evaluation")
        val store = EvaluationReportStore(root)
        val report = EvaluationReport(
            id = "eval-1",
            baseVersionId = "base",
            candidateVersionId = "candidate",
            evaluatedExampleCount = 1,
            baseMeanLoss = 1.0,
            candidateMeanLoss = 0.9,
            safetyChecksPassed = true,
            compatibilityChecksPassed = true,
            completedAtEpochMs = 1L
        )

        store.save(report)

        val error = runCatching { store.save(report) }.exceptionOrNull()

        assertEquals("Evaluation report already exists: eval-1", error?.message)
    }
}
