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
        val withoutCorrection = sample(ExperienceRecord.Outcome.CORRECTED)
        assertEquals(
            LearningEligibility.Result.REQUIRES_CORRECTED_OUTPUT,
            checkRaw(
                userInput = withoutCorrection.userInput,
                assistantOutput = withoutCorrection.assistantOutput,
                outcome = withoutCorrection.outcome,
                correctedOutput = null
            )
        )

        val corrected = withoutCorrection.copy(correctedOutput = "Use the updated verified procedure.")
        assertEquals(
            LearningEligibility.Result.ELIGIBLE,
            LearningEligibility.check(corrected, true)
        )
    }

    @Test
    fun blank_input_or_output_is_rejected() {
        val blankInput = sample(ExperienceRecord.Outcome.SUCCESS).copy(userInput = " ")
        assertEquals(
            LearningEligibility.Result.EMPTY_INPUT,
            LearningEligibility.check(
                record = ExperienceRecord(
                    id = blankInput.id,
                    userInput = "fallback",
                    assistantOutput = blankInput.assistantOutput,
                    outcome = blankInput.outcome,
                    createdAtEpochMs = blankInput.createdAtEpochMs
                ),
                consentGranted = true
            )
        )
    }

    private fun checkRaw(
        userInput: String,
        assistantOutput: String,
        outcome: ExperienceRecord.Outcome,
        correctedOutput: String?
    ): LearningEligibility.Result {
        return LearningEligibility.check(
            ExperienceRecord(
                id = "experience-raw",
                userInput = userInput,
                assistantOutput = assistantOutput,
                outcome = outcome,
                createdAtEpochMs = 1L,
                correctedOutput = correctedOutput
            ),
            true
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
