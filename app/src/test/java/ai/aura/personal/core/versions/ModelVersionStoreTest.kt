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

    @Test
    fun candidateOutsideArtifactDirectoryIsRejected() {
        val root = temporaryFolder.newFolder("versions")
        val outside = temporaryFolder.newFile("outside.adapter").apply {
            writeText("adapter")
        }
        val candidate = ModelVersion(
            id = "candidate-outside",
            baseModelId = "base-1",
            adapterFile = outside,
            state = ModelVersion.State.CANDIDATE,
            evaluationReportId = "eval-outside",
            createdAtEpochMs = 1L
        )

        val error = runCatching {
            ModelVersionStore(root).registerCandidate(candidate)
        }.exceptionOrNull()

        assertEquals(
            "Candidate adapter file is outside the candidate artifact directory",
            error?.message
        )
    }

    @Test
    fun activationRejectsMissingCandidateArtifact() {
        val root = temporaryFolder.newFolder("versions")
        val adapter = TrainingArtifactStore(root).publish(
            temporaryFolder.newFile("candidate-source.adapter").apply {
                writeText("adapter")
            },
            "candidate-missing"
        )
        val candidate = ModelVersion(
            id = "candidate-missing",
            baseModelId = "base-1",
            adapterFile = adapter,
            state = ModelVersion.State.CANDIDATE,
            evaluationReportId = "eval-missing",
            createdAtEpochMs = 1L
        )
        val store = ModelVersionStore(root)
        store.registerCandidate(candidate)
        assertTrue(adapter.delete())

        val report = EvaluationReport(
            id = "eval-missing",
            baseVersionId = "base-1",
            candidateVersionId = "candidate-missing",
            evaluatedExampleCount = 1,
            baseMeanLoss = 1.0,
            candidateMeanLoss = 0.9,
            safetyChecksPassed = true,
            compatibilityChecksPassed = true,
            completedAtEpochMs = 2L
        )

        val error = runCatching {
            store.activateCandidate(
                candidateId = "candidate-missing",
                evaluationReport = report,
                approvalGranted = true
            )
        }.exceptionOrNull()

        assertEquals("Candidate adapter file is unavailable", error?.message)
    }
}
