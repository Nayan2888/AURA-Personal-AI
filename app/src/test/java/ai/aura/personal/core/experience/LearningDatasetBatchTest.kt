package ai.aura.personal.core.experience

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningDatasetBatchTest {
    private fun record(
        id: String,
        input: String,
        output: String,
        outcome: ExperienceRecord.Outcome = ExperienceRecord.Outcome.SUCCESS,
        timestamp: Long = 100L,
        correctedOutput: String? = null
    ) = ExperienceRecord(
        id = id,
        userInput = input,
        assistantOutput = output,
        outcome = outcome,
        createdAtEpochMs = timestamp,
        correctedOutput = correctedOutput
    )

    @Test
    fun buildAllRequiresConsentAndFiltersUnverifiedExperiences() {
        val records = listOf(
            record("ok", "  hello  ", "  world  "),
            record("failed", "bad", "answer", ExperienceRecord.Outcome.FAILURE),
            record("unknown", "unknown", "answer", ExperienceRecord.Outcome.UNKNOWN)
        )

        assertTrue(LearningDatasetBuilder.buildAll(records, consentGranted = false).isEmpty())

        val entries = LearningDatasetBuilder.buildAll(records, consentGranted = true)
        assertEquals(1, entries.size)
        assertEquals("hello", entries.single().input)
        assertEquals("world", entries.single().target)
    }

    @Test
    fun correctedExperienceUsesCorrectedTarget() {
        val record = record(
            id = "corrected",
            input = "Explain Kotlin",
            output = "Wrong answer",
            outcome = ExperienceRecord.Outcome.CORRECTED,
            correctedOutput = "Kotlin is a statically typed programming language."
        )

        val entry = LearningDatasetBuilder.build(record, consentGranted = true)

        assertEquals(
            "Kotlin is a statically typed programming language.",
            entry?.target
        )
    }

    @Test
    fun buildAllDeduplicatesIdenticalPairsAndSortsDeterministically() {
        val records = listOf(
            record("b", "same", "answer", timestamp = 200L),
            record("a", "second", "answer", timestamp = 100L),
            record("c", "same", "answer", timestamp = 300L)
        )

        val entries = LearningDatasetBuilder.buildAll(records, consentGranted = true)

        assertEquals(listOf("second", "same"), entries.map { it.input })
        assertEquals(listOf("a", "b"), entries.map { it.sourceExperienceId })
    }

    @Test
    fun jsonlExportsOnlyModelFacingFields() {
        val entries = listOf(
            record("one", "नमस्ते", "हैलो", timestamp = 50L)
        ).let { records ->
            LearningDatasetBuilder.buildAll(records, consentGranted = true)
        }

        val jsonl = LearningDatasetBuilder.toJsonl(entries)
        val line = jsonl.trimEnd().lineSequence().single()
        val json = JSONObject(line)

        assertEquals("नमस्ते", json.getString("input"))
        assertEquals("हैलो", json.getString("target"))
        assertEquals(false, json.has("sourceExperienceId"))
        assertEquals(false, json.has("createdAtEpochMs"))
    }

    @Test
    fun emptyJsonlIsEmpty() {
        assertEquals("", LearningDatasetBuilder.toJsonl(emptyList()))
    }
}
