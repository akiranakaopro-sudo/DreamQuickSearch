package gd.app.quicksearch.ui.activity

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import gd.app.quicksearch.search.apps.InstalledApp
import gd.app.quicksearch.search.calendar.CalendarItem
import gd.app.quicksearch.search.contacts.ContactItem
import gd.app.quicksearch.search.files.FileItem
import gd.app.quicksearch.search.messages.MessageItem
import gd.app.quicksearch.search.notes.NoteItem
import gd.app.quicksearch.search.settings.SettingItem

/** Opens a search result and closes the search UI, shared by the result screens. */
internal class SearchLauncher(
    private val activity: Activity,
    private val onLaunchFailed: () -> Unit = {},
) {
    fun openApp(app: InstalledApp) {
        launch(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }

    fun openSetting(item: SettingItem) {
        launch(item.launchIntent() ?: return)
    }

    fun openContact(item: ContactItem) {
        launch(item.viewIntent())
    }

    fun callContact(item: ContactItem) {
        launch(item.callIntent() ?: return)
    }

    fun messageContact(item: ContactItem) {
        launch(item.messageIntent() ?: return)
    }

    fun openFile(item: FileItem) {
        launch(item.viewIntent())
    }

    fun openNote(item: NoteItem) {
        launchFirst(item.viewIntents())
    }

    fun openCalendar(item: CalendarItem) {
        launchFirst(item.viewIntents())
    }

    fun openMessage(item: MessageItem) {
        launchFirst(item.conversationIntents(activity))
    }

    private fun launch(intent: Intent) {
        try {
            activity.startActivity(intent)
            closeSearch()
        } catch (_: ActivityNotFoundException) {
            onLaunchFailed()
        }
    }

    private fun launchFirst(intents: List<Intent>) {
        for (intent in intents) {
            try {
                activity.startActivity(intent)
                closeSearch()
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }

    private fun closeSearch() {
        hideIme()
        // A result opened from the category page should close the search stack, not
        // drop the user back onto the list they just left.
        runCatching { activity.finishAffinity() }.onFailure { activity.finish() }
    }

    private fun hideIme() {
        val imm = activity.getSystemService(InputMethodManager::class.java) ?: return
        imm.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
    }
}
