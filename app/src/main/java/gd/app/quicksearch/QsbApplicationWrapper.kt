package gd.app.quicksearch

import android.app.Application
import android.content.Context
import gd.app.quicksearch.search.apps.InstalledAppIndex
import gd.app.quicksearch.search.calendar.CalendarIndex
import gd.app.quicksearch.search.contacts.ContactsIndex
import gd.app.quicksearch.search.files.FilesIndex
import gd.app.quicksearch.search.messages.MessagesIndex
import gd.app.quicksearch.search.notes.NotesIndex
import gd.app.quicksearch.search.settings.SettingsIndex
import gd.app.quicksearch.ui.home.SearchHomeBackdrop

class QsbApplicationWrapper : Application() {

    val installedApps by lazy { InstalledAppIndex(this) }
    val settings by lazy { SettingsIndex(this) }
    val contacts by lazy { ContactsIndex(this) }
    val messages by lazy { MessagesIndex(this) }
    val notes by lazy { NotesIndex(this) }
    val calendar by lazy { CalendarIndex(this) }
    val files by lazy { FilesIndex(this) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // COUI spring overscroll writes View.mScrollY via reflection (ViewNative).
        // Double-reflection VMRuntime is filtered on generic Android 14; LSPosed
        // HiddenApiBypass still reaches the field.
        try {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("L")
        } catch (_: Throwable) {
        }
        // Wallpaper / blur reflection (Dialer pattern).
        HiddenApiExempt.apply()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Warm wallpaper frost like Dialer InCallApp.prefetchBlurredWallpaper.
        SearchHomeBackdrop.prefetch(this)
    }

    companion object {
        private lateinit var instance: QsbApplicationWrapper

        fun app(): QsbApplicationWrapper = instance

        fun appContext(): Context = instance.applicationContext
    }
}
