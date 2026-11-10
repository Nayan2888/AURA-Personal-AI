package dev.nayan.hermes

import android.app.Service
import android.content.Intent
import android.os.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class EngineService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val active = AtomicReference<JobControl?>()
    private val endpoint = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == Protocol.CANCEL) active.get()?.cancel() else dispatch(message)
        true
    })

    private fun python(): Python {
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        return Python.getInstance()
    }

    private fun dispatch(message: Message) {
        val reply = message.replyTo ?: return
        val request = Bundle(message.data)
        val control = JobControl()
        if (!active.compareAndSet(null, control)) {
            send(reply, JSONObject().put("status", "busy"), request.getString("request_id"))
            return
        }
        val operation = message.what
        val id = UUID.randomUUID().toString()
        executor.execute {
            val job = File(filesDir, "jobs/$id").apply { mkdirs() }
            val workspace = File(filesDir, "workspace").apply { mkdirs() }
            val result = try {
                val text = request.getString("text", "")
                require(text.toByteArray().size <= Protocol.MAX_INPUT) { "Input exceeds 32 KiB" }
                val timeout = request.getLong("timeout_ms", 10000).coerceIn(100, 600000)
                when (operation) {
                    Protocol.INITIALIZE -> {
                        val root = RuntimePayload.prepare(this)
                        JSONObject(python().getModule("hermes_android.runtime").callAttr("initialize",
                            File(filesDir, "hermes").absolutePath, workspace.absolutePath, root.absolutePath).toString())
                    }
                    Protocol.FILES -> {
                        val root = RuntimePayload.prepare(this)
                        python().getModule("sys").get("path")!!.callAttr("insert", 0, root.absolutePath)
                        JSONObject(python().getModule("hermes_android.runtime").callAttr("file_probe",
                            workspace.absolutePath, "phase1/probe.txt", text).toString())
                    }
                    Protocol.SHELL -> ShellRunner().run(text, workspace, job, timeout, control)
                    Protocol.PYTHON -> PythonWorkerRunner(this).run(text, workspace, job, timeout, control)
                    else -> throw IllegalArgumentException("Unknown operation $operation")
                }.put("status", "operation_returned")
            } catch (error: Throwable) {
                JSONObject().put("status", "error").put("error", error.stackTraceToString())
            }
            result.put("job_id", id).put("operation", operation).put("upstream_commit", RuntimePayload.UPSTREAM)
            result.put("android_api", Build.VERSION.SDK_INT).put("device", Build.MODEL)
                .put("abis", Build.SUPPORTED_ABIS.joinToString()).put("engine_pid", Process.myPid())
            File(job, "result.json").writeText(result.toString(2))
            File(filesDir, "latest-result.json").writeText(result.toString(2))
            active.compareAndSet(control, null)
            send(reply, result, request.getString("request_id"))
        }
    }

    private fun send(reply: Messenger, result: JSONObject, requestId: String?) {
        try {
            Protocol.send(reply, Protocol.RESULT, Bundle().apply {
                putString("result", result.toString()); putString("request_id", requestId)
            })
        } catch (_: RemoteException) { /* Result remains in filesDir after UI disconnects. */ }
    }
    override fun onBind(intent: Intent): IBinder = endpoint.binder
    override fun onDestroy() {
        active.get()?.cancel()
        executor.shutdown()
        super.onDestroy()
    }
}
