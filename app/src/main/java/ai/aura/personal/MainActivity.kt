package ai.aura.personal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ai.aura.personal.core.chat.ChatMessage
import ai.aura.personal.core.chat.ChatSession
import ai.aura.personal.core.history.ChatHistoryStore

private data class SelectedAttachment(
    val uri: Uri,
    val name: String,
    val mimeType: String
)

private fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
    }
    return uri.lastPathSegment ?: "Selected file"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuraRoot() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuraRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val historyStore = remember { ChatHistoryStore(context) }
    var session by remember { mutableStateOf(ChatSession("main-session")) }
    var sessionName by remember { mutableStateOf("AURA Chat") }
    var draft by remember { mutableStateOf("") }
    var selectedAttachment by remember { mutableStateOf<SelectedAttachment?>(null) }
    var selectedSection by remember { mutableStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(historyStore.list()) }
    val attachments = remember { mutableStateMapOf<String, SelectedAttachment>() }

    fun refreshHistory() {
        history = historyStore.list()
    }

    fun saveCurrentSession() {
        if (session.state.messages.isNotEmpty()) {
            historyStore.save(session, sessionName)
            refreshHistory()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Some providers do not offer persistable permissions.
            }
            selectedAttachment = SelectedAttachment(
                uri = uri,
                name = displayName(context, uri),
                mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            )
        }
    }

    fun sendMessage() {
        val text = draft.trim()
        val attachment = selectedAttachment
        if (text.isEmpty() && attachment == null) return
        val messageId = "message-${session.state.messages.size + 1}"
        val updated = session.appendMessage(
            ChatMessage(
                id = messageId,
                role = ChatMessage.Role.USER,
                content = text.ifEmpty { "Attachment" },
                createdAtEpochMs = System.currentTimeMillis()
            )
        )
        session = updated
        if (attachment != null) attachments[messageId] = attachment
        historyStore.save(updated, sessionName)
        refreshHistory()
        draft = ""
        selectedAttachment = null
    }

    fun openHistory(id: String) {
        val restored = historyStore.load(id) ?: return
        session = restored
        history.firstOrNull { it.id == id }?.let { sessionName = it.title }
        attachments.clear()
        selectedSection = 0
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        if (showInfo) {
            AlertDialog(
                onDismissRequest = { showInfo = false },
                title = { Text("Session info") },
                text = { Text("Name: $sessionName\nSession ID: ${session.id}\nMessages: ${session.state.messages.size}") },
                confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Close") } }
            )
        }
        if (showRename) {
            AlertDialog(
                onDismissRequest = { showRename = false },
                title = { Text("Rename chat") },
                text = { OutlinedTextField(value = renameDraft, onValueChange = { renameDraft = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        if (renameDraft.trim().isNotEmpty()) {
                            sessionName = renameDraft.trim()
                            historyStore.rename(session.id, sessionName)
                            refreshHistory()
                        }
                        showRename = false
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(sessionName, style = MaterialTheme.typography.titleLarge)
                            Text("Learn • Assist • Evolve", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    actions = {
                        TextButton(onClick = { menuExpanded = true }) { Text("⋮", style = MaterialTheme.typography.headlineSmall) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("New chat") }, onClick = {
                                saveCurrentSession()
                                session = ChatSession("session-${System.currentTimeMillis()}")
                                sessionName = "AURA Chat"
                                attachments.clear()
                                selectedAttachment = null
                                menuExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Rename chat") }, onClick = {
                                renameDraft = sessionName
                                showRename = true
                                menuExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Clear conversation") }, onClick = {
                                session = ChatSession(session.id)
                                attachments.clear()
                                selectedAttachment = null
                                menuExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Session info") }, onClick = {
                                showInfo = true
                                menuExpanded = false
                            })
                        }
                    }
                )
            },
            bottomBar = {
                Surface(tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                    Column {
                        Column(modifier = Modifier.fillMaxWidth().imePadding().padding(12.dp)) {
                            selectedAttachment?.let { attachment ->
                                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("📎 ${attachment.name}")
                                        if (attachment.mimeType.startsWith("image/")) {
                                            AndroidView(
                                                factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP; adjustViewBounds = true } },
                                                update = { it.setImageURI(attachment.uri) },
                                                modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
                                            )
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            TextButton(onClick = { selectedAttachment = null }) { Text("Remove") }
                                        }
                                    }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { picker.launch(arrayOf("image/*", "application/pdf", "text/*", "application/octet-stream")) }) { Text("＋", style = MaterialTheme.typography.headlineSmall) }
                                OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), minLines = 1, maxLines = 4, placeholder = { Text("Message AURA…") })
                                TextButton(onClick = { sendMessage() }, enabled = draft.isNotBlank() || selectedAttachment != null) { Text("➤", style = MaterialTheme.typography.headlineSmall) }
                            }
                        }
                        NavigationBar {
                            val labels = listOf("Chat", "Tools", "Memory", "Skills", "Settings")
                            val icons = listOf("⌂", "▦", "◉", "◆", "⚙")
                            labels.forEachIndexed { index, label ->
                                NavigationBarItem(selected = selectedSection == index, onClick = { selectedSection = index; if (index == 2) refreshHistory() }, icon = { Text(icons[index]) }, label = { Text(label) })
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Surface(modifier = Modifier.fillMaxSize().padding(paddingValues), color = MaterialTheme.colorScheme.background) {
                if (selectedSection == 2) {
                    HistoryScreen(history = history, onOpen = ::openHistory, onDelete = { id -> historyStore.delete(id); refreshHistory() })
                } else {
                    ChatScreen(session = session, attachments = attachments)
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(session: ChatSession, attachments: Map<String, SelectedAttachment>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AURA is ready", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Your conversation workspace. Real model, research, tools and learning will connect through this interface.")
                }
            }
        }
        if (session.state.messages.isEmpty()) {
            item { Text("What would you like to do?", style = MaterialTheme.typography.titleMedium) }
            item {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {}) { Text("📚 Learn") }
                    TextButton(onClick = {}) { Text("🔧 Plan a task") }
                    TextButton(onClick = {}) { Text("🌐 Research") }
                    TextButton(onClick = {}) { Text("🧠 Memory") }
                }
            }
        }
        items(session.state.messages, key = { it.id }) { message ->
            val isUser = message.role == ChatMessage.Role.USER
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                Card(modifier = Modifier.fillMaxWidth(0.88f), colors = CardDefaults.cardColors(containerColor = if (isUser) Color(0xFF304FFE) else MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(if (isUser) "YOU" else "AURA", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(message.content)
                        attachments[message.id]?.let { attachment ->
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("📎 ${attachment.name}")
                            if (attachment.mimeType.startsWith("image/")) {
                                AndroidView(
                                    factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP; adjustViewBounds = true } },
                                    update = { it.setImageURI(attachment.uri) },
                                    modifier = Modifier.fillMaxWidth().height(180.dp).padding(top = 6.dp).clip(RoundedCornerShape(10.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(history: List<ChatHistoryStore.HistorySummary>, onOpen: (String) -> Unit, onDelete: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Chat History", style = MaterialTheme.typography.headlineSmall) }
        if (history.isEmpty()) {
            item { Text("अभी कोई saved conversation नहीं है।") }
        }
        items(history, key = { it.id }) { item ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text("${item.messageCount} messages", style = MaterialTheme.typography.bodySmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onOpen(item.id) }) { Text("Open") }
                        TextButton(onClick = { onDelete(item.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
