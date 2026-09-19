package ai.aura.personal.core.experience

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Device-local training dataset artifact store.
 *
 * Dataset generation is an explicit, consent-gated step. It never trains or
 * switches a model. The JSONL file contains only model-facing input/target
 * pairs; an adjacent manifest keeps audit metadata local to the device.
 */
class LearningDatasetStore(context: Context) {
    private val directory = File(
        context.applicationContext.filesDir,
        DIRECTORY_NAME
    ).apply { mkdirs() }

    private val datasetFile = File(directory, DATASET_FILE_NAME)
    private val manifestFile = File(directory, MANIFEST_FILE_NAME)

    fun build(
        experienceStore: ExperienceStore,
        consentGranted: Boolean,
        createdAtEpochMs: Long
    ): DatasetBuildResult {
        require(consentGranted) { "Learning consent is required to build a dataset" }
        require(createdAtEpochMs >= 0L) { "Dataset timestamp must not be negative" }

        val entries = LearningDatasetBuilder.buildAll(
            records = experienceStore.list(),
            consentGranted = consentGranted
        )
        val jsonl = LearningDatasetBuilder.toJsonl(entries)

        writeAtomically(datasetFile, jsonl)

        val manifest = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("createdAtEpochMs", createdAtEpochMs)
            .put("entryCount", entries.size)
            .put(
                "sourceExperienceIds",
                JSONArray().apply {
                    entries.forEach { put(it.sourceExperienceId) }
                }
            )

        writeAtomically(manifestFile, manifest.toString())

        return DatasetBuildResult(
            file = datasetFile,
            manifestFile = manifestFile,
            entryCount = entries.size,
            createdAtEpochMs = createdAtEpochMs
        )
    }

    fun datasetFile(): File = datasetFile.takeIf { it.exists() }

    fun manifestFile(): File = manifestFile.takeIf { it.exists() }

    private fun writeAtomically(target: File, content: String) {
        val temp = File(directory, target.name + ".tmp")
        temp.writeText(content, Charsets.UTF_8)

        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    data class DatasetBuildResult(
        val file: File,
        val manifestFile: File,
        val entryCount: Int,
        val createdAtEpochMs: Long
    )

    private companion object {
        const val DIRECTORY_NAME = "learning"
        const val DATASET_FILE_NAME = "aura-training-v1.jsonl"
        const val MANIFEST_FILE_NAME = "aura-training-v1.manifest.json"
        const val SCHEMA_VERSION = 1
    }
}
