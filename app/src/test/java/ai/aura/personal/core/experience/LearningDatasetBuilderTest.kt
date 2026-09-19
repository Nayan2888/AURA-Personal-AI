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
    fun corrected_experience_uses_trusted_correction_as_target() {
        val record = sample(ExperienceRecord.Outcome.CORRECTED)

        val entry = LearningDatasetBuilder.build(record, consentGranted = true)

        requireNotNull(entry)
        assertEquals(record.correctedOutput, entry.target)
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
    }

    private fun sample(outcome: ExperienceRecord.Outcome): ExperienceRecord =
        if (outcome == ExperienceRecord.Outcome.CORRECTED) {
            ExperienceRecord(
                id = "experience-1",
                userInput = "How do I learn this?",
                assistantOutput = "Use the old procedure.",
                outcome = outcome,
                createdAtEpochMs = 1L,
                correctedOutput = "Use the verified procedure."
            )
        } else {
            ExperienceRecord(
                id = "experience-1",
                userInput = "How do I learn this?",
                assistantOutput = "Use the verified procedure.",
                outcome = outcome,
                createdAtEpochMs = 1L
            )
        }
}
