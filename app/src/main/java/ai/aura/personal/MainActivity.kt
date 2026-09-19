package ai.aura.personal

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.foundation.shape.RoundedCornerShape
import ai.aura.personal.core.chat.ChatMessage
import ai.aura.personal.core.chat.ChatSession


private data class SelectedAttachment(
    val uri: Uri,
    val name: String,
    val mimeType: String
)

private fun resolveDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            return cursor.getString(index)
        }
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
    var session by remember { mutableStateOf(ChatSession(id = "main-session")) }
    var draft by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var sessionName by remember { mutableStateOf("AURA Chat") }
    var showSessionInfo by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf(sessionName) }
    var selectedAttachment by remember { mutableStateOf<SelectedAttachment?>(null) }
    var selectedSection by remember { mutableStateOf(0) }
    val messageAttachments = remember { mutableStateMapOf<String, SelectedAttachment>() }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        attachedFileName = uri?.lastPathSegment?.substringAfterLast('/') ?: uri?.toString()
    }

    fun appendUserMessage(content: String) {
        val cleanContent = content.trim()
        if (cleanContent.isNotEmpty()) {
            val message = ChatMessage(
                id = "message-${session.state.messages.size + 1}",
                role = ChatMessage.Role.USER,
                content = if (attachedFileName == null) cleanContent else "$cleanContent\n📎 Attachment: $attachedFileName",
                createdAtEpochMs = System.currentTimeMillis()
            )
            session = session.appendMessage(message)
            draft = ""
            selectedAttachment = null
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        if (showSessionInfo) {
            AlertDialog(
                onDismissRequest = { showSessionInfo = false },
                title = { Text("Session info") },
                text = {
                    Text(
                        "Name: $sessionName\nSession ID: ${session.id}\nMessages: ${session.state.messages.size}\nAttachment handling: local picker enabled"
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showSessionInfo = false }) { Text("Close") }
                }
            )
        }

        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename chat") },
                text = {
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        singleLine = true,
                        label = { Text("Chat name") }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (renameDraft.trim().isNotEmpty()) sessionName = renameDraft.trim()
                            showRenameDialog = false
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(sessionName, style = MaterialTheme.typography.titleLarge)
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
                                    attachedFileName = null
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename chat") },
                                onClick = {
                                    renameDraft = sessionName
                                    showRenameDialog = true
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear conversation") },
                                onClick = {
                                    session = ChatSession(id = session.id)
                                    attachedFileName = null
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Session info") },
                                onClick = {
                                    showSessionInfo = true
                                    menuExpanded = false
                                }
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
                        .navigationBarsPadding()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .imePadding()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            selectedAttachment?.let { attachment ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            "📎 ${attachment.name}",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        if (attachment.mimeType.startsWith("image/")) {
                                            AndroidView(
                                                factory = { ctx ->
                                                    ImageView(ctx).apply {
                                                        scaleType = ImageView.ScaleType.CENTER_CROP
                                                        adjustViewBounds = true
                                                    }
                                                },
                                                update = { imageView ->
                                                    imageView.setImageURI(attachment.uri)
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(150.dp)
                                                    .padding(top = 8.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(onClick = { selectedAttachment = null }) {
                                                Text("Remove")
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        documentPicker.launch(
                                            arrayOf(
                                                "image/*",
                                                "application/pdf",
                                                "text/*",
                                                "application/octet-stream"
                                            )
                                        )
                                    },
                                    modifier = Modifier.padding(bottom = 2.dp)
                                ) {
                                    Text("＋", style = MaterialTheme.typography.headlineSmall)
                                }

                                OutlinedTextField(
                                    value = draft,
                                    onValueChange = { draft = it },
                                    modifier = Modifier.weight(1f),
                                    minLines = 1,
                                    maxLines = 4,
                                    placeholder = { Text("Message AURA…") }
                                )

                                TextButton(
                                    onClick = { appendUserMessage(draft) },
                                    enabled = draft.isNotBlank() || selectedAttachment != null,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                ) {
                                    Text("➤", style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                        }

                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedSection == 0,
                                onClick = { selectedSection = 0 },
                                icon = { Text("⌂") },
                                label = { Text("Chat") }
                            )
                            NavigationBarItem(
                                selected = selectedSection == 1,
                                onClick = { selectedSection = 1 },
                                icon = { Text("▦") },
                                label = { Text("Tools") }
                            )
                            NavigationBarItem(
                                selected = selectedSection == 2,
                                onClick = { selectedSection = 2 },
                                icon = { Text("◉") },
                                label = { Text("Memory") }
                            )
                            NavigationBarItem(
                                selected = selectedSection == 3,
                                onClick = { selectedSection = 3 },
                                icon = { Text("◆") },
                                label = { Text("Skills") }
                            )
                            NavigationBarItem(
                                selected = selectedSection == 4,
                                onClick = { selectedSection = 4 },
                                icon = { Text("⚙") },
                                label = { Text("Settings") }
                            )
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
                                        color = if (isUser) Color.White.copy(alpha = 0.8f)
                                        else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(message.content)

                                    messageAttachments[message.id]?.let { attachment ->
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text(
                                            "📎 ${attachment.name}",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        if (attachment.mimeType.startsWith("image/")) {
                                            AndroidView(
                                                factory = { ctx ->
                                                    ImageView(ctx).apply {
                                                        scaleType = ImageView.ScaleType.CENTER_CROP
                                                        adjustViewBounds = true
                                                    }
                                                },
                                                update = { imageView ->
                                                    imageView.setImageURI(attachment.uri)
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(190.dp)
                                                    .padding(top = 6.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

