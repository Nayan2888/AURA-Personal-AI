package ai.aura.personal.core.versions

import ai.aura.personal.core.evaluation.EvaluationReport
import ai.aura.personal.core.training.TrainingArtifactStore
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
        val adapter = TrainingArtifactStore(root).publish(
            temporaryFolder.newFile("candidate-source.adapter").apply {
                writeText("adapter")
            },
            "candidate-1"
        )
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
        val adapter = TrainingArtifactStore(root).publish(
            temporaryFolder.newFile("candidate-source.adapter").apply {
                writeText("adapter")
            },
            "candidate-1"
        )
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
            baseMeanError = 0.5,
            candidateMeanError = 0.4,
            safetyChecksPassed = true,
            compatibilityChecksPassed = true,
            completedAtEpochMs = 2L
        )

        assertTrue(
            store.activateCandidate(
                candidateId = "candidate-1",
                evaluationReport = report,
                approval = null
            ) is ModelVersionStore.ActivationResult.REJECTED
        )

        val approval = ActivationApproval(
            id = "approval-1",
            candidateVersionId = "candidate-1",
            evaluationReportId = "eval-1",
            grantedAtEpochMs = 3L
        )
        assertTrue(
            store.activateCandidate(
                candidateId = "candidate-1",
                evaluationReport = report,
                approval = approval
            ) is ModelVersionStore.ActivationResult.ACTIVATED
        )

        assertEquals("candidate-1", store.active()?.id)
    }

    @Test
    fun approvalCannotPredateEvaluation() {
        val root = temporaryFolder.newFolder("approval-order")
        val adapter = TrainingArtifactStore(root).publish(
            temporaryFolder.newFile("candidate-source.adapter").apply {
                writeText("adapter")
            },
            "candidate-1"
        )
        val store = ModelVersionStore(root)
        store.registerCandidate(
            ModelVersion(
                id = "candidate-1",
                baseModelId = "base-1",
                adapterFile = adapter,
                state = ModelVersion.State.CANDIDATE,
                evaluationReportId = "eval-1",
                createdAtEpochMs = 1L
            )
        )

        val error = store.activateCandidate(
            candidateId = "candidate-1",
            evaluationReport = EvaluationReport(
                id = "eval-1",
                baseVersionId = "base-1",
                candidateVersionId = "candidate-1",
                evaluatedExampleCount = 1,
                baseMeanError = 0.5,
                candidateMeanError = 0.4,
                safetyChecksPassed = true,
                compatibilityChecksPassed = true,
                completedAtEpochMs = 100L
            ),
            approval = ActivationApproval(
                id = "approval-1",
                candidateVersionId = "candidate-1",
                evaluationReportId = "eval-1",
                grantedAtEpochMs = 99L
            )
        )

        assertEquals(
            "Activation approval must be granted after evaluation completed",
            (error as ModelVersionStore.ActivationResult.REJECTED).reason
        )
    }
}
