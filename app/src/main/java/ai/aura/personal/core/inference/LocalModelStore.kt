package ai.aura.personal.core.inference

import android.content.Context
import android.net.Uri
import ai.aura.personal.core.security.ArtifactDigest
import java.io.File
import java.io.InputStream
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Owns installed LiteRT-LM files. Temporary transfers never appear in the model library. */
class LocalModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("aura_local_model", Context.MODE_PRIVATE)
    private val modelDirectory = File(appContext.filesDir, "models").apply { mkdirs() }

    fun installedModels(): List<File> = modelDirectory.listFiles().orEmpty()
        .filter { it.isFile && it.length() > 0L && it.extension.equals("litertlm", ignoreCase = true) }
        .sortedBy { it.name.lowercase() }

    fun selectedModel(): File? {
        val path = preferences.getString("selected_model_path", null) ?: return null
        return installedModels().firstOrNull { it.absolutePath == path }
    }

    fun select(file: File?) {
        require(file == null || file in installedModels()) { "Model is no longer installed." }
        preferences.edit().apply {
            if (file == null) remove("selected_model_path")
            else putString("selected_model_path", file.absolutePath)
        }.apply()
    }

    suspend fun importModel(uri: Uri, displayName: String, progress: (Long, Long?) -> Unit): File {
        val input = requireNotNull(appContext.contentResolver.openInputStream(uri)) { "Cannot open the selected file." }
        return input.use { install(it, displayName, null, null, progress) }
    }

    suspend fun downloadModel(
        url: String,
        displayName: String,
        sha256: String?,
        progress: (Long, Long?) -> Unit
    ): File {
        require(sha256 == null || sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Enter a 64-character SHA-256 hash." }
        var source = URI(url)
        repeat(6) {
            require(source.scheme == "https" && source.host != null && source.userInfo == null) {
                "Use an HTTPS model download URL."
            }
            val connection = source.toURL().openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Accept-Encoding", "identity")
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: error("Download redirect has no destination.")
                    source = source.resolve(location)
                } else {
                    check(status == 200) { "Model download returned HTTP $status." }
                    val contentType = connection.contentType.orEmpty().lowercase()
                    check(!contentType.startsWith("text/") && !contentType.contains("json")) {
                        "URL returned a page instead of a model file. Use a direct download URL."
                    }
                    val bytes = connection.contentLengthLong.takeIf { it >= 0 }
                    return connection.inputStream.use { install(it, displayName, bytes, sha256, progress) }
                }
            } finally {
                connection.disconnect()
            }
        }
        error("Too many model download redirects.")
    }

    private suspend fun install(
        input: InputStream,
        displayName: String,
        expectedBytes: Long?,
        sha256: String?,
        progress: (Long, Long?) -> Unit
    ): File {
        val name = displayName.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
        require(name.length <= 180 && name.endsWith(".litertlm", ignoreCase = true) &&
            name.substringBeforeLast('.').any { it.isLetterOrDigit() }) { "Enter a valid .litertlm file name." }
        val destination = File(modelDirectory, name)
        check(!destination.exists()) { "A model with this file name is already installed. Use a different name." }
        check(expectedBytes == null || expectedBytes <= modelDirectory.usableSpace) { "Free storage is too low for this model." }
        val temp = File.createTempFile("transfer-", ".part", modelDirectory)
        try {
            var copied = 0L
            var lastUpdate = 0L
            val buffer = ByteArray(64 * 1024)
            temp.outputStream().buffered().use { output ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count == -1) break
                    output.write(buffer, 0, count)
                    copied += count
                    if (copied - lastUpdate >= 1024 * 1024) {
                        progress(copied, expectedBytes)
                        lastUpdate = copied
                    }
                }
            }
            check(copied > 0 && (expectedBytes == null || copied == expectedBytes)) { "Model transfer is incomplete." }
            if (sha256 != null) {
                check(ArtifactDigest.sha256(temp).equals(sha256, ignoreCase = true)) { "SHA-256 mismatch. Model was not installed." }
            }
            currentCoroutineContext().ensureActive()
            // Atomic move without replacing a file installed by another activity instance.
            java.nio.file.Files.createLink(destination.toPath(), temp.toPath())
            progress(copied, expectedBytes)
            return destination
        } finally {
            temp.delete()
        }
    }
}
