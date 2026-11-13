package ai.aura.personal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.aura.personal.core.providers.ProviderProfile
import java.io.File

internal data class ChatModel(val title: String, val file: File? = null, val provider: ProviderProfile? = null) {
    val id: String get() = file?.let { "local:" + it.name } ?: "api:" + provider!!.id + ":" + provider.model
    val subtitle: String get() = provider?.let { "Cloud · " + it.name } ?: "On device"
}

internal fun chatModels(models: List<File>, providers: List<ProviderProfile>, fetched: Map<String, List<String>>): List<ChatModel> =
    models.map { ChatModel(it.name, file = it) } + providers.flatMap { provider ->
        (listOf(provider.model) + fetched[provider.id].orEmpty()).filter { it.isNotBlank() }.distinct()
            .map { ChatModel(it, provider = provider.copy(model = it)) }
    }

@Composable
internal fun ChatModelDialog(
    choices: List<ChatModel>, selected: String?, editing: Boolean, hidden: Set<String>,
    onClose: () -> Unit, onSettings: () -> Unit, onChoose: (ChatModel) -> Unit, onToggle: (String) -> Unit
) {
    val visible = if (editing) choices else choices.filterNot { it.id in hidden }
    AlertDialog(onDismissRequest = onClose,
        title = { Text(if (editing) "Models shown in chat" else "Choose a model") },
        text = {
            if (visible.isEmpty()) Text(if (editing) "Install a model or fetch API models first."
                else "No models are visible. Check Settings to install or show models.")
            else LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(visible, key = { it.id }) { model ->
                    if (editing) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = model.id !in hidden, onCheckedChange = { onToggle(model.id) })
                        Column(Modifier.weight(1f)) {
                            Text(model.title)
                            Text(model.subtitle, style = MaterialTheme.typography.labelSmall)
                        }
                    } else TextButton(onClick = { onChoose(model) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(model.title)
                            Text(model.subtitle + if (model.id == selected) " · Selected" else "",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
        dismissButton = if (editing) null else ({ TextButton(onClick = onSettings) { Text("Settings") } }))
}
