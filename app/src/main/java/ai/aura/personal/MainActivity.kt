package ai.aura.personal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.ImageView
import java.io.File
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import ai.aura.personal.core.evaluation.EvaluationReportStore
import ai.aura.personal.core.inference.AssistantRuntimeManager
import ai.aura.personal.core.experience.ExperienceCapture
import ai.aura.personal.core.experience.ExperienceFeedback
import ai.aura.personal.core.experience.ExperienceRecord
import ai.aura.personal.core.experience.ExperienceStore
import ai.aura.personal.core.experience.LearningConsentStore
import ai.aura.personal.core.experience.LearningDatasetStore
import ai.aura.personal.core.inference.LocalModelStore
import ai.aura.personal.core.navigation.AuraDestination
import ai.aura.personal.core.versions.ModelVersionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val modelStore = remember { LocalModelStore(context) }
    val modelVersionStore = remember { ModelVersionStore(context.filesDir) }
    val evaluationReportStore = remember { EvaluationReportStore(context.filesDir) }
    val experienceStore = remember { ExperienceStore(context) }
    val learningConsentStore = remember { LearningConsentStore(context) }
    val learningDatasetStore = remember { LearningDatasetStore(context) }
    val runtime = remember {
        AssistantRuntimeManager(
            modelVersionStore = modelVersionStore,
            evaluationReportStore = evaluationReportStore
        )
    }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(ChatSession("main-session")) }
    var sessionName by remember { mutableStateOf("AURA Chat") }
    var draft by remember { mutableStateOf("") }
    var selectedAttachment by remember { mutableStateOf<SelectedAttachment?>(null) }
    var selectedDestination by remember { mutableStateOf(AuraDestination.CHAT) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(historyStore.list()) }
    var selectedModelPath by remember { mutableStateOf(modelStore.selectedModel()?.absolutePath) }
    var modelRevision by remember { mutableStateOf(0) }
    var modelLoading by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var runtimeError by remember { mutableStateOf<String?>(null) }
    var learningConsent by remember { mutableStateOf(learningConsentStore.isGranted()) }
    var showLearningConsent by remember { mutableStateOf(false) }
    var datasetEntryCount by remember { mutableStateOf(0) }
    var datasetStatus by remember { mutableStateOf<String?>(null) }
    val feedbackStates = remember { mutableStateMapOf<String, ExperienceRecord.Outcome>() }
    val attachments = remember { mutableStateMapOf<String, SelectedAttachment>() }

    fun refreshHistory() {
        history = historyStore.list()
    }

    fun buildLearningDataset() {
        if (!learningConsent) {
            datasetStatus = "Enable Learning first."
            return
        }

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    learningDatasetStore.build(
                        experienceStore = experienceStore,
                        consentGranted = true,
                        createdAtEpochMs = System.currentTimeMillis()
                    )
                }
            }.onSuccess { result ->
                datasetEntryCount = result.entryCount
                datasetStatus = "Dataset built: ${result.entryCount} examples"
            }.onFailure { error ->
                datasetStatus = error.message ?: "Dataset build failed."
            }
        }
    }

    fun saveCurrentSession() {
        if (session.state.messages.isNotEmpty()) {
            historyStore.save(session, sessionName)
            refreshHistory()
        }
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = displayName(context, uri)
            scope.launch {
                runtimeError = null
                runCatching {
                    withContext(Dispatchers.IO) { modelStore.importModel(uri, name) }
                }.onSuccess { imported ->
                    selectedModelPath = imported.absolutePath
                    modelRevision += 1
                }.onFailure { error ->
                    runtimeError = error.message ?: "Local model import failed."
                }
            }
        }
    }

    LaunchedEffect(selectedModelPath, modelRevision) {
        modelLoading = selectedModelPath != null
        runtimeError = null
        val requestedPath = selectedModelPath

        if (requestedPath == null) {
            runtime.close()
        } else {
            val requestedModel = File(requestedPath)
            runCatching {
                runtime.load(requestedModel)
            }.onSuccess {
                modelStore.selectModel(requestedModel)
            }.onFailure { error ->
                runtimeError = error.message ?: "Local model initialization failed."

                val persistedPath = modelStore.selectedModel()?.absolutePath
                if (persistedPath != requestedPath) {
                    selectedModelPath = persistedPath
                    modelRevision += 1
                }
            }
        }
        modelLoading = false
    }

    DisposableEffect(Unit) {
        onDispose { runtime.close() }
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

    fun applyExperienceFeedback(
        assistantMessageId: String,
        outcome: ExperienceRecord.Outcome,
        correctedOutput: String? = null
    ) {
        if (!learningConsent) return

        val messages = session.state.messages
        val assistantIndex = messages.indexOfFirst {
            it.id == assistantMessageId && it.role == ChatMessage.Role.ASSISTANT
        }
        if (assistantIndex <= 0) return

        val userMessage = messages.subList(0, assistantIndex)
            .lastOrNull { it.role == ChatMessage.Role.USER }
            ?: return
        val assistantMessage = messages[assistantIndex]

        val existing = experienceStore.load("experience-${userMessage.id}-${assistantMessage.id}")
        val captured = existing ?: ExperienceCapture.capture(
            userMessage = userMessage,
            assistantMessage = assistantMessage,
            outcome = ExperienceRecord.Outcome.UNKNOWN
        ) ?: return

        val updated = runCatching {
            ExperienceFeedback.apply(captured, outcome, correctedOutput)
        }.getOrNull() ?: return

        experienceStore.save(updated)
        feedbackStates[assistantMessage.id] = updated.outcome
    }

    fun sendMessage() {
        val text = draft.trim()
        val attachment = selectedAttachment
        if (text.isEmpty() && attachment == null) return
        if (isProcessing || !runtime.isReady()) return

        val messageId = "message-" + (session.state.messages.size + 1)
        val userMessage = ChatMessage(
            id = messageId,
            role = ChatMessage.Role.USER,
            content = text.ifEmpty { "Attachment" },
            createdAtEpochMs = System.currentTimeMillis()
        )
        val updated = session.appendMessage(userMessage)
        session = updated
        if (attachment != null) attachments[messageId] = attachment
        historyStore.save(updated, sessionName)
        refreshHistory()
        draft = ""
        selectedAttachment = null
        runtimeError = null
        isProcessing = true

        scope.launch {
            try {
                val assistantMessage = runtime.respond(
                    history = updated.state.messages.dropLast(1),
                    userMessage = userMessage
                )
                val completed = updated.appendMessage(assistantMessage)
                session = completed
                historyStore.save(completed, sessionName)

                if (learningConsent) {
                    ExperienceCapture.capture(
                        userMessage = userMessage,
                        assistantMessage = assistantMessage,
                        outcome = ExperienceRecord.Outcome.UNKNOWN
                    )?.let { record ->
                        experienceStore.save(record)
                        feedbackStates[assistantMessage.id] = record.outcome
                    }
                }

                refreshHistory()
            } catch (error: Throwable) {
                runtimeError = error.message ?: "Local inference failed."
            } finally {
                isProcessing = false
            }
        }
    }

    fun openHistory(id: String) {
        val restored = historyStore.load(id) ?: return
        session = restored
        history.firstOrNull { it.id == id }?.let { sessionName = it.title }
        attachments.clear()
        selectedDestination = AuraDestination.CHAT
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
        if (showLearningConsent) {
            AlertDialog(
                onDismissRequest = { showLearningConsent = false },
                title = { Text("Learning & feedback") },
                text = {
                    Text(
                        if (learningConsent) {
                            "Learning is ON. Completed chat experiences are stored on this device so you can verify answers or provide corrections."
                        } else {
                            "Learning is OFF by default. Enable it to store completed chat experiences on this device and provide answer feedback."
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val next = !learningConsent
                        learningConsent = next
                        learningConsentStore.setGranted(next)
                        showLearningConsent = false
                    }) {
                        Text(if (learningConsent) "Disable" else "Enable")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLearningConsent = false }) {
                        Text("Close")
                    }
                }
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
                            Text(
                                when {
                                    modelLoading -> "Loading local model…"
                                    runtime.isReady() -> "Local model: " + (modelStore.selectedModelName() ?: "ready")
                                    else -> "No local model installed"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }                    },
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
                            DropdownMenuItem(
                                text = { Text("Learning: " + if (learningConsent) "ON" else "OFF") },
                                onClick = {
                                    showLearningConsent = true
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(text = { Text("Install local model") }, onClick = {
                                modelPicker.launch(arrayOf("application/octet-stream", "*/*"))
                                menuExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Remove local model") }, enabled = selectedModelPath != null, onClick = {
                                runtime.close()
                                modelStore.selectedModel()?.delete()
                                modelStore.clearSelection()
                                selectedModelPath = null
                                runtimeError = null
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
                                TextButton(onClick = { sendMessage() }, enabled = (draft.isNotBlank() || selectedAttachment != null) && runtime.isReady() && !isProcessing) { Text("➤", style = MaterialTheme.typography.headlineSmall) }
                            }
                        }
                        NavigationBar {
                            val destinations = listOf(
                                AuraDestination.CHAT,
                                AuraDestination.TOOLS,
                                AuraDestination.MEMORY,
                                AuraDestination.SKILLS,
                                AuraDestination.SETTINGS
                            )
                            val icons = listOf("⌂", "▦", "◉", "◆", "⚙")
                            destinations.forEachIndexed { index, destination ->
                                NavigationBarItem(
                                    selected = selectedDestination == destination,
                                    onClick = {
                                        selectedDestination = destination
                                        if (destination == AuraDestination.MEMORY) refreshHistory()
                                    },
                                    icon = { Text(icons[index]) },
                                    label = { Text(destination.title) }
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Surface(modifier = Modifier.fillMaxSize().padding(paddingValues), color = MaterialTheme.colorScheme.background) {
                when (selectedDestination) {
                    AuraDestination.CHAT -> ChatScreen(
                        session = session,
                        attachments = attachments,
                        modelReady = runtime.isReady(),
                        modelLoading = modelLoading,
                        isProcessing = isProcessing,
                        errorMessage = runtimeError,
                        learningEnabled = learningConsent,
                        feedbackStates = feedbackStates,
                        onFeedback = ::applyExperienceFeedback
                    )
                    AuraDestination.MEMORY -> HistoryScreen(
                        history = history,
                        onOpen = ::openHistory,
                        onDelete = { id -> historyStore.delete(id); refreshHistory() },
                        learningEnabled = learningConsent,
                        datasetExists = learningDatasetStore.datasetFile()?.exists() == true,
                        datasetEntryCount = datasetEntryCount,
                        datasetStatus = datasetStatus,
                        onBuildDataset = ::buildLearningDataset
                    )
                    AuraDestination.TOOLS,
                    AuraDestination.SKILLS,
                    AuraDestination.SETTINGS -> FeatureStatusScreen(selectedDestination)
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(
    session: ChatSession,
    attachments: Map<String, SelectedAttachment>,
    modelReady: Boolean,
    modelLoading: Boolean,
    isProcessing: Boolean,
    errorMessage: String?,
    learningEnabled: Boolean,
    feedbackStates: Map<String, ExperienceRecord.Outcome>,
    onFeedback: (String, ExperienceRecord.Outcome, String?) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        when {
                            modelLoading -> "AURA local model is loading…"
                            modelReady -> "AURA is ready"
                            else -> "AURA chat is waiting for a local model"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        if (modelReady) {
                            "Real on-device inference is connected. Responses are generated by the installed local model."
                        } else {
                            "Install a .litertlm model from the top-right menu. AURA will not fabricate an assistant response without a real model."
                        }
                    )
                }
            }
        }
        errorMessage?.let { error ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Inference status", style = MaterialTheme.typography.labelLarge)
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        if (isProcessing) {
            item { Text("AURA is generating a real local response…", style = MaterialTheme.typography.bodyMedium) }
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
                        containerColor = if (isUser) Color(0xFF304FFE) else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(if (isUser) "YOU" else "AURA", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(message.content)

                        if (!isUser && learningEnabled) {
                            var showCorrection by remember(message.id) { mutableStateOf(false) }
                            var correctionDraft by remember(message.id) { mutableStateOf("") }
                            val feedback = feedbackStates[message.id]

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    showCorrection = false
                                    onFeedback(message.id, ExperienceRecord.Outcome.SUCCESS, null)
                                }) {
                                    Text(if (feedback == ExperienceRecord.Outcome.SUCCESS) "Helpful ✓" else "Helpful")
                                }
                                TextButton(onClick = {
                                    showCorrection = false
                                    onFeedback(message.id, ExperienceRecord.Outcome.FAILURE, null)
                                }) {
                                    Text(if (feedback == ExperienceRecord.Outcome.FAILURE) "Not helpful ✓" else "Not helpful")
                                }
                                TextButton(onClick = { showCorrection = true }) {
                                    Text(if (feedback == ExperienceRecord.Outcome.CORRECTED) "Corrected ✓" else "Correct")
                                }
                            }

                            if (showCorrection) {
                                AlertDialog(
                                    onDismissRequest = { showCorrection = false },
                                    title = { Text("Correct AURA's answer") },
                                    text = {
                                        OutlinedTextField(
                                            value = correctionDraft,
                                            onValueChange = { correctionDraft = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 3,
                                            maxLines = 8,
                                            placeholder = { Text("Enter the corrected answer") }
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                val correction = correctionDraft.trim()
                                                if (correction.isNotEmpty()) {
                                                    onFeedback(
                                                        message.id,
                                                        ExperienceRecord.Outcome.CORRECTED,
                                                        correction
                                                    )
                                                    showCorrection = false
                                                }
                                            },
                                            enabled = correctionDraft.trim().isNotEmpty()
                                        ) {
                                            Text("Save correction")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showCorrection = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                        }

                        attachments[message.id]?.let { attachment ->
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("📎 " + attachment.name)
                            if (attachment.mimeType.startsWith("image/")) {
                                AndroidView(
                                    factory = {
                                        ImageView(it).apply {
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                            adjustViewBounds = true
                                        }
                                    },
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
private fun HistoryScreen(
    history: List<ChatHistoryStore.HistorySummary>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    learningEnabled: Boolean,
    datasetExists: Boolean,
    datasetEntryCount: Int,
    datasetStatus: String?,
    onBuildDataset: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Chat History", style = MaterialTheme.typography.headlineSmall) }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Learning dataset", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !learningEnabled -> "Learning is OFF. Enable it from the top-right menu."
                            datasetExists -> "A real local training dataset exists. It is not trained automatically."
                            else -> "No dataset artifact has been built yet."
                        }
                    )
                    if (learningEnabled) {
                        Text("Examples in last build: $datasetEntryCount")
                        TextButton(onClick = onBuildDataset) {
                            Text("Build dataset from verified feedback")
                        }
                    }
                    datasetStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

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
