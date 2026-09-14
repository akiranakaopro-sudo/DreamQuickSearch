package gd.app.quicksearch

import android.app.Application
import android.content.Context
import gd.app.quicksearch.search.apps.InstalledAppIndex
import gd.app.quicksearch.search.contacts.ContactsIndex
import gd.app.quicksearch.search.messages.MessagesIndex
import gd.app.quicksearch.search.notes.NotesIndex
import gd.app.quicksearch.search.settings.SettingsIndex

class QsbApplicationWrapper : Application() {

    val installedApps by lazy { InstalledAppIndex(this) }
    val settings by lazy { SettingsIndex(this) }
    val contacts by lazy { ContactsIndex(this) }
    val messages by lazy { MessagesIndex(this) }
    val notes by lazy { NotesIndex(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private lateinit var instance: QsbApplicationWrapper

        fun app(): QsbApplicationWrapper = instance

        fun appContext(): Context = instance.applicationContext
    }
}
