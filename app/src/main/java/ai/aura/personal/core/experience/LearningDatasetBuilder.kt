package ai.aura.personal.core.experience

import org.json.JSONObject

/**
 * A single supervised learning example.
 *
 * sourceExperienceId is retained for auditability but is not emitted into the
 * model-facing JSONL payload.
 */
data class LearningDatasetEntry(
    val sourceExperienceId: String,
    val input: String,
    val target: String,
    val createdAtEpochMs: Long
)

/**
 * Builds training data only from consented, verified experiences.
 *
 * No training or model mutation happens here. This stage produces clean,
 * auditable examples for a later training/evaluation pipeline.
 */
object LearningDatasetBuilder {
    fun build(
        record: ExperienceRecord,
        consentGranted: Boolean
    ): LearningDatasetEntry? {
        if (
            LearningEligibility.check(
                record,
                consentGranted
            ) != LearningEligibility.Result.ELIGIBLE
        ) {
            return null
        }

        val target = when (record.outcome) {
            ExperienceRecord.Outcome.CORRECTED -> record.correctedOutput
            ExperienceRecord.Outcome.SUCCESS -> record.assistantOutput
            ExperienceRecord.Outcome.UNKNOWN,
            ExperienceRecord.Outcome.FAILURE -> null
        } ?: return null

        val input = record.userInput.trim()
        val normalizedTarget = target.trim()
        if (input.isEmpty() || normalizedTarget.isEmpty()) return null

        return LearningDatasetEntry(
            sourceExperienceId = record.id,
            input = input,
            target = normalizedTarget,
            createdAtEpochMs = record.createdAtEpochMs
        )
    }

    /**
     * Builds a deterministic batch from all supplied experiences.
     *
     * Duplicate input/target pairs are removed so repeated identical feedback
     * cannot inflate a future training set. Output ordering is deterministic.
     */
    fun buildAll(
        records: Iterable<ExperienceRecord>,
        consentGranted: Boolean
    ): List<LearningDatasetEntry> {
        return records
            .asSequence()
            .mapNotNull { build(it, consentGranted) }
            .distinctBy { it.input to it.target }
            .sortedWith(
                compareBy<LearningDatasetEntry>(
                    { it.createdAtEpochMs },
                    { it.sourceExperienceId }
                )
            )
            .toList()
    }

    /**
     * Encodes model-facing examples as JSON Lines (JSONL).
     *
     * Only input and target are exported. Internal experience IDs and
     * timestamps remain audit metadata inside the app and are not exposed to
     * the training payload.
     */
    fun toJsonl(entries: Iterable<LearningDatasetEntry>): String {
        val items = entries.toList()
        return items.joinToString(
            separator = "\n",
            postfix = if (items.isNotEmpty()) "\n" else ""
        ) {
            JSONObject()
                .put("input", it.input)
                .put("target", it.target)
                .toString()
        }
    }
}
