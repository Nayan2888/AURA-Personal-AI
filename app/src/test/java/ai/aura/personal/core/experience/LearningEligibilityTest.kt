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
    fun corrected_outcome_is_eligible_only_with_a_trusted_answer() {
        val corrected = sample(ExperienceRecord.Outcome.CORRECTED)

        assertEquals(
            LearningEligibility.Result.ELIGIBLE,
            LearningEligibility.check(corrected, true)
        )

        assertEquals(
            LearningEligibility.Result.NOT_CONSENTED,
            LearningEligibility.check(corrected, false)
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
