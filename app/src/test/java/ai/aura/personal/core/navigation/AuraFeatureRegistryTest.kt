package ai.aura.personal.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraFeatureRegistryTest {
    @Test
    fun every_destination_has_exactly_one_contract() {
        assertEquals(AuraDestination.entries.size, AuraFeatureRegistry.contracts.size)
        assertEquals(
            AuraDestination.entries.toSet(),
            AuraFeatureRegistry.contracts.map { it.destination }.toSet()
        )
    }

    @Test
    fun unfinished_features_are_not_reported_as_implemented() {
        assertTrue(
            AuraFeatureRegistry.contractFor(AuraDestination.TOOLS).status != AuraFeatureStatus.IMPLEMENTED
        )
        assertTrue(
            AuraFeatureRegistry.contractFor(AuraDestination.SKILLS).status != AuraFeatureStatus.IMPLEMENTED
        )
        assertTrue(
            AuraFeatureRegistry.contractFor(AuraDestination.SETTINGS).status != AuraFeatureStatus.IMPLEMENTED
        )
    }
}
