package ai.aura.personal.core.evaluation

import ai.aura.personal.core.inference.AssistantEngine
import java.io.File

/**
 * Runs the same independent evaluation examples through the real AssistantEngine
 * with the base model and candidate adapter.
 *
 * It measures an explicit objective text error metric. It does not invent safety,
 * compatibility, or approval evidence.
 */
class ModelEvaluationEngine(
    private val assistantEngine: AssistantEngine,
    private val metric: EvaluationMetric = TokenEditDistanceMetric()
) {
    suspend fun evaluate(
        examples: List<EvaluationExample>,
        candidateAdapterFile: File
    ): EvaluationRun {
        require(examples.isNotEmpty()) {
            "Evaluation requires at least one example"
        }
        require(candidateAdapterFile.isFile && candidateAdapterFile.length() > 0L) {
            "Candidate adapter file must be a non-empty file"
        }
        require(assistantEngine.isInitialized()) {
            "Assistant engine is not initialized"
        }

        val results = examples.map { example ->
            val baseOutput = assistantEngine.generate(
                history = emptyList(),
                userInput = example.input,
                loraAdapterFile = null
            )
            val candidateOutput = assistantEngine.generate(
                history = emptyList(),
                userInput = example.input,
                loraAdapterFile = candidateAdapterFile
            )

            SampleResult(
                exampleId = example.id,
                baseError = metric.error(example.expectedOutput, baseOutput),
                candidateError = metric.error(example.expectedOutput, candidateOutput)
            )
        }

        val baseMeanError = results.map { it.baseError }.average()
        val candidateMeanError = results.map { it.candidateError }.average()

        check(baseMeanError.isFinite()) {
            "Base evaluation error is not finite"
        }
        check(candidateMeanError.isFinite()) {
            "Candidate evaluation error is not finite"
        }

        return EvaluationRun(
            evaluatedExampleCount = results.size,
            baseMeanError = baseMeanError,
            candidateMeanError = candidateMeanError,
            samples = results
        )
    }

    data class EvaluationRun(
        val evaluatedExampleCount: Int,
        val baseMeanError: Double,
        val candidateMeanError: Double,
        val samples: List<SampleResult>
    ) {
        init {
            require(evaluatedExampleCount > 0) {
                "Evaluation run must contain at least one example"
            }
            require(samples.size == evaluatedExampleCount) {
                "Evaluation sample count does not match evaluated example count"
            }
            require(baseMeanError.isFinite()) {
                "Base evaluation error must be finite"
            }
            require(candidateMeanError.isFinite()) {
                "Candidate evaluation error must be finite"
            }
        }
    }

    data class SampleResult(
        val exampleId: String,
        val baseError: Double,
        val candidateError: Double
    ) {
        init {
            require(exampleId.isNotBlank()) {
                "Evaluation sample id must not be blank"
            }
            require(baseError.isFinite() && baseError >= 0.0) {
                "Base sample error must be finite and non-negative"
            }
            require(candidateError.isFinite() && candidateError >= 0.0) {
                "Candidate sample error must be finite and non-negative"
            }
        }
    }
}
