package gd.app.quicksearch.search.contacts

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import gd.app.quicksearch.search.MatchKeys

data class ContactItem(
    val id: Long,
    val lookupKey: String?,
    val name: String,
    val phone: String?,
    val phoneDigits: List<String>,
    val photoUri: String?,
    val keys: MatchKeys,
) {
    fun viewIntent(): Intent {
        val uri = if (!lookupKey.isNullOrEmpty()) {
            ContactsContract.Contacts.getLookupUri(id, lookupKey)
        } else {
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
        }
        return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
