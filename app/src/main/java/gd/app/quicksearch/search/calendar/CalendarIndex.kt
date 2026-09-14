package gd.app.quicksearch.search.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import gd.app.quicksearch.search.SearchText
import java.util.concurrent.Executor

class CalendarIndex(context: Context) {

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
    private var snapshot: List<CalendarItem> = emptyList()
    private var onInvalidated: (() -> Unit)? = null

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun invalidate() {
        dirty = true
    }

    fun warm(executor: Executor) {
        executor.execute { events() }
    }

    fun watch(onInvalidated: () -> Unit) {
        this.onInvalidated = onInvalidated
        if (watching || !hasPermission()) {
            return
        }
        watching = true
        runCatching {
            appContext.contentResolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI,
                true,
                observer,
            )
        }
    }

    fun clearWatch() {
        onInvalidated = null
    }

    fun search(query: String): List<CalendarItem> {
        if (!hasPermission()) {
            return emptyList()
        }
        val needle = SearchText.needle(query)
        if (needle.isEmpty()) {
            return emptyList()
        }
        val compact = SearchText.compact(needle)
        val now = System.currentTimeMillis()
        val ranked = ArrayList<Pair<Int, CalendarItem>>()
        for (item in events()) {
            val rank = SearchText.rank(item.keys, needle, compact) ?: continue
            ranked += rank to item
        }
        ranked.sortWith { a, b ->
            val rankCmp = a.first.compareTo(b.first)
            if (rankCmp != 0) rankCmp else compareStart(a.second.start, b.second.start, now)
        }
        if (ranked.size <= MAX_RESULTS) {
            return ranked.map { it.second }
        }
        return ranked.subList(0, MAX_RESULTS).map { it.second }
    }

    private fun events(): List<CalendarItem> {
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

    private fun loadLocked(): List<CalendarItem> {
        val started = System.nanoTime()
        val cursor = queryEvents(
            "${CalendarContract.Events.DELETED}=0 AND ${CalendarContract.Events.VISIBLE}=1",
        ) ?: queryEvents("${CalendarContract.Events.DELETED}=0")
        if (cursor == null) {
            Log.d(TAG, "indexed 0 events")
            return emptyList()
        }
        val items = ArrayList<CalendarItem>(MAX_INDEX)
        cursor.use { rows ->
            val idIdx = rows.getColumnIndex(CalendarContract.Events._ID)
            val titleIdx = rows.getColumnIndex(CalendarContract.Events.TITLE)
            val descIdx = rows.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            val locIdx = rows.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
            val startIdx = rows.getColumnIndex(CalendarContract.Events.DTSTART)
            val allDayIdx = rows.getColumnIndex(CalendarContract.Events.ALL_DAY)
            if (idIdx < 0) {
                return emptyList()
            }
            while (rows.moveToNext() && items.size < MAX_INDEX) {
                val title = rows.stringAt(titleIdx)?.trim().orEmpty()
                val location = rows.stringAt(locIdx)?.trim().orEmpty()
                val description = rows.stringAt(descIdx)?.trim().orEmpty()
                if (title.isEmpty() && location.isEmpty() && description.isEmpty()) {
                    continue
                }
                val clipped = if (description.length > BODY_INDEX_CHARS) {
                    description.substring(0, BODY_INDEX_CHARS)
                } else {
                    description
                }
                val label = title.ifEmpty { location.ifEmpty { clipped } }
                items += CalendarItem(
                    id = rows.getLong(idIdx),
                    title = label,
                    location = location,
                    start = rows.longAt(startIdx),
                    allDay = allDayIdx >= 0 && rows.getInt(allDayIdx) == 1,
                    keys = SearchText.keys(label, listOf(location, clipped).filter { it.isNotEmpty() }.joinToString(",")),
                )
            }
        }
        Log.d(TAG, "indexed ${items.size} events in ${(System.nanoTime() - started) / 1_000_000}ms")
        return items
    }

    private fun queryEvents(selection: String): Cursor? {
        return runCatching {
            appContext.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.ALL_DAY,
                ),
                selection,
                null,
                "${CalendarContract.Events.DTSTART} DESC",
            )
        }.getOrNull()
    }

    companion object {
        private const val TAG = "CalendarSearch"
        private const val MAX_RESULTS = 20
        private const val MAX_INDEX = 1500
        private const val BODY_INDEX_CHARS = 400

        private fun compareStart(a: Long, b: Long, now: Long): Int {
            val aFuture = a >= now
            val bFuture = b >= now
            return when {
                aFuture && bFuture -> a.compareTo(b)
                aFuture -> -1
                bFuture -> 1
                else -> b.compareTo(a)
            }
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
