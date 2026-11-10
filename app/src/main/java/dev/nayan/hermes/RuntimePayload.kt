package dev.nayan.hermes

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object RuntimePayload {
    const val UPSTREAM = "fc86fb39a1ffb2071e0bcda4e7f837e7d79e05b8"
    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    @Synchronized fun prepare(context: Context): File {
        val manifestText = context.assets.open("source-manifest.json").bufferedReader().use { it.readText() }
        val manifest = JSONObject(manifestText)
        check(manifest.getString("upstream_commit") == UPSTREAM)
        val stamp = MessageDigest.getInstance("SHA-256").digest(manifestText.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val root = File(context.codeCacheDir, "hermes-$stamp")
        if (!File(root, ".complete").isFile) {
            root.deleteRecursively()
            check(root.mkdirs())
            ZipInputStream(context.assets.open("hermes-runtime.zip")).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val file = File(root, entry.name).canonicalFile
                    check(file.path.startsWith(root.canonicalPath + File.separator)) { "Unsafe runtime archive path" }
                    if (entry.isDirectory) file.mkdirs() else {
                        file.parentFile!!.mkdirs()
                        file.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        val files = manifest.getJSONObject("files")
        for (name in files.keys()) {
            val file = File(root, name)
            check(file.isFile && digest(file) == files.getString(name)) { "Runtime digest mismatch: $name" }
        }
        check(File(root, "hermes_android_runtime.json").readText() == manifestText)
        File(root, ".complete").writeText(stamp)
        return root
    }
}
