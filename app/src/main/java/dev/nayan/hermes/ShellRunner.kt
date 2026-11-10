package dev.nayan.hermes

import android.os.SystemClock
import org.json.JSONObject
import java.io.File

class ShellRunner {
    fun run(command: String, workspace: File, job: File, timeoutMs: Long, control: JobControl): JSONObject {
        require(command.toByteArray().size <= Protocol.MAX_INPUT && '\u0000' !in command)
        val start = SystemClock.elapsedRealtime()
        if (control.cancelled.get()) return outputResult(job, null, "cancelled_before_start", start)
        val pid = NativeProcess.spawn("/system/bin/sh".toByteArray(), workspace.absolutePath.toByteArray(), command.toByteArray(),
            File(job, "stdout.txt").absolutePath.toByteArray(), File(job, "stderr.txt").absolutePath.toByteArray())
        check(pid > 1) { "Native shell spawn failed: errno=${-pid}" }
        control.pid = pid
        var reason = "exited"
        var status: Int
        try {
            while (true) {
                status = NativeProcess.poll(pid)
                if (status != -1000) break
                reason = when {
                    control.cancelled.get() -> "cancelled"
                    SystemClock.elapsedRealtime() - start >= timeoutMs -> "timeout"
                    File(job, "stdout.txt").length() + File(job, "stderr.txt").length() > 64L * 1024 * 1024 -> "output_limit"
                    else -> "exited"
                }
                if (reason != "exited") {
                    check(NativeProcess.killGroup(pid, 9) == 0) { "Failed to terminate shell process group" }
                }
                Thread.sleep(25)
            }
            control.pid = 0
            if (control.cancelled.get()) reason = "cancelled"
            return outputResult(job, status.takeIf { it >= 0 }, reason, start)
                .put("wait_error", if (status < 0) -status else JSONObject.NULL)
                .put("shell", "/system/bin/sh").put("pid", pid)
        } finally {
            if (control.pid > 1) NativeProcess.killGroup(pid, 9)
            control.pid = 0
        }
    }
}
