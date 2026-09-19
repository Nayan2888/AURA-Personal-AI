package ai.aura.personal.core.experience

/**
 * Applies a user-verified outcome to an existing experience.
 *
 * The corrected text is accepted only for CORRECTED outcomes. Other outcomes
 * clear any previous correction so stale training targets cannot survive a
 * later feedback change.
 */
object ExperienceFeedback {
    fun apply(
        record: ExperienceRecord,
        outcome: ExperienceRecord.Outcome,
        correctedOutput: String? = null
    ): ExperienceRecord {
        val normalizedCorrection = correctedOutput?.trim()?.takeIf { it.isNotEmpty() }

        if (outcome == ExperienceRecord.Outcome.CORRECTED) {
            require(normalizedCorrection != null) {
                "Corrected outcome requires corrected output"
            }
        }

        return record.copy(
            outcome = outcome,
            correctedOutput = normalizedCorrection.takeIf {
                outcome == ExperienceRecord.Outcome.CORRECTED
            }
        )
    }
}
