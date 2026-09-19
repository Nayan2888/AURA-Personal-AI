package ai.aura.personal.core.evaluation

/**
 * Deterministic whitespace-token edit-distance metric.
 *
 * This is an objective text-overlap metric, not a semantic judge and not
 * language-model perplexity. It is used until AURA has a real token-level
 * loss evaluator backed by the training model.
 */
class TokenEditDistanceMetric : EvaluationMetric {
    override fun error(expected: String, actual: String): Double {
        val expectedTokens = normalize(expected)
        val actualTokens = normalize(actual)

        if (expectedTokens.isEmpty() && actualTokens.isEmpty()) return 0.0
        if (expectedTokens.isEmpty()) return actualTokens.size.toDouble()
        if (actualTokens.isEmpty()) return expectedTokens.size.toDouble()

        var previous = IntArray(actualTokens.size + 1) { it }

        for (i in expectedTokens.indices) {
            val current = IntArray(actualTokens.size + 1)
            current[0] = i + 1

            for (j in actualTokens.indices) {
                val substitutionCost =
                    if (expectedTokens[i] == actualTokens[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + substitutionCost
                )
            }
            previous = current
        }

        val distance = previous[actualTokens.size].toDouble()
        return distance / maxOf(expectedTokens.size, actualTokens.size).toDouble()
    }

    private fun normalize(value: String): List<String> =
        value.trim()
            .split(WHITESPACE_REGEX)
            .filter { it.isNotEmpty() }

    companion object {
        private val WHITESPACE_REGEX = Regex("\s+")
    }
}
