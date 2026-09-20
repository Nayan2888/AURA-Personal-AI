package ai.aura.personal.core.research

/**
 * Conservative, deterministic trigger for when a local answer should be
 * supplemented with network research.
 *
 * This is intentionally not presented as model confidence. It only detects
 * explicit freshness requests or common uncertainty phrases.
 */
object ResearchTrigger {

    private val freshnessMarkers = listOf(
        "latest",
        "today",
        "current",
        "currently",
        "right now",
        "recent",
        "recently",
        "this week",
        "this month",
        "now",
        "आज",
        "अभी",
        "वर्तमान",
        "ताज़ा",
        "ताजा",
        "हालिया",
        "हाल ही में"
    )

    private val uncertaintyMarkers = listOf(
        "i don't know",
        "i do not know",
        "not sure",
        "i'm not sure",
        "cannot answer",
        "can't answer",
        "unknown",
        "i don't have enough information",
        "मुझे नहीं पता",
        "मुझे पता नहीं",
        "पता नहीं",
        "पता नही",
        "निश्चित नहीं",
        "यकीन नहीं",
        "मालूम नहीं",
        "जानकारी नहीं है"
    )

    fun shouldResearch(
        userInput: String,
        localAnswer: String
    ): Boolean {
        val input = userInput.trim().lowercase()
        val answer = localAnswer.trim().lowercase()
        if (input.isEmpty() || answer.isEmpty()) return false

        return freshnessMarkers.any(input::contains) ||
            uncertaintyMarkers.any(answer::contains)
    }
}
