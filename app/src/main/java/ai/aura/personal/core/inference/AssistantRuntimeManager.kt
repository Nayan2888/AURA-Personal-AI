package ai.aura.personal.core.inference

import ai.aura.personal.core.chat.ChatMessage
import ai.aura.personal.core.evaluation.EvaluationReportStore
import ai.aura.personal.core.security.ArtifactDigest
import ai.aura.personal.core.versions.ModelVersionStore
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the currently selected local model runtime.
 *
 * When version-management stores are supplied, the runtime is pinned to the
 * currently ACTIVE version. Candidate/retired adapters are never accepted.
 * If activation or rollback changes the active version, the next response
 * automatically reloads the runtime from the new active version.
 */
class AssistantRuntimeManager(
    private val modelVersionStore: ModelVersionStore? = null,
    private val evaluationReportStore: EvaluationReportStore? = null,
    private val engineFactory: (File) -> AssistantEngine = { LiteRtLmAssistantEngine(it) }
) : AutoCloseable {

    private var engine: AssistantEngine? = null
    private var loadedModelFile: File? = null
    private var loadedActiveVersionId: String? = null
    private var loadedAdapterFile: File? = null
    private var loadedAdapterSha256: String? = null

    /**
     * Serializes runtime lifecycle and generation so a model cannot be closed
     * or replaced while an inference call is using it.
     */
    private val lifecycleMutex = Mutex()

    /**
     * Tracks callers that have entered a lifecycle operation, including callers
     * waiting for lifecycleMutex. This lets synchronous close() defer destruction
     * safely until those operations finish.
     */
    private var activeOperations = 0
    private var closeRequested = false
    private val operationStateLock = Any()

    private fun beginOperation() {
        synchronized(operationStateLock) {
            activeOperations += 1
        }
    }

    private fun endOperation() {
        synchronized(operationStateLock) {
            activeOperations -= 1
            check(activeOperations >= 0) { "Runtime operation count underflow." }
            if (activeOperations == 0 && closeRequested) {
                closeRequested = false
                closeInternal()
            }
        }
    }

    suspend fun load(modelFile: File) {
        beginOperation()
        try {
            lifecycleMutex.withLock {
                val selection = readActiveSelection(modelFile)
                val newEngine = engineFactory(modelFile)

                try {
                    newEngine.initialize()
                } catch (error: Throwable) {
                    runCatching { newEngine.close() }
                    throw error
                }

                installRuntime(
                    newEngine = newEngine,
                    modelFile = modelFile,
                    selection = selection
                )
            }
        } finally {
            endOperation()
        }
    }

    fun isReady(): Boolean = engine?.isInitialized() == true

    suspend fun respond(
        history: List<ChatMessage>,
        userMessage: ChatMessage,
        loraAdapterFile: File? = null
    ): ChatMessage {
        beginOperation()
        try {
            return lifecycleMutex.withLock {
                ensureRuntimeMatchesActiveVersion()

                val activeEngine = checkNotNull(engine) {
                    "No local model is installed. Import a .litertlm model first."
                }

                val pinnedAdapter = loadedAdapterFile
                if (loraAdapterFile != null && !sameFile(loraAdapterFile, pinnedAdapter)) {
                    throw IllegalArgumentException(
                        "Direct LoRA adapter injection is not allowed unless it is the active version."
                    )
                }

                ConversationOrchestrator(activeEngine).respond(
                    history = history,
                    userMessage = userMessage,
                    loraAdapterFile = pinnedAdapter
                )
            }
        } finally {
            endOperation()
        }
    }

    /**
     * Returns the active version currently pinned to the loaded runtime.
     * Null means the base local model is running without an active adapter version.
     */
    fun loadedActiveVersionId(): String? = loadedActiveVersionId

    private suspend fun ensureRuntimeMatchesActiveVersion() {
        val modelFile = loadedModelFile ?: return
        val selection = readActiveSelection(modelFile)

        val matches = selection?.versionId == loadedActiveVersionId &&
            sameFile(selection?.adapterFile, loadedAdapterFile) &&
            selection?.adapterSha256 == loadedAdapterSha256

        if (!matches) {
            reloadInternal(modelFile, selection)
        }
    }

    private suspend fun reloadInternal(modelFile: File, selection: ActiveSelection?) {
        val newEngine = engineFactory(modelFile)

        try {
            newEngine.initialize()
        } catch (error: Throwable) {
            runCatching { newEngine.close() }
            throw error
        }

        installRuntime(
            newEngine = newEngine,
            modelFile = modelFile,
            selection = selection
        )
    }

    private fun installRuntime(
        newEngine: AssistantEngine,
        modelFile: File,
        selection: ActiveSelection?
    ) {
        val previousEngine = engine

        engine = newEngine
        loadedModelFile = modelFile
        loadedActiveVersionId = selection?.versionId
        loadedAdapterFile = selection?.adapterFile
        loadedAdapterSha256 = selection?.adapterSha256

        runCatching { previousEngine?.close() }
    }

    private fun readActiveSelection(modelFile: File): ActiveSelection? {
        val store = modelVersionStore ?: return null
        val active = store.active() ?: return null

        check(modelFile.isFile && modelFile.length() > 0L) {
            "Local base model file is missing or empty."
        }

        val expectedBaseModelSha256 = active.baseModelSha256
            ?: throw IllegalStateException(
                "Active model version " + active.id +
                    " has no base model fingerprint."
            )
        val actualBaseModelSha256 = ArtifactDigest.sha256(modelFile)
        if (actualBaseModelSha256 != expectedBaseModelSha256) {
            throw IllegalStateException(
                "Loaded base model hash does not match active model version."
            )
        }

        val adapter = active.adapterFile
            ?: throw IllegalStateException(
                "Active model version " + active.id + " has no adapter artifact."
            )
        if (!adapter.isFile || adapter.length() <= 0L) {
            throw IllegalStateException(
                "Active model version " + active.id + " has an unavailable adapter artifact."
            )
        }

        val reportId = active.evaluationReportId
            ?: throw IllegalStateException(
                "Active model version " + active.id + " has no evaluation report."
            )
        val reportStore = evaluationReportStore
            ?: throw IllegalStateException(
                "Evaluation report storage is required for active-version runtime validation."
            )
        val report = reportStore.get(reportId)
            ?: throw IllegalStateException(
                "Evaluation report " + reportId +
                    " for active model version " + active.id + " was not found."
            )

        if (report.candidateVersionId != active.id) {
            throw IllegalStateException(
                "Evaluation report " + reportId +
                    " does not match active model version " + active.id + "."
            )
        }

        val adapterSha256 = ArtifactDigest.sha256(adapter)
        if (adapterSha256 != report.candidateAdapterSha256) {
            throw IllegalStateException(
                "Active adapter artifact hash does not match evaluation evidence."
            )
        }

        return ActiveSelection(
            versionId = active.id,
            adapterFile = adapter,
            adapterSha256 = adapterSha256
        )
    }

    private fun sameFile(first: File?, second: File?): Boolean {
        if (first == null || second == null) return first == second
        return runCatching { first.canonicalFile == second.canonicalFile }
            .getOrElse { first.absoluteFile == second.absoluteFile }
    }

    override fun close() {
        synchronized(operationStateLock) {
            if (activeOperations > 0) {
                closeRequested = true
                return
            }
            closeInternal()
        }
    }

    private fun closeInternal() {
        val currentEngine = engine
        engine = null
        loadedModelFile = null
        loadedActiveVersionId = null
        loadedAdapterFile = null
        loadedAdapterSha256 = null
        currentEngine?.close()
    }

    private data class ActiveSelection(
        val versionId: String,
        val adapterFile: File,
        val adapterSha256: String
    )
}
