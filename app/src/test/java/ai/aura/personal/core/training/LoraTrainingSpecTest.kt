package ai.aura.personal.core.training

import org.junit.Assert.assertEquals
import org.junit.Test

class LoraTrainingSpecTest {
    @Test
    fun defaultSpecIsValid() {
        val spec = LoraTrainingSpec()
        assertEquals(8, spec.rank)
        assertEquals(16, spec.alpha)
        assertEquals(1, spec.epochs)
    }

    @Test
    fun invalidSpecIsRejected() {
        val error = runCatching { LoraTrainingSpec(rank = 0) }.exceptionOrNull()
        assertEquals("LoRA rank must be positive", error?.message)
    }
}
