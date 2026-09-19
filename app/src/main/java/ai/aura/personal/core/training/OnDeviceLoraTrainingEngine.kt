package ai.aura.personal.core.training

import java.io.File

/**
 * Contract for a real on-device LoRA trainer.
 *
 * The training artifact is a dedicated ExecuTorch training model (.pte, optionally
 * accompanied by external data), not the active LiteRT-LM inference .litertlm file.
 */
interface OnDeviceLoraTrainingEngine {
    suspend fun train(
        trainingModel: File,
        dataset: File,
        outputDirectory: File,
        spec: LoraTrainingSpec,
        consentGranted: Boolean
    ): TrainingResult
}

sealed interface TrainingResult {
    data class Success(
        val adapterFile: File,
        val adapterVersion: String
    ) : TrainingResult

    data class Rejected(
        val reason: String
    ) : TrainingResult

    data class Failed(
        val reason: String,
        val cause: Throwable? = null
    ) : TrainingResult
}
