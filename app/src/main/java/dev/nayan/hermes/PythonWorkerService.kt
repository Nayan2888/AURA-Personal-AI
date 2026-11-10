package dev.nayan.hermes

import android.app.Service
import android.content.Intent
import android.os.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class PythonWorkerService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val used = AtomicBoolean(false)
    private val endpoint = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == Protocol.CANCEL) {
            Process.killProcess(Process.myPid())
        } else if (message.what == Protocol.PYTHON && used.compareAndSet(false, true)) {
            val data = Bundle(message.data)
            val reply = message.replyTo
            executor.execute {
                try {
                    check(NativeProcess.ownGroup() == 0) { "Cannot isolate Python worker process group" }
                    Protocol.send(reply, Protocol.WORKER_STARTED, Bundle().apply {
                        putString("id", data.getString("id")); putInt("pid", Process.myPid())
                    })
                    if (!Python.isStarted()) Python.start(AndroidPlatform(this))
                    val job = File(data.getString("job")!!).canonicalFile
                    check(job.path.startsWith(File(filesDir, "jobs").canonicalPath + File.separator))
                    val workspace = File(filesDir, "workspace").apply { mkdirs() }
                    val result = Python.getInstance().getModule("hermes_android.worker").callAttr(
                        "execute", File(job, "worker.py").absolutePath, workspace.absolutePath,
                        File(job, "stdout.txt").absolutePath, File(job, "stderr.txt").absolutePath
                    ).toString()
                    File(job, "script-result.json").writeText(result)
                    Protocol.send(reply, Protocol.WORKER_FINISHED, Bundle().apply {
                        putString("id", data.getString("id")); putString("result", result)
                    })
                } catch (error: Throwable) {
                    val result = JSONObject().put("error", error.stackTraceToString()).toString()
                    Protocol.send(reply, Protocol.WORKER_FINISHED, Bundle().apply {
                        putString("id", data.getString("id")); putString("result", result)
                    })
                }
            }
        }
        true
    })
    override fun onBind(intent: Intent): IBinder = endpoint.binder
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
