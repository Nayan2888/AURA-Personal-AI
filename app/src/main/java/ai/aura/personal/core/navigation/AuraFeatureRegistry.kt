package ai.aura.personal.core.navigation

/**
 * Describes the implementation status of each top-level workspace feature.
 * This registry is deliberately non-UI: it prevents unfinished destinations
 * from being treated as implemented functionality.
 */
enum class AuraFeatureStatus {
    IMPLEMENTED,
    FOUNDATION_ONLY,
    PLANNED
}

data class AuraFeatureContract(
    val destination: AuraDestination,
    val status: AuraFeatureStatus,
    val description: String
)

object AuraFeatureRegistry {
    val contracts: List<AuraFeatureContract> = listOf(
        AuraFeatureContract(AuraDestination.CHAT, AuraFeatureStatus.IMPLEMENTED, "Conversation workspace"),
        AuraFeatureContract(AuraDestination.MEMORY, AuraFeatureStatus.IMPLEMENTED, "Saved conversation history"),
        AuraFeatureContract(AuraDestination.TOOLS, AuraFeatureStatus.FOUNDATION_ONLY, "Tool catalog and execution routing"),
        AuraFeatureContract(AuraDestination.SKILLS, AuraFeatureStatus.FOUNDATION_ONLY, "Skill discovery and learning lifecycle"),
        AuraFeatureContract(AuraDestination.SETTINGS, AuraFeatureStatus.PLANNED, "Application and model configuration")
    )

    fun contractFor(destination: AuraDestination): AuraFeatureContract =
        contracts.first { it.destination == destination }
}
