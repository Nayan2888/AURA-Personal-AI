package ai.aura.personal.core.research

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchTriggerTest {

    @Test
    fun freshnessRequestTriggersResearch() {
        assertTrue(
            ResearchTrigger.shouldResearch(
                userInput = "What is the latest version of Android?",
                localAnswer = "Android has many versions."
            )
        )
    }

    @Test
    fun uncertaintyTriggersResearch() {
        assertTrue(
            ResearchTrigger.shouldResearch(
                userInput = "Who won this match?",
                localAnswer = "मुझे नहीं पता।"
            )
        )
    }

    @Test
    fun ordinaryConfidentTurnDoesNotTriggerResearch() {
        assertFalse(
            ResearchTrigger.shouldResearch(
                userInput = "Explain gravity simply.",
                localAnswer = "Gravity is the attraction between masses."
            )
        )
    }
}
