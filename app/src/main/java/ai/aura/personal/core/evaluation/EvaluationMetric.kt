package ai.aura.personal.core.evaluation

interface EvaluationMetric {
    /**
     * Returns an error value where lower is better.
     */
    fun error(expected: String, actual: String): Double
}
