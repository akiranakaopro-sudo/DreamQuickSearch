package gd.app.quicksearch.search.apps

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class AppSearch(
    private val index: InstalledAppIndex,
    private val onResult: (query: String, apps: List<InstalledApp>) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "app-search").apply { isDaemon = true }
    }
    private val debounce = Runnable { dispatch(pendingQuery) }
    private var pendingQuery = ""

    fun warm() {
        index.warm(executor)
    }

    fun onQueryChanged(raw: String) {
        pendingQuery = raw
        main.removeCallbacks(debounce)
        if (raw.isBlank()) {
            publish("", emptyList())
            return
        }
        main.postDelayed(debounce, DEBOUNCE_MS)
    }

    fun submitNow(raw: String) {
        pendingQuery = raw
        main.removeCallbacks(debounce)
        dispatch(raw)
    }

    fun refresh() {
        index.invalidate()
        index.warm(executor)
        dispatch(pendingQuery)
    }

    fun release() {
        main.removeCallbacks(debounce)
        generation.incrementAndGet()
        executor.shutdownNow()
    }

    private fun dispatch(raw: String) {
        val query = raw.trim()
        val token = generation.incrementAndGet()
        if (query.isEmpty()) {
            publish("", emptyList())
            return
        }
        executor.execute {
            if (token != generation.get()) {
                return@execute
            }
            val started = System.nanoTime()
            val apps = index.search(query)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            Log.d(TAG, "query='$query' hits=${apps.size} ${elapsedMs}ms")
            main.post {
                if (token == generation.get()) {
                    onResult(query, apps)
                }
            }
        }
    }

    private fun publish(query: String, apps: List<InstalledApp>) {
        generation.incrementAndGet()
        onResult(query, apps)
    }

    companion object {
        private const val TAG = "AppSearch"
        private const val DEBOUNCE_MS = 50L
    }
}
