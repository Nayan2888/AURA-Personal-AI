package ai.aura.personal.core.evaluation

data class EvaluationReport(
    val id: String,
    val baseVersionId: String,
    val candidateVersionId: String,
    val evaluatedExampleCount: Int,
    val baseMeanLoss: Double,
    val candidateMeanLoss: Double,
    val safetyChecksPassed: Boolean,
    val compatibilityChecksPassed: Boolean,
    val completedAtEpochMs: Long
) {
    init {
        require(id.isNotBlank()) { "Evaluation id must not be blank" }
        require(baseVersionId.isNotBlank()) { "Base version id must not be blank" }
        require(candidateVersionId.isNotBlank()) { "Candidate version id must not be blank" }
        require(evaluatedExampleCount > 0) { "Evaluation must contain at least one example" }
        require(baseMeanLoss.isFinite()) { "Base mean loss must be finite" }
        require(candidateMeanLoss.isFinite()) { "Candidate mean loss must be finite" }
        require(completedAtEpochMs >= 0L) { "Evaluation timestamp must not be negative" }
    }
}
