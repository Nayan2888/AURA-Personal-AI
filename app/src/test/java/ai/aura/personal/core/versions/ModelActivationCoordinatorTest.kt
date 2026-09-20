package ai.aura.personal.core.versions

import ai.aura.personal.core.evaluation.EvaluationReport
import ai.aura.personal.core.evaluation.EvaluationReportStore
import ai.aura.personal.core.security.ArtifactDigest
import ai.aura.personal.core.training.TrainingArtifactStore
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelActivationCoordinatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun activationRequiresPersistedReportAndApproval() {
        val root = temporaryFolder.newFolder("activation")
        val baseModel = temporaryFolder.newFile("base.litertlm").apply {
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
        val versionStore = ModelVersionStore(root)
        val reportStore = EvaluationReportStore(root)
        val approvalStore = ActivationApprovalStore(root)

        versionStore.registerCandidate(candidate)

        val noApproval = ModelActivationCoordinator(
            versionStore,
            reportStore,
            approvalStore
        ).activate(
            candidateVersionId = "candidate-1",
            evaluationReportId = "eval-1",
            approvalId = "approval-1"
        )
        assertTrue(noApproval is ModelVersionStore.ActivationResult.REJECTED)

        reportStore.save(
            EvaluationReport(
                id = "eval-1",
                baseVersionId = "base-1",
                candidateVersionId = "candidate-1",
                candidateAdapterSha256 = ArtifactDigest.sha256(adapter),
                evaluatedExampleCount = 2,
                baseMeanError = 1.0,
                candidateMeanError = 0.9,
                safetyChecksPassed = true,
                compatibilityChecksPassed = true,
                completedAtEpochMs = 100L
            )
        )
        approvalStore.save(
            ActivationApproval(
                id = "approval-1",
                candidateVersionId = "candidate-1",
                evaluationReportId = "eval-1",
                grantedAtEpochMs = 101L
            )
        )

        val activated = ModelActivationCoordinator(
            versionStore,
            reportStore,
            approvalStore
        ).activate(
            candidateVersionId = "candidate-1",
            evaluationReportId = "eval-1",
            approvalId = "approval-1"
        )

        assertTrue(activated is ModelVersionStore.ActivationResult.ACTIVATED)
    }

    @Test
    fun approvalMustMatchCandidateAndReport() {
        val root = temporaryFolder.newFolder("activation-mismatch")
        val baseModel = temporaryFolder.newFile("base.litertlm").apply {
            writeText("base")
        }
        val adapter = TrainingArtifactStore(root).publish(
            temporaryFolder.newFile("candidate-source.adapter").apply {
                writeText("adapter")
            },
            "candidate-1"
        )
        val versionStore = ModelVersionStore(root)
        versionStore.registerCandidate(
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

        EvaluationReportStore(root).save(
            EvaluationReport(
                id = "eval-1",
                baseVersionId = "base-1",
                candidateVersionId = "candidate-1",
                candidateAdapterSha256 = ArtifactDigest.sha256(adapter),
                evaluatedExampleCount = 1,
                baseMeanError = 1.0,
                candidateMeanError = 0.9,
                safetyChecksPassed = true,
                compatibilityChecksPassed = true,
                completedAtEpochMs = 100L
            )
        )
        ActivationApprovalStore(root).save(
            ActivationApproval(
                id = "approval-wrong",
                candidateVersionId = "other-candidate",
                evaluationReportId = "eval-1",
                grantedAtEpochMs = 101L
            )
        )

        val result = ModelActivationCoordinator(
            versionStore,
            EvaluationReportStore(root),
            ActivationApprovalStore(root)
        ).activate(
            candidateVersionId = "candidate-1",
            evaluationReportId = "eval-1",
            approvalId = "approval-wrong"
        )

        assertTrue(result is ModelVersionStore.ActivationResult.REJECTED)
    }
}
