package gd.app.quicksearch.search.calendar

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateUtils
import gd.app.quicksearch.search.MatchKeys

data class CalendarItem(
    val id: Long,
    val title: String,
    val location: String,
    val start: Long,
    val allDay: Boolean,
    val keys: MatchKeys,
    val fromDreamCalendar: Boolean = false,
    val isTodo: Boolean = false,
) {
    fun subtitle(context: Context): String {
        if (start <= 0L) {
            return location
        }
        val flags = if (allDay) {
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL
        } else {
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL
        }
        val whenText = DateUtils.formatDateTime(context, start, flags)
        return if (location.isEmpty()) whenText else "$whenText  $location"
    }

    fun viewIntents(): List<Intent> {
        if (fromDreamCalendar && isTodo) {
            return dreamTodoIntents()
        }
        if (fromDreamCalendar) {
            return dreamEventIntents()
        }
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        val intents = ArrayList<Intent>(CALENDAR_PACKAGES.size + 1)
        intents += Intent(Intent.ACTION_VIEW, uri).withEventExtras()
        for (pkg in CALENDAR_PACKAGES) {
            intents += Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).withEventExtras()
        }
        return intents
    }

    private fun dreamEventIntents(): List<Intent> {
        val intents = ArrayList<Intent>(3)
        intents += Intent(DREAM_VIEW_ACTION).setPackage(DREAM_PACKAGE).withDreamEventExtras()
        intents += Intent().setClassName(DREAM_PACKAGE, DREAM_EVENT_INFO).withDreamEventExtras()
        intents += Intent(DREAM_VIEW_ACTION).withDreamEventExtras()
        return intents
    }

    private fun dreamTodoIntents(): List<Intent> {
        val intents = ArrayList<Intent>(3)
        intents += Intent(DREAM_VIEW_TODO_ACTION).setPackage(DREAM_PACKAGE).withDreamTodoExtras()
        intents += Intent().setClassName(DREAM_PACKAGE, DREAM_EDITOR).withDreamTodoExtras()
        intents += Intent(DREAM_VIEW_TODO_ACTION).withDreamTodoExtras()
        return intents
    }

    private fun Intent.withEventExtras(): Intent {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        putExtra("eventId", id.toString())
        return this
    }

    private fun Intent.withDreamEventExtras(): Intent {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        putExtra(DREAM_EXTRA_EVENT_ID, id)
        putExtra(DREAM_EXTRA_BEGIN_TIME, start)
        putExtra(DREAM_EXTRA_END_TIME, -1L)
        return this
    }

    private fun Intent.withDreamTodoExtras(): Intent {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        putExtra(DREAM_EXTRA_TODO_ID, id)
        putExtra(DREAM_EXTRA_INITIAL_MODE, DREAM_TODO_MODE)
        return this
    }

    companion object {
        const val DREAM_PACKAGE = "gd.app.calendar"
        const val DREAM_VIEW_ACTION = "gd.app.calendar.VIEW_EVENT"
        const val DREAM_VIEW_TODO_ACTION = "gd.app.calendar.VIEW_TODO"
        const val DREAM_EVENT_INFO = "com.gdcalendar.feature.editor.eventinfo.EventInfoActivity"
        const val DREAM_EDITOR = "com.gdcalendar.feature.editor.EditorActivity"
        const val DREAM_EXTRA_EVENT_ID = "id"
        const val DREAM_EXTRA_BEGIN_TIME = "beginTime"
        const val DREAM_EXTRA_END_TIME = "endTime"
        const val DREAM_EXTRA_TODO_ID = "com.gdcalendar.feature.editor.EXTRA_TODO_ID"
        const val DREAM_EXTRA_INITIAL_MODE = "com.gdcalendar.feature.editor.EXTRA_INITIAL_MODE"
        const val DREAM_TODO_MODE = "Todos"

        val CALENDAR_PACKAGES = arrayOf(
            DREAM_PACKAGE,
            "com.coloros.calendar",
            "com.oplus.calendar",
            "com.android.calendar",
            "com.google.android.calendar",
        )
    }
}
