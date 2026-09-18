package ai.aura.personal.core.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStateTest {
    @Test
    fun `default state starts empty and idle`() {
        val state = ChatState()

        assertTrue(state.messages.isEmpty())
        assertFalse(state.isProcessing)
        assertNull(state.errorMessage)
    }

    @Test
    fun `append message returns new state without mutating original`() {
        val original = ChatState()
        val message = ChatMessage("m1", ChatMessage.Role.USER, "Hello", 1L)

        val updated = original.appendMessage(message)

        assertTrue(original.messages.isEmpty())
        assertEquals(listOf(message), updated.messages)
    }

    @Test
    fun `processing state is immutable`() {
        val original = ChatState()
        val updated = original.withProcessing(true)

        assertFalse(original.isProcessing)
        assertTrue(updated.isProcessing)
    }

    @Test
    fun `setting error clears previous error on append`() {
        val message = ChatMessage("m1", ChatMessage.Role.USER, "Hello", 1L)
        val state = ChatState().withError("Network error")

        val updated = state.appendMessage(message)

        assertNull(updated.errorMessage)
    }
}
