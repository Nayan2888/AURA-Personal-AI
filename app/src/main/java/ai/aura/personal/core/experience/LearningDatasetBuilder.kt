package ai.aura.personal.core.experience

/**
 * Converts an approved experience into a minimal learning example.
 *
 * Metadata is kept so the source experience can be audited later without
 * copying unrelated payloads.
 */
data class LearningDatasetEntry(
    val sourceExperienceId: String,
    val input: String,
    val target: String,
    val createdAtEpochMs: Long
)

object LearningDatasetBuilder {
    fun build(
        record: ExperienceRecord,
        consentGranted: Boolean
    ): LearningDatasetEntry? {
        if (LearningEligibility.check(record, consentGranted) != LearningEligibility.Result.ELIGIBLE) {
            return null
        }

        val target = when (record.outcome) {
            ExperienceRecord.Outcome.CORRECTED -> record.correctedOutput
            ExperienceRecord.Outcome.SUCCESS -> record.assistantOutput
            ExperienceRecord.Outcome.UNKNOWN,
            ExperienceRecord.Outcome.FAILURE -> null
        } ?: return null

        return LearningDatasetEntry(
            sourceExperienceId = record.id,
            input = record.userInput,
            target = target,
            createdAtEpochMs = record.createdAtEpochMs
        )
    }
}
