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
    private var activeSpec: LoraTrainingSpec? = null

    @Synchronized
    fun initialize(spec: LoraTrainingSpec) {
        check(trainingModelFile.isFile) {
            "ExecuTorch training model does not exist: ${trainingModelFile.absolutePath}"
        }
        check(trainingModelFile.length() > 0L) {
            "ExecuTorch training model is empty: ${trainingModelFile.absolutePath}"
        }

        module?.let {
            check(activeSpec == spec) {
                "Training session is already initialized with a different specification."
            }
            return
        }

        val loaded = TrainingModule.load(trainingModelFile.absolutePath)
        try {
            val parameters = loaded.namedParameters("forward")
            check(parameters.isNotEmpty()) {
                "Training module exposes no named trainable parameters."
            }
            optimizer = SGD.create(parameters, spec.learningRate)
            module = loaded
            activeSpec = spec
        } catch (error: Throwable) {
            runCatching { loaded.close() }
            throw error
        }
    }

    /**
     * Runs one forward/backward training step and applies one optimizer update.
     * LLM fine-tuning normally supplies two inputs: token IDs and labels.
     */
    @Synchronized
    fun trainStep(
        vararg inputs: EValue,
        methodName: String = "forward"
    ): Float {
        require(inputs.isNotEmpty()) {
            "Training step requires at least one input."
        }

        val activeModule = checkNotNull(module) {
            "Training session is not initialized."
        }
        val activeOptimizer = checkNotNull(optimizer) {
            "Training optimizer is not initialized."
        }

        val outputs = activeModule.executeForwardBackward(methodName, *inputs)
        check(outputs.isNotEmpty()) {
            "Training module returned no outputs."
        }

        val lossTensor = outputs.first().toTensor()
        check(lossTensor.numel() == 1L) {
            "Training loss must be a scalar tensor."
        }

        val lossValues = lossTensor.dataAsFloatArray
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

    @Synchronized
    fun namedParameters(methodName: String = "forward"): Map<String, Tensor> =
        checkNotNull(module) { "Training session is not initialized." }
            .namedParameters(methodName)

    @Synchronized
    fun namedGradients(methodName: String = "forward"): Map<String, Tensor> =
        checkNotNull(module) { "Training session is not initialized." }
            .namedGradients(methodName)

    @Synchronized
    override fun close() {
        optimizer = null
        module?.close()
        module = null
        activeSpec = null
    }
}
