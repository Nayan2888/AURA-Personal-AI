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
            candidateAdapterSha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            evaluatedExampleCount = 10,
            baseMeanError = 1.2,
            candidateMeanError = 1.1,
            safetyChecksPassed = true,
            compatibilityChecksPassed = true,
            completedAtEpochMs = 42L
        )

        assertEquals(1.2, report.baseMeanError, 0.0)
        assertEquals(1.1, report.candidateMeanError, 0.0)
        assertEquals(10, report.evaluatedExampleCount)
        assertEquals(64, report.candidateAdapterSha256.length)
    }
}
