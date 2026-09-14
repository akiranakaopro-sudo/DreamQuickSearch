package gd.app.quicksearch.search.notes

import android.content.Intent
import gd.app.quicksearch.search.MatchKeys

data class NoteItem(
    val id: String,
    val guid: String,
    val title: String,
    val body: String,
    val updated: Long,
    val keys: MatchKeys,
) {
    fun viewIntents(): List<Intent> {
        val intents = ArrayList<Intent>(NOTE_PACKAGES.size * 2)
        for (pkg in NOTE_PACKAGES) {
            intents += Intent(VIEW_ACTION).setPackage(pkg).putExtras()
            intents += Intent(Intent.ACTION_VIEW).setPackage(pkg).putExtras()
        }
        intents += Intent(VIEW_ACTION).putExtras()
        return intents
    }

    private fun Intent.putExtras(): Intent {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (guid.isNotEmpty()) {
            putExtra("guid", guid)
            putExtra("note_guid", guid)
        }
        putExtra("view", true)
        return this
    }

    companion object {
        const val VIEW_ACTION = "action.nearme.note.textnote"
        val NOTE_PACKAGES = arrayOf(
            "com.coloros.note",
            "com.oplus.note",
            "com.nearme.note",
            "com.oneplus.note",
        )
    }
}
