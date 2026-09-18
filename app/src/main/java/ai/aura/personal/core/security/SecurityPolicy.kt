package ai.aura.personal.core.security

/**
 * Risk classification for an operation requested by the assistant.
 *
 * This enum is intentionally conservative: callers must explicitly classify
 * an operation before it can be authorized.
 */
enum class ActionRisk {
    SAFE,
    REVIEW,
    SENSITIVE,
    BLOCKED
}

data class AuthorizationRequest(
    val risk: ActionRisk,
    val userConfirmedImmediatelyBeforeExecution: Boolean = false
)

enum class AuthorizationDecision {
    ALLOWED,
    CONFIRMATION_REQUIRED,
    DENIED;

    val isAllowed: Boolean
        get() = this == ALLOWED
}

/**
 * Pure, deterministic authorization gate.
 *
 * No model output can grant permission. Higher-risk operations require an
 * explicit confirmation flag, while BLOCKED operations can never be allowed.
 */
object SecurityPolicy {
    fun decide(request: AuthorizationRequest): AuthorizationDecision =
        when (request.risk) {
            ActionRisk.SAFE -> AuthorizationDecision.ALLOWED
            ActionRisk.REVIEW,
            ActionRisk.SENSITIVE ->
                if (request.userConfirmedImmediatelyBeforeExecution) {
                    AuthorizationDecision.ALLOWED
                } else {
                    AuthorizationDecision.CONFIRMATION_REQUIRED
                }

            ActionRisk.BLOCKED -> AuthorizationDecision.DENIED
        }
}
