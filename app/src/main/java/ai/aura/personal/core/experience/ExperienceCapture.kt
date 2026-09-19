package ai.aura.personal.core.experience

import ai.aura.personal.core.chat.ChatMessage

/**
 * Builds a learning experience only from a completed user/assistant exchange.
 *
 * System messages are never accepted as either side of the learning pair.
 */
object ExperienceCapture {
    fun capture(
        userMessage: ChatMessage,
        assistantMessage: ChatMessage,
        outcome: ExperienceRecord.Outcome
    ): ExperienceRecord? {
        if (userMessage.role != ChatMessage.Role.USER) return null
        if (assistantMessage.role != ChatMessage.Role.ASSISTANT) return null
        if (userMessage.content.isBlank() || assistantMessage.content.isBlank()) return null

        return ExperienceRecord(
            id = "experience-" + userMessage.id + "-" + assistantMessage.id,
            userInput = userMessage.content,
            assistantOutput = assistantMessage.content,
            outcome = outcome,
            createdAtEpochMs = maxOf(
                userMessage.createdAtEpochMs,
                assistantMessage.createdAtEpochMs
            )
        )
    }
}
