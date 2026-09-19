package ai.aura.personal.core.training

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TrainingPreflightTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun consentIsRequired() {
        val base = temporaryFolder.newFile("model.litertlm").apply { writeText("model") }
        val dataset = temporaryFolder.newFile("data.jsonl").apply { writeText("{"input":"a","target":"b"}") }
        val output = File(temporaryFolder.root, "out")

        assertEquals(
            TrainingPreflight.Result.NOT_CONSENTED,
            TrainingPreflight.check(false, base, dataset, output, LoraTrainingSpec())
        )
    }

    @Test
    fun validInputsAreReady() {
        val base = temporaryFolder.newFile("model.litertlm").apply { writeText("model") }
        val dataset = temporaryFolder.newFile("data.jsonl").apply { writeText("{"input":"a","target":"b"}") }
        val output = File(temporaryFolder.root, "out")

        assertEquals(
            TrainingPreflight.Result.READY,
            TrainingPreflight.check(true, base, dataset, output, LoraTrainingSpec())
        )
    }

    @Test
    fun missingDatasetIsRejected() {
        val base = temporaryFolder.newFile("model.litertlm").apply { writeText("model") }
        val dataset = File(temporaryFolder.root, "missing.jsonl")
        val output = File(temporaryFolder.root, "out")

        assertEquals(
            TrainingPreflight.Result.INVALID_DATASET,
            TrainingPreflight.check(true, base, dataset, output, LoraTrainingSpec())
        )
    }
}
