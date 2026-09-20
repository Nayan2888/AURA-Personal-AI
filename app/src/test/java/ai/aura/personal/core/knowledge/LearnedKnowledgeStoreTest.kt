package ai.aura.personal.core.knowledge

import ai.aura.personal.core.research.ResearchSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LearnedKnowledgeStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun knowledgeIsNotStoredWithoutLearningConsent() {
        val store = LearnedKnowledgeStore(temporaryFolder.root)

        val error = runCatching {
            store.remember(
                query = "AURA",
                sources = listOf(source("AURA overview")),
                consentGranted = false,
                learnedAtEpochMs = 1L
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun storedKnowledgeCanBeRetrievedOfflineByRelevantTokens() {
        val store = LearnedKnowledgeStore(temporaryFolder.root)
        store.remember(
            query = "James Webb Space Telescope",
            sources = listOf(
                source(
                    title = "James Webb Space Telescope",
                    excerpt = "JWST is a space telescope designed to observe infrared astronomy."
                )
            ),
            consentGranted = true,
            learnedAtEpochMs = 10L
        )

        val results = store.search("JWST infrared telescope")

        assertEquals(1, results.size)
        assertEquals("James Webb Space Telescope", results.single().title)
    }

    @Test
    fun duplicateKnowledgeIsReplacedInsteadOfGrowingStore() {
        val store = LearnedKnowledgeStore(temporaryFolder.root)
        val first = source("AURA", "First excerpt")
        val second = source("AURA", "First excerpt")

        assertEquals(
            1,
            store.remember("AURA", listOf(first), true, 10L)
        )
        assertEquals(
            0,
            store.remember("AURA", listOf(second), true, 20L)
        )
        assertEquals(1, store.list().size)
        assertEquals(20L, store.list().single().learnedAtEpochMs)
    }

    @Test
    fun storedKnowledgeIsBounded() {
        val store = LearnedKnowledgeStore(temporaryFolder.root)
        val sources = (1..10).map { index ->
            source(
                title = "Topic " + index,
                excerpt = "Unique research excerpt " + index
            )
        }

        store.remember("research", sources, true, 1L)

        assertTrue(store.list().size <= 10)
    }

    private fun source(
        title: String,
        excerpt: String = "Useful evidence about AURA."
    ) = ResearchSource(
        title = title,
        url = "https://example.org/" + title.lowercase().replace(" ", "-"),
        excerpt = excerpt,
        provider = "Test"
    )
}
