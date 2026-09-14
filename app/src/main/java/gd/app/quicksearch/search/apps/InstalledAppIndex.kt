package gd.app.quicksearch.search.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import gd.app.quicksearch.search.SearchText
import java.text.Collator
import java.util.concurrent.Executor

class InstalledAppIndex(context: Context) {

    private val appContext = context.applicationContext
    private val lock = Any()
    private val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    @Volatile
    private var dirty = true
    private var snapshot: List<InstalledApp> = emptyList()

    fun invalidate() {
        dirty = true
    }

    fun warm(executor: Executor) {
        executor.execute { apps() }
    }

    fun search(query: String): List<InstalledApp> {
        val needle = SearchText.needle(query)
        if (needle.isEmpty()) {
            return emptyList()
        }
        val compact = SearchText.compact(needle)
        val ranked = ArrayList<Pair<Int, InstalledApp>>()
        for (app in apps()) {
            val rank = SearchText.rank(app.keys, needle, compact) ?: continue
            ranked += rank to app
        }
        ranked.sortWith { a, b ->
            val rankCmp = a.first.compareTo(b.first)
            if (rankCmp != 0) rankCmp else collator.compare(a.second.label, b.second.label)
        }
        if (ranked.size <= MAX_RESULTS) {
            return ranked.map { it.second }
        }
        return ranked.subList(0, MAX_RESULTS).map { it.second }
    }

    private fun apps(): List<InstalledApp> {
        if (!dirty) {
            return snapshot
        }
        synchronized(lock) {
            while (dirty) {
                dirty = false
                snapshot = loadLocked()
            }
            return snapshot
        }
    }

    private fun loadLocked(): List<InstalledApp> {
        val started = System.nanoTime()
        val pm = appContext.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = queryLaunchers(pm, intent)
        val apps = ArrayList<InstalledApp>(resolved.size)
        val seen = HashSet<ComponentName>(resolved.size)
        for (info in resolved) {
            val activity = info.activityInfo ?: continue
            if (!activity.exported || !activity.enabled || !activity.applicationInfo.enabled) {
                continue
            }
            val component = ComponentName(activity.packageName, activity.name)
            if (!seen.add(component)) {
                continue
            }
            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
            if (label.isEmpty()) {
                continue
            }
            apps += InstalledApp(
                component = component,
                label = label,
                keys = SearchText.keys(label),
            )
        }
        apps.sortWith { a, b -> collator.compare(a.label, b.label) }
        Log.d(TAG, "indexed ${apps.size} apps in ${(System.nanoTime() - started) / 1_000_000}ms")
        return apps
    }

    private fun queryLaunchers(pm: PackageManager, intent: Intent) = if (Build.VERSION.SDK_INT >= 33) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0)
    }

    companion object {
        private const val TAG = "AppSearch"
        private const val MAX_RESULTS = 32
    }
}
