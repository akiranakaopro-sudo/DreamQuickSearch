package gd.app.quicksearch.search.notes

import android.content.Intent
import android.net.Uri
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
        val intents = ArrayList<Intent>(NOTE_PACKAGES.size * 2 + 3)
        intents += Intent(DREAM_VIEW_ACTION).setPackage(DREAM_PACKAGE).withDreamExtras()
        intents += Intent().setClassName(DREAM_PACKAGE, DREAM_NOTE_ACTIVITY).withDreamExtras()
        dreamUri()?.let { uri ->
            intents += Intent(Intent.ACTION_VIEW, uri).setPackage(DREAM_PACKAGE).withFlagsOnly()
        }
        for (pkg in NOTE_PACKAGES) {
            if (pkg == DREAM_PACKAGE) continue
            intents += Intent(VIEW_ACTION).setPackage(pkg).putOemExtras()
            intents += Intent(Intent.ACTION_VIEW).setPackage(pkg).putOemExtras()
        }
        intents += Intent(VIEW_ACTION).putOemExtras()
        return intents
    }

    private fun Intent.withFlagsOnly(): Intent {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return this
    }

    private fun Intent.withDreamExtras(): Intent {
        withFlagsOnly()
        noteId()?.let { putExtra("id", it) }
        if (guid.isNotEmpty()) {
            putExtra("guid", guid)
            putExtra("note_guid", guid)
        }
        putExtra("view", true)
        return this
    }

    private fun Intent.putOemExtras(): Intent {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (guid.isNotEmpty()) {
            putExtra("guid", guid)
            putExtra("note_guid", guid)
        }
        putExtra("view", true)
        return this
    }

    private fun noteId(): Long? = guid.toLongOrNull() ?: id.toLongOrNull()

    private fun dreamUri(): Uri? {
        val noteId = noteId() ?: return null
        return Uri.parse("notes://note/$noteId")
    }

    companion object {
        const val VIEW_ACTION = "action.nearme.note.textnote"
        const val DREAM_PACKAGE = "gd.app.note"
        const val DREAM_VIEW_ACTION = "gd.app.note.VIEW_NOTE"
        const val DREAM_NOTE_ACTIVITY = "gd.app.note.widget.WidgetNoteActivity"
        val NOTE_PACKAGES = arrayOf(
            DREAM_PACKAGE,
            "com.coloros.note",
            "com.oplus.note",
            "com.nearme.note",
            "com.oneplus.note",
        )
    }
}
