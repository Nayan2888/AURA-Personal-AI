package dev.nayan.hermes

import android.content.*
import android.os.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs only on an installed ARM64 APK. A host compilation is not this test. */
@RunWith(AndroidJUnit4::class)
class RuntimeInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var engine: Messenger
    private val connected = CountDownLatch(1)
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) { engine = Messenger(service); connected.countDown() }
        override fun onServiceDisconnected(name: ComponentName) {}
    }
    @Before fun bind() {
        assertTrue(Build.SUPPORTED_ABIS.contains("arm64-v8a"))
        assertTrue(context.bindService(Intent(context, EngineService::class.java), connection, Context.BIND_AUTO_CREATE))
        assertTrue("Engine service did not connect", connected.await(15, TimeUnit.SECONDS))
    }
    @After fun unbind() { context.unbindService(connection) }

    private fun request(operation: Int, text: String = "", timeout: Long = 10000, cancel: Boolean = false): JSONObject {
        val id = UUID.randomUUID().toString()
        val done = CountDownLatch(1)
        var result: JSONObject? = null
        val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.data.getString("request_id") == id) {
                result = JSONObject(message.data.getString("result")!!); done.countDown()
            }
            true
        })
        Protocol.send(engine, operation, Bundle().apply {
            putString("request_id", id); putString("text", text); putLong("timeout_ms", timeout)
        }, receiver)
        if (cancel) {
            val ready = File(context.filesDir, "workspace/cancel-ready")
            val deadline = SystemClock.elapsedRealtime() + 10000
            while (!ready.exists() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
            assertTrue("Shell did not start before cancel", ready.exists())
            Protocol.send(engine, Protocol.CANCEL, Bundle())
        }
        assertTrue("No terminal operation result", done.await(90, TimeUnit.SECONDS))
        val received = result!!
        assertFalse(received.toString(), received.has("error"))
        return received
    }
    @Test fun originalAgentImports() {
        val result = request(Protocol.INITIALIZE)
        assertTrue(result.toString(), result.getBoolean("engine_imported"))
        assertEquals("run_agent.AIAgent", result.getString("engine_class"))
        assertTrue(result.getString("python").startsWith("3.14."))
        assertEquals(RuntimePayload.UPSTREAM, result.getString("upstream_commit"))
    }
    @Test fun originalFileOperationsPersistUnicode() {
        val text = "नमस्ते ✅ persisted"
        val result = request(Protocol.FILES, text)
        assertEquals(text, result.getString("content"))
        assertEquals(text, File(context.filesDir, "workspace/phase1/probe.txt").readText())
    }
    @Test fun nativeShellReportsActualStreamsAndExit() {
        val result = request(Protocol.SHELL, "printf 'नमस्ते ✅'; printf 'error' >&2; exit 7")
        assertEquals(7, result.getInt("exit_code"))
        assertEquals("नमस्ते ✅", result.getString("stdout"))
        assertEquals("error", result.getString("stderr"))
    }
    @Test fun shellTimeoutTerminatesRealProcess() {
        val result = request(Protocol.SHELL, "sleep 60", 250)
        assertEquals("timeout", result.getString("termination"))
        assertEquals(137, result.getInt("exit_code"))
    }
    @Test fun shellCancellationTerminatesRealProcess() {
        File(context.filesDir, "workspace/cancel-ready").delete()
        val result = request(Protocol.SHELL, "printf ready > cancel-ready; sleep 60", cancel = true)
        assertEquals("cancelled", result.getString("termination"))
        assertEquals(137, result.getInt("exit_code"))
    }
    @Test fun pythonWorkerExecutesAndReturnsScriptExit() {
        val result = request(Protocol.PYTHON, "import sys\nfrom pathlib import Path\nPath('worker-proof.txt').write_text('real')\nprint('worker ✅')\nprint('stderr',file=sys.stderr)\nsys.exit(7)")
        assertEquals(7, result.getInt("exit_code"))
        assertEquals("worker ✅\n", result.getString("stdout"))
        assertEquals("stderr\n", result.getString("stderr"))
        assertEquals("real", File(context.filesDir, "workspace/worker-proof.txt").readText())
    }
    @Test fun pythonTimeoutDoesNotBecomeSuccess() {
        val result = request(Protocol.PYTHON, "while True: pass", 2000)
        assertEquals("timeout", result.getString("termination"))
        assertTrue(result.isNull("exit_code"))
        val recovery = request(Protocol.PYTHON, "print('fresh worker')", 15000)
        assertEquals("fresh worker\n", recovery.getString("stdout"))
        assertEquals(0, recovery.getInt("exit_code"))
    }
}
