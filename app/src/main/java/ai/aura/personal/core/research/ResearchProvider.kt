package ai.aura.personal.core.research

/**
 * Network-backed research boundary.
 *
 * Implementations must honor the caller's network-access policy and return
 * source metadata alongside any extracted text.
 */
interface ResearchProvider {
    suspend fun search(
        query: String,
        maxResults: Int = DEFAULT_MAX_RESULTS
    ): ResearchResponse

    companion object {
        const val DEFAULT_MAX_RESULTS = 5
        const val MAX_RESULTS = 10
    }
}

data class ResearchResponse(
    val query: String,
    val sources: List<ResearchSource>
) {
    init {
        require(query.isNotBlank()) { "Research query must not be blank" }
        require(sources.size <= ResearchProvider.MAX_RESULTS) {
            "Research response exceeds the maximum result count"
        }
    }
}

data class ResearchSource(
    val title: String,
    val url: String,
    val excerpt: String,
    val provider: String
) {
    init {
        require(title.isNotBlank()) { "Research source title must not be blank" }
        require(url.startsWith("https://")) {
            "Research source URL must use HTTPS"
        }
        require(excerpt.isNotBlank()) { "Research source excerpt must not be blank" }
        require(provider.isNotBlank()) { "Research source provider must not be blank" }
    }
}
