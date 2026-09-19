package ai.aura.personal.core.navigation

/**
 * Stable top-level destinations for the AURA workspace.
 *
 * These destinations intentionally define navigation contracts only. Feature
 * implementations must be connected in their respective milestones and must
 * not be represented as fake or non-functional screens.
 */
enum class AuraDestination(
    val route: String,
    val title: String
) {
    CHAT("chat", "Chat"),
    TOOLS("tools", "Tools"),
    MEMORY("memory", "Memory"),
    SKILLS("skills", "Skills"),
    SETTINGS("settings", "Settings")
}

/**
 * Converts a persisted/received route into a safe destination.
 * Unknown routes default to Chat rather than opening an undefined feature.
 */
fun auraDestinationFromRoute(route: String?): AuraDestination =
    AuraDestination.entries.firstOrNull { it.route == route } ?: AuraDestination.CHAT
