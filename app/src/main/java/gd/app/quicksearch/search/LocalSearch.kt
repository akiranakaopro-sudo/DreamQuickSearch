package gd.app.quicksearch.search

import android.os.Handler
import android.os.Looper
import android.util.Log
import gd.app.quicksearch.search.apps.InstalledApp
import gd.app.quicksearch.search.apps.InstalledAppIndex
import gd.app.quicksearch.search.contacts.ContactItem
import gd.app.quicksearch.search.contacts.ContactsIndex
import gd.app.quicksearch.search.settings.SettingItem
import gd.app.quicksearch.search.settings.SettingsIndex
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class LocalSearch(
    private val apps: InstalledAppIndex,
    private val settings: SettingsIndex,
    private val contacts: ContactsIndex,
    private val listener: Listener,
) {
    interface Listener {
        fun onQueryStarted(query: String)
        fun onApps(query: String, apps: List<InstalledApp>)
        fun onSettings(query: String, settings: List<SettingItem>)
        fun onContacts(query: String, contacts: List<ContactItem>)
        fun onCleared()
    }

    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)
    private val appsExecutor = newWorker("app-search")
    private val settingsExecutor = newWorker("settings-search")
    private val contactsExecutor = newWorker("contacts-search")
    private val debounce = Runnable { dispatch(pendingQuery) }
    private var pendingQuery = ""
    private var contactsGranted = false

    fun warm() {
        apps.warm(appsExecutor)
        settings.warm(settingsExecutor)
        contactsGranted = contacts.hasPermission()
        if (contactsGranted) {
            attachContacts()
        }
    }

    fun onQueryChanged(raw: String) {
        pendingQuery = raw
        main.removeCallbacks(debounce)
        if (raw.isBlank()) {
            generation.incrementAndGet()
            listener.onCleared()
            return
        }
        main.postDelayed(debounce, DEBOUNCE_MS)
    }

    fun submitNow(raw: String) {
        pendingQuery = raw
        main.removeCallbacks(debounce)
        dispatch(raw)
    }

    fun refreshApps() {
        apps.invalidate()
        apps.warm(appsExecutor)
        if (pendingQuery.isNotBlank()) {
            dispatch(pendingQuery)
        }
    }

    fun onContactsPermissionChanged() {
        val granted = contacts.hasPermission()
        if (granted == contactsGranted) {
            return
        }
        contactsGranted = granted
        if (granted) {
            attachContacts()
        } else {
            contacts.invalidate()
        }
        if (pendingQuery.isNotBlank()) {
            dispatch(pendingQuery)
        }
    }

    fun release() {
        main.removeCallbacks(debounce)
        generation.incrementAndGet()
        contacts.clearWatch()
        appsExecutor.shutdownNow()
        settingsExecutor.shutdownNow()
        contactsExecutor.shutdownNow()
    }

    private fun attachContacts() {
        contacts.invalidate()
        contacts.warm(contactsExecutor)
        contacts.watch {
            main.post {
                if (pendingQuery.isNotBlank()) {
                    dispatch(pendingQuery)
                }
            }
        }
    }

    private fun dispatch(raw: String) {
        val query = raw.trim()
        val token = generation.incrementAndGet()
        if (query.isEmpty()) {
            listener.onCleared()
            return
        }
        listener.onQueryStarted(query)
        appsExecutor.execute { searchApps(token, query) }
        settingsExecutor.execute { searchSettings(token, query) }
        contactsExecutor.execute { searchContacts(token, query) }
    }

    private fun searchApps(token: Int, query: String) {
        if (token != generation.get()) {
            return
        }
        val started = System.nanoTime()
        val result = apps.search(query)
        Log.d(TAG, "apps query='$query' hits=${result.size} ${(System.nanoTime() - started) / 1_000_000}ms")
        main.post {
            if (token == generation.get()) {
                listener.onApps(query, result)
            }
        }
    }

    private fun searchSettings(token: Int, query: String) {
        if (token != generation.get()) {
            return
        }
        val started = System.nanoTime()
        val result = settings.search(query)
        Log.d(TAG, "settings query='$query' hits=${result.size} ${(System.nanoTime() - started) / 1_000_000}ms")
        main.post {
            if (token == generation.get()) {
                listener.onSettings(query, result)
            }
        }
    }

    private fun searchContacts(token: Int, query: String) {
        if (token != generation.get()) {
            return
        }
        val started = System.nanoTime()
        val result = contacts.search(query)
        Log.d(TAG, "contacts query='$query' hits=${result.size} ${(System.nanoTime() - started) / 1_000_000}ms")
        main.post {
            if (token == generation.get()) {
                listener.onContacts(query, result)
            }
        }
    }

    companion object {
        private const val TAG = "LocalSearch"
        private const val DEBOUNCE_MS = 50L

        private fun newWorker(name: String): ExecutorService =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, name).apply { isDaemon = true }
            }
    }
}
