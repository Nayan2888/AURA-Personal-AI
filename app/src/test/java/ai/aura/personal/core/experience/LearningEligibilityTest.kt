package ai.aura.personal.core.experience

import org.junit.Assert.assertEquals
import org.junit.Test

class LearningEligibilityTest {
    @Test
    fun success_requires_explicit_consent() {
        val record = sample(ExperienceRecord.Outcome.SUCCESS)

        assertEquals(
            LearningEligibility.Result.NOT_CONSENTED,
            LearningEligibility.check(record, consentGranted = false)
        )
        assertEquals(
            LearningEligibility.Result.ELIGIBLE,
            LearningEligibility.check(record, consentGranted = true)
        )
    }

    @Test
    fun unknown_and_failed_outcomes_are_not_promoted() {
        assertEquals(
            LearningEligibility.Result.UNVERIFIED_OUTCOME,
            LearningEligibility.check(sample(ExperienceRecord.Outcome.UNKNOWN), true)
        )
        assertEquals(
            LearningEligibility.Result.UNVERIFIED_OUTCOME,
            LearningEligibility.check(sample(ExperienceRecord.Outcome.FAILURE), true)
        )
    }

    @Test
    fun corrected_outcome_requires_a_trusted_corrected_answer() {
        assertEquals(
            LearningEligibility.Result.REQUIRES_CORRECTED_OUTPUT,
            LearningEligibility.check(sample(ExperienceRecord.Outcome.CORRECTED), true)
        )
    }

    @Test
    fun blank_input_or_output_is_rejected() {
        val blankInput = sample(ExperienceRecord.Outcome.SUCCESS).copy(userInput = " ")
        assertEquals(
            LearningEligibility.Result.EMPTY_INPUT,
            LearningEligibility.check(blankInput, true)
        )

        val blankOutput = sample(ExperienceRecord.Outcome.SUCCESS).copy(assistantOutput = " ")
        assertEquals(
            LearningEligibility.Result.EMPTY_OUTPUT,
            LearningEligibility.check(blankOutput, true)
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
