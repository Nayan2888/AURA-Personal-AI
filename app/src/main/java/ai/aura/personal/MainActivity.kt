package ai.aura.personal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ai.aura.personal.core.chat.ChatMessage
import ai.aura.personal.core.chat.ChatSession

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuraRoot() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuraRoot() {
    var session by remember { mutableStateOf(ChatSession(id = "main-session")) }
    var draft by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }

    fun appendUserMessage(content: String) {
        val cleanContent = content.trim()
        if (cleanContent.isNotEmpty()) {
            val message = ChatMessage(
                id = "message-${session.state.messages.size + 1}",
                role = ChatMessage.Role.USER,
                content = cleanContent,
                createdAtEpochMs = System.currentTimeMillis()
            )
            session = session.appendMessage(message)
            draft = ""
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("AURA", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Learn • Assist • Evolve",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { menuExpanded = true }) {
                            Text("⋮", style = MaterialTheme.typography.headlineSmall)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New chat") },
                                onClick = {
                                    session = ChatSession(id = "session-${System.currentTimeMillis()}")
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear conversation") },
                                onClick = {
                                    session = ChatSession(id = session.id)
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Session info") },
                                onClick = { menuExpanded = false }
                            )
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f),
                            minLines = 1,
                            maxLines = 4,
                            placeholder = { Text("Message AURA…") }
                        )
                        Button(
                            onClick = { appendUserMessage(draft) },
                            enabled = draft.isNotBlank(),
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Text("➤")
                        }
                    }
                }
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colorScheme.background
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "AURA is ready",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                                Text(
                                    "Your conversation workspace. Real model, research, tools and learning will connect through this interface.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (session.state.messages.isEmpty()) {
                        item {
                            Text(
                                "What would you like to do?",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(onClick = { appendUserMessage("मुझे कुछ नया सिखाओ") }) {
                                    Text("📚 Learn")
                                }
                                TextButton(onClick = { appendUserMessage("मेरे लिए एक काम की योजना बनाओ") }) {
                                    Text("🔧 Plan a task")
                                }
                                TextButton(onClick = { appendUserMessage("वेब पर कुछ खोजो") }) {
                                    Text("🌐 Research")
                                }
                                TextButton(onClick = { appendUserMessage("मेरी saved memory दिखाओ") }) {
                                    Text("🧠 Memory")
                                }
                            }
                        }
                    }

                    items(session.state.messages, key = { it.id }) { message ->
                        val isUser = message.role == ChatMessage.Role.USER
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(0.88f),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isUser) {
                                        Color(0xFF304FFE)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        if (isUser) "YOU" else "AURA",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isUser) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(message.content)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
