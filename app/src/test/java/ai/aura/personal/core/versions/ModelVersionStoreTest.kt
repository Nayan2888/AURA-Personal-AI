package ai.aura.personal.core.versions

import ai.aura.personal.core.evaluation.EvaluationReport
import ai.aura.personal.core.security.ArtifactDigest
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
        val baseModel = temporaryFolder.newFile("base-initial.litertlm").apply {
            writeText("base")
        }
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
            baseModelSha256 = ArtifactDigest.sha256(baseModel),
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
            candidateAdapterSha256 = ArtifactDigest.sha256(adapter),
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
        val baseModel = temporaryFolder.newFile("base-approval-order.litertlm").apply {
            writeText("base")
        }
        val adapter = TrainingArtifactStore(root).publish(
            temporaryFolder.newFile("candidate-source.adapter")
                .apply { writeText("adapter") },
            "candidate-1"
        )
        val store = ModelVersionStore(root)
        store.registerCandidate(
            ModelVersion(
                id = "candidate-1",
                baseModelId = "base-1",
                adapterFile = adapter,
                state = ModelVersion.State.CANDIDATE,
                baseModelSha256 = ArtifactDigest.sha256(baseModel),
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
                candidateAdapterSha256 = ArtifactDigest.sha256(adapter),
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
 
    @Test
    fun activationRejectsDifferentBaseModelFingerprintEvenWhenIdsMatch() {
        val root = temporaryFolder.newFolder("fingerprint-mismatch")
        val firstBase = temporaryFolder.newFile("first-base.litertlm").apply {
            writeText("first-base")
        }
        val secondBase = temporaryFolder.newFile("second-base.litertlm").apply {
            writeText("second-base")
        }
        val firstAdapter = TrainingArtifactStore(root).publish(
            temporaryFolder.newFile("first.adapter").apply { writeText("first") },
            "candidate-1"
        )
        val secondAdapter = TrainingArtifactStore(root).publish(
            temporaryFolder.newFile("second.adapter").apply { writeText("second") },
            "candidate-2"
        )
        val store = ModelVersionStore(root)

        store.registerCandidate(
            ModelVersion(
                id = "candidate-1",
                baseModelId = "base-1",
                adapterFile = firstAdapter,
                state = ModelVersion.State.CANDIDATE,
                baseModelSha256 = ArtifactDigest.sha256(firstBase),
                evaluationReportId = "eval-1",
                createdAtEpochMs = 1L
            )
        )
        val firstReport = EvaluationReport(
            id = "eval-1",
            baseVersionId = "base-1",
            candidateVersionId = "candidate-1",
            candidateAdapterSha256 = ArtifactDigest.sha256(firstAdapter),
            evaluatedExampleCount = 1,
            baseMeanError = 1.0,
            candidateMeanError = 0.9,
            safetyChecksPassed = true,
            compatibilityChecksPassed = true,
            completedAtEpochMs = 2L
        )
        assertTrue(
            store.activateCandidate(
                "candidate-1",
                firstReport,
                ActivationApproval("approval-1", "candidate-1", "eval-1", 3L)
            ) is ModelVersionStore.ActivationResult.ACTIVATED
        )

        store.registerCandidate(
            ModelVersion(
                id = "candidate-2",
                baseModelId = "base-1",
                adapterFile = secondAdapter,
                state = ModelVersion.State.CANDIDATE,
                baseModelSha256 = ArtifactDigest.sha256(secondBase),
                evaluationReportId = "eval-2",
                createdAtEpochMs = 4L
            )
        )
        val result = store.activateCandidate(
            "candidate-2",
            EvaluationReport(
                id = "eval-2",
                baseVersionId = "candidate-1",
                candidateVersionId = "candidate-2",
                candidateAdapterSha256 = ArtifactDigest.sha256(secondAdapter),
                evaluatedExampleCount = 1,
                baseMeanError = 1.0,
                candidateMeanError = 0.9,
                safetyChecksPassed = true,
                compatibilityChecksPassed = true,
                completedAtEpochMs = 5L
            ),
            ActivationApproval("approval-2", "candidate-2", "eval-2", 6L)
        )

        assertEquals(
            "Candidate base model fingerprint does not match the current active model",
            (result as ModelVersionStore.ActivationResult.REJECTED).reason
        )
    }

    @Test
    fun activationRejectsArtifactChangedAfterEvaluation() {
        val root = temporaryFolder.newFolder("versions-tampered")
        val baseModel = temporaryFolder.newFile("base-tampered.litertlm").apply {
            writeText("base")
        }
        val adapter = TrainingArtifactStore(root).publish(
            temporaryFolder.newFile("candidate-source.adapter")
                .apply { writeText("adapter") },
            "candidate-1"
        )
        val store = ModelVersionStore(root)
        store.registerCandidate(
            ModelVersion(
                id = "candidate-1",
                baseModelId = "base-1",
                baseModelSha256 = ArtifactDigest.sha256(baseModel),
                adapterFile = adapter,
                state = ModelVersion.State.CANDIDATE,
                evaluationReportId = "eval-1",
                createdAtEpochMs = 1L
            )
        )

        val report = EvaluationReport(
            id = "eval-1",
            baseVersionId = "base-1",
            candidateVersionId = "candidate-1",
            candidateAdapterSha256 = ArtifactDigest.sha256(adapter),
            evaluatedExampleCount = 1,
            baseMeanError = 0.5,
            candidateMeanError = 0.4,
            safetyChecksPassed = true,
            compatibilityChecksPassed = true,
            completedAtEpochMs = 2L
        )
        val approval = ActivationApproval(
            id = "approval-1",
            candidateVersionId = "candidate-1",
            evaluationReportId = "eval-1",
            grantedAtEpochMs = 3L
        )

        adapter.writeText("tampered")

        val result = store.activateCandidate(
            candidateId = "candidate-1",
            evaluationReport = report,
            approval = approval
        )

        assertEquals(
            "Candidate adapter hash does not match evaluation evidence",
            (result as ModelVersionStore.ActivationResult.REJECTED).reason
        )
    }
}
