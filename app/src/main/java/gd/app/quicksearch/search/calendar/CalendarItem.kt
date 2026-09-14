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
) {
    fun subtitle(context: Context): String {
        val flags = if (allDay) {
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL
        } else {
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL
        }
        val whenText = DateUtils.formatDateTime(context, start, flags)
        return if (location.isEmpty()) whenText else "$whenText  $location"
    }

    fun viewIntents(): List<Intent> {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        val intents = ArrayList<Intent>(CALENDAR_PACKAGES.size + 1)
        intents += Intent(Intent.ACTION_VIEW, uri).withEventExtras()
        for (pkg in CALENDAR_PACKAGES) {
            intents += Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).withEventExtras()
        }
        return intents
    }

    private fun Intent.withEventExtras(): Intent {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        putExtra("eventId", id.toString())
        return this
    }

    companion object {
        val CALENDAR_PACKAGES = arrayOf(
            "com.coloros.calendar",
            "com.oplus.calendar",
            "com.android.calendar",
            "com.google.android.calendar",
        )
    }
}
