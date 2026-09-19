package ai.aura.personal.core.inference

import ai.aura.personal.core.chat.ChatMessage
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Real on-device assistant engine backed by LiteRT-LM.
 *
 * The model is supplied by a local .litertlm file; no network call is performed by this class.
 */
class LiteRtLmAssistantEngine(
    private val modelFile: File,
    private val backend: Backend = Backend.CPU()
) : AssistantEngine {

    private var engine: Engine? = null
    private val generationMutex = Mutex()

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        check(modelFile.isFile) { "Local model file does not exist: ${modelFile.absolutePath}" }
        check(modelFile.length() > 0L) { "Local model file is empty: ${modelFile.absolutePath}" }

        if (engine?.isInitialized() == true) return@withContext

        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        val created = Engine(
            com.google.ai.edge.litertlm.EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = backend,
                cacheDir = File(modelFile.parentFile ?: File("."), "cache").absolutePath
            )
        )
        try {
            created.initialize()
            engine = created
        } catch (error: Throwable) {
            runCatching { created.close() }
            throw error
        }
    }

    override fun isInitialized(): Boolean = engine?.isInitialized() == true

    override suspend fun generate(
        history: List<ChatMessage>,
        userInput: String
    ): String = generationMutex.withLock {
        check(isInitialized()) { "Local assistant model is not initialized." }
        check(userInput.isNotBlank()) { "User input must not be blank." }

        withContext(Dispatchers.Default) {
            val initialMessages = history.mapNotNull { message ->
                when (message.role) {
                    ChatMessage.Role.SYSTEM -> Message.system(message.content)
                    ChatMessage.Role.USER -> Message.user(message.content)
                    ChatMessage.Role.ASSISTANT -> Message.model(message.content)
                }
            }

            val conversation = engine!!.createConversation(
                ConversationConfig(initialMessages = initialMessages)
            )

            try {
                val response = StringBuilder()
                conversation.sendMessageAsync(userInput).collect { message ->
                    if (message.role == com.google.ai.edge.litertlm.Role.MODEL) {
                        response.append(message.toString())
                    }
                }

                response.toString().trim().also {
                    check(it.isNotBlank()) { "Local model returned an empty response." }
                }
            } finally {
                conversation.close()
            }
        }
    }

    override fun close() {
        engine?.let { current ->
            if (current.isInitialized()) {
                runCatching { current.close() }
            }
        }
        engine = null
    }
}
