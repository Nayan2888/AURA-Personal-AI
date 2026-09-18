package ai.aura.personal.core.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSessionTest {
    @Test
    fun `session requires a non blank id`() {
        try {
            ChatSession(" ")
            throw AssertionError("Expected blank session id to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Session id must not be blank", expected.message)
        }
    }

    @Test
    fun `append message returns a new session without mutating original`() {
        val original = ChatSession("session-1")
        val message = ChatMessage("message-1", ChatMessage.Role.USER, "Hello", 1L)

        val updated = original.appendMessage(message)

        assertTrue(original.state.messages.isEmpty())
        assertEquals(listOf(message), updated.state.messages)
        assertEquals(original.id, updated.id)
    }

    @Test
    fun `processing transition preserves session identity and immutability`() {
        val original = ChatSession("session-1")
        val updated = original.withProcessing(true)

        assertFalse(original.state.isProcessing)
        assertTrue(updated.state.isProcessing)
        assertEquals(original.id, updated.id)
    }
}
