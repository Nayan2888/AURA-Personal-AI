package ai.aura.personal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val workTemplates = linkedMapOf(
    "Plan a task" to "Make a practical plan for this task. List assumptions, steps, and how I can verify the result.",
    "Draft a script" to "Draft a Hindi video script with a hook, narration, captions, and visual suggestions.",
    "Review an idea" to "Review this idea. Identify gaps, tradeoffs, and one concrete next step."
)

@Composable
internal fun WorkScreen(onPrepare: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(workTemplates.keys.first()) }
    var details by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Work with AURA", style = MaterialTheme.typography.headlineSmall)
            Text("Prepare a task brief, then send it to the model selected in Chat.")
        }
        item {
            Column {
                workTemplates.keys.forEach { name ->
                    FilterChip(selected = selected == name, onClick = { selected = name }, label = { Text(name) })
                }
            }
        }
        item {
            OutlinedTextField(details, { details = it }, label = { Text("What are you working on?") },
                modifier = Modifier.fillMaxWidth(), minLines = 4)
        }
        item {
            Button(enabled = details.isNotBlank(), onClick = {
                onPrepare(workTemplates.getValue(selected) + "\n\n" + details.trim())
            }) { Text("Open draft in Chat") }
        }
    }
}
