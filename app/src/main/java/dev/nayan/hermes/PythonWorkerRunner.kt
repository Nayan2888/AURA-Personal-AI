package dev.nayan.hermes

import android.content.*
import android.os.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

class PythonWorkerRunner(private val context: Context) {
    fun run(script: String, workspace: File, job: File, timeoutMs: Long, control: JobControl): JSONObject {
        require(script.toByteArray().size <= Protocol.MAX_INPUT)
        workspace.mkdirs()
        File(job, "worker.py").writeText(script)
        val start = SystemClock.elapsedRealtime()
        val done = CountDownLatch(1)
        val result = AtomicReference<JSONObject?>()
        val remote = AtomicReference<Messenger?>()
        val issued = AtomicBoolean(false)
        val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.data.getString("id") == job.name) {
                if (message.what == Protocol.WORKER_STARTED) {
                    control.pid = message.data.getInt("pid")
                    if (control.cancelled.get()) control.cancel()
                }
                if (message.what == Protocol.WORKER_FINISHED) {
                    result.set(JSONObject(message.data.getString("result")!!)); done.countDown()
                }
            }
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val worker = Messenger(binder)
                remote.set(worker)
                val data = Bundle().apply { putString("id", job.name); putString("job", job.absolutePath) }
                if (control.cancelled.get() || !issued.compareAndSet(false, true)) {
                    Protocol.send(worker, Protocol.CANCEL, data); done.countDown()
                } else Protocol.send(worker, Protocol.PYTHON, data, receiver)
            }
            override fun onServiceDisconnected(name: ComponentName) { done.countDown() }
            override fun onBindingDied(name: ComponentName) { done.countDown() }
            override fun onNullBinding(name: ComponentName) { done.countDown() }
        }
        check(context.bindService(Intent(context, PythonWorkerService::class.java), connection, Context.BIND_AUTO_CREATE))
        var reason = "worker_died"
        try {
            while (true) {
                if (done.await(25, TimeUnit.MILLISECONDS)) {
                    reason = if (result.get() == null) "worker_died" else "exited"
                    break
                }
                reason = when {
                    control.cancelled.get() -> "cancelled"
                    SystemClock.elapsedRealtime() - start >= timeoutMs -> "timeout"
                    File(job, "stdout.txt").length() + File(job, "stderr.txt").length() > 64L * 1024 * 1024 -> "output_limit"
                    else -> "running"
                }
                if (reason != "running") break
            }
            val scriptResult = result.get()
            val exit = scriptResult?.takeIf { it.has("exit_code") }?.getInt("exit_code")
            return outputResult(job, exit, reason, start)
                .put("worker_pid", control.pid.takeIf { it > 1 } ?: JSONObject.NULL)
                .put("exit_code_kind", "script SystemExit / exception status; OS exit status unavailable for Android service")
                .put("worker_result", scriptResult ?: JSONObject.NULL)
        } finally {
            control.cancel()
            try { remote.get()?.let { Protocol.send(it, Protocol.CANCEL, Bundle()) } }
            catch (_: RemoteException) { /* Process already terminated. */ }
            context.unbindService(connection)
            control.pid = 0
        }
    }
}
