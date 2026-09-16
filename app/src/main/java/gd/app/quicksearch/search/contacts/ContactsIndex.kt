package gd.app.quicksearch.search.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import android.util.Log
import androidx.core.content.ContextCompat
import gd.app.quicksearch.search.SearchText
import java.text.Collator
import java.util.concurrent.Executor

class ContactsIndex(context: Context) {

    private val appContext = context.applicationContext
    private val lock = Any()
    private val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            invalidate()
            onInvalidated?.invoke()
        }
    }

    @Volatile
    private var dirty = true
    @Volatile
    private var watching = false
    private var snapshot: List<ContactItem> = emptyList()
    private var onInvalidated: (() -> Unit)? = null

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun invalidate() {
        dirty = true
    }

    fun warm(executor: Executor) {
        executor.execute { contacts() }
    }

    fun watch(onInvalidated: () -> Unit) {
        this.onInvalidated = onInvalidated
        if (watching || !hasPermission()) {
            return
        }
        watching = true
        appContext.contentResolver.registerContentObserver(Contacts.CONTENT_URI, true, observer)
    }

    fun clearWatch() {
        onInvalidated = null
    }

    fun search(query: String): List<ContactItem> {
        if (!hasPermission()) {
            return emptyList()
        }
        val needle = SearchText.needle(query)
        if (needle.isEmpty()) {
            return emptyList()
        }
        val compact = SearchText.compact(needle)
        val digits = digitsOf(needle)
        val ranked = ArrayList<Pair<Int, ContactItem>>()
        val pool = if (dirty) filteredContacts(query) ?: contacts() else contacts()
        for (contact in pool) {
            val rank = rank(contact, needle, compact, digits) ?: continue
            ranked += rank to contact
        }
        ranked.sortWith { a, b ->
            val rankCmp = a.first.compareTo(b.first)
            if (rankCmp != 0) rankCmp else collator.compare(a.second.name, b.second.name)
        }
        if (ranked.size <= MAX_RESULTS) {
            return ranked.map { it.second }
        }
        return ranked.subList(0, MAX_RESULTS).map { it.second }
    }

    private fun contacts(): List<ContactItem> {
        if (!hasPermission()) {
            return emptyList()
        }
        if (!dirty) {
            return snapshot
        }
        synchronized(lock) {
            if (!dirty) {
                return snapshot
            }
            dirty = false
            snapshot = loadLocked()
            return snapshot
        }
    }

    private fun loadLocked(): List<ContactItem> {
        val started = System.nanoTime()
        val drafts = LinkedHashMap<Long, Draft>()
        loadNames(drafts)
        loadPhones(drafts)
        val items = toItems(drafts)
        Log.d(TAG, "indexed ${items.size} contacts in ${(System.nanoTime() - started) / 1_000_000}ms")
        return items
    }

    private fun filteredContacts(query: String): List<ContactItem>? {
        val started = System.nanoTime()
        val uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(query))
        val cursor = runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(
                    Phone.CONTACT_ID,
                    Contacts.DISPLAY_NAME_PRIMARY,
                    Contacts.LOOKUP_KEY,
                    Phone.NUMBER,
                    Contacts.PHOTO_THUMBNAIL_URI,
                ),
                null,
                null,
                null,
            )
        }.getOrNull() ?: return null
        val drafts = LinkedHashMap<Long, Draft>()
        cursor.use { rows ->
            val idIdx = rows.getColumnIndex(Phone.CONTACT_ID)
            val nameIdx = rows.getColumnIndex(Contacts.DISPLAY_NAME_PRIMARY)
            val lookupIdx = rows.getColumnIndex(Contacts.LOOKUP_KEY)
            val numberIdx = rows.getColumnIndex(Phone.NUMBER)
            val photoIdx = rows.getColumnIndex(Contacts.PHOTO_THUMBNAIL_URI)
            if (idIdx < 0) {
                return null
            }
            while (rows.moveToNext()) {
                val id = rows.getLong(idIdx)
                val draft = drafts.getOrPut(id) {
                    Draft(
                        id = id,
                        lookupKey = rows.stringAt(lookupIdx),
                        name = rows.stringAt(nameIdx)?.trim().orEmpty(),
                        photoUri = rows.stringAt(photoIdx),
                    )
                }
                val number = rows.stringAt(numberIdx)?.trim().orEmpty()
                if (number.isNotEmpty() && number !in draft.phones) {
                    draft.phones += number
                    if (draft.primaryPhone == null) {
                        draft.primaryPhone = number
                    }
                }
                val numberDigits = digitsOf(number)
                if (numberDigits.isNotEmpty() && numberDigits !in draft.digits) {
                    draft.digits += numberDigits
                }
                if (drafts.size >= MAX_FILTERED_CONTACTS) {
                    break
                }
            }
        }
        val items = toItems(drafts)
        Log.d(TAG, "filtered ${items.size} contacts in ${(System.nanoTime() - started) / 1_000_000}ms")
        return items
    }

    private fun toItems(drafts: LinkedHashMap<Long, Draft>): List<ContactItem> {
        val items = ArrayList<ContactItem>(drafts.size)
        for (draft in drafts.values) {
            val name = draft.name.ifBlank { draft.primaryPhone.orEmpty() }
            if (name.isEmpty()) {
                continue
            }
            val keywords = (draft.phones + draft.digits).joinToString(",")
            items += ContactItem(
                id = draft.id,
                lookupKey = draft.lookupKey,
                name = name,
                phone = draft.primaryPhone,
                phoneDigits = draft.digits,
                photoUri = draft.photoUri,
                keys = SearchText.keys(name, keywords),
            )
        }
        items.sortWith { a, b -> collator.compare(a.name, b.name) }
        return items
    }

    private fun loadNames(drafts: LinkedHashMap<Long, Draft>) {
        val cursor = runCatching {
            appContext.contentResolver.query(
                Contacts.CONTENT_URI,
                arrayOf(
                    Contacts._ID,
                    Contacts.DISPLAY_NAME_PRIMARY,
                    Contacts.LOOKUP_KEY,
                    Contacts.PHOTO_THUMBNAIL_URI,
                ),
                null,
                null,
                null,
            )
        }.getOrNull() ?: return
        cursor.use { rows ->
            val idIdx = rows.getColumnIndex(Contacts._ID)
            val nameIdx = rows.getColumnIndex(Contacts.DISPLAY_NAME_PRIMARY)
            val lookupIdx = rows.getColumnIndex(Contacts.LOOKUP_KEY)
            val photoIdx = rows.getColumnIndex(Contacts.PHOTO_THUMBNAIL_URI)
            if (idIdx < 0) {
                return
            }
            while (rows.moveToNext()) {
                val id = rows.getLong(idIdx)
                drafts[id] = Draft(
                    id = id,
                    lookupKey = rows.stringAt(lookupIdx),
                    name = rows.stringAt(nameIdx)?.trim().orEmpty(),
                    photoUri = rows.stringAt(photoIdx),
                )
            }
        }
    }

    private fun loadPhones(drafts: LinkedHashMap<Long, Draft>) {
        val cursor = runCatching {
            appContext.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(
                    Phone.CONTACT_ID,
                    Phone.NUMBER,
                    Phone.NORMALIZED_NUMBER,
                    Phone.IS_SUPER_PRIMARY,
                    Phone.IS_PRIMARY,
                ),
                null,
                null,
                null,
            )
        }.getOrNull() ?: return
        cursor.use { rows ->
            val idIdx = rows.getColumnIndex(Phone.CONTACT_ID)
            val numberIdx = rows.getColumnIndex(Phone.NUMBER)
            val normalizedIdx = rows.getColumnIndex(Phone.NORMALIZED_NUMBER)
            val superIdx = rows.getColumnIndex(Phone.IS_SUPER_PRIMARY)
            val primaryIdx = rows.getColumnIndex(Phone.IS_PRIMARY)
            if (idIdx < 0) {
                return
            }
            while (rows.moveToNext()) {
                val id = rows.getLong(idIdx)
                val draft = drafts.getOrPut(id) { Draft(id = id) }
                val number = rows.stringAt(numberIdx)?.trim().orEmpty()
                if (number.isNotEmpty()) {
                    draft.phones += number
                    if (draft.primaryPhone == null || rows.intAt(superIdx) == 1 || rows.intAt(primaryIdx) == 1) {
                        draft.primaryPhone = number
                    }
                }
                val digits = digitsOf(number).ifEmpty { digitsOf(rows.stringAt(normalizedIdx).orEmpty()) }
                if (digits.isNotEmpty() && digits !in draft.digits) {
                    draft.digits += digits
                }
            }
        }
    }

    private fun rank(contact: ContactItem, needle: String, compact: String, digits: String): Int? {
        SearchText.rank(contact.keys, needle, compact)?.let { return it }
        if (digits.length < MIN_PHONE_DIGITS) {
            return null
        }
        return when {
            contact.phoneDigits.any { it.startsWith(digits) } -> 2
            contact.phoneDigits.any { it.contains(digits) } -> 6
            else -> null
        }
    }

    private class Draft(
        val id: Long,
        var lookupKey: String? = null,
        var name: String = "",
        var photoUri: String? = null,
        val phones: ArrayList<String> = ArrayList(),
        val digits: ArrayList<String> = ArrayList(),
        var primaryPhone: String? = null,
    )

    companion object {
        private const val TAG = "ContactsSearch"
        private const val MAX_RESULTS = 20
        private const val MAX_FILTERED_CONTACTS = 100
        private const val MIN_PHONE_DIGITS = 2

        fun digitsOf(raw: String): String {
            if (raw.isEmpty()) {
                return ""
            }
            val builder = StringBuilder(raw.length)
            for (char in raw) {
                if (char.isDigit()) {
                    builder.append(char)
                }
            }
            return builder.toString()
        }
    }
}

private fun Cursor.stringAt(index: Int): String? {
    if (index < 0 || isNull(index)) {
        return null
    }
    return getString(index)
}

private fun Cursor.intAt(index: Int): Int {
    if (index < 0 || isNull(index)) {
        return 0
    }
    return getInt(index)
}
