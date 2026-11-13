package ai.aura.personal

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import java.io.File
import ai.aura.personal.core.providers.ProviderClient
import ai.aura.personal.core.providers.ProviderProfile
import ai.aura.personal.core.providers.ProviderStore
import kotlinx.coroutines.CancellationException
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index)?.let { return it }
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
    val providerStore = remember { ProviderStore(context) }
    val providerClient = remember { ProviderClient(providerStore) }
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
    var selectedDestination by remember { mutableStateOf(AuraDestination.CHAT) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(historyStore.list()) }
    var models by remember { mutableStateOf(modelStore.installedModels()) }
    var loadedModel by remember { mutableStateOf<File?>(null) }
    var modelLoading by remember { mutableStateOf(false) }
    var settingsBusy by remember { mutableStateOf(false) }
    var transferJob by remember { mutableStateOf<Job?>(null) }
    var modelStatus by remember { mutableStateOf<String?>(null) }
    var apiStatus by remember { mutableStateOf<String?>(null) }
    var providers by remember { mutableStateOf(providerStore.profiles()) }
    var activeProvider by remember { mutableStateOf<ProviderProfile?>(null) }
    var confirmProvider by remember { mutableStateOf<ProviderProfile?>(null) }
    var availableModels by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var isProcessing by remember { mutableStateOf(false) }
    var runtimeError by remember { mutableStateOf<String?>(null) }
    var learningConsent by remember { mutableStateOf(learningConsentStore.isGranted()) }
    var showLearningConsent by remember { mutableStateOf(false) }
    var datasetEntryCount by remember { mutableStateOf(0) }
    var datasetStatus by remember { mutableStateOf<String?>(null) }
    val feedbackStates = remember { mutableStateMapOf<String, ExperienceRecord.Outcome>() }

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

    fun manageModel(transfer: Boolean = false, action: suspend () -> Unit) {
        if (settingsBusy || modelLoading || isProcessing) return
        settingsBusy = true
        modelStatus = null
        val job = scope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                if (transfer) modelStatus = "Transfer cancelled."
                throw error
            } catch (error: Exception) {
                modelStatus = error.message ?: "Model operation failed."
            } finally {
                models = modelStore.installedModels()
                transferJob = null
                settingsBusy = false
            }
        }
        if (transfer && job.isActive) transferJob = job
    }

    suspend fun loadModel(file: File) {
        modelLoading = true
        runtimeError = null
        try {
            withContext(Dispatchers.IO) { runtime.load(file) }
            loadedModel = file
            modelStore.select(file)
            modelStatus = "Loaded on device."
        } finally {
            modelLoading = false
        }
    }

    fun transferProgress(bytes: Long, total: Long?) {
        val amount = android.text.format.Formatter.formatFileSize(context, bytes)
        val expected = total?.let { " / " + android.text.format.Formatter.formatFileSize(context, it) }.orEmpty()
        modelStatus = "Transferred " + amount + expected
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) manageModel(transfer = true) {
            val file = withContext(Dispatchers.IO) {
                modelStore.importModel(uri, displayName(context, uri), ::transferProgress)
            }
            modelStatus = "Installed " + file.name + ". Select Load on device to initialize it."
        }
    }

    LaunchedEffect(Unit) {
        modelStore.selectedModel()?.let { file ->
            try {
                loadModel(file)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                runtimeError = error.message ?: "Local model initialization failed."
                modelStatus = runtimeError
            }
        }
    }

    fun manageApi(action: suspend () -> Unit) {
        if (settingsBusy || modelLoading || isProcessing) return
        settingsBusy = true
        apiStatus = null
        scope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                apiStatus = error.message ?: "API operation failed."
            } finally {
                settingsBusy = false
            }
        }
    }

    fun selectProvider(profile: ProviderProfile?) {
        saveCurrentSession()
        session = ChatSession("session-" + java.util.UUID.randomUUID())
        sessionName = "AURA Chat"
        draft = ""
        runtimeError = null
        activeProvider = profile
        selectedDestination = AuraDestination.CHAT
    }

    DisposableEffect(Unit) {
        onDispose { runtime.close() }
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
        if (text.isEmpty()) return
        val provider = activeProvider
        if (isProcessing || settingsBusy || modelLoading || (provider == null && loadedModel == null)) return
        val messageId = "message-" + (session.state.messages.size + 1)
        val userMessage = ChatMessage(
            id = messageId,
            role = ChatMessage.Role.USER,
            content = text,
            createdAtEpochMs = System.currentTimeMillis()
        )
        val updated = session.appendMessage(userMessage)
        session = updated
        historyStore.save(updated, sessionName)
        refreshHistory()
        draft = ""
        runtimeError = null
        isProcessing = true

        scope.launch {
            try {
                val messages = updated.state.messages.dropLast(1)
                val assistantMessage = if (provider == null) runtime.respond(messages, userMessage) else {
                    val answer = withContext(Dispatchers.IO) { providerClient.generate(provider, messages, text) }
                    ChatMessage("assistant-" + java.util.UUID.randomUUID(), ChatMessage.Role.ASSISTANT,
                        answer, System.currentTimeMillis())
                }
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
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                runtimeError = error.message ?: "Inference failed."
            } finally {
                isProcessing = false
            }
        }
    }

    fun openHistory(id: String) {
        if (isProcessing) return
        val restored = historyStore.load(id) ?: return
        activeProvider = null
        session = restored
        history.firstOrNull { it.id == id }?.let { sessionName = it.title }
        selectedDestination = AuraDestination.CHAT
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        confirmProvider?.let { profile ->
            AlertDialog(
                onDismissRequest = { confirmProvider = null },
                title = { Text("Use " + profile.name + " for chat?") },
                text = { Text("A new chat will open. Messages and its history will be sent to " +
                    profile.baseUrl + ". The provider may charge for inference.") },
                confirmButton = { TextButton(onClick = {
                    confirmProvider = null
                    selectProvider(profile)
                }) { Text("Use cloud API") } },
                dismissButton = { TextButton(onClick = { confirmProvider = null }) { Text("Cancel") } }
            )
        }
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
                                    activeProvider != null -> "Cloud API · " + activeProvider?.name
                                    modelLoading -> "Loading local model…"
                                    loadedModel != null -> "Local model · " + loadedModel?.name
                                    else -> "No local model loaded"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }                    },
                    actions = {
                        TextButton(onClick = { menuExpanded = true }) { Text("⋮", style = MaterialTheme.typography.headlineSmall) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("New chat") }, enabled = !isProcessing, onClick = {
                                saveCurrentSession()
                                session = ChatSession("session-${System.currentTimeMillis()}")
                                sessionName = "AURA Chat"
                                menuExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Rename chat") }, onClick = {
                                renameDraft = sessionName
                                showRename = true
                                menuExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Clear conversation") }, enabled = !isProcessing, onClick = {
                                session = ChatSession(session.id)
                                menuExpanded = false
                            })
                            DropdownMenuItem(
                                text = { Text("Learning: " + if (learningConsent) "ON" else "OFF") },
                                onClick = {
                                    showLearningConsent = true
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(text = { Text("Models & APIs") }, onClick = {
                                selectedDestination = AuraDestination.SETTINGS
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
                        if (selectedDestination == AuraDestination.CHAT) Column(modifier = Modifier.fillMaxWidth().imePadding().padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), minLines = 1, maxLines = 4, placeholder = { Text("Message AURA…") })
                                TextButton(onClick = { sendMessage() }, enabled = draft.isNotBlank() && (loadedModel != null || activeProvider != null) && !isProcessing && !settingsBusy && !modelLoading) { Text("➤", style = MaterialTheme.typography.headlineSmall) }
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
                        modelReady = loadedModel != null || activeProvider != null,
                        providerName = activeProvider?.name,
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
                    AuraDestination.SETTINGS -> ModelSettingsScreen(
                        models, loadedModel, providers, activeProvider, settingsBusy || modelLoading || isProcessing,
                        modelStatus, apiStatus, availableModels,
                        onImport = { modelPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                        onCancelTransfer = transferJob?.let { job -> { job.cancel() } },
                        onDownload = { url, name, sha -> manageModel(transfer = true) {
                            val file = withContext(Dispatchers.IO) { modelStore.downloadModel(url, name, sha, ::transferProgress) }
                            modelStatus = "Downloaded " + file.name + if (sha == null) " · no checksum supplied." else " · SHA-256 matched."
                        } },
                        onLoad = { file -> manageModel { loadModel(file) } },
                        onUnload = { manageModel {
                            withContext(Dispatchers.IO) { runtime.close() }
                            loadedModel = null
                            modelStore.select(null)
                            modelStatus = "Unloaded. Model file is still installed."
                        } },
                        onSaveProvider = { profile, key -> manageApi {
                            withContext(Dispatchers.IO) { providerStore.save(profile, key) }
                            if (activeProvider?.id == profile.id) selectProvider(null)
                            availableModels = availableModels - profile.id
                            providers = providerStore.profiles()
                            apiStatus = "Saved on this device. Fetch models to test access."
                        } },
                        onRemoveProvider = { profile -> manageApi {
                            withContext(Dispatchers.IO) { providerStore.remove(profile) }
                            if (activeProvider?.id == profile.id) selectProvider(null)
                            availableModels = availableModels - profile.id
                            providers = providerStore.profiles()
                            apiStatus = "API profile removed."
                        } },
                        onListModels = { profile -> manageApi {
                            availableModels = availableModels - profile.id
                            val ids = withContext(Dispatchers.IO) { providerClient.listModels(profile) }
                            availableModels = availableModels + (profile.id to ids)
                            apiStatus = profile.name + " returned " + ids.size + " model IDs. Open Edit to select one."
                        } },
                        onUseProvider = { profile ->
                            if (profile == null) selectProvider(null) else confirmProvider = profile
                        }
                    )
                    AuraDestination.TOOLS,
                    AuraDestination.SKILLS -> FeatureStatusScreen(selectedDestination)
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(
    session: ChatSession,
    modelReady: Boolean,
    providerName: String?,
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
                            providerName != null -> "Cloud API selected"
                            modelReady -> "AURA is ready"
                            else -> "AURA chat is waiting for a local model"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        if (modelReady) {
                            providerName?.let { "Chat is routed to " + it + "." }
                                ?: "The local model is initialized. Send a message to run on-device inference."
                        } else {
                            "Open Settings to install and load a .litertlm model, or configure a cloud API."
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
