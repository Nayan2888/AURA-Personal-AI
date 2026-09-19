package ai.aura.personal.core.training

/**
 * Backend-neutral configuration for a LoRA adapter training job.
 *
 * This contract intentionally contains no trainer implementation. A concrete
 * backend must prove that it can train the selected base model on-device before
 * it is allowed to execute a job.
 */
data class LoraTrainingSpec(
    val rank: Int = 8,
    val alpha: Int = 16,
    val epochs: Int = 1,
    val learningRate: Double = 2e-4,
    val maxSequenceLength: Int = 1024
) {
    init {
        require(rank > 0) { "LoRA rank must be positive" }
        require(alpha > 0) { "LoRA alpha must be positive" }
        require(epochs > 0) { "Training epochs must be positive" }
        require(learningRate > 0.0) { "Learning rate must be positive" }
        require(maxSequenceLength > 0) { "Max sequence length must be positive" }
    }
}
