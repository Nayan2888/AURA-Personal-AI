package ai.aura.personal.core.training

import java.io.File

/**
 * Deterministic gate that must pass before any training backend is invoked.
 *
 * This gate does not start training and does not claim that a backend exists.
 */
object TrainingPreflight {
    fun check(
        consentGranted: Boolean,
        trainingModel: File,
        dataset: File,
        outputDirectory: File,
        spec: LoraTrainingSpec
    ): Result {
        if (!consentGranted) return Result.NOT_CONSENTED
        if (!trainingModel.isFile || trainingModel.length() <= 0L) {
            return Result.INVALID_TRAINING_MODEL
        }
        if (!dataset.isFile || dataset.length() <= 0L) return Result.INVALID_DATASET
        if (outputDirectory.exists() && !outputDirectory.isDirectory) {
            return Result.INVALID_OUTPUT_DIRECTORY
        }
        if (!outputDirectory.exists() && !outputDirectory.mkdirs() && !outputDirectory.isDirectory) {
            return Result.OUTPUT_DIRECTORY_UNAVAILABLE
        }

        return Result.READY
    }

    enum class Result {
        READY,
        NOT_CONSENTED,
        INVALID_TRAINING_MODEL,
        INVALID_DATASET,
        INVALID_OUTPUT_DIRECTORY,
        OUTPUT_DIRECTORY_UNAVAILABLE
    }
}
