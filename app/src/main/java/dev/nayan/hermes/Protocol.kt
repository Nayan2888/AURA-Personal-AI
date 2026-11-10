package dev.nayan.hermes

import android.os.Bundle
import android.os.Message
import android.os.Messenger
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object Protocol {
    const val INITIALIZE = 1
    const val FILES = 2
    const val SHELL = 3
    const val PYTHON = 4
    const val CANCEL = 5
    const val RESULT = 6
    const val WORKER_STARTED = 7
    const val WORKER_FINISHED = 8
    const val MAX_INPUT = 32768
    fun send(target: Messenger, type: Int, data: Bundle, reply: Messenger? = null) {
        target.send(Message.obtain(null, type).apply { this.data = data; replyTo = reply })
    }
}

class JobControl {
    val cancelled = AtomicBoolean(false)
    @Volatile var pid: Int = 0
    fun cancel() {
        cancelled.set(true)
        val child = pid
        if (child > 1) NativeProcess.killGroup(child, 9)
    }
}

fun outputResult(job: File, exitCode: Int?, reason: String, started: Long): JSONObject {
    fun preview(name: String): String {
        val file = File(job, name)
        if (!file.isFile) return ""
        return file.inputStream().use { String(it.readNBytes(32768), Charsets.UTF_8) }
    }
    return JSONObject().put("exit_code", exitCode ?: JSONObject.NULL)
        .put("termination", reason).put("elapsed_ms", android.os.SystemClock.elapsedRealtime() - started)
        .put("stdout", preview("stdout.txt")).put("stderr", preview("stderr.txt"))
        .put("stdout_path", File(job, "stdout.txt").absolutePath)
        .put("stderr_path", File(job, "stderr.txt").absolutePath)
        .put("preview_limit_bytes", 32768)
        .put("stdout_bytes", File(job, "stdout.txt").length())
        .put("stderr_bytes", File(job, "stderr.txt").length())
}
