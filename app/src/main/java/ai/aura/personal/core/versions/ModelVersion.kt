package ai.aura.personal.core.versions

import java.io.File

data class ModelVersion(
    val id: String,
    val baseModelId: String,
    val adapterFile: File?,
    val state: State,
    val baseModelSha256: String? = null,
    val evaluationReportId: String?,
    val createdAtEpochMs: Long
) {
    init {
        require(id.matches(ID_PATTERN)) {
            "Model version id contains unsupported characters"
        }
        require(baseModelId.isNotBlank()) { "Base model id must not be blank" }
        require(createdAtEpochMs >= 0L) { "Model version timestamp must not be negative" }
        baseModelSha256?.let {
            require(it.matches(SHA256_PATTERN)) {
                "Base model SHA-256 must be 64 lowercase hexadecimal characters"
            }
        }

        if (state == State.CANDIDATE) {
            require(adapterFile != null) {
                "Candidate version requires an adapter file"
            }
            require(!evaluationReportId.isNullOrBlank()) {
                "Candidate version requires an evaluation report id"
            }
        }
    }

    enum class State {
        CANDIDATE,
        ACTIVE,
        RETIRED
    }

    companion object {
        private val ID_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
