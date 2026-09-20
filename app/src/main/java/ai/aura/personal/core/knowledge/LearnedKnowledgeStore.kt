package ai.aura.personal.core.knowledge

import ai.aura.personal.core.research.ResearchSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Device-local persistent store for consented research knowledge.
 *
 * Knowledge is stored with source URL, provider, query and acquisition time so
 * later offline reuse keeps provenance. The store never activates or mutates
 * model weights.
 */
class LearnedKnowledgeStore(
    rootDirectory: File
) {
    private val directory = File(rootDirectory, DIRECTORY_NAME).apply { mkdirs() }
    private val storeFile = File(directory, FILE_NAME)

    fun remember(
        query: String,
        sources: Iterable<ResearchSource>,
        consentGranted: Boolean,
        learnedAtEpochMs: Long
    ): Int {
        require(consentGranted) { "Learning consent is required to store research knowledge" }
        require(learnedAtEpochMs >= 0L) { "Knowledge timestamp must not be negative" }

        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotEmpty()) { "Knowledge query must not be blank" }

        val entries = readEntries().toMutableList()
        var added = 0
        for (source in sources.take(MAX_SOURCES_PER_WRITE)) {
            val excerpt = source.excerpt.trim().take(MAX_EXCERPT_CHARS)
            if (excerpt.isEmpty()) continue

            val id = idFor(normalizedQuery, source, excerpt)
            val entry = LearnedKnowledgeEntry(
                id = id,
                query = normalizedQuery.take(MAX_QUERY_CHARS),
                title = source.title.trim().take(MAX_TITLE_CHARS),
                url = source.url,
                excerpt = excerpt,
                provider = source.provider.trim().take(MAX_PROVIDER_CHARS),
                learnedAtEpochMs = learnedAtEpochMs
            )

            val existingIndex = entries.indexOfFirst { it.id == id }
            if (existingIndex >= 0) {
                entries[existingIndex] = entry
            } else {
                entries += entry
                added += 1
            }
        }

        writeEntries(entries.sortedByDescending { it.learnedAtEpochMs }.take(MAX_ENTRIES))
        return added
    }

    /**
     * Retrieves relevant learned entries using deterministic token overlap.
     * This remains fully offline and requires no embeddings or network access.
     */
    fun search(
        query: String,
        maxResults: Int = DEFAULT_MAX_RESULTS
    ): List<LearnedKnowledgeEntry> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()
        require(maxResults in 1..MAX_RESULTS_PER_SEARCH)

        val queryTokens = tokens(normalizedQuery)
        if (queryTokens.isEmpty()) return emptyList()

        return readEntries()
            .asSequence()
            .map { entry ->
                val searchableTokens = tokens(
                    entry.query + " " + entry.title + " " + entry.excerpt
                )
                val overlap = queryTokens.count { it in searchableTokens }
                entry to overlap
            }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<LearnedKnowledgeEntry, Int>> { it.second }
                    .thenByDescending { it.first.learnedAtEpochMs }
            )
            .take(maxResults)
            .map { it.first }
            .toList()
    }

    fun list(): List<LearnedKnowledgeEntry> =
        readEntries().sortedByDescending { it.learnedAtEpochMs }

    fun deleteAll() {
        if (storeFile.exists()) storeFile.delete()
    }

    private fun readEntries(): List<LearnedKnowledgeEntry> {
        if (!storeFile.isFile) return emptyList()

        return runCatching {
            val root = JSONObject(storeFile.readText(Charsets.UTF_8))
            val array = root.optJSONArray("entries") ?: JSONArray()

            buildList {
                for (index in 0 until array.length()) {
                    toEntryOrNull(array.optJSONObject(index))?.let(::add)
                }
            }.take(MAX_ENTRIES)
        }.getOrElse { emptyList() }
    }

    private fun writeEntries(entries: List<LearnedKnowledgeEntry>) {
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put(
                "entries",
                JSONArray().apply {
                    entries.forEach { put(it.toJson()) }
                }
            )

        val temp = File(directory, FILE_NAME + ".tmp")
        temp.writeText(root.toString(), Charsets.UTF_8)

        try {
            Files.move(
                temp.toPath(),
                storeFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(
                temp.toPath(),
                storeFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun idFor(
        query: String,
        source: ResearchSource,
        excerpt: String
    ): String {
        val payload = query + "\u0000" + source.title + "\u0000" +
            source.url + "\u0000" + excerpt
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun tokens(value: String): Set<String> {
        val token = StringBuilder()
        val result = linkedSetOf<String>()

        fun flush() {
            if (token.length >= MIN_TOKEN_LENGTH) {
                result += token.toString()
            }
            token.setLength(0)
        }

        value.lowercase().forEach { character ->
            if (character.isLetterOrDigit()) {
                token.append(character)
            } else {
                flush()
            }
        }
        flush()
        return result
    }

    private fun toEntryOrNull(item: JSONObject?): LearnedKnowledgeEntry? = runCatching {
        if (item == null) return null

        LearnedKnowledgeEntry(
            id = item.getString("id"),
            query = item.getString("query"),
            title = item.getString("title"),
            url = item.getString("url"),
            excerpt = item.getString("excerpt"),
            provider = item.getString("provider"),
            learnedAtEpochMs = item.getLong("learnedAtEpochMs")
        )
    }.getOrNull()

    private fun LearnedKnowledgeEntry.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("query", query)
            .put("title", title)
            .put("url", url)
            .put("excerpt", excerpt)
            .put("provider", provider)
            .put("learnedAtEpochMs", learnedAtEpochMs)

    data class LearnedKnowledgeEntry(
        val id: String,
        val query: String,
        val title: String,
        val url: String,
        val excerpt: String,
        val provider: String,
        val learnedAtEpochMs: Long
    ) {
        init {
            require(id.matches(SHA256_PATTERN)) {
                "Knowledge id must be a lowercase SHA-256 digest"
            }
            require(query.isNotBlank()) { "Knowledge query must not be blank" }
            require(title.isNotBlank()) { "Knowledge title must not be blank" }
            require(url.startsWith("https://")) { "Knowledge URL must use HTTPS" }
            require(excerpt.isNotBlank()) { "Knowledge excerpt must not be blank" }
            require(provider.isNotBlank()) { "Knowledge provider must not be blank" }
            require(learnedAtEpochMs >= 0L) { "Knowledge timestamp must not be negative" }
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "knowledge"
        const val FILE_NAME = "learned-research-v1.json"
        const val SCHEMA_VERSION = 1
        const val MAX_ENTRIES = 256
        const val MAX_SOURCES_PER_WRITE = 10
        const val MAX_EXCERPT_CHARS = 4_000
        const val MAX_QUERY_CHARS = 500
        const val MAX_TITLE_CHARS = 500
        const val MAX_PROVIDER_CHARS = 100
        const val DEFAULT_MAX_RESULTS = 3
        const val MAX_RESULTS_PER_SEARCH = 10
        const val MIN_TOKEN_LENGTH = 2
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
