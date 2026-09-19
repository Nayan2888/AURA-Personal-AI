package ai.aura.personal.core.experience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExperienceFeedbackTest {
    private fun record(): ExperienceRecord = ExperienceRecord(
        id = "experience-1",
        userInput = "Explain Kotlin",
        assistantOutput = "Kotlin is a programming language.",
        outcome = ExperienceRecord.Outcome.UNKNOWN,
        createdAtEpochMs = 100L
    )

    @Test
    fun successMarksRecordEligibleWithoutCorrection() {
        val updated = ExperienceFeedback.apply(
            record = record(),
            outcome = ExperienceRecord.Outcome.SUCCESS
        )

        assertEquals(ExperienceRecord.Outcome.SUCCESS, updated.outcome)
        assertNull(updated.correctedOutput)
    }

    @Test
    fun failureClearsPreviousCorrection() {
        val corrected = ExperienceFeedback.apply(
            record = record(),
            outcome = ExperienceRecord.Outcome.CORRECTED,
            correctedOutput = "Kotlin is a statically typed programming language."
        )

        val failed = ExperienceFeedback.apply(
            record = corrected,
            outcome = ExperienceRecord.Outcome.FAILURE
        )

        assertEquals(ExperienceRecord.Outcome.FAILURE, failed.outcome)
        assertNull(failed.correctedOutput)
    }

    @Test
    fun correctedOutcomeRequiresNonBlankCorrection() {
        val error = runCatching {
            ExperienceFeedback.apply(
                record = record(),
                outcome = ExperienceRecord.Outcome.CORRECTED,
                correctedOutput = "   "
            )
        }.exceptionOrNull()

        assertEquals("Corrected outcome requires corrected output", error?.message)
    }

    @Test
    fun correctedTextIsTrimmedAndPersisted() {
        val updated = ExperienceFeedback.apply(
            record = record(),
            outcome = ExperienceRecord.Outcome.CORRECTED,
            correctedOutput = "  बेहतर उत्तर  "
        )

        assertEquals(ExperienceRecord.Outcome.CORRECTED, updated.outcome)
        assertEquals("बेहतर उत्तर", updated.correctedOutput)
    }
}
