package ai.aura.personal.core.inference

import ai.aura.personal.core.chat.ChatMessage
import ai.aura.personal.core.evaluation.EvaluationReportStore
import ai.aura.personal.core.security.ArtifactDigest
import ai.aura.personal.core.versions.ModelVersionStore
import java.io.File

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

    suspend fun load(modelFile: File) {
        val selection = readActiveSelection()

        close()
        val newEngine = engineFactory(modelFile)
        try {
            newEngine.initialize()
            engine = newEngine
            loadedModelFile = modelFile
            loadedActiveVersionId = selection?.versionId
            loadedAdapterFile = selection?.adapterFile
            loadedAdapterSha256 = selection?.adapterSha256
        } catch (error: Throwable) {
            newEngine.close()
            throw error
        }
    }

    fun isReady(): Boolean = engine?.isInitialized() == true

    suspend fun respond(
        history: List<ChatMessage>,
        userMessage: ChatMessage,
        loraAdapterFile: File? = null
    ): ChatMessage {
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

        return ConversationOrchestrator(activeEngine).respond(
            history = history,
            userMessage = userMessage,
            loraAdapterFile = pinnedAdapter
        )
    }

    /**
     * Returns the active version currently pinned to the loaded runtime.
     * Null means the base local model is running without an active adapter version.
     */
    fun loadedActiveVersionId(): String? = loadedActiveVersionId

    private suspend fun ensureRuntimeMatchesActiveVersion() {
        val modelFile = loadedModelFile ?: return
        val selection = readActiveSelection()

        val matches = selection?.versionId == loadedActiveVersionId &&
            sameFile(selection?.adapterFile, loadedAdapterFile) &&
            selection?.adapterSha256 == loadedAdapterSha256

        if (!matches) {
            load(modelFile)
        }
    }

    private fun readActiveSelection(): ActiveSelection? {
        val store = modelVersionStore ?: return null
        val active = store.active() ?: return null

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
        engine?.close()
        engine = null
        loadedModelFile = null
        loadedActiveVersionId = null
        loadedAdapterFile = null
        loadedAdapterSha256 = null
    }

    private data class ActiveSelection(
        val versionId: String,
        val adapterFile: File,
        val adapterSha256: String
    )
}
