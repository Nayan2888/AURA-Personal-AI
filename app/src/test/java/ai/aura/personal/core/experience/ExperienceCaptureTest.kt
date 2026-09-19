package ai.aura.personal.core.experience

import ai.aura.personal.core.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExperienceCaptureTest {
    @Test
    fun completed_user_assistant_exchange_becomes_experience() {
        val user = ChatMessage("u1", ChatMessage.Role.USER, "How do I learn?", 10L)
        val assistant = ChatMessage("a1", ChatMessage.Role.ASSISTANT, "Use the verified procedure.", 20L)

        val record = ExperienceCapture.capture(
            userMessage = user,
            assistantMessage = assistant,
            outcome = ExperienceRecord.Outcome.SUCCESS
        )

        requireNotNull(record)
        assertEquals("experience-u1-a1", record.id)
        assertEquals(user.content, record.userInput)
        assertEquals(assistant.content, record.assistantOutput)
        assertEquals(ExperienceRecord.Outcome.SUCCESS, record.outcome)
        assertEquals(20L, record.createdAtEpochMs)
    }

    @Test
    fun non_user_or_non_assistant_messages_are_rejected() {
        val user = ChatMessage("u1", ChatMessage.Role.USER, "Question", 10L)
        val assistant = ChatMessage("a1", ChatMessage.Role.ASSISTANT, "Answer", 20L)
        val system = ChatMessage("s1", ChatMessage.Role.SYSTEM, "System", 30L)

        assertNull(
            ExperienceCapture.capture(
                userMessage = assistant,
                assistantMessage = assistant,
                outcome = ExperienceRecord.Outcome.SUCCESS
            )
        )
        assertNull(
            ExperienceCapture.capture(
                userMessage = user,
                assistantMessage = system,
                outcome = ExperienceRecord.Outcome.SUCCESS
            )
        )
    }
}
