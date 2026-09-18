package ai.aura.personal.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityPolicyTest {
    @Test
    fun safeOperationIsAllowedWithoutConfirmation() {
        val decision = SecurityPolicy.decide(AuthorizationRequest(ActionRisk.SAFE))

        assertEquals(AuthorizationDecision.ALLOWED, decision)
        assertTrue(decision.isAllowed)
    }

    @Test
    fun reviewOperationRequiresImmediateConfirmation() {
        val decision = SecurityPolicy.decide(AuthorizationRequest(ActionRisk.REVIEW))

        assertEquals(AuthorizationDecision.CONFIRMATION_REQUIRED, decision)
    }

    @Test
    fun sensitiveOperationRequiresImmediateConfirmation() {
        val decision = SecurityPolicy.decide(AuthorizationRequest(ActionRisk.SENSITIVE))

        assertEquals(AuthorizationDecision.CONFIRMATION_REQUIRED, decision)
    }

    @Test
    fun confirmedSensitiveOperationIsAllowed() {
        val decision = SecurityPolicy.decide(
            AuthorizationRequest(
                risk = ActionRisk.SENSITIVE,
                userConfirmedImmediatelyBeforeExecution = true
            )
        )

        assertEquals(AuthorizationDecision.ALLOWED, decision)
    }

    @Test
    fun blockedOperationCanNeverBeAllowed() {
        val decision = SecurityPolicy.decide(
            AuthorizationRequest(
                risk = ActionRisk.BLOCKED,
                userConfirmedImmediatelyBeforeExecution = true
            )
        )

        assertEquals(AuthorizationDecision.DENIED, decision)
    }
}
