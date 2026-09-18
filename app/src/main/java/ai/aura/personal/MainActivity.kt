package ai.aura.personal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.aura.personal.core.chat.ChatMessage
import ai.aura.personal.core.chat.ChatSession

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuraRoot() }
    }
}

@Composable
private fun AuraRoot() {
    var session by remember { mutableStateOf(ChatSession(id = "main-session")) }
    var draft by remember { mutableStateOf("") }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("AURA") }) },
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Message AURA…") }
                    )
                    Button(
                        onClick = {
                            val content = draft.trim()
                            if (content.isNotEmpty()) {
                                val message = ChatMessage(
                                    id = "message-${session.state.messages.size + 1}",
                                    role = ChatMessage.Role.USER,
                                    content = content,
                                    createdAtEpochMs = System.currentTimeMillis()
                                )
                                session = session.appendMessage(message)
                                draft = ""
                            }
                        },
                        enabled = draft.isNotBlank()
                    ) {
                        Text("Send")
                    }
                }
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (session.state.messages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "AURA AI",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Chat session connected. AI response engine will be integrated separately."
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(session.state.messages, key = { it.id }) { message ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = message.role.name,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(text = message.content)
                            }
                        }
                    }
                }
            }
        }
    }
}
