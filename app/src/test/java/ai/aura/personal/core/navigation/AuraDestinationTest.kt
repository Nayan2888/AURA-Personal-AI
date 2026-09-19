package ai.aura.personal.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AuraDestinationTest {
    @Test
    fun knownRoutesResolveToTheirDestination() {
        AuraDestination.entries.forEach { destination ->
            assertEquals(destination, auraDestinationFromRoute(destination.route))
        }
    }

    @Test
    fun unknownOrNullRoutesSafelyFallbackToChat() {
        assertEquals(AuraDestination.CHAT, auraDestinationFromRoute(null))
        assertEquals(AuraDestination.CHAT, auraDestinationFromRoute("unknown"))
    }
}
