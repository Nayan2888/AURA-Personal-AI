package ai.aura.personal.core.versions

data class ActivationApproval(
    val id: String,
    val candidateVersionId: String,
    val evaluationReportId: String,
    val grantedAtEpochMs: Long
) {
    init {
        require(id.matches(ID_PATTERN)) {
            "Activation approval id contains unsupported characters"
        }
        require(candidateVersionId.matches(ID_PATTERN)) {
            "Candidate version id contains unsupported characters"
        }
        require(evaluationReportId.matches(ID_PATTERN)) {
            "Evaluation report id contains unsupported characters"
        }
        require(grantedAtEpochMs >= 0L) {
            "Activation approval timestamp must not be negative"
        }
    }

    companion object {
        private val ID_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")
    }
}
