package ai.aura.personal.core.evaluation

import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluationReportTest {
    @Test
    fun reportStoresMeasuredValues() {
        val report = EvaluationReport(
            id = "eval-1",
            baseVersionId = "base-1",
            candidateVersionId = "candidate-1",
            evaluatedExampleCount = 10,
            baseMeanLoss = 1.2,
            candidateMeanLoss = 1.1,
            safetyChecksPassed = true,
            compatibilityChecksPassed = true,
            completedAtEpochMs = 42L
        )

        assertEquals(1.2, report.baseMeanLoss, 0.0)
        assertEquals(1.1, report.candidateMeanLoss, 0.0)
        assertEquals(10, report.evaluatedExampleCount)
    }
}
