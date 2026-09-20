package ai.aura.personal.core.evaluation

import ai.aura.personal.core.security.ArtifactDigest
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
        baseModelFile: File,
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
        require(baseModelFile.isFile && baseModelFile.length() > 0L) {
            "Base model file must be a non-empty file"
        }

        val baseModelSha256Before = ArtifactDigest.sha256(baseModelFile)
        val adapterSha256Before = ArtifactDigest.sha256(candidateAdapterFile)
        val evaluation = evaluationEngine.evaluate(
            examples = examples,
            candidateAdapterFile = candidateAdapterFile
        )
        val baseModelSha256After = ArtifactDigest.sha256(baseModelFile)
        check(baseModelSha256Before == baseModelSha256After) {
            "Base model changed during evaluation"
        }
        val adapterSha256After = ArtifactDigest.sha256(candidateAdapterFile)
        check(adapterSha256Before == adapterSha256After) {
            "Candidate adapter changed during evaluation"
        }

        val report = EvaluationReport(
            id = reportId,
            baseVersionId = baseVersionId,
            candidateVersionId = candidateVersionId,
            candidateAdapterSha256 = adapterSha256After,
            baseModelSha256 = baseModelSha256After,
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
