package ai.aura.personal

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ai.aura.personal.core.research.ResearchProvider
import ai.aura.personal.core.research.ResearchSource
import kotlinx.coroutines.launch

@Composable
internal fun ResearchScreen(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    provider: ResearchProvider
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var sources by remember { mutableStateOf<List<ResearchSource>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }

    fun search() {
        val normalized = query.trim()
        if (normalized.isEmpty() || searching) return

        searching = true
        status = null
        sources = emptyList()

        scope.launch {
            runCatching {
                provider.search(normalized, ResearchProvider.DEFAULT_MAX_RESULTS)
            }.onSuccess { response ->
                sources = response.sources
                status = if (response.sources.isEmpty()) {
                    "No usable research sources were returned."
                } else {
                    "Found " + response.sources.size + " source(s)."
                }
            }.onFailure { error ->
                status = error.message ?: "Research request failed."
            }
            searching = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Research", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Internet research", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (enabled) {
                                    "Enabled only with an available network. Requests are HTTPS-only."
                                } else {
                                    "Disabled by default. Turn this on before AURA can access the network."
                                }
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = onEnabledChange
                        )
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled && !searching,
                        singleLine = true,
                        label = { Text("Research query") },
                        placeholder = { Text("e.g. James Webb Space Telescope") }
                    )

                    Button(
                        onClick = ::search,
                        enabled = enabled && query.trim().isNotEmpty() && !searching,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (searching) "Researching…" else "Research")
                    }

                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        if (enabled && sources.isEmpty() && status == null) {
            item {
                Text(
                    "Search uses Wikimedia's public research API. The current provider is intentionally bounded to allowlisted Wikipedia hosts.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        items(sources, key = { it.url }) { source ->
            ResearchSourceCard(
                source = source,
                onOpen = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(source.url))
                        )
                    }.onFailure { error ->
                        if (error is ActivityNotFoundException) {
                            status = "No browser is available to open this source."
                        } else {
                            status = error.message ?: "Unable to open source."
                        }
                    }
                }
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun ResearchSourceCard(
    source: ResearchSource,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(source.title, style = MaterialTheme.typography.titleMedium)
            Text(source.excerpt, style = MaterialTheme.typography.bodyMedium)
            Text(
                source.provider + " · " + source.url,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onOpen) {
                Text("Open source")
            }
        }
    }
}
