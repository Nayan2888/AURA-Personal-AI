package ai.aura.personal.core.training

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Device-local storage for verified candidate adapter artifacts.
 *
 * Publishing is intentionally separate from activation: storing a candidate
 * never changes the active model. Published versions are immutable.
 */
class TrainingArtifactStore(rootDirectory: File) {
    private val candidatesDirectory = File(rootDirectory, "training/candidates").apply {
        require(isDirectory || (mkdirs() && isDirectory)) {
            "Candidate artifact directory is unavailable"
        }
    }

    @Synchronized
    fun publish(adapterFile: File, adapterVersion: String): File {
        require(adapterFile.isFile && adapterFile.length() > 0L) {
            "Adapter artifact must be a non-empty file"
        }

        val safeVersion = adapterVersion.trim()
        require(safeVersion.matches(VERSION_PATTERN)) {
            "Adapter version contains unsupported characters"
        }

        val destination = File(candidatesDirectory, "$safeVersion.adapter")
        require(!destination.exists()) {
            "Adapter version already exists: $safeVersion"
        }

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
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }

        check(destination.isFile && destination.length() > 0L) {
            "Published adapter artifact is invalid"
        }
        return destination
    }

    companion object {
        private val VERSION_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")
    }
}
