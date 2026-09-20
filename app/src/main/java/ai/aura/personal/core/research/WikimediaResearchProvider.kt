package ai.aura.personal.core.research

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Real HTTPS research provider backed by Wikimedia's public APIs.
 *
 * Search uses MediaWiki OpenSearch; top results are enriched with article
 * summaries from the Wikimedia REST API. Requests are bounded by result count,
 * timeouts and response size.
 */
class WikimediaResearchProvider(
    private val accessController: ResearchAccessController,
    private val wikiHost: String = DEFAULT_WIKI_HOST
) : ResearchProvider {

    init {
        require(ALLOWED_WIKI_HOSTS.contains(wikiHost)) {
            "Unsupported Wikimedia host: " + wikiHost
        }
    }

    override suspend fun search(
        query: String,
        maxResults: Int
    ): ResearchResponse = withContext(Dispatchers.IO) {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotEmpty()) { "Research query must not be blank" }
        require(maxResults in 1..ResearchProvider.MAX_RESULTS) {
            "Research result count must be between 1 and " + ResearchProvider.MAX_RESULTS
        }

        check(accessController.isResearchAllowed()) {
            "Network research is disabled or no usable network is available."
        }

        val searchJson = getHttps(
            buildSearchUrl(
                host = wikiHost,
                query = normalizedQuery,
                limit = maxResults
            )
        )
        val searchItems = parseSearchResults(searchJson)

        val sources = searchItems.take(maxResults).mapNotNull { item ->
            runCatching {
                val summaryJson = getHttps(
                    buildSummaryUrl(
                        host = wikiHost,
                        title = item.title
                    )
                )
                parseSummary(summaryJson, item.title, item.url)
            }.getOrNull()
        }

        ResearchResponse(
            query = normalizedQuery,
            sources = sources
        )
    }

    private fun getHttps(urlString: String): String {
        val url = URL(urlString)
        check(url.protocol == "https") { "Research request must use HTTPS" }
        check(url.host in ALLOWED_WIKI_HOSTS) {
            "Research request host is not allowlisted"
        }

        val connection = (url.openConnection() as? HttpURLConnection)
            ?: throw IllegalStateException("Unable to open research connection")

        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", USER_AGENT)

        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Research provider returned HTTP " + status)
            }
            readBounded(connection.inputStream).toString(StandardCharsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: InputStream): ByteArray {
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0

            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break

                total += count
                check(total <= MAX_RESPONSE_BYTES) {
                    "Research response exceeded the maximum allowed size"
                }
                output.write(buffer, 0, count)
            }

            return output.toByteArray()
        }
    }

    internal fun buildSearchUrl(
        host: String,
        query: String,
        limit: Int
    ): String {
        check(host in ALLOWED_WIKI_HOSTS) { "Unsupported Wikimedia host" }
        require(limit in 1..ResearchProvider.MAX_RESULTS)
        return "https://" + host + "/w/api.php" +
            "?action=opensearch" +
            "&namespace=0" +
            "&limit=" + limit +
            "&format=json" +
            "&search=" + encode(query)
    }

    internal fun buildSummaryUrl(
        host: String,
        title: String
    ): String {
        check(host in ALLOWED_WIKI_HOSTS) { "Unsupported Wikimedia host" }
        val encodedPath = title.trim()
            .split("/")
            .joinToString("/") { encode(it).replace("+", "%20") }
        return "https://" + host + "/api/rest_v1/page/summary/" + encodedPath
    }

    private fun parseSearchResults(json: String): List<SearchItem> {
        val root = JSONArray(json)
        if (root.length() < 4) return emptyList()

        val titles = root.optJSONArray(1) ?: return emptyList()
        val urls = root.optJSONArray(3) ?: return emptyList()

        return buildList {
            val count = minOf(titles.length(), urls.length(), ResearchProvider.MAX_RESULTS)
            for (index in 0 until count) {
                val title = titles.optString(index).trim()
                val url = urls.optString(index).trim()
                if (title.isNotEmpty() && url.startsWith("https://")) {
                    add(SearchItem(title, url))
                }
            }
        }
    }

    private fun parseSummary(
        json: String,
        fallbackTitle: String,
        fallbackUrl: String
    ): ResearchSource {
        val root = JSONObject(json)
        val title = root.optString("title").trim().ifEmpty { fallbackTitle }
        val excerpt = root.optString("extract").trim()
        val url = root.optJSONObject("content_urls")
            ?.optJSONObject("desktop")
            ?.optString("page")
            ?.trim()
            ?.takeIf { it.startsWith("https://") }
            ?: fallbackUrl

        check(excerpt.isNotEmpty()) { "Research summary did not contain an extract" }

        return ResearchSource(
            title = title,
            url = url,
            excerpt = excerpt,
            provider = "Wikimedia"
        )
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value.trim(), StandardCharsets.UTF_8.name())

    private data class SearchItem(
        val title: String,
        val url: String
    )

    private companion object {
        const val DEFAULT_WIKI_HOST = "en.wikipedia.org"
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 12_000
        const val MAX_RESPONSE_BYTES = 1_000_000
        const val BUFFER_SIZE = 16 * 1024
        const val USER_AGENT = "AURA-Personal-AI/0.1"
        val ALLOWED_WIKI_HOSTS = setOf("en.wikipedia.org", "hi.wikipedia.org")
    }
}
