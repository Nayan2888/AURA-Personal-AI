package ai.aura.personal.core.experience

/**
 * Immutable record of one user interaction and its outcome.
 *
 * A corrected outcome must carry the user/trusted corrected answer so a later
 * dataset builder can learn from the mistake without treating the original
 * assistant output as the target.
 */
data class ExperienceRecord(
    val id: String,
    val userInput: String,
    val assistantOutput: String,
    val outcome: Outcome,
    val createdAtEpochMs: Long,
    val correctedOutput: String? = null
) {
    init {
        require(id.isNotBlank()) { "Experience id must not be blank" }
        require(userInput.isNotBlank()) { "Experience user input must not be blank" }
        require(assistantOutput.isNotBlank()) { "Experience assistant output must not be blank" }
        require(createdAtEpochMs >= 0L) { "Experience timestamp must not be negative" }

        if (outcome == Outcome.CORRECTED) {
            require(!correctedOutput.isNullOrBlank()) {
                "Corrected outcome requires a corrected output"
            }
        }
    }

    enum class Outcome {
        UNKNOWN,
        SUCCESS,
        FAILURE,
        CORRECTED
    }
}
