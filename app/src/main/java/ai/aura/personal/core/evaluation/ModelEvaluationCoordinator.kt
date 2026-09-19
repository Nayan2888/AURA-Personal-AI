package ai.aura.personal.core.evaluation

import java.io.File

/**
 * Production coordinator for the evaluation stage.
 *
 * It runs the real base-vs-candidate evaluation engine and persists the resulting
 * immutable evidence report. It does not approve or activate a candidate.
 */
class ModelEvaluationCoordinator(
    private val evaluationEngine: ModelEvaluationEngine,
    private val reportStore: EvaluationReportStore,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun evaluateAndPersist(
        reportId: String,
        baseVersionId: String,
        candidateVersionId: String,
        examples: List<EvaluationExample>,
        candidateAdapterFile: File,
        safetyChecksPassed: Boolean,
        compatibilityChecksPassed: Boolean
    ): EvaluationReport {
        require(reportId.isNotBlank()) {
            "Evaluation report id must not be blank"
        }
        require(baseVersionId.isNotBlank()) {
            "Base version id must not be blank"
        }
        require(candidateVersionId.isNotBlank()) {
            "Candidate version id must not be blank"
        }

        val evaluation = evaluationEngine.evaluate(
            examples = examples,
            candidateAdapterFile = candidateAdapterFile
        )

        val report = EvaluationReport(
            id = reportId,
            baseVersionId = baseVersionId,
            candidateVersionId = candidateVersionId,
            evaluatedExampleCount = evaluation.evaluatedExampleCount,
            baseMeanError = evaluation.baseMeanError,
            candidateMeanError = evaluation.candidateMeanError,
            safetyChecksPassed = safetyChecksPassed,
            compatibilityChecksPassed = compatibilityChecksPassed,
            completedAtEpochMs = nowEpochMs()
        )

        reportStore.save(report)
        return report
    }
}
