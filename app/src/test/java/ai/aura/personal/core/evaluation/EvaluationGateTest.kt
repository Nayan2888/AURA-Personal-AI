package ai.aura.personal.core.evaluation

import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluationGateTest {
    private fun report(
        candidateLoss: Double = 0.9,
        safety: Boolean = true,
        compatibility: Boolean = true
    ) = EvaluationReport(
        id = "eval-1",
        baseVersionId = "base-1",
        candidateVersionId = "candidate-1",
        evaluatedExampleCount = 5,
        baseMeanLoss = 1.0,
        candidateMeanLoss = candidateLoss,
        safetyChecksPassed = safety,
        compatibilityChecksPassed = compatibility,
        completedAtEpochMs = 1L
    )

    @Test
    fun approvalIsRequired() {
        assertEquals(
            EvaluationGate.Decision.NOT_APPROVED,
            EvaluationGate.check(report(), approvalGranted = false)
        )
    }

    @Test
    fun safetyFailureBlocksActivation() {
        assertEquals(
            EvaluationGate.Decision.SAFETY_FAILED,
            EvaluationGate.check(report(safety = false), approvalGranted = true)
        )
    }

    @Test
    fun qualityRegressionBlocksActivationByDefault() {
        assertEquals(
            EvaluationGate.Decision.QUALITY_REGRESSION,
            EvaluationGate.check(report(candidateLoss = 1.01), approvalGranted = true)
        )
    }

    @Test
    fun passingCandidateCanActivate() {
        assertEquals(
            EvaluationGate.Decision.PASSED,
            EvaluationGate.check(report(candidateLoss = 0.9), approvalGranted = true)
        )
    }
}
