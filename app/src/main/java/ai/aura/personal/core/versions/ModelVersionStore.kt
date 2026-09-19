package ai.aura.personal.core.versions

import ai.aura.personal.core.evaluation.EvaluationGate
import ai.aura.personal.core.evaluation.EvaluationReport
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Persistent registry for candidate/active/retired model versions.
 *
 * The registry changes metadata only. It does not download models, train models,
 * or silently replace the active inference runtime.
 */
class ModelVersionStore(
    private val rootDirectory: File
) {
    private val manifestFile = File(rootDirectory, "model-versions.properties")

    init {
        require(rootDirectory.exists() || rootDirectory.mkdirs() || rootDirectory.isDirectory) {
            "Model version root directory is unavailable"
        }
    }

    @Synchronized
    fun registerCandidate(version: ModelVersion) {
        require(version.state == ModelVersion.State.CANDIDATE) {
            "Only candidate versions can be registered"
        }
        require(version.adapterFile?.isFile == true && version.adapterFile.length() > 0L) {
            "Candidate adapter file must be a non-empty file"
        }

        val data = loadData()
        require(data.versions.none { it.id == version.id }) {
            "Model version already exists: " + version.id
        }

        data.versions.add(version)
        saveData(data)
    }

    @Synchronized
    fun listCandidates(): List<ModelVersion> =
        loadData().versions
            .filter { it.state == ModelVersion.State.CANDIDATE }
            .sortedWith(compareBy<ModelVersion>({ it.createdAtEpochMs }, { it.id }))

    @Synchronized
    fun active(): ModelVersion? {
        val data = loadData()
        return data.activeId?.let { id -> data.versions.firstOrNull { it.id == id } }
    }

    @Synchronized
    fun activateCandidate(
        candidateId: String,
        evaluationReport: EvaluationReport,
        approvalGranted: Boolean,
        maxQualityRegression: Double = 0.0
    ): ActivationResult {
        val data = loadData()
        val candidate = data.versions.firstOrNull { it.id == candidateId }
            ?: return ActivationResult.REJECTED("Candidate version not found: " + candidateId)

        if (candidate.state != ModelVersion.State.CANDIDATE) {
            return ActivationResult.REJECTED("Model version is not a candidate: " + candidateId)
        }
        if (candidate.evaluationReportId != evaluationReport.id) {
            return ActivationResult.REJECTED("Evaluation report does not match candidate")
        }
        if (evaluationReport.candidateVersionId != candidateId) {
            return ActivationResult.REJECTED("Evaluation candidate id does not match candidate")
        }
        if (evaluationReport.baseVersionId.isBlank()) {
            return ActivationResult.REJECTED("Evaluation base version is missing")
        }

        val decision = EvaluationGate.check(
            report = evaluationReport,
            approvalGranted = approvalGranted,
            maxQualityRegression = maxQualityRegression
        )
        if (decision != EvaluationGate.Decision.PASSED) {
            return ActivationResult.REJECTED(decision.name)
        }

        data.versions.replaceAll {
            when {
                it.id == candidateId ->
                    it.copy(state = ModelVersion.State.ACTIVE)
                it.state == ModelVersion.State.ACTIVE ->
                    it.copy(state = ModelVersion.State.RETIRED)
                else -> it
            }
        }
        data.activeId = candidateId
        saveData(data)

        return ActivationResult.ACTIVATED(candidateId)
    }

    @Synchronized
    fun rollback(): RollbackResult {
        val data = loadData()
        val activeId = data.activeId
            ?: return RollbackResult.REJECTED("No active model version exists")

        val previous = data.versions
            .asSequence()
            .filter { it.state == ModelVersion.State.RETIRED }
            .maxByOrNull { it.createdAtEpochMs }
            ?: return RollbackResult.REJECTED("No retired version is available for rollback")

        data.versions.replaceAll {
            when (it.id) {
                activeId -> it.copy(state = ModelVersion.State.RETIRED)
                previous.id -> it.copy(state = ModelVersion.State.ACTIVE)
                else -> it
            }
        }
        data.activeId = previous.id
        saveData(data)

        return RollbackResult.ROLLED_BACK(previous.id)
    }

    private fun loadData(): StoreData {
        if (!manifestFile.isFile || manifestFile.length() == 0L) return StoreData()

        val properties = Properties()
        FileInputStream(manifestFile).use { properties.load(it) }

        val count = properties.getProperty("version.count", "0").toIntOrNull()
            ?: throw IllegalStateException("Invalid model version manifest")

        val versions = mutableListOf<ModelVersion>()
        for (index in 0 until count) {
            val prefix = "version." + index + "."
            val id = properties.getProperty(prefix + "id")
                ?: throw IllegalStateException("Missing model version id")

            val stateName = properties.getProperty(prefix + "state")
                ?: throw IllegalStateException("Missing model version state for " + id)
            val state = runCatching { ModelVersion.State.valueOf(stateName) }
                .getOrElse {
                    throw IllegalStateException(
                        "Invalid model version state for " + id,
                        it
                    )
                }

            val adapterPath = properties.getProperty(prefix + "adapterPath")
            val evaluationReportId = properties.getProperty(prefix + "evaluationReportId")
                ?.takeIf { it.isNotBlank() }

            val createdAt = properties.getProperty(prefix + "createdAt")?.toLongOrNull()
                ?: throw IllegalStateException("Invalid model version timestamp for " + id)

            versions += ModelVersion(
                id = id,
                baseModelId = properties.getProperty(prefix + "baseModelId")
                    ?: throw IllegalStateException("Missing base model id for " + id),
                adapterFile = adapterPath?.let(::File),
                state = state,
                evaluationReportId = evaluationReportId,
                createdAtEpochMs = createdAt
            )
        }

        return StoreData(
            activeId = properties.getProperty("activeId")?.takeIf { it.isNotBlank() },
            versions = versions.toMutableList()
        )
    }

    private fun saveData(data: StoreData) {
        val properties = Properties()
        properties.setProperty("version.count", data.versions.size.toString())
        data.activeId?.let { properties.setProperty("activeId", it) }

        data.versions.forEachIndexed { index, version ->
            val prefix = "version." + index + "."
            properties.setProperty(prefix + "id", version.id)
            properties.setProperty(prefix + "baseModelId", version.baseModelId)
            properties.setProperty(prefix + "state", version.state.name)
            properties.setProperty(prefix + "createdAt", version.createdAtEpochMs.toString())
            version.adapterFile?.let {
                properties.setProperty(prefix + "adapterPath", it.absolutePath)
            }
            version.evaluationReportId?.let {
                properties.setProperty(prefix + "evaluationReportId", it)
            }
        }

        val temporary = File(rootDirectory, manifestFile.name + ".tmp")
        FileOutputStream(temporary).use { output ->
            properties.store(output, "AURA model versions")
            output.fd.sync()
        }

        try {
            Files.move(
                temporary.toPath(),
                manifestFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(
                temporary.toPath(),
                manifestFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private data class StoreData(
        var activeId: String? = null,
        val versions: MutableList<ModelVersion> = mutableListOf()
    )

    sealed interface ActivationResult {
        data class ACTIVATED(val versionId: String) : ActivationResult
        data class REJECTED(val reason: String) : ActivationResult
    }

    sealed interface RollbackResult {
        data class ROLLED_BACK(val versionId: String) : RollbackResult
        data class REJECTED(val reason: String) : RollbackResult
    }
}
