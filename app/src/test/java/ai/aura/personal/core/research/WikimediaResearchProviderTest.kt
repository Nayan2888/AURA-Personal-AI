package ai.aura.personal.core.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WikimediaResearchProviderTest {

    @Test
    fun networkResearchIsBlockedWithoutAccessPermission() {
        val provider = WikimediaResearchProvider(
            accessController = object : ResearchAccessController {
                override fun isResearchAllowed(): Boolean = false
            }
        )

        val error = runCatching {
            kotlinx.coroutines.runBlocking {
                provider.search("AURA")
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(
            "Network research is disabled or no usable network is available.",
            error?.message
        )
    }

    @Test
    fun searchUrlUsesHttpsAndAllowlistedHostAndEncodesQuery() {
        val provider = WikimediaResearchProvider(
            accessController = object : ResearchAccessController {
                override fun isResearchAllowed(): Boolean = true
            }
        )

        val url = provider.buildSearchUrl(
            host = "hi.wikipedia.org",
            query = "अंतरिक्ष यान",
            limit = 5
        )

        assertTrue(url.startsWith("https://hi.wikipedia.org/w/api.php"))
        assertTrue(url.contains("action=opensearch"))
        assertTrue(url.contains("search="))
        assertFalse(url.contains(" "))
    }

    @Test
    fun unsupportedHostIsRejected() {
        val error = runCatching {
            WikimediaResearchProvider(
                accessController = object : ResearchAccessController {
                    override fun isResearchAllowed(): Boolean = true
                },
                wikiHost = "example.com"
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
