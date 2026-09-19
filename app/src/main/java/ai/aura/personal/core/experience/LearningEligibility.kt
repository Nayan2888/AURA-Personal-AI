package ai.aura.personal.core.experience

/**
 * Determines whether an experience is safe to promote into future learning data.
 *
 * Consent is mandatory. Successful answers are eligible directly; corrected
 * answers are eligible only when a trusted corrected output is present.
 */
object LearningEligibility {
    fun check(record: ExperienceRecord, consentGranted: Boolean): Result {
        if (!consentGranted) return Result.NOT_CONSENTED
        if (record.userInput.isBlank()) return Result.EMPTY_INPUT
        if (record.assistantOutput.isBlank()) return Result.EMPTY_OUTPUT

        return when (record.outcome) {
            ExperienceRecord.Outcome.SUCCESS -> Result.ELIGIBLE
            ExperienceRecord.Outcome.CORRECTED ->
                if (record.correctedOutput.isNullOrBlank()) {
                    Result.REQUIRES_CORRECTED_OUTPUT
                } else {
                    Result.ELIGIBLE
                }
            ExperienceRecord.Outcome.FAILURE,
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
