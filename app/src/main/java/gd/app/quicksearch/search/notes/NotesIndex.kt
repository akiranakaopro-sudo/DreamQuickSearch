package gd.app.quicksearch.search.notes

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import gd.app.quicksearch.search.SearchText
import java.util.concurrent.Executor

class NotesIndex(context: Context) {

    private val appContext = context.applicationContext
    private val lock = Any()
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            invalidate()
            onInvalidated?.invoke()
        }
    }

    @Volatile
    private var dirty = true
    @Volatile
    private var watching = false
    private var snapshot: List<NoteItem> = emptyList()
    private var onInvalidated: (() -> Unit)? = null

    fun invalidate() {
        dirty = true
    }

    fun warm(executor: Executor) {
        executor.execute { notes() }
    }

    fun watch(onInvalidated: () -> Unit) {
        this.onInvalidated = onInvalidated
        if (watching) {
            return
        }
        watching = true
        val resolver = appContext.contentResolver
        for (uri in searchUris()) {
            runCatching { resolver.registerContentObserver(uri, true, observer) }
        }
    }

    fun clearWatch() {
        onInvalidated = null
    }

    fun search(query: String): List<NoteItem> {
        val needle = SearchText.needle(query)
        if (needle.isEmpty()) {
            return emptyList()
        }
        val compact = SearchText.compact(needle)
        val pool = notes().ifEmpty { queryLive(query) }
        val ranked = ArrayList<Pair<Int, NoteItem>>()
        for (item in pool) {
            val rank = rank(item, needle, compact) ?: continue
            ranked += rank to item
        }
        ranked.sortWith { a, b ->
            val rankCmp = a.first.compareTo(b.first)
            if (rankCmp != 0) rankCmp else b.second.updated.compareTo(a.second.updated)
        }
        if (ranked.size <= MAX_RESULTS) {
            return ranked.map { it.second }
        }
        return ranked.subList(0, MAX_RESULTS).map { it.second }
    }

    private fun notes(): List<NoteItem> {
        if (!dirty) {
            return snapshot
        }
        synchronized(lock) {
            if (!dirty) {
                return snapshot
            }
            dirty = false
            snapshot = loadLocked()
            return snapshot
        }
    }

    private fun loadLocked(): List<NoteItem> {
        val started = System.nanoTime()
        for (uri in searchUris()) {
            val items = queryUri(uri, pattern = null)
            if (items.isEmpty()) {
                continue
            }
            Log.d(TAG, "indexed ${items.size} notes from $uri in ${(System.nanoTime() - started) / 1_000_000}ms")
            return items
        }
        Log.d(TAG, "indexed 0 notes in ${(System.nanoTime() - started) / 1_000_000}ms")
        return emptyList()
    }

    private fun queryLive(query: String): List<NoteItem> {
        for (uri in searchUris()) {
            val items = queryUri(uri, pattern = query)
            if (items.isNotEmpty()) {
                return items
            }
        }
        return emptyList()
    }

    private fun searchUris(): List<Uri> {
        val uris = ArrayList<Uri>(AUTHORITIES.size)
        val seen = HashSet<String>()
        for (authority in AUTHORITIES) {
            if (appContext.packageManager.resolveContentProvider(authority, 0) == null) {
                continue
            }
            val uri = Uri.parse("content://$authority/Search")
            if (seen.add(uri.toString())) {
                uris += uri
            }
        }
        return uris
    }

    private fun queryUri(base: Uri, pattern: String?): List<NoteItem> {
        val uri = if (pattern.isNullOrEmpty()) {
            base
        } else {
            base.buildUpon().appendQueryParameter("pattern", pattern).build()
        }
        val cursor = runCatching {
            appContext.contentResolver.query(uri, null, null, null, "updated DESC")
        }.getOrNull() ?: return emptyList()
        val items = ArrayList<NoteItem>(32)
        cursor.use { rows ->
            val idIdx = rows.columnIndex("_id", "id")
            val guidIdx = rows.columnIndex("note_guid", "guid")
            val contentIdx = rows.columnIndex("content", "text", "body", "title")
            val updatedIdx = rows.columnIndex("updated", "modified", "date")
            if (contentIdx < 0 && guidIdx < 0) {
                return emptyList()
            }
            var count = 0
            while (rows.moveToNext() && count < MAX_INDEX) {
                val raw = rows.stringAt(contentIdx)?.trim().orEmpty()
                val content = stripBoxes(raw)
                if (content.isEmpty()) {
                    continue
                }
                val guid = rows.stringAt(guidIdx).orEmpty()
                val id = when {
                    guid.isNotEmpty() -> guid
                    idIdx >= 0 -> rows.getLong(idIdx).toString()
                    else -> content.hashCode().toString()
                }
                items += toItem(id, guid.ifEmpty { id }, content, rows.longAt(updatedIdx))
                count++
            }
        }
        return items
    }

    private fun toItem(id: String, guid: String, content: String, updated: Long): NoteItem {
        val clipped = if (content.length > BODY_INDEX_CHARS) {
            content.substring(0, BODY_INDEX_CHARS)
        } else {
            content
        }
        val lines = clipped.split('\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }
        val title = lines.firstOrNull().orEmpty()
        val body = if (lines.size > 1) lines.drop(1).joinToString(" ") else clipped
        return NoteItem(
            id = id,
            guid = guid,
            title = title.ifEmpty { clipped },
            body = body,
            updated = updated,
            keys = SearchText.keys(clipped),
        )
    }

    private fun rank(item: NoteItem, needle: String, compact: String): Int? {
        SearchText.rank(item.keys, needle, compact)?.let { return it }
        val title = item.title.lowercase()
        val body = item.body.lowercase()
        return when {
            title.contains(needle) -> 5
            body.contains(needle) -> 6
            else -> null
        }
    }

    companion object {
        private const val TAG = "NotesSearch"
        private const val MAX_RESULTS = 20
        private const val MAX_INDEX = 800
        private const val BODY_INDEX_CHARS = 800
        private val AUTHORITIES = arrayOf(
            "com.coloros.note.notesprovider",
            "com.nearme.note",
            "com.oplus.note.notesprovider",
            "com.coloros.note",
            "com.nearme.note.notesprovider",
            "com.oplus.note",
        )

        private fun stripBoxes(raw: String): String {
            if (raw.isEmpty()) {
                return raw
            }
            return raw.replace("□", "").replace("■", "").trim()
        }
    }
}

private fun Cursor.columnIndex(vararg names: String): Int {
    for (name in names) {
        val index = getColumnIndex(name)
        if (index >= 0) {
            return index
        }
    }
    return -1
}

private fun Cursor.stringAt(index: Int): String? {
    if (index < 0 || isNull(index)) {
        return null
    }
    return getString(index)
}

private fun Cursor.longAt(index: Int): Long {
    if (index < 0 || isNull(index)) {
        return 0L
    }
    return getLong(index)
}
