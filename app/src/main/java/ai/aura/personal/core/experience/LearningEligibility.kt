package ai.aura.personal.core.experience

/**
 * Determines whether an experience is safe to promote into future learning data.
 *
 * This gate intentionally requires explicit consent and a verified successful
 * outcome. Corrected, failed, or unknown outcomes are retained as experiences
 * but are not silently promoted into training data because the current record
 * does not yet contain a trusted corrected answer.
 */
object LearningEligibility {
    fun check(record: ExperienceRecord, consentGranted: Boolean): Result {
        if (!consentGranted) return Result.NOT_CONSENTED
        if (record.userInput.isBlank()) return Result.EMPTY_INPUT
        if (record.assistantOutput.isBlank()) return Result.EMPTY_OUTPUT

        return when (record.outcome) {
            ExperienceRecord.Outcome.SUCCESS -> Result.ELIGIBLE
            ExperienceRecord.Outcome.FAILURE -> Result.UNVERIFIED_OUTCOME
            ExperienceRecord.Outcome.CORRECTED -> Result.REQUIRES_CORRECTED_OUTPUT
            ExperienceRecord.Outcome.UNKNOWN -> Result.UNVERIFIED_OUTCOME
        }
    }

    enum class Result {
        ELIGIBLE,
        NOT_CONSENTED,
        EMPTY_INPUT,
        EMPTY_OUTPUT,
        UNVERIFIED_OUTCOME,
        REQUIRES_CORRECTED_OUTPUT
    }
}
