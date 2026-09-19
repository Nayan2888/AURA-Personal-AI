package ai.aura.personal.core.evaluation

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenEditDistanceMetricTest {
    private val metric = TokenEditDistanceMetric()

    @Test
    fun identicalTextHasZeroError() {
        assertEquals(0.0, metric.error("hello world", "hello world"), 0.0)
    }

    @Test
    fun oneSubstitutionHasExpectedNormalizedError() {
        assertEquals(0.5, metric.error("hello world", "hello aura"), 0.0)
    }

    @Test
    fun whitespaceIsNormalized() {
        assertEquals(0.0, metric.error("hello   world", "hello world"), 0.0)
    }
}
