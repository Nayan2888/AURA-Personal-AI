package ai.aura.personal.core.evaluation

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Device-local durable storage for immutable evaluation reports.
 *
 * Reports are evidence used by the model version activation gate. Saving a
 * report never activates or changes a model version.
 */
class EvaluationReportStore(
    private val rootDirectory: File
) {
    private val manifestFile = File(rootDirectory, "evaluation-reports.properties")

    init {
        require(rootDirectory.exists() || rootDirectory.mkdirs() || rootDirectory.isDirectory) {
            "Evaluation report root directory is unavailable"
        }
    }

    @Synchronized
    fun save(report: EvaluationReport) {
        val data = loadData()
        require(data.reports.none { it.id == report.id }) {
            "Evaluation report already exists: " + report.id
        }
        data.reports += report
        saveData(data)
    }

    @Synchronized
    fun get(id: String): EvaluationReport? =
        loadData().reports.firstOrNull { it.id == id }

    @Synchronized
    fun list(): List<EvaluationReport> =
        loadData().reports
            .sortedWith(compareBy<EvaluationReport>({ it.completedAtEpochMs }, { it.id }))

    private fun loadData(): StoreData {
        if (!manifestFile.isFile || manifestFile.length() == 0L) return StoreData()

        val properties = Properties()
        FileInputStream(manifestFile).use { properties.load(it) }

        val count = properties.getProperty("report.count", "0").toIntOrNull()
            ?: throw IllegalStateException("Invalid evaluation report manifest")

        val reports = mutableListOf<EvaluationReport>()
        for (index in 0 until count) {
            val prefix = "report." + index + "."
            val id = properties.getProperty(prefix + "id")
                ?: throw IllegalStateException("Missing evaluation report id")
            val baseVersionId = properties.getProperty(prefix + "baseVersionId")
                ?: throw IllegalStateException("Missing evaluation base version for " + id)
            val candidateVersionId = properties.getProperty(prefix + "candidateVersionId")
                ?: throw IllegalStateException(
                    "Missing evaluation candidate version for " + id
                )
            val candidateAdapterSha256 =
                properties.getProperty(prefix + "candidateAdapterSha256")
                    ?: throw IllegalStateException(
                        "Missing candidate adapter SHA-256 for " + id
                    )
            val baseModelSha256 =
                properties.getProperty(prefix + "baseModelSha256")
                    ?.takeIf { it.isNotBlank() }
            val evaluatedExampleCount =
                properties.getProperty(prefix + "evaluatedExampleCount")?.toIntOrNull()
                    ?: throw IllegalStateException(
                        "Invalid evaluated example count for " + id
                    )
            val baseMeanError =
                properties.getProperty(prefix + "baseMeanError")?.toDoubleOrNull()
                    ?: throw IllegalStateException("Invalid base mean error for " + id)
            val candidateMeanError =
                properties.getProperty(prefix + "candidateMeanError")?.toDoubleOrNull()
                    ?: throw IllegalStateException(
                        "Invalid candidate mean error for " + id
                    )
            val safetyChecksPassed =
                properties.getProperty(prefix + "safetyChecksPassed")?.toBooleanStrictOrNull()
                    ?: throw IllegalStateException("Invalid safety result for " + id)
            val compatibilityChecksPassed =
                properties.getProperty(prefix + "compatibilityChecksPassed")
                    ?.toBooleanStrictOrNull()
                    ?: throw IllegalStateException(
                        "Invalid compatibility result for " + id
                    )
            val completedAtEpochMs =
                properties.getProperty(prefix + "completedAtEpochMs")?.toLongOrNull()
                    ?: throw IllegalStateException("Invalid evaluation timestamp for " + id)

            reports += EvaluationReport(
                id = id,
                baseVersionId = baseVersionId,
                candidateVersionId = candidateVersionId,
                candidateAdapterSha256 = candidateAdapterSha256,
                baseModelSha256 = baseModelSha256,
                evaluatedExampleCount = evaluatedExampleCount,
                baseMeanError = baseMeanError,
                candidateMeanError = candidateMeanError,
                safetyChecksPassed = safetyChecksPassed,
                compatibilityChecksPassed = compatibilityChecksPassed,
                completedAtEpochMs = completedAtEpochMs
            )
        }

        return StoreData(reports)
    }

    private fun saveData(data: StoreData) {
        val properties = Properties()
        properties.setProperty("report.count", data.reports.size.toString())

        data.reports.forEachIndexed { index, report ->
            val prefix = "report." + index + "."
            properties.setProperty(prefix + "id", report.id)
            properties.setProperty(prefix + "baseVersionId", report.baseVersionId)
            properties.setProperty(prefix + "candidateVersionId", report.candidateVersionId)
            properties.setProperty(
                prefix + "candidateAdapterSha256",
                report.candidateAdapterSha256
            )
            report.baseModelSha256?.let {
                properties.setProperty(prefix + "baseModelSha256", it)
            }
            properties.setProperty(
                prefix + "evaluatedExampleCount",
                report.evaluatedExampleCount.toString()
            )
            properties.setProperty(prefix + "baseMeanError", report.baseMeanError.toString())
            properties.setProperty(
                prefix + "candidateMeanError",
                report.candidateMeanError.toString()
            )
            properties.setProperty(
                prefix + "safetyChecksPassed",
                report.safetyChecksPassed.toString()
            )
            properties.setProperty(
                prefix + "compatibilityChecksPassed",
                report.compatibilityChecksPassed.toString()
            )
            properties.setProperty(
                prefix + "completedAtEpochMs",
                report.completedAtEpochMs.toString()
            )
        }

        val temporary = File(rootDirectory, manifestFile.name + ".tmp")
        FileOutputStream(temporary).use { output ->
            properties.store(output, "AURA evaluation reports")
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
        val reports: MutableList<EvaluationReport> = mutableListOf()
    )
}
