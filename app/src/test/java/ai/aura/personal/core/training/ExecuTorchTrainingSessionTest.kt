package ai.aura.personal.core.training

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExecuTorchTrainingSessionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun initializeRejectsMissingTrainingModelBeforeNativeLoad() {
        val missing = File(temporaryFolder.root, "missing.pte")
        val session = ExecuTorchTrainingSession(missing)

        val error = runCatching { session.initialize(LoraTrainingSpec()) }.exceptionOrNull()

        assertEquals(
            "ExecuTorch training model does not exist: ${missing.absolutePath}",
            error?.message
        )
    }
}
