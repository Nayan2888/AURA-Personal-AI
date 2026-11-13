package ai.aura.personal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ai.aura.personal.core.providers.ProviderPreset
import ai.aura.personal.core.providers.ProviderProfile
import ai.aura.personal.core.providers.ProviderProtocol
import ai.aura.personal.core.inference.DiscoveredModel
import ai.aura.personal.core.inference.localModelCatalog
import java.io.File
import java.util.UUID

@Composable
internal fun ModelSettingsScreen(
    models: List<File>,
    loaded: File?,
    providers: List<ProviderProfile>,
    activeProvider: ProviderProfile?,
    busy: Boolean,
    modelStatus: String?,
    apiStatus: String?,
    availableModels: Map<String, List<String>>,
    discovered: List<DiscoveredModel>,
    onChooseFolder: () -> Unit,
    onImportDiscovered: (DiscoveredModel) -> Unit,
    onChatVisibility: () -> Unit,
    onTestReply: (ProviderProfile) -> Unit,
    onImport: () -> Unit,
    onCancelTransfer: (() -> Unit)?,
    onDownload: (String, String, String?) -> Unit,
    onLoad: (File) -> Unit,
    onUnload: () -> Unit,
    onSaveProvider: (ProviderProfile, String?) -> Unit,
    onRemoveProvider: (ProviderProfile) -> Unit,
    onListModels: (ProviderProfile) -> Unit,
    onUseProvider: (ProviderProfile?) -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var download by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProviderProfile?>(null) }
    Column(Modifier.padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { tab = 0 }) { Text(if (tab == 0) "● Model library" else "Model library") }
            TextButton(onClick = { tab = 1 }) { Text(if (tab == 1) "● API providers" else "API providers") }
        }
        TextButton(onClick = onChatVisibility) { Text("Choose models shown in chat") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tab == 0) {
                item {
                    Text("On-device models", style = MaterialTheme.typography.titleLarge)
                    Text("Installed models appear automatically. Choose a folder to find .litertlm files on your phone.")
                    Row {
                        TextButton(onClick = onImport, enabled = !busy) { Text("Import file") }
                        TextButton(onClick = onChooseFolder, enabled = !busy) { Text("Scan folder") }
                        TextButton(onClick = { download = true }, enabled = !busy) { Text("Direct URL") }
                    }
                    modelStatus?.let { Text(it) }
                    if (onCancelTransfer != null) TextButton(onClick = onCancelTransfer) { Text("Cancel transfer") }
                    if (models.isEmpty()) Text("No .litertlm files installed.")
                }
                items(discovered, key = { it.uri.toString() }) { file ->
                    val installed = models.any { it.name.equals(file.name, ignoreCase = true) }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(file.name)
                            Text(if (installed) "Same filename already installed" else "Found on device")
                            TextButton(onClick = { onImportDiscovered(file) }, enabled = !busy && !installed) { Text("Import") }
                        }
                    }
                }
                item { Text("Download catalog", style = MaterialTheme.typography.titleMedium) }
                items(localModelCatalog, key = { it.file }) { model ->
                    val installed = models.any { it.name == model.file }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(model.title)
                            val size = android.text.format.Formatter.formatFileSize(androidx.compose.ui.platform.LocalContext.current, model.bytes)
                            Text("LiteRT Community · " + size + " · SHA-256 checked during download")
                            TextButton(onClick = { onDownload(model.url, model.file, model.sha256) }, enabled = !busy && !installed) {
                                Text(if (installed) "Installed" else "Download")
                            }
                        }
                    }
                }
                items(models, key = { it.path }) { model ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(model.name, style = MaterialTheme.typography.titleMedium)
                            Text(android.text.format.Formatter.formatFileSize(androidx.compose.ui.platform.LocalContext.current, model.length()) +
                                if (model == loaded) " · Loaded" else " · Installed")
                            TextButton(onClick = { if (model == loaded) onUnload() else onLoad(model) }, enabled = !busy) {
                                Text(if (model == loaded) "Unload" else "Load on device")
                            }
                        }
                    }
                }
            } else {
                item {
                    Text("API management", style = MaterialTheme.typography.titleLarge)
                    Text("Chat starts on-device. Selecting an API sends subsequent chat text and conversation history to that provider for this app session.")
                    Text(activeProvider?.let { "Chat route · ${it.name} / ${it.model}" } ?: "Chat route · On-device")
                    Row {
                        TextButton(onClick = { editing = null; edit = true }, enabled = !busy) { Text("Add API") }
                        TextButton(onClick = { onUseProvider(null) }, enabled = !busy && activeProvider != null) { Text("Use on-device") }
                    }
                    apiStatus?.let { Text(it) }
                    if (providers.isEmpty()) Text("No API profiles configured.")
                }
                items(providers, key = { it.id }) { provider ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(provider.name, style = MaterialTheme.typography.titleMedium)
                            Text(provider.baseUrl)
                            Text(provider.model.ifBlank { "No chat model selected" })
                            availableModels[provider.id]?.let { Text("Last fetch · " + it.size + " model IDs") }
                            Row {
                                TextButton(onClick = { editing = provider; edit = true }, enabled = !busy) { Text("Edit") }
                                TextButton(onClick = { onListModels(provider) }, enabled = !busy) { Text("Fetch models") }
                                TextButton(onClick = { onRemoveProvider(provider) }, enabled = !busy) { Text("Remove") }
                            }
                            TextButton(onClick = { onTestReply(provider) }, enabled = !busy && provider.model.isNotBlank()) {
                                Text("Test reply")
                            }
                            TextButton(onClick = { onUseProvider(provider) }, enabled = !busy && provider.model.isNotBlank()) {
                                Text(if (activeProvider?.id == provider.id) "Select for a new chat" else "Use for chat")
                            }
                        }
                    }
                }
            }
        }
    }
    if (download) {
        var url by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var checksum by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { download = false }, title = { Text("Download LiteRT-LM model") },
            text = {
                Column {
                    OutlinedTextField(url, { url = it }, label = { Text("Direct HTTPS URL") }, singleLine = true)
                    OutlinedTextField(name, { name = it }, label = { Text("File name ending in .litertlm") }, singleLine = true)
                    OutlinedTextField(checksum, { checksum = it }, label = { Text("Expected SHA-256 (optional)") }, singleLine = true)
                }
            }, confirmButton = {
                TextButton(onClick = {
                    download = false
                    onDownload(url.trim(), name.trim(), checksum.trim().ifBlank { null })
                }, enabled = url.isNotBlank() && name.isNotBlank()) { Text("Download") }
            }, dismissButton = { TextButton(onClick = { download = false }) { Text("Cancel") } })
    }
    if (edit) ProviderEditor(editing, availableModels[editing?.id].orEmpty(), { edit = false }) { profile, key ->
        edit = false
        onSaveProvider(profile, key)
    }
}

@Composable
private fun ProviderEditor(
    existing: ProviderProfile?,
    models: List<String>,
    onClose: () -> Unit,
    onSave: (ProviderProfile, String?) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var base by remember { mutableStateOf(existing?.baseUrl.orEmpty()) }
    var protocol by remember { mutableStateOf(existing?.protocol ?: ProviderProtocol.OPENAI) }
    var model by remember { mutableStateOf(existing?.model.orEmpty()) }
    var key by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("API profile") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    Text("Provider presets")
                    ProviderPreset.entries.chunked(2).forEach { presets ->
                        Row {
                            presets.forEach { preset ->
                                TextButton(onClick = {
                                    name = preset.label
                                    base = preset.baseUrl
                                    protocol = preset.protocol
                                    model = ""
                                    key = ""
                                }) { Text(preset.label) }
                            }
                        }
                    }
                    TextButton(onClick = {
                        protocol = if (protocol == ProviderProtocol.OPENAI) ProviderProtocol.CLAUDE else ProviderProtocol.OPENAI
                    }) { Text(protocol.label) }
                    OutlinedTextField(name, { name = it }, label = { Text("Profile name") }, singleLine = true)
                    OutlinedTextField(base, { base = it }, label = { Text("HTTPS API base URL") }, singleLine = true)
                    OutlinedTextField(key, { key = it }, label = { Text(if (existing == null) "API key" else "New key (optional)") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    OutlinedTextField(model, { model = it }, label = { Text("Chat model ID") }, singleLine = true)
                    if (models.isNotEmpty()) Text("Models returned by provider")
                }
                items(models) { id -> TextButton(onClick = { model = id }) { Text(id) } }
            }
        }, confirmButton = {
            TextButton(onClick = {
                onSave(ProviderProfile(existing?.id ?: UUID.randomUUID().toString(), name.trim(),
                    base.trim().trimEnd('/'), protocol, model.trim()), key.trim().ifBlank { null })
            }, enabled = name.isNotBlank() && base.isNotBlank() && (existing != null || key.isNotBlank())) { Text("Save") }
        }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}
