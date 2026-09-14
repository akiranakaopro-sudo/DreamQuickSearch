package gd.app.quicksearch.search.messages

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import gd.app.quicksearch.R
import gd.app.quicksearch.search.SearchText
import java.util.concurrent.Executor

class MessagesIndex(context: Context) {

    private val appContext = context.applicationContext
    private val lock = Any()
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
    private var snapshot: List<MessageItem> = emptyList()
    private var onInvalidated: (() -> Unit)? = null

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun invalidate() {
        dirty = true
    }

    fun warm(executor: Executor) {
        executor.execute { messages() }
    }

    fun watch(onInvalidated: () -> Unit) {
        this.onInvalidated = onInvalidated
        if (watching || !hasPermission()) {
            return
        }
        watching = true
        val resolver = appContext.contentResolver
        resolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        resolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, observer)
    }

    fun clearWatch() {
        onInvalidated = null
    }

    fun search(query: String): List<MessageItem> {
        if (!hasPermission()) {
            return emptyList()
        }
        val needle = SearchText.needle(query)
        if (needle.isEmpty()) {
            return emptyList()
        }
        val compact = SearchText.compact(needle)
        val digits = digitsOf(needle)
        val ranked = ArrayList<Pair<Int, MessageItem>>()
        for (item in messages()) {
            val rank = rank(item, needle, compact, digits) ?: continue
            ranked += rank to item
        }
        ranked.sortWith { a, b ->
            val rankCmp = a.first.compareTo(b.first)
            if (rankCmp != 0) rankCmp else b.second.date.compareTo(a.second.date)
        }
        if (ranked.size <= MAX_RESULTS) {
            return ranked.map { it.second }
        }
        return ranked.subList(0, MAX_RESULTS).map { it.second }
    }

    private fun messages(): List<MessageItem> {
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

    private fun loadLocked(): List<MessageItem> {
        val started = System.nanoTime()
        val names = loadPhoneNames()
        val items = ArrayList<MessageItem>(MAX_SMS + MAX_MMS)
        loadSms(items, names)
        loadMms(items, names)
        Log.d(TAG, "indexed ${items.size} messages in ${(System.nanoTime() - started) / 1_000_000}ms")
        return items
    }

    private fun loadSms(items: ArrayList<MessageItem>, names: Map<String, String>) {
        val cursor = runCatching {
            appContext.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC",
            )
        }.getOrNull() ?: return
        cursor.use { rows ->
            val idIdx = rows.getColumnIndex(Telephony.Sms._ID)
            val threadIdx = rows.columnIndex(Telephony.Sms.THREAD_ID, "threadid")
            val addressIdx = rows.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = rows.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = rows.getColumnIndex(Telephony.Sms.DATE)
            if (idIdx < 0) {
                return
            }
            var count = 0
            while (rows.moveToNext() && count < MAX_SMS) {
                val body = rows.stringAt(bodyIdx)?.trim().orEmpty()
                val address = rows.stringAt(addressIdx)?.trim().orEmpty()
                if (body.isEmpty() && address.isEmpty()) {
                    continue
                }
                items += toItem(
                    id = "sms:${rows.getLong(idIdx)}",
                    threadId = rows.longAt(threadIdx),
                    address = address,
                    body = body,
                    date = rows.longAt(dateIdx),
                    names = names,
                )
                count++
            }
        }
    }

    private fun loadMms(items: ArrayList<MessageItem>, names: Map<String, String>) {
        val texts = loadMmsTexts()
        val addresses = loadMmsAddresses()
        val cursor = runCatching {
            appContext.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID, Telephony.Mms.THREAD_ID, Telephony.Mms.DATE),
                null,
                null,
                "${Telephony.Mms.DATE} DESC",
            )
        }.getOrNull() ?: return
        cursor.use { rows ->
            val idIdx = rows.getColumnIndex(Telephony.Mms._ID)
            val threadIdx = rows.columnIndex(Telephony.Mms.THREAD_ID, "threadid")
            val dateIdx = rows.getColumnIndex(Telephony.Mms.DATE)
            if (idIdx < 0) {
                return
            }
            var count = 0
            while (rows.moveToNext() && count < MAX_MMS) {
                val id = rows.getLong(idIdx)
                val body = texts[id].orEmpty()
                val address = addresses[id].orEmpty()
                if (body.isEmpty() && address.isEmpty()) {
                    continue
                }
                items += toItem(
                    id = "mms:$id",
                    threadId = rows.longAt(threadIdx),
                    address = address,
                    body = body.ifEmpty { appContext.getString(R.string.search_mms_empty) },
                    date = rows.longAt(dateIdx) * 1000L,
                    names = names,
                )
                count++
            }
        }
    }

    private fun loadMmsTexts(): Map<Long, String> {
        val cursor = runCatching {
            appContext.contentResolver.query(
                Uri.parse("content://mms/part"),
                arrayOf("mid", "text", "ct"),
                "ct=?",
                arrayOf("text/plain"),
                null,
            )
        }.getOrNull() ?: return emptyMap()
        val texts = HashMap<Long, StringBuilder>()
        cursor.use { rows ->
            val midIdx = rows.getColumnIndex("mid")
            val textIdx = rows.getColumnIndex("text")
            if (midIdx < 0) {
                return emptyMap()
            }
            while (rows.moveToNext()) {
                val text = rows.stringAt(textIdx)?.trim().orEmpty()
                if (text.isEmpty()) {
                    continue
                }
                val mid = rows.getLong(midIdx)
                val builder = texts.getOrPut(mid) { StringBuilder() }
                if (builder.isNotEmpty()) {
                    builder.append(' ')
                }
                builder.append(text)
            }
        }
        return texts.mapValues { it.value.toString() }
    }

    private fun loadMmsAddresses(): Map<Long, String> {
        val cursor = runCatching {
            appContext.contentResolver.query(
                Uri.parse("content://mms/addr"),
                arrayOf("msg_id", "address", "type"),
                "type=?",
                arrayOf(MMS_FROM.toString()),
                null,
            )
        }.getOrNull() ?: return emptyMap()
        val addresses = HashMap<Long, String>()
        cursor.use { rows ->
            val idIdx = rows.getColumnIndex("msg_id")
            val addressIdx = rows.getColumnIndex("address")
            if (idIdx < 0) {
                return emptyMap()
            }
            while (rows.moveToNext()) {
                val address = rows.stringAt(addressIdx)?.trim().orEmpty()
                if (address.isEmpty() || address == INSERT_ADDRESS_TOKEN) {
                    continue
                }
                addresses.putIfAbsent(rows.getLong(idIdx), address)
            }
        }
        return addresses
    }

    private fun loadPhoneNames(): Map<String, String> {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return emptyMap()
        }
        val cursor = runCatching {
            appContext.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.NUMBER, Phone.DISPLAY_NAME),
                null,
                null,
                null,
            )
        }.getOrNull() ?: return emptyMap()
        val names = HashMap<String, String>()
        cursor.use { rows ->
            val numberIdx = rows.getColumnIndex(Phone.NUMBER)
            val nameIdx = rows.getColumnIndex(Phone.DISPLAY_NAME)
            if (numberIdx < 0) {
                return emptyMap()
            }
            while (rows.moveToNext()) {
                val digits = digitsOf(rows.stringAt(numberIdx).orEmpty())
                val name = rows.stringAt(nameIdx)?.trim().orEmpty()
                if (digits.isNotEmpty() && name.isNotEmpty()) {
                    names.putIfAbsent(digits, name)
                }
            }
        }
        return names
    }

    private fun toItem(
        id: String,
        threadId: Long,
        address: String,
        body: String,
        date: Long,
        names: Map<String, String>,
    ): MessageItem {
        val clipped = if (body.length > BODY_INDEX_CHARS) body.substring(0, BODY_INDEX_CHARS) else body
        val digits = digitsOf(address)
        val sender = names[digits] ?: address.ifEmpty { clipped }
        val keywords = listOf(sender, address, digits).filter { it.isNotEmpty() }.joinToString(",")
        return MessageItem(
            id = id,
            threadId = threadId,
            address = address,
            sender = sender,
            body = clipped,
            date = date,
            addressDigits = digits,
            keys = SearchText.keys(clipped, keywords),
        )
    }

    private fun rank(item: MessageItem, needle: String, compact: String, digits: String): Int? {
        SearchText.rank(item.keys, needle, compact)?.let { return it }
        if (digits.length < MIN_PHONE_DIGITS) {
            return null
        }
        return when {
            item.addressDigits.startsWith(digits) -> 2
            item.addressDigits.contains(digits) -> 6
            else -> null
        }
    }

    companion object {
        private const val TAG = "MessagesSearch"
        private const val MAX_RESULTS = 20
        private const val MAX_SMS = 1500
        private const val MAX_MMS = 300
        private const val BODY_INDEX_CHARS = 400
        private const val MIN_PHONE_DIGITS = 2
        private const val MMS_FROM = 137
        private const val INSERT_ADDRESS_TOKEN = "insert-address-token"

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

private fun Cursor.columnIndex(vararg names: String): Int {
    for (name in names) {
        val index = getColumnIndex(name)
        if (index >= 0) {
            return index
        }
    }
    return -1
}

private fun Cursor.stringAt(index: Int): String? {
    if (index < 0 || isNull(index)) {
        return null
    }
    return getString(index)
}

private fun Cursor.longAt(index: Int): Long {
    if (index < 0 || isNull(index)) {
        return 0L
    }
    return getLong(index)
}
