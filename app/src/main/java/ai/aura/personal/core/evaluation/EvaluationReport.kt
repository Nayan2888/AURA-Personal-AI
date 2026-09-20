package ai.aura.personal.core.evaluation

data class EvaluationReport(
    val id: String,
    val baseVersionId: String,
    val candidateVersionId: String,
    val candidateAdapterSha256: String,
    val baseModelSha256: String? = null,
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
        require(candidateAdapterSha256.matches(SHA256_PATTERN)) {
            "Candidate adapter SHA-256 must be a lowercase 64-character hexadecimal digest"
        }
        baseModelSha256?.let {
            require(it.matches(SHA256_PATTERN)) {
                "Base model SHA-256 must be a lowercase 64-character hexadecimal digest"
            }
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

    private companion object {
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
