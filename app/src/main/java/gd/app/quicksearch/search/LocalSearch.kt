package gd.app.quicksearch.search

import android.os.Handler
import android.os.Looper
import android.util.Log
import gd.app.quicksearch.search.apps.InstalledApp
import gd.app.quicksearch.search.apps.InstalledAppIndex
import gd.app.quicksearch.search.contacts.ContactItem
import gd.app.quicksearch.search.contacts.ContactsIndex
import gd.app.quicksearch.search.files.FileItem
import gd.app.quicksearch.search.files.FilesIndex
import gd.app.quicksearch.search.messages.MessageItem
import gd.app.quicksearch.search.messages.MessagesIndex
import gd.app.quicksearch.search.notes.NoteItem
import gd.app.quicksearch.search.notes.NotesIndex
import gd.app.quicksearch.search.settings.SettingItem
import gd.app.quicksearch.search.settings.SettingsIndex
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class LocalSearch(
    private val apps: InstalledAppIndex,
    private val settings: SettingsIndex,
    private val contacts: ContactsIndex,
    private val messages: MessagesIndex,
    private val notes: NotesIndex,
    private val files: FilesIndex,
    private val listener: Listener,
) {
    interface Listener {
        fun onQueryStarted(query: String)
        fun onApps(query: String, apps: List<InstalledApp>)
        fun onSettings(query: String, settings: List<SettingItem>)
        fun onContacts(query: String, contacts: List<ContactItem>)
        fun onMessages(query: String, messages: List<MessageItem>)
        fun onNotes(query: String, notes: List<NoteItem>)
        fun onFiles(query: String, files: List<FileItem>)
        fun onCleared()
    }

    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)
    private val appsExecutor = newWorker("app-search")
    private val settingsExecutor = newWorker("settings-search")
    private val contactsExecutor = newWorker("contacts-search")
    private val messagesExecutor = newWorker("messages-search")
    private val notesExecutor = newWorker("notes-search")
    private val filesExecutor = newWorker("files-search")
    private val debounce = Runnable { dispatch(pendingQuery) }
    private var pendingQuery = ""
    private var contactsGranted = false
    private var messagesGranted = false
    private var filesGranted = false

    fun warm() {
        apps.warm(appsExecutor)
        settings.warm(settingsExecutor)
        attachNotes()
        contactsGranted = contacts.hasPermission()
        if (contactsGranted) {
            attachContacts()
        }
        messagesGranted = messages.hasPermission()
        if (messagesGranted) {
            attachMessages()
        }
        filesGranted = files.hasPermission()
        if (filesGranted) {
            attachFiles()
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

    fun onSmsPermissionChanged() {
        val granted = messages.hasPermission()
        if (granted == messagesGranted) {
            return
        }
        messagesGranted = granted
        if (granted) {
            attachMessages()
        } else {
            messages.invalidate()
        }
        if (pendingQuery.isNotBlank()) {
            dispatch(pendingQuery)
        }
    }

    fun onStoragePermissionChanged() {
        val granted = files.hasPermission()
        if (granted == filesGranted) {
            return
        }
        filesGranted = granted
        if (granted) {
            attachFiles()
        } else {
            files.invalidate()
        }
        if (pendingQuery.isNotBlank()) {
            dispatch(pendingQuery)
        }
    }

    fun release() {
        main.removeCallbacks(debounce)
        generation.incrementAndGet()
        contacts.clearWatch()
        messages.clearWatch()
        notes.clearWatch()
        files.clearWatch()
        appsExecutor.shutdownNow()
        settingsExecutor.shutdownNow()
        contactsExecutor.shutdownNow()
        messagesExecutor.shutdownNow()
        notesExecutor.shutdownNow()
        filesExecutor.shutdownNow()
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

    private fun attachMessages() {
        messages.invalidate()
        messages.warm(messagesExecutor)
        messages.watch {
            main.post {
                if (pendingQuery.isNotBlank()) {
                    dispatch(pendingQuery)
                }
            }
        }
    }

    private fun attachNotes() {
        notes.invalidate()
        notes.warm(notesExecutor)
        notes.watch {
            main.post {
                if (pendingQuery.isNotBlank()) {
                    dispatch(pendingQuery)
                }
            }
        }
    }

    private fun attachFiles() {
        files.invalidate()
        files.warm(filesExecutor)
        files.watch {
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
        messagesExecutor.execute { searchMessages(token, query) }
        notesExecutor.execute { searchNotes(token, query) }
        filesExecutor.execute { searchFiles(token, query) }
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

    private fun searchMessages(token: Int, query: String) {
        if (token != generation.get()) {
            return
        }
        val started = System.nanoTime()
        val result = messages.search(query)
        Log.d(TAG, "messages query='$query' hits=${result.size} ${(System.nanoTime() - started) / 1_000_000}ms")
        main.post {
            if (token == generation.get()) {
                listener.onMessages(query, result)
            }
        }
    }

    private fun searchNotes(token: Int, query: String) {
        if (token != generation.get()) {
            return
        }
        val started = System.nanoTime()
        val result = notes.search(query)
        Log.d(TAG, "notes query='$query' hits=${result.size} ${(System.nanoTime() - started) / 1_000_000}ms")
        main.post {
            if (token == generation.get()) {
                listener.onNotes(query, result)
            }
        }
    }

    private fun searchFiles(token: Int, query: String) {
        if (token != generation.get()) {
            return
        }
        val started = System.nanoTime()
        val result = files.search(query)
        Log.d(TAG, "files query='$query' hits=${result.size} ${(System.nanoTime() - started) / 1_000_000}ms")
        main.post {
            if (token == generation.get()) {
                listener.onFiles(query, result)
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
