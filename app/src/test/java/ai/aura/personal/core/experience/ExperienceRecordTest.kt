package ai.aura.personal.core.experience

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ExperienceRecordTest {
    @Test
    fun corrected_outcome_requires_correction() {
        try {
            ExperienceRecord(
                id = "experience-1",
                userInput = "Question",
                assistantOutput = "Wrong answer",
                outcome = ExperienceRecord.Outcome.CORRECTED,
                createdAtEpochMs = 1L
            )
            fail("Expected corrected outcome without correction to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals(
                "Corrected outcome requires a corrected output",
                expected.message
            )
        }
    }

    @Test
    fun invalid_identity_and_payload_are_rejected() {
        expectIllegalArgument("Experience id must not be blank") {
            ExperienceRecord("", "Question", "Answer", ExperienceRecord.Outcome.SUCCESS, 1L)
        }
        expectIllegalArgument("Experience user input must not be blank") {
            ExperienceRecord("experience-1", " ", "Answer", ExperienceRecord.Outcome.SUCCESS, 1L)
        }
        expectIllegalArgument("Experience assistant output must not be blank") {
            ExperienceRecord("experience-1", "Question", " ", ExperienceRecord.Outcome.SUCCESS, 1L)
        }
        expectIllegalArgument("Experience timestamp must not be negative") {
            ExperienceRecord("experience-1", "Question", "Answer", ExperienceRecord.Outcome.SUCCESS, -1L)
        }
    }

    private fun expectIllegalArgument(message: String, block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertEquals(message, expected.message)
        }
    }
}
