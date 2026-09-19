package ai.aura.personal.core.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TrainingArtifactStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun publishCreatesCandidateWithoutChangingSource() {
        val source = temporaryFolder.newFile("adapter.tmp").apply { writeText("adapter-bytes") }
        val store = TrainingArtifactStore(temporaryFolder.root)

        val published = store.publish(source, "v1")

        assertTrue(published.isFile)
        assertEquals("adapter-bytes", published.readText())
        assertEquals("adapter-bytes", source.readText())
    }

    @Test
    fun pathSeparatorsAreRejected() {
        val source = temporaryFolder.newFile("adapter.tmp").apply { writeText("adapter-bytes") }
        val store = TrainingArtifactStore(temporaryFolder.root)

        val error = runCatching { store.publish(source, "../v1") }.exceptionOrNull()

        assertEquals("Adapter version must not contain path separators", error?.message)
    }
}
