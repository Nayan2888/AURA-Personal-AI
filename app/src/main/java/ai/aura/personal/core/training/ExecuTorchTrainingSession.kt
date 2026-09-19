package ai.aura.personal.core.training

import java.io.File
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Tensor
import org.pytorch.executorch.training.SGD
import org.pytorch.executorch.training.TrainingModule

/**
 * Thin Android runtime bridge for ExecuTorch's real training API.
 *
 * The supplied PTE must be a training-exported module with a joint forward/backward
 * graph and mutable trainable parameters. This class does not convert an inference
 * model into a trainable model and does not manufacture gradients.
 */
class ExecuTorchTrainingSession(
    private val trainingModelFile: File
) : AutoCloseable {

    private var module: TrainingModule? = null
    private var optimizer: SGD? = null

    fun initialize(spec: LoraTrainingSpec) {
        check(trainingModelFile.isFile) {
            "ExecuTorch training model does not exist: ${trainingModelFile.absolutePath}"
        }
        check(trainingModelFile.length() > 0L) {
            "ExecuTorch training model is empty: ${trainingModelFile.absolutePath}"
        }

        if (module != null) return

        val loaded = TrainingModule.load(trainingModelFile.absolutePath)
        try {
            val parameters = loaded.namedParameters("forward")
            check(parameters.isNotEmpty()) {
                "Training module exposes no named trainable parameters."
            }
            optimizer = SGD.create(parameters, spec.learningRate)
            module = loaded
        } catch (error: Throwable) {
            runCatching { loaded.close() }
            throw error
        }
    }

    fun trainStep(
        inputs: EValue,
        methodName: String = "forward"
    ): Float {
        val activeModule = checkNotNull(module) {
            "Training session is not initialized."
        }
        val activeOptimizer = checkNotNull(optimizer) {
            "Training optimizer is not initialized."
        }

        val outputs = activeModule.executeForwardBackward(methodName, inputs)
        check(outputs.isNotEmpty()) {
            "Training module returned no outputs."
        }

        val lossTensor = outputs.first().toTensor()
        val lossValues = when (lossTensor.numel()) {
            1L -> lossTensor.getDataAsFloatArray()
            else -> throw IllegalStateException(
                "Training loss must be a scalar tensor."
            )
        }
        check(lossValues.size == 1) {
            "Training loss must contain exactly one value."
        }

        val gradients = activeModule.namedGradients(methodName)
        check(gradients.isNotEmpty()) {
            "Training module produced no named gradients."
        }

        activeOptimizer.step(gradients)
        return lossValues[0]
    }

    fun namedParameters(methodName: String = "forward"): Map<String, Tensor> =
        checkNotNull(module) { "Training session is not initialized." }
            .namedParameters(methodName)

    fun namedGradients(methodName: String = "forward"): Map<String, Tensor> =
        checkNotNull(module) { "Training session is not initialized." }
            .namedGradients(methodName)

    override fun close() {
        optimizer = null
        module?.close()
        module = null
    }
}
