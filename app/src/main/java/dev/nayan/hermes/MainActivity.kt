package dev.nayan.hermes

import android.content.*
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var engine: Messenger? = null
    private var connected by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var cancellable by mutableStateOf(false)
    private var result by mutableStateOf("No operation has run on this device yet.")
    private val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == Protocol.RESULT) {
            busy = false
            result = JSONObject(message.data.getString("result")!!).toString(2)
        }
        true
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            engine = Messenger(service); connected = true
        }
        override fun onServiceDisconnected(name: ComponentName) {
            engine = null; connected = false; busy = false
            result = "Engine process disconnected. The previous job may be incomplete; inspect its files before retrying."
        }
    }
    private fun run(type: Int, text: String = "", timeout: Long = 10000) {
        val target = engine ?: return
        busy = true
        cancellable = type == Protocol.SHELL || type == Protocol.PYTHON
        result = "Running locally…"
        try {
            Protocol.send(target, type, Bundle().apply {
                putString("request_id", UUID.randomUUID().toString()); putString("text", text)
                putLong("timeout_ms", timeout)
            }, receiver)
        } catch (error: RemoteException) { busy = false; result = error.toString() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindService(Intent(this, EngineService::class.java), connection, BIND_AUTO_CREATE)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Hermes · Phase 1", style = MaterialTheme.typography.headlineMedium)
                        Text("Original Python engine · local runtime qualification")
                        Text(if (connected) "Engine service connected" else "Connecting to local engine service…")
                        Text("These checks report real results. Agent conversations and model setup require Phase 2.",
                            style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { run(Protocol.INITIALIZE) }, enabled = connected && !busy) {
                            Text("Initialize original Hermes")
                        }
                        var content by remember { mutableStateOf("नमस्ते Hermes — real file ✅") }
                        OutlinedTextField(content, { content = it }, label = { Text("File contents") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { run(Protocol.FILES, content) }, enabled = connected && !busy) { Text("Write, read and verify file") }
                        var shell by remember { mutableStateOf("printf 'shell stdout\\n'; printf 'shell stderr\\n' >&2; exit 7") }
                        OutlinedTextField(shell, { shell = it }, label = { Text("Local shell command") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { run(Protocol.SHELL, shell) }, enabled = connected && !busy) { Text("Run shell · 10 s limit") }
                        var script by remember { mutableStateOf("import sys\nprint('Python stdout ✅')\nprint('Python stderr', file=sys.stderr)\nsys.exit(7)") }
                        OutlinedTextField(script, { script = it }, label = { Text("Python 3.14 worker script") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                        Button(onClick = { run(Protocol.PYTHON, script) }, enabled = connected && !busy) { Text("Run Python worker · 10 s limit") }
                        OutlinedButton(onClick = {
                            engine?.let { Protocol.send(it, Protocol.CANCEL, Bundle()) }
                        }, enabled = connected && busy && cancellable) { Text("Cancel running job") }
                        HorizontalDivider()
                        Text("Actual result", style = MaterialTheme.typography.titleLarge)
                        Text(result, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("Hermes Phase 1 evidence", result))
                        }) { Text("Copy result") }
                        Text("Independent Android port. Upstream: fc86fb39a1ff. No device compatibility claim is implied by installation.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    override fun onDestroy() { unbindService(connection); super.onDestroy() }
}
