package ai.aura.personal.core.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatMessageTest {
    @Test
    fun `valid message preserves supplied values`() {
        val message = ChatMessage("m1", ChatMessage.Role.USER, "Hello", 123L)

        assertEquals("m1", message.id)
        assertEquals(ChatMessage.Role.USER, message.role)
        assertEquals("Hello", message.content)
        assertEquals(123L, message.createdAtEpochMs)
    }

    @Test
    fun `blank id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ChatMessage("", ChatMessage.Role.USER, "Hello", 1L)
        }
    }

    @Test
    fun `blank content is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ChatMessage("m1", ChatMessage.Role.USER, "  ", 1L)
        }
    }

    @Test
    fun `negative timestamp is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ChatMessage("m1", ChatMessage.Role.USER, "Hello", -1L)
        }
    }
}
