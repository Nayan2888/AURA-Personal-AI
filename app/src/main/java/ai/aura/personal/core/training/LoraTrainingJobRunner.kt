package ai.aura.personal.core.training

import java.io.File

/**
 * Executes one explicitly authorized training attempt.
 *
 * The runner only orchestrates a real backend. It never implements training
 * itself and never activates a produced candidate.
 */
class LoraTrainingJobRunner(
    private val trainingEngine: OnDeviceLoraTrainingEngine,
    private val artifactStore: TrainingArtifactStore
) {

    suspend fun run(
        baseModel: File,
        dataset: File,
        outputDirectory: File,
        spec: LoraTrainingSpec,
        consentGranted: Boolean,
        adapterVersion: String
    ): Result {
        when (
            val preflight = TrainingPreflight.check(
                consentGranted = consentGranted,
                baseModel = baseModel,
                dataset = dataset,
                outputDirectory = outputDirectory,
                spec = spec
            )
        ) {
            TrainingPreflight.Result.READY -> Unit
            TrainingPreflight.Result.NOT_CONSENTED ->
                return Result.Rejected("Training consent is required.")
            TrainingPreflight.Result.INVALID_BASE_MODEL ->
                return Result.Rejected("Base model is missing or empty.")
            TrainingPreflight.Result.INVALID_DATASET ->
                return Result.Rejected("Training dataset is missing or empty.")
            TrainingPreflight.Result.INVALID_OUTPUT_DIRECTORY ->
                return Result.Rejected("Training output path is not a directory.")
            TrainingPreflight.Result.OUTPUT_DIRECTORY_UNAVAILABLE ->
                return Result.Rejected("Training output directory is unavailable.")
        }

        return when (
            val training = trainingEngine.train(
                baseModel = baseModel,
                dataset = dataset,
                outputDirectory = outputDirectory,
                spec = spec,
                consentGranted = consentGranted
            )
        ) {
            is TrainingResult.Rejected -> Result.Rejected(training.reason)
            is TrainingResult.Failed -> Result.Failed(training.reason, training.cause)
            is TrainingResult.Success -> {
                when (TrainingArtifactValidator.validate(training.adapterFile, outputDirectory)) {
                    TrainingArtifactValidator.Result.VALID -> Unit
                    TrainingArtifactValidator.Result.INVALID_OUTPUT_DIRECTORY ->
                        return Result.Failed("Training output directory became invalid.")
                    TrainingArtifactValidator.Result.INVALID_ADAPTER ->
                        return Result.Failed("Training backend returned an invalid adapter artifact.")
                    TrainingArtifactValidator.Result.OUTSIDE_OUTPUT_DIRECTORY ->
                        return Result.Failed("Training backend returned an adapter outside its output directory.")
                }

                val published = runCatching {
                    artifactStore.publish(training.adapterFile, adapterVersion)
                }.getOrElse { error ->
                    return Result.Failed("Candidate adapter publish failed.", error)
                }

                Result.Success(
                    adapterFile = published,
                    adapterVersion = training.adapterVersion
                )
            }
        }
    }

    sealed interface Result {
        data class Success(
            val adapterFile: File,
            val adapterVersion: String
        ) : Result

        data class Rejected(
            val reason: String
        ) : Result

        data class Failed(
            val reason: String,
            val cause: Throwable? = null
        ) : Result
    }
}
