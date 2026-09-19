package ai.aura.personal.core.training

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TrainingArtifactValidatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun acceptsNonEmptyAdapterInsideOutputDirectory() {
        val output = temporaryFolder.newFolder("out")
        val adapter = output.resolve("adapter.bin").apply { writeText("adapter") }

        assertEquals(
            TrainingArtifactValidator.Result.VALID,
            TrainingArtifactValidator.validate(adapter, output)
        )
    }

    @Test
    fun rejectsEmptyAdapter() {
        val output = temporaryFolder.newFolder("out")
        val adapter = output.resolve("adapter.bin").apply { createNewFile() }

        assertEquals(
            TrainingArtifactValidator.Result.INVALID_ADAPTER,
            TrainingArtifactValidator.validate(adapter, output)
        )
    }

    @Test
    fun rejectsAdapterOutsideOutputDirectory() {
        val output = temporaryFolder.newFolder("out")
        val outside = temporaryFolder.newFile("adapter.bin").apply { writeText("adapter") }

        assertEquals(
            TrainingArtifactValidator.Result.OUTSIDE_OUTPUT_DIRECTORY,
            TrainingArtifactValidator.validate(outside, output)
        )
    }
}
