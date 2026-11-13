package ai.aura.personal

import android.content.Context
import android.content.Intent
import ai.aura.personal.core.inference.discoverModels
import ai.aura.personal.core.inference.DiscoveredModel
import androidx.compose.material3.FilterChip
import org.json.JSONArray
import org.json.JSONObject
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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

private val AuraColors = darkColorScheme(
    primary = Color(0xFFA6F0CD), onPrimary = Color(0xFF12362A),
    primaryContainer = Color(0xFF214B3C), onPrimaryContainer = Color(0xFFD1F8E6),
    background = Color(0xFF10171B), onBackground = Color(0xFFECF4F1),
    surface = Color(0xFF151F24), onSurface = Color(0xFFECF4F1),
    surfaceVariant = Color(0xFF202E34), onSurfaceVariant = Color(0xFFADBFBE),
    outline = Color(0xFF627975), errorContainer = Color(0xFF402A2F),
    onErrorContainer = Color(0xFFFFD9DB)
)

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
    val pickerPrefs = remember { context.getSharedPreferences("aura_model_picker", Context.MODE_PRIVATE) }
    var hiddenModels by remember { mutableStateOf(pickerPrefs.getStringSet("hidden", emptySet()).orEmpty().toSet()) }
    var availableModels by remember {
        val cache = runCatching { JSONObject(pickerPrefs.getString("catalog", "{}") ?: "{}") }.getOrElse { JSONObject() }
        mutableStateOf(cache.keys().asSequence().associateWith { id ->
            val ids = cache.optJSONArray(id) ?: JSONArray()
            (0 until ids.length()).map { ids.optString(it) }.filter { it.isNotBlank() }
        })
    }
    var modelDialog by remember { mutableStateOf(false) }
    var visibilityDialog by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf<List<DiscoveredModel>>(emptyList()) }
    var scannedFolder by remember { mutableStateOf(pickerPrefs.getString("folder", null)?.let(Uri::parse)) }
    var partialReply by remember { mutableStateOf("") }
    var testProvider by remember { mutableStateOf<ProviderProfile?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var failedMessageId by remember { mutableStateOf<String?>(null) }
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

    fun importModel(uri: Uri, name: String? = null) {
        manageModel(transfer = true) {
            val file = withContext(Dispatchers.IO) {
                modelStore.importModel(uri, name ?: displayName(context, uri), ::transferProgress)
            }
            modelStatus = "Installed " + file.name + ". Select it in chat to load."
        }
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importModel(uri)
    }
    suspend fun scanFolder(folder: Uri) {
        discovered = emptyList()
        discovered = withContext(Dispatchers.IO) { discoverModels(context.contentResolver, folder) }
        modelStatus = "Found " + discovered.size + " LiteRT-LM files in the selected folder."
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder ->
        if (folder != null) manageModel {
            try {
                context.contentResolver.takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val previous = scannedFolder
                if (previous != null && previous != folder) runCatching {
                    context.contentResolver.releasePersistableUriPermission(previous, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                pickerPrefs.edit().putString("folder", folder.toString()).apply()
            } catch (_: SecurityException) {
                pickerPrefs.edit().remove("folder").apply()
            }
            scannedFolder = folder
            scanFolder(folder)
        }
    }

    LaunchedEffect(scannedFolder) {
        val folder = scannedFolder ?: return@LaunchedEffect
        if (!settingsBusy) manageModel { scanFolder(folder) }
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

    fun updateModelCache(values: Map<String, List<String>>) {
        availableModels = values
        val json = JSONObject()
        values.forEach { (id, models) -> json.put(id, JSONArray(models)) }
        pickerPrefs.edit().putString("catalog", json.toString()).apply()
    }

    fun selectProvider(profile: ProviderProfile?, keepDraft: Boolean = false) {
        saveCurrentSession()
        session = ChatSession("session-" + java.util.UUID.randomUUID())
        sessionName = "AURA Chat"
        if (!keepDraft) draft = ""
        runtimeError = null
        failedMessageId = null
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

    fun sendMessage(retry: Boolean = false) {
        val last = session.state.messages.lastOrNull()
        if (retry && (last?.id != failedMessageId || last?.role != ChatMessage.Role.USER)) return
        val text = if (retry) last?.content.orEmpty() else draft.trim()
        if (text.isEmpty()) return
        val provider = activeProvider
        if (isProcessing || settingsBusy || modelLoading || (provider == null && loadedModel == null)) return
        val messageId = "message-" + (session.state.messages.size + 1)
        val userMessage = if (retry) checkNotNull(last) else ChatMessage(
            id = messageId,
            role = ChatMessage.Role.USER,
            content = text,
            createdAtEpochMs = System.currentTimeMillis()
        )
        val updated = if (retry) session else session.appendMessage(userMessage)
        if (!retry) {
            session = updated
            historyStore.save(updated, sessionName)
            refreshHistory()
            draft = ""
        }
        failedMessageId = null
        runtimeError = null
        isProcessing = true
        partialReply = ""

        scope.launch {
            try {
                val messages = updated.state.messages.dropLast(1)
                val assistantMessage = if (provider == null) runtime.respond(messages, userMessage) else {
                    val answer = withContext(Dispatchers.IO) { providerClient.generate(provider, messages, text) { partialReply = it } }
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
                failedMessageId = userMessage.id
                runtimeError = error.message ?: "Inference failed."
            } finally {
                isProcessing = false
                partialReply = ""
            }
        }
    }

    fun openHistory(id: String) {
        if (isProcessing) return
        val restored = historyStore.load(id) ?: return
        activeProvider = null
        failedMessageId = null
        runtimeError = null
        session = restored
        history.firstOrNull { it.id == id }?.let { sessionName = it.title }
        selectedDestination = AuraDestination.CHAT
    }

    val choices = chatModels(models, providers, availableModels)
    val selectedModelId = activeProvider?.let { ChatModel(it.model, provider = it).id }
        ?: loadedModel?.let { ChatModel(it.name, file = it).id }
    MaterialTheme(colorScheme = AuraColors) {
        if (modelDialog || visibilityDialog) ChatModelDialog(
            choices, selectedModelId, visibilityDialog, hiddenModels,
            onClose = { modelDialog = false; visibilityDialog = false },
            onSettings = { modelDialog = false; selectedDestination = AuraDestination.SETTINGS },
            onChoose = { choice ->
                modelDialog = false
                val provider = choice.provider
                val file = choice.file
                if (choice.id != selectedModelId) {
                    if (provider != null) confirmProvider = provider
                    else if (file != null) manageModel {
                        try {
                            if (loadedModel != file) loadModel(file)
                            if (activeProvider != null) selectProvider(null, keepDraft = true)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            runtimeError = error.message ?: "Could not load the local model."
                            throw error
                        }
                    }
                }
            },
            onToggle = { id ->
                hiddenModels = if (id in hiddenModels) hiddenModels - id else hiddenModels + id
                pickerPrefs.edit().putStringSet("hidden", hiddenModels).apply()
            }
        )
        testProvider?.let { profile ->
            AlertDialog(onDismissRequest = { testProvider = null }, title = { Text("Test ${profile.name} reply?") },
                text = { Text("Send one short test prompt to ${profile.model}. The provider may count it against your quota.") },
                confirmButton = { TextButton(onClick = {
                    testProvider = null
                    manageApi {
                        apiStatus = "Waiting for ${profile.name} / ${profile.model}…"
                        val reply = withContext(Dispatchers.IO) {
                            providerClient.generate(profile, emptyList(), "Reply with the word OK.") { }
                        }
                        apiStatus = "${profile.name} replied: " + reply.take(280)
                    }
                }) { Text("Send test") } },
                dismissButton = { TextButton(onClick = { testProvider = null }) { Text("Cancel") } })
        }
        confirmProvider?.let { profile ->
            AlertDialog(
                onDismissRequest = { confirmProvider = null },
                title = { Text("Use " + profile.name + " for chat?") },
                text = { Text("A new chat will open. Messages and its history will be sent to " +
                    profile.baseUrl + ". The provider may charge for inference.") },
                confirmButton = { TextButton(onClick = {
                    confirmProvider = null
                    selectProvider(profile, keepDraft = true)
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AuraOrb(36)
                            Column {
                                Text(if (selectedDestination == AuraDestination.CHAT) sessionName else selectedDestination.title,
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val route = activeProvider?.let { it.name + " · " + it.model }
                                    ?: if (modelLoading) "Loading local model…" else loadedModel?.name ?: "On-device · no model loaded"
                                Text(route, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    actions = {
                        TextButton(onClick = { menuExpanded = true }) { Text("⋮", style = MaterialTheme.typography.headlineSmall) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("New chat") }, enabled = !isProcessing, onClick = {
                                saveCurrentSession()
                                session = ChatSession("session-${System.currentTimeMillis()}")
                                sessionName = "AURA Chat"
                                failedMessageId = null
                                runtimeError = null
                                menuExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Rename chat") }, onClick = {
                                renameDraft = sessionName
                                showRename = true
                                menuExpanded = false
                            })
                            DropdownMenuItem(text = { Text("Clear conversation") }, enabled = !isProcessing, onClick = {
                                session = ChatSession(session.id)
                                failedMessageId = null
                                runtimeError = null
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
                Surface(color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
                    Column {
                        if (selectedDestination == AuraDestination.CHAT) Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                            FilterChip(
                                selected = selectedModelId != null,
                                onClick = { modelDialog = true },
                                enabled = !isProcessing && !settingsBusy && !modelLoading,
                                label = { Text(activeProvider?.let { it.name + " · " + it.model } ?: loadedModel?.name ?: "Choose model",
                                    maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = { Text("⌄") }
                            )
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp),
                                    minLines = 1, maxLines = 4, placeholder = { Text("Message AURA…") })
                                Button(onClick = { sendMessage() }, contentPadding = PaddingValues(horizontal = 14.dp),
                                    enabled = draft.isNotBlank() && (loadedModel != null || activeProvider != null) && !isProcessing && !settingsBusy && !modelLoading) { Text("Send") }
                            }
                        }
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            val destinations = listOf(
                                AuraDestination.CHAT,
                                AuraDestination.WORK,
                                AuraDestination.MEMORY,
                                AuraDestination.SETTINGS
                            )
                            val icons = listOf("⌂", "▦", "◉", "⚙")
                            destinations.forEachIndexed { index, destination ->
                                NavigationBarItem(
                                    selected = selectedDestination == destination,
                                    onClick = {
                                        selectedDestination = destination
                                        if (destination == AuraDestination.MEMORY) refreshHistory()
                                    },
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                                    icon = { Text(icons[index], fontSize = 19.sp) },
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
                        partialReply = partialReply,
                        errorMessage = runtimeError,
                        onRetry = if (failedMessageId != null && !settingsBusy && !modelLoading &&
                            (loadedModel != null || activeProvider != null)) { { sendMessage(retry = true) } } else null,
                        onSettings = { selectedDestination = AuraDestination.SETTINGS },
                        learningEnabled = learningConsent,
                        feedbackStates = feedbackStates,
                        onFeedback = ::applyExperienceFeedback
                    )
                    AuraDestination.WORK -> WorkScreen { brief ->
                        draft = brief
                        selectedDestination = AuraDestination.CHAT
                    }
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
                        discovered = discovered,
                        onChooseFolder = { folderPicker.launch(scannedFolder) },
                        onImportDiscovered = { importModel(it.uri, it.name) },
                        onChatVisibility = { visibilityDialog = true },
                        onTestReply = { testProvider = it },
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
                            val previous = providers.firstOrNull { it.id == profile.id }
                            withContext(Dispatchers.IO) { providerStore.save(profile, key) }
                            if (activeProvider?.id == profile.id) selectProvider(null, keepDraft = true)
                            if (key != null || previous?.baseUrl != profile.baseUrl || previous.protocol != profile.protocol) {
                                updateModelCache(availableModels - profile.id)
                            }
                            providers = providerStore.profiles()
                            apiStatus = "Saved on this device. Fetch models to test access."
                        } },
                        onRemoveProvider = { profile -> manageApi {
                            withContext(Dispatchers.IO) { providerStore.remove(profile) }
                            if (activeProvider?.id == profile.id) selectProvider(null)
                            updateModelCache(availableModels - profile.id)
                            providers = providerStore.profiles()
                            apiStatus = "API profile removed."
                        } },
                        onListModels = { profile -> manageApi {
                            updateModelCache(availableModels - profile.id)
                            val ids = withContext(Dispatchers.IO) { providerClient.listModels(profile) }
                            updateModelCache(availableModels + (profile.id to ids))
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
    partialReply: String,
    errorMessage: String?,
    onRetry: (() -> Unit)?,
    onSettings: () -> Unit,
    learningEnabled: Boolean,
    feedbackStates: Map<String, ExperienceRecord.Outcome>,
    onFeedback: (String, ExperienceRecord.Outcome, String?) -> Unit
) {
    val listState = rememberLazyListState()
    val messages = session.state.messages
    LaunchedEffect(session.id, messages.size, isProcessing, errorMessage) {
        val count = messages.size + (if (messages.isEmpty()) 1 else 0) +
            (if (isProcessing || errorMessage != null) 1 else 0)
        if (count > 0) listState.scrollToItem(count - 1)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (messages.isEmpty()) item {
            Column(
                modifier = Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(Color(0xFF214235), Color(0xFF18262D))),
                    RoundedCornerShape(24.dp)
                ).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                AuraOrb(62)
                Text("Your space to think", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(when {
                    providerName != null -> "New messages in this chat go to $providerName."
                    modelLoading -> "Initializing your local model…"
                    modelReady -> "Your local model is loaded. Send a message to begin."
                    else -> "Load a local model or choose an API to start chatting."
                }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!modelReady && !modelLoading) Button(onClick = onSettings) { Text("Models & APIs") }
            }
        }
        items(session.state.messages, key = { it.id }) { message ->
            val isUser = message.role == ChatMessage.Role.USER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                Card(
                    modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth(if (isUser) 0.83f else 0.94f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(if (isUser) "You" else "AURA", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
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
        if (isProcessing) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (partialReply.isNotEmpty()) {
                    Text("AURA · ${providerName.orEmpty()}", color = MaterialTheme.colorScheme.primary)
                    Text(partialReply)
                } else Text(providerName?.let { "Waiting for $it…" } ?: "Generating on device…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        } else if (errorMessage != null) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Reply unavailable", fontWeight = FontWeight.SemiBold)
                    Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
                    if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry message") }
                }
            }
        }
    }
}

@Composable
private fun AuraOrb(diameter: Int) {
    Canvas(Modifier.size(diameter.dp)) {
        val unit = size.minDimension
        drawCircle(Brush.radialGradient(listOf(Color(0xFF5FCBA1), Color(0xFF1D5749), Color(0xFF172C2C))))
        drawCircle(Color(0xFFB5F5D9), radius = unit * 0.34f, style = Stroke(width = unit * 0.018f))
        drawCircle(Color(0xFFDDFCEF), radius = unit * 0.13f)
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
