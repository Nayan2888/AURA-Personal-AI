package ai.aura.personal.core.training

import java.io.File

/**
 * Verifies that a trainer returned a concrete adapter artifact inside the job's
 * private output directory before AURA stores it as a candidate.
 */
object TrainingArtifactValidator {

    fun validate(
        adapterFile: File,
        outputDirectory: File
    ): Result {
        if (!outputDirectory.isDirectory) {
            return Result.INVALID_OUTPUT_DIRECTORY
        }
        if (!adapterFile.isFile || adapterFile.length() <= 0L) {
            return Result.INVALID_ADAPTER
        }

        val outputRoot = runCatching { outputDirectory.toPath().toRealPath() }
            .getOrElse { return Result.INVALID_OUTPUT_DIRECTORY }
        val adapterPath = runCatching { adapterFile.toPath().toRealPath() }
            .getOrElse { return Result.INVALID_ADAPTER }

        return if (adapterPath.startsWith(outputRoot)) {
            Result.VALID
        } else {
            Result.OUTSIDE_OUTPUT_DIRECTORY
        }
    }

    enum class Result {
        VALID,
        INVALID_OUTPUT_DIRECTORY,
        INVALID_ADAPTER,
        OUTSIDE_OUTPUT_DIRECTORY
    }
}
