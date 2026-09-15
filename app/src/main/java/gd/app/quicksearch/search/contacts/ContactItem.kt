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

    fun callIntent(): Intent? {
        val number = phone?.trim().orEmpty()
        if (number.isEmpty()) {
            return null
        }
        return Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun messageIntent(): Intent? {
        val number = phone?.trim().orEmpty()
        if (number.isEmpty()) {
            return null
        }
        return Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
