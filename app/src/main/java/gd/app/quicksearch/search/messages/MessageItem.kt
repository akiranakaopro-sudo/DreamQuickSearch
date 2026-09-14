package gd.app.quicksearch.search.messages

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import gd.app.quicksearch.search.MatchKeys

data class MessageItem(
    val id: String,
    val threadId: Long,
    val address: String,
    val sender: String,
    val body: String,
    val date: Long,
    val addressDigits: String,
    val keys: MatchKeys,
) {
    fun conversationIntents(context: Context): List<Intent> {
        val thread = resolvedThreadId(context)
        if (thread <= 0L) {
            return emptyList()
        }
        val uri = Uri.parse("content://mms-sms/conversations/$thread")
        val defaultPkg = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
        val intents = ArrayList<Intent>(12)
        val seen = HashSet<String>()
        fun add(intent: Intent) {
            intent.addFlags(LAUNCH_FLAGS)
            intent.putExtra(EXTRA_THREAD_ID, thread)
            intent.putExtra("fromNotification", true)
            val key = "${intent.component?.flattenToShortString()}|${intent.`package`}|${intent.action}|${intent.data}"
            if (seen.add(key)) {
                intents += intent
            }
        }

        if (!defaultPkg.isNullOrEmpty()) {
            for (component in conversationComponents(context, defaultPkg)) {
                add(
                    Intent(Intent.ACTION_VIEW, uri).setComponent(component),
                )
            }
            add(Intent(Intent.ACTION_VIEW, uri).setPackage(defaultPkg))
            add(
                Intent(Intent.ACTION_VIEW)
                    .setPackage(defaultPkg)
                    .setType(MMS_SMS_TYPE)
                    .putExtra(EXTRA_THREAD_ID, thread),
            )
        }
        add(Intent(Intent.ACTION_VIEW, uri))
        add(Intent(Intent.ACTION_VIEW).setDataAndType(uri, MMS_SMS_TYPE))
        add(Intent(Intent.ACTION_VIEW).setType(MMS_SMS_TYPE).putExtra(EXTRA_THREAD_ID, thread))
        return intents
    }

    private fun resolvedThreadId(context: Context): Long {
        if (threadId > 0L) {
            return threadId
        }
        if (address.isEmpty()) {
            return 0L
        }
        val fromSms = runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID),
                "${Telephony.Sms.ADDRESS}=?",
                arrayOf(address),
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }
        }.getOrNull() ?: 0L
        if (fromSms > 0L) {
            return fromSms
        }
        return runCatching { Telephony.Threads.getOrCreateThreadId(context, address) }.getOrDefault(0L)
    }

    private fun conversationComponents(context: Context, pkg: String): List<ComponentName> {
        val ranked = ArrayList<Pair<Int, ComponentName>>()
        val seen = HashSet<String>()
        fun offer(component: ComponentName, rank: Int) {
            if (!seen.add(component.className)) {
                return
            }
            ranked += rank to component
        }
        for (known in KNOWN_CONVERSATION_COMPONENTS) {
            if (known.packageName == pkg) {
                offer(known, rankOf(known.className) - 10)
            }
        }
        val info = runCatching {
            context.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
        }.getOrNull()
        for (activity in info?.activities.orEmpty()) {
            if (!activity.exported) {
                continue
            }
            val name = activity.name ?: continue
            val rank = rankOf(name)
            if (rank < 100) {
                offer(ComponentName(pkg, name), rank)
            }
        }
        ranked.sortBy { it.first }
        return ranked.map { it.second }
    }

    companion object {
        private const val EXTRA_THREAD_ID = "thread_id"
        private const val MMS_SMS_TYPE = "vnd.android-dir/mms-sms"
        private const val LAUNCH_FLAGS =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

        private val KNOWN_CONVERSATION_COMPONENTS = arrayOf(
            ComponentName("com.android.mms", "com.android.mms.ui.ComposeMessageActivity"),
            ComponentName("com.oplus.mms", "com.android.mms.ui.ComposeMessageActivity"),
            ComponentName("com.oplus.mms", "com.oplus.mms.ui.conversation.ConversationDetailActivity"),
            ComponentName("com.coloros.mms", "com.android.mms.ui.ComposeMessageActivity"),
            ComponentName("com.heytap.mms", "com.android.mms.ui.ComposeMessageActivity"),
            ComponentName("com.android.messaging", "com.android.messaging.ui.conversation.ConversationActivity"),
        )

        private fun rankOf(className: String): Int {
            val name = className.lowercase()
            return when {
                name.endsWith("composemessageactivity") -> 0
                name.contains("conversationdetail") -> 1
                name.endsWith("conversationactivity") -> 2
                name.contains("composemessage") -> 3
                name.contains("conversation") &&
                    !name.contains("list") &&
                    !name.contains("setting") &&
                    !name.contains("search") -> 4
                else -> 100
            }
        }
    }
}
