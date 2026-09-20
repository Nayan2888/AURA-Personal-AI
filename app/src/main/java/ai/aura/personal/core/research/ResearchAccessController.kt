package ai.aura.personal.core.research

/**
 * Single policy boundary consulted before any network-backed research call.
 */
interface ResearchAccessController {
    fun isResearchAllowed(): Boolean
}
