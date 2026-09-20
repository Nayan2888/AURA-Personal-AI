package ai.aura.personal.core.inference

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Device-local model import and selection.
 *
 * Models are copied into the app-private files directory so inference does not
 * depend on external document-provider availability after installation.
 */
class LocalModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val modelDirectory = File(appContext.filesDir, "models").apply { mkdirs() }

    fun selectedModel(): File? {
        val storedPath = preferences.getString(KEY_SELECTED_MODEL_PATH, null) ?: return null
        val file = File(storedPath)
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    fun selectedModelName(): String? = selectedModel()?.name

    fun importModel(uri: Uri, displayName: String): File {
        require(displayName.isNotBlank()) { "Model name must not be blank." }
        require(displayName.lowercase().endsWith(".litertlm")) {
            "AURA currently accepts .litertlm model files."
        }

        val safeName = displayName.substringAfterLast('/').replace(
            Regex("[^A-Za-z0-9._-]"),
            "_"
        )
        val destination = File(modelDirectory, safeName)

        appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected model." }
            destination.outputStream().use { output -> input.copyTo(output) }
        }

        require(destination.isFile && destination.length() > 0L) {
            "Imported model is empty or could not be stored."
        }

        return destination
    }

    /**
     * Commits an imported model as the selected model only after runtime
     * initialization has succeeded.
     */
    fun selectModel(modelFile: File) {
        val modelRoot = runCatching { modelDirectory.canonicalFile }
            .getOrElse { throw IllegalStateException("Model directory is unavailable", it) }
        val candidate = runCatching { modelFile.canonicalFile }
            .getOrElse { throw IllegalArgumentException("Model file is unavailable", it) }

        require(candidate.isFile && candidate.length() > 0L) {
            "Selected model must be a non-empty file."
        }
        require(candidate.startsWith(modelRoot)) {
            "Selected model must be inside AURA's private model directory."
        }
        require(candidate.extension.equals("litertlm", ignoreCase = true)) {
            "AURA currently accepts .litertlm model files."
        }

        preferences.edit()
            .putString(KEY_SELECTED_MODEL_PATH, candidate.absolutePath)
            .apply()
    }

    fun clearSelection() {
        preferences.edit().remove(KEY_SELECTED_MODEL_PATH).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "aura_local_model"
        const val KEY_SELECTED_MODEL_PATH = "selected_model_path"
    }
}
