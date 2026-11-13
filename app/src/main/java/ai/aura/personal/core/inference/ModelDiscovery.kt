package ai.aura.personal.core.inference

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class DiscoveredModel(val uri: Uri, val name: String, val bytes: Long?)

internal suspend fun discoverModels(resolver: ContentResolver, folder: Uri): List<DiscoveredModel> {
    val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE)
    val pending = ArrayDeque<String>()
    pending.add(DocumentsContract.getTreeDocumentId(folder))
    val seen = mutableSetOf<String>()
    val models = mutableListOf<DiscoveredModel>()
    while (pending.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val id = pending.removeFirst()
        if (!seen.add(id)) continue
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, id)
        val cursor = checkNotNull(resolver.query(children, columns, null, null, null)) { "Android could not read the selected folder." }
        cursor.use {
            while (it.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val child = it.getString(0)
                val name = it.getString(1).orEmpty()
                if (it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) pending.add(child)
                else if (name.endsWith(".litertlm", ignoreCase = true)) {
                    models.add(DiscoveredModel(DocumentsContract.buildDocumentUriUsingTree(folder, child), name,
                        if (it.isNull(3)) null else it.getLong(3)))
                }
            }
        }
    }
    return models.sortedBy { it.name.lowercase() }
}
