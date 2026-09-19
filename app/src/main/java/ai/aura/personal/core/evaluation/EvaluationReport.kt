package ai.aura.personal.core.evaluation

data class EvaluationReport(
    val id: String,
    val baseVersionId: String,
    val candidateVersionId: String,
    val evaluatedExampleCount: Int,
    val baseMeanError: Double,
    val candidateMeanError: Double,
    val safetyChecksPassed: Boolean,
    val compatibilityChecksPassed: Boolean,
    val completedAtEpochMs: Long
) {
    init {
        require(id.isNotBlank()) { "Evaluation id must not be blank" }
        require(baseVersionId.isNotBlank()) { "Base version id must not be blank" }
        require(candidateVersionId.isNotBlank()) {
            "Candidate version id must not be blank"
        }
        require(evaluatedExampleCount > 0) {
            "Evaluation must contain at least one example"
        }
        require(baseMeanError.isFinite() && baseMeanError >= 0.0) {
            "Base mean error must be finite and non-negative"
        }
        require(candidateMeanError.isFinite() && candidateMeanError >= 0.0) {
            "Candidate mean error must be finite and non-negative"
        }
        require(completedAtEpochMs >= 0L) {
            "Evaluation timestamp must not be negative"
        }
    }
}
