package ai.aura.personal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.aura.personal.core.navigation.AuraDestination
import ai.aura.personal.core.navigation.AuraFeatureRegistry

/**
 * Truthful workspace status screen for destinations whose full implementation
 * is not connected yet. It must never pretend that planned functionality works.
 */
@Composable
internal fun FeatureStatusScreen(destination: AuraDestination) {
    val contract = AuraFeatureRegistry.contractFor(destination)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(destination.title, style = MaterialTheme.typography.headlineSmall)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Status: ${contract.status}", style = MaterialTheme.typography.titleMedium)
                Text(contract.description)
                Text(
                    "यह workspace अभी foundation चरण में है। वास्तविक feature जोड़ने से पहले उसका backend, security और tests connect किए जाएंगे।"
                )
            }
        }
    }
}
