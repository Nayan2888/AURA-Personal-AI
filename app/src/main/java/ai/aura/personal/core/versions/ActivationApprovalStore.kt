package ai.aura.personal.core.versions

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Device-local append-only store for explicit model activation approvals.
 *
 * An approval is scoped to one candidate and one evaluation report. Saving an
 * approval never activates a model.
 */
class ActivationApprovalStore(
    private val rootDirectory: File
) {
    private val manifestFile = File(rootDirectory, "activation-approvals.properties")

    init {
        require(rootDirectory.exists() || rootDirectory.mkdirs() || rootDirectory.isDirectory) {
            "Activation approval root directory is unavailable"
        }
    }

    @Synchronized
    fun save(approval: ActivationApproval) {
        val data = loadData()
        require(data.approvals.none { it.id == approval.id }) {
            "Activation approval already exists: " + approval.id
        }
        data.approvals += approval
        saveData(data)
    }

    @Synchronized
    fun get(id: String): ActivationApproval? =
        loadData().approvals.firstOrNull { it.id == id }

    @Synchronized
    fun list(): List<ActivationApproval> =
        loadData().approvals
            .sortedWith(compareBy<ActivationApproval>({ it.grantedAtEpochMs }, { it.id }))

    private fun loadData(): StoreData {
        if (!manifestFile.isFile || manifestFile.length() == 0L) return StoreData()

        val properties = Properties()
        FileInputStream(manifestFile).use { properties.load(it) }

        val count = properties.getProperty("approval.count", "0").toIntOrNull()
            ?: throw IllegalStateException("Invalid activation approval manifest")

        val approvals = mutableListOf<ActivationApproval>()
        for (index in 0 until count) {
            val prefix = "approval." + index + "."
            val id = properties.getProperty(prefix + "id")
                ?: throw IllegalStateException("Missing activation approval id")
            val candidateVersionId = properties.getProperty(prefix + "candidateVersionId")
                ?: throw IllegalStateException(
                    "Missing candidate version for activation approval " + id
                )
            val evaluationReportId = properties.getProperty(prefix + "evaluationReportId")
                ?: throw IllegalStateException(
                    "Missing evaluation report for activation approval " + id
                )
            val grantedAtEpochMs = properties.getProperty(prefix + "grantedAtEpochMs")
                ?.toLongOrNull()
                ?: throw IllegalStateException(
                    "Invalid activation approval timestamp for " + id
                )

            approvals += ActivationApproval(
                id = id,
                candidateVersionId = candidateVersionId,
                evaluationReportId = evaluationReportId,
                grantedAtEpochMs = grantedAtEpochMs
            )
        }

        return StoreData(approvals)
    }

    private fun saveData(data: StoreData) {
        val properties = Properties()
        properties.setProperty("approval.count", data.approvals.size.toString())

        data.approvals.forEachIndexed { index, approval ->
            val prefix = "approval." + index + "."
            properties.setProperty(prefix + "id", approval.id)
            properties.setProperty(
                prefix + "candidateVersionId",
                approval.candidateVersionId
            )
            properties.setProperty(
                prefix + "evaluationReportId",
                approval.evaluationReportId
            )
            properties.setProperty(
                prefix + "grantedAtEpochMs",
                approval.grantedAtEpochMs.toString()
            )
        }

        val temporary = File(rootDirectory, manifestFile.name + ".tmp")
        FileOutputStream(temporary).use { output ->
            properties.store(output, "AURA activation approvals")
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
        val approvals: MutableList<ActivationApproval> = mutableListOf()
    )
}
