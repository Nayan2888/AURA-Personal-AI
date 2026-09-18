package ai.aura.personal.core.experience

/**
 * Immutable record of one user interaction and its outcome.
 * This is the first domain building block for AURA's learning pipeline.
 */
data class ExperienceRecord(
    val id: String,
    val userInput: String,
    val assistantOutput: String,
    val outcome: Outcome,
    val createdAtEpochMs: Long
) {
    enum class Outcome {
        UNKNOWN,
        SUCCESS,
        FAILURE,
        CORRECTED
    }
}
