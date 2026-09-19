package ai.aura.personal.core.security

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Computes a deterministic SHA-256 digest over the exact bytes of a local artifact.
 *
 * The digest binds evaluation evidence to the artifact that was actually evaluated.
 */
object ArtifactDigest {
    private const val BUFFER_SIZE = 32 * 1024

    fun sha256(file: File): String {
        require(file.isFile) {
            "Artifact does not exist: ${file.absolutePath}"
        }

        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)

        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                digest.update(buffer, 0, read)
            }
        }

        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }
}
