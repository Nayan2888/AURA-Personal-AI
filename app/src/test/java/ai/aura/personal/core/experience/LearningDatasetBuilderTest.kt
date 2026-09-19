package ai.aura.personal.core.experience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LearningDatasetBuilderTest {
    @Test
    fun eligible_success_becomes_dataset_entry_with_provenance() {
        val record = sample(ExperienceRecord.Outcome.SUCCESS)

        val entry = LearningDatasetBuilder.build(record, consentGranted = true)

        requireNotNull(entry)
        assertEquals(record.id, entry.sourceExperienceId)
        assertEquals(record.userInput, entry.input)
        assertEquals(record.assistantOutput, entry.target)
        assertEquals(record.createdAtEpochMs, entry.createdAtEpochMs)
    }

    @Test
    fun non_eligible_experiences_are_not_promoted() {
        assertNull(
            LearningDatasetBuilder.build(
                sample(ExperienceRecord.Outcome.SUCCESS),
                consentGranted = false
            )
        )
        assertNull(
            LearningDatasetBuilder.build(
                sample(ExperienceRecord.Outcome.FAILURE),
                consentGranted = true
            )
        )
        assertNull(
            LearningDatasetBuilder.build(
                sample(ExperienceRecord.Outcome.UNKNOWN),
                consentGranted = true
            )
        )
        assertNull(
            LearningDatasetBuilder.build(
                sample(ExperienceRecord.Outcome.CORRECTED),
                consentGranted = true
            )
        )
    }

    private fun sample(outcome: ExperienceRecord.Outcome): ExperienceRecord =
        ExperienceRecord(
            id = "experience-1",
            userInput = "How do I learn this?",
            assistantOutput = "Use the verified procedure.",
            outcome = outcome,
            createdAtEpochMs = 1L
        )
}
