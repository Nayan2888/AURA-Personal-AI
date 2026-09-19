package ai.aura.personal.core.experience

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
        if (LearningEligibility.check(record, consentGranted) != LearningEligibility.Result.ELIGIBLE) {
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

    fun buildAll(
        records: Iterable<ExperienceRecord>,
        consentGranted: Boolean
    ): List<LearningDatasetEntry> {
        return records
            .asSequence()
            .mapNotNull { build(it, consentGranted) }
            .distinctBy { it.input to it.target }
            .sortedWith(compareBy<LearningDatasetEntry>({ it.createdAtEpochMs }, { it.sourceExperienceId }))
            .toList()
    }

    /** Pure Kotlin JSONL encoder; does not depend on Android framework classes. */
    fun toJsonl(entries: Iterable<LearningDatasetEntry>): String {
        val items = entries.toList()
        return items.joinToString(
            separator = "\n",
            postfix = if (items.isNotEmpty()) "\n" else ""
        ) {
            "{\"input\":\"" + escapeJson(it.input) + "\",\"target\":\"" + escapeJson(it.target) + "\"}"
        }
    }

    private fun escapeJson(value: String): String = buildString(value.length + 16) {
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
    }
}
