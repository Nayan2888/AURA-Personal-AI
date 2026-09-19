package ai.aura.personal.core.experience

/**
 * Converts an approved experience into a minimal learning example.
 *
 * The builder is intentionally strict: an experience becomes training data
 * only after LearningEligibility reports ELIGIBLE. Metadata is kept so the
 * source experience can be audited later without copying unrelated payloads.
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

        return LearningDatasetEntry(
            sourceExperienceId = record.id,
            input = record.userInput,
            target = record.assistantOutput,
            createdAtEpochMs = record.createdAtEpochMs
        )
    }
}
