package gd.app.quicksearch.search.files

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import gd.app.quicksearch.search.SearchText
import java.util.concurrent.Executor

class FilesIndex(context: Context) {

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
    private var snapshot: List<FileItem> = emptyList()
    private var onInvalidated: (() -> Unit)? = null

    fun hasPermission(): Boolean {
        return neededPermissions().any {
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun invalidate() {
        dirty = true
    }

    fun warm(executor: Executor) {
        executor.execute { files() }
    }

    fun watch(onInvalidated: () -> Unit) {
        this.onInvalidated = onInvalidated
        if (watching || !hasPermission()) {
            return
        }
        watching = true
        runCatching {
            appContext.contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri("external"),
                true,
                observer,
            )
        }
    }

    fun clearWatch() {
        onInvalidated = null
    }

    fun search(query: String): List<FileItem> {
        if (!hasPermission()) {
            return emptyList()
        }
        val needle = SearchText.needle(query)
        if (needle.isEmpty()) {
            return emptyList()
        }
        val compact = SearchText.compact(needle)
        val ranked = ArrayList<Pair<Int, FileItem>>()
        for (item in files()) {
            val rank = SearchText.rank(item.keys, needle, compact) ?: continue
            ranked += rank to item
        }
        ranked.sortWith { a, b ->
            val rankCmp = a.first.compareTo(b.first)
            if (rankCmp != 0) rankCmp else b.second.date.compareTo(a.second.date)
        }
        if (ranked.size <= MAX_RESULTS) {
            return ranked.map { it.second }
        }
        return ranked.subList(0, MAX_RESULTS).map { it.second }
    }

    private fun files(): List<FileItem> {
        if (!hasPermission()) {
            return emptyList()
        }
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

    private fun loadLocked(): List<FileItem> {
        val started = System.nanoTime()
        val items = LinkedHashMap<String, FileItem>(MAX_INDEX)
        loadUri(MediaStore.Files.getContentUri("external"), items)
        if (Build.VERSION.SDK_INT >= 29 && items.size < MAX_INDEX) {
            loadUri(MediaStore.Downloads.EXTERNAL_CONTENT_URI, items)
        }
        Log.d(TAG, "indexed ${items.size} files in ${(System.nanoTime() - started) / 1_000_000}ms")
        return items.values.toList()
    }

    private fun loadUri(uri: Uri, items: LinkedHashMap<String, FileItem>) {
        if (items.size >= MAX_INDEX) {
            return
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            pathColumn(),
        )
        val cursor = runCatching {
            appContext.contentResolver.query(
                uri,
                projection,
                "${MediaStore.MediaColumns.SIZE}>0",
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )
        }.getOrNull() ?: return
        cursor.use { rows ->
            val idIdx = rows.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameIdx = rows.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeIdx = rows.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val dateIdx = rows.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val pathIdx = rows.getColumnIndex(pathColumn())
            if (idIdx < 0 || nameIdx < 0) {
                return
            }
            while (rows.moveToNext() && items.size < MAX_INDEX) {
                val name = rows.stringAt(nameIdx)?.trim().orEmpty()
                if (name.isEmpty()) {
                    continue
                }
                val id = rows.getLong(idIdx)
                val key = "$uri/$id"
                if (items.containsKey(key)) {
                    continue
                }
                val mime = rows.stringAt(mimeIdx).orEmpty().ifEmpty { mimeOf(name) }
                val path = rows.stringAt(pathIdx)?.trim().orEmpty()
                items[key] = FileItem(
                    id = key,
                    name = name,
                    mime = mime,
                    uri = ContentUris.withAppendedId(uri, id),
                    path = path,
                    date = rows.longAt(dateIdx),
                    keys = SearchText.keys(name, path),
                )
            }
        }
    }

    companion object {
        private const val TAG = "FilesSearch"
        private const val MAX_RESULTS = 20
        private const val MAX_INDEX = 2000

        fun neededPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= 33) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                )
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        private fun pathColumn(): String {
            return if (Build.VERSION.SDK_INT >= 29) {
                MediaStore.MediaColumns.RELATIVE_PATH
            } else {
                MediaStore.MediaColumns.DATA
            }
        }

        private fun mimeOf(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty()) {
                return ""
            }
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext).orEmpty()
        }
    }
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
