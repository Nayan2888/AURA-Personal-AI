package ai.aura.personal.core.versions

import ai.aura.personal.core.evaluation.EvaluationReport
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelVersionStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun candidateCanBeRegisteredAndPersisted() {
        val root = temporaryFolder.newFolder("versions")
        val adapter = File(root, "candidate.adapter").apply { writeText("adapter") }
        val candidate = ModelVersion(
            id = "candidate-1",
            baseModelId = "base-1",
            adapterFile = adapter,
            state = ModelVersion.State.CANDIDATE,
            evaluationReportId = "eval-1",
            createdAtEpochMs = 1L
        )

        ModelVersionStore(root).registerCandidate(candidate)

        val reloaded = ModelVersionStore(root).listCandidates()
        assertEquals(1, reloaded.size)
        assertEquals("candidate-1", reloaded.single().id)
    }

    @Test
    fun activationRequiresApprovalAndEvaluationChecks() {
        val root = temporaryFolder.newFolder("versions")
        val adapter = File(root, "candidate.adapter").apply { writeText("adapter") }
        val candidate = ModelVersion(
            id = "candidate-1",
            baseModelId = "base-1",
            adapterFile = adapter,
            state = ModelVersion.State.CANDIDATE,
            evaluationReportId = "eval-1",
            createdAtEpochMs = 1L
        )
        val store = ModelVersionStore(root)
        store.registerCandidate(candidate)

        val report = EvaluationReport(
            id = "eval-1",
            baseVersionId = "base-1",
            candidateVersionId = "candidate-1",
            evaluatedExampleCount = 5,
            baseMeanLoss = 1.0,
            candidateMeanLoss = 0.9,
            safetyChecksPassed = true,
            compatibilityChecksPassed = true,
            completedAtEpochMs = 2L
        )

        assertTrue(
            store.activateCandidate(
                candidateId = "candidate-1",
                evaluationReport = report,
                approvalGranted = false
            ) is ModelVersionStore.ActivationResult.REJECTED
        )

        assertTrue(
            store.activateCandidate(
                candidateId = "candidate-1",
                evaluationReport = report,
                approvalGranted = true
            ) is ModelVersionStore.ActivationResult.ACTIVATED
        )

        assertEquals("candidate-1", store.active()?.id)
    }
}
