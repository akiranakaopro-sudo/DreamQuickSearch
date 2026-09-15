package gd.app.quicksearch.search

import androidx.annotation.StringRes
import gd.app.quicksearch.R

/** One result group on the search home; also the target of its "More" button. */
enum class SearchCategory(val key: String, @StringRes val titleRes: Int) {
    APPS("apps", R.string.search_section_apps),
    CONTACTS("contacts", R.string.search_section_contacts),
    MESSAGES("messages", R.string.search_section_messages),
    NOTES("notes", R.string.search_section_notes),
    CALENDAR("calendar", R.string.search_section_calendar),
    FILES("files", R.string.search_section_files),
    SETTINGS("settings", R.string.search_section_settings),
    ;

    companion object {
        /** Results shown inline per category before the user taps "More". */
        const val COLLAPSED_COUNT = 3

        fun of(key: String?): SearchCategory? = values().firstOrNull { it.key == key }
    }
}
