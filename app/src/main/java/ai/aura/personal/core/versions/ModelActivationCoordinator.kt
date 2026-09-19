package ai.aura.personal.core.versions

import ai.aura.personal.core.evaluation.EvaluationReportStore

/**
 * Production activation boundary.
 *
 * Activation uses only durable evaluation evidence and durable explicit
 * approval records. Missing or mismatched persisted evidence blocks activation.
 */
class ModelActivationCoordinator(
    private val modelVersionStore: ModelVersionStore,
    private val evaluationReportStore: EvaluationReportStore,
    private val activationApprovalStore: ActivationApprovalStore
) {
    fun activate(
        candidateVersionId: String,
        evaluationReportId: String,
        approvalId: String,
        maxQualityRegression: Double = 0.0
    ): ModelVersionStore.ActivationResult {
        val report = evaluationReportStore.get(evaluationReportId)
            ?: return ModelVersionStore.ActivationResult.REJECTED(
                "Evaluation report not found: " + evaluationReportId
            )
        val approval = activationApprovalStore.get(approvalId)
            ?: return ModelVersionStore.ActivationResult.REJECTED(
                "Activation approval not found: " + approvalId
            )

        if (report.candidateVersionId != candidateVersionId) {
            return ModelVersionStore.ActivationResult.REJECTED(
                "Evaluation candidate id does not match candidate"
            )
        }
        if (approval.candidateVersionId != candidateVersionId) {
            return ModelVersionStore.ActivationResult.REJECTED(
                "Activation approval candidate id does not match candidate"
            )
        }
        if (approval.evaluationReportId != evaluationReportId) {
            return ModelVersionStore.ActivationResult.REJECTED(
                "Activation approval evaluation id does not match report"
            )
        }

        return modelVersionStore.activateCandidate(
            candidateId = candidateVersionId,
            evaluationReport = report,
            approval = approval,
            maxQualityRegression = maxQualityRegression
        )
    }
}
