package ai.aura.personal.core.training

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Device-local storage for verified candidate adapter artifacts.
 *
 * Publishing is intentionally separate from activation: storing a candidate
 * never changes the active model.
 */
class TrainingArtifactStore(rootDirectory: File) {
    private val candidatesDirectory = File(rootDirectory, "training/candidates").apply { mkdirs() }

    fun publish(adapterFile: File, adapterVersion: String): File {
        require(adapterFile.isFile && adapterFile.length() > 0L) {
            "Adapter artifact must be a non-empty file"
        }
        val safeVersion = adapterVersion.trim()
        require(safeVersion.isNotEmpty()) { "Adapter version must not be blank" }
        require(safeVersion.none { it == '/' || it == '\\' }) {
            "Adapter version must not contain path separators"
        }

        val destination = File(candidatesDirectory, "$safeVersion.adapter")
        val temporary = File(candidatesDirectory, "$safeVersion.adapter.tmp")
        Files.copy(
            adapterFile.toPath(),
            temporary.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
        return destination
    }
}
