package gd.app.quicksearch.ui.home

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.itemview.COUIBaseListItemView
import com.coui.appcompat.itemview.COUIBaseListItemViewHolder
import gd.app.quicksearch.R
import gd.app.quicksearch.search.contacts.ContactItem

class SearchContactsAdapter(
    private val onContactClicked: (ContactItem) -> Unit,
    private val onCallClicked: (ContactItem) -> Unit,
    private val onMessageClicked: (ContactItem) -> Unit,
) : RecyclerView.Adapter<COUIBaseListItemViewHolder>() {

    private var icon: Drawable? = null
    private val items = ArrayList<ContactItem>()
    private var query = ""

    init {
        setHasStableIds(true)
    }

    fun submit(contacts: List<ContactItem>, query: String) {
        val oldQuery = this.query
        val old = ArrayList(items)
        this.query = query
        items.clear()
        items.addAll(contacts)
        DiffUtil.calculateDiff(Diff(old, items, oldQuery, query), false).dispatchUpdatesTo(this)
        if (items.isNotEmpty()) {
            notifyItemRangeChanged(0, items.size, PAYLOAD_CARD)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long = items[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): COUIBaseListItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_app, parent, false)
        val iconSize = parent.resources.getDimensionPixelSize(R.dimen.search_app_icon_size)
        val item = view as COUIBaseListItemView
        item.iconView.layoutParams = item.iconView.layoutParams.apply {
            width = iconSize
            height = iconSize
        }
        item.setIconStyle(COUIBaseListItemView.ROUND)
        item.setWidgetView(R.layout.item_search_contact_actions)
        SearchCategoryCard.style(item)
        return COUIBaseListItemViewHolder(item)
    }

    override fun onBindViewHolder(holder: COUIBaseListItemViewHolder, position: Int) {
        bind(holder, position)
    }

    override fun onBindViewHolder(
        holder: COUIBaseListItemViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.size == 1 && payloads[0] == PAYLOAD_CARD) {
            SearchCategoryCard.bindCorners(holder, itemCount, position)
            return
        }
        bind(holder, position)
    }

    private fun bind(holder: COUIBaseListItemViewHolder, position: Int) {
        val contact = items[position]
        val item = holder.itemView as COUIBaseListItemView
        item.setTitle(SearchCategoryCard.highlighted(item, contact.name, query))
        item.setSummary(SearchCategoryCard.highlighted(item, contact.phone.orEmpty(), query))
        item.setIcon(iconFor(item))
        bindActions(item, contact)
        item.setOnClickListener { onContactClicked(contact) }
        SearchCategoryCard.bindCorners(holder, itemCount, position)
    }

    private fun bindActions(item: COUIBaseListItemView, contact: ContactItem) {
        val hasPhone = !contact.phone.isNullOrBlank()
        val actions = item.findViewById<View>(android.R.id.widget_frame)
        val call = item.findViewById<View>(R.id.contact_call)
        val message = item.findViewById<View>(R.id.contact_message)
        actions?.visibility = if (hasPhone) View.VISIBLE else View.GONE
        call?.visibility = if (hasPhone) View.VISIBLE else View.GONE
        message?.visibility = if (hasPhone) View.VISIBLE else View.GONE
        call?.setOnClickListener {
            if (hasPhone) {
                onCallClicked(contact)
            }
        }
        message?.setOnClickListener {
            if (hasPhone) {
                onMessageClicked(contact)
            }
        }
    }

    private fun iconFor(item: COUIBaseListItemView): Drawable? {
        icon?.let { return it }
        val launcher = contactsLauncher(item.context.packageManager) ?: return null
        val pm = item.context.packageManager
        val component = ComponentName(launcher.activityInfo.packageName, launcher.activityInfo.name)
        val loaded = runCatching { pm.getActivityIcon(component) }.getOrNull()
            ?: launcher.loadIcon(pm)
        icon = loaded
        return loaded
    }

    private fun contactsLauncher(pm: PackageManager): ResolveInfo? {
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        for (pkg in CONTACT_PACKAGES) {
            pickContactsLauncher(pm, Intent(launch).setPackage(pkg))?.let { return it }
        }
        return pickContactsLauncher(pm, launch)
    }

    private fun pickContactsLauncher(pm: PackageManager, intent: Intent): ResolveInfo? {
        var best: ResolveInfo? = null
        var bestScore = 0
        for (info in queryActivities(pm, intent)) {
            val score = contactsLauncherScore(info, pm)
            if (score > bestScore) {
                bestScore = score
                best = info
            }
        }
        return best
    }

    private fun queryActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
    }

    private fun contactsLauncherScore(info: ResolveInfo, pm: PackageManager): Int {
        val pkg = info.activityInfo?.packageName.orEmpty().lowercase()
        val cls = info.activityInfo?.name.orEmpty().lowercase()
        val label = info.loadLabel(pm)?.toString().orEmpty().lowercase()
        if (isPhoneLauncher(cls, label)) {
            return 0
        }
        var score = 0
        if (pkg.contains("contacts")) score += 4
        if (cls.contains("people") || cls.contains("contacts")) score += 3
        if (isContactsLabel(label)) score += 5
        return score
    }

    private fun isPhoneLauncher(cls: String, label: String): Boolean {
        if (cls.contains("dialtact") ||
            cls.contains(".dial.") ||
            cls.contains("dialactivity") ||
            cls.contains("incall")
        ) {
            return true
        }
        return isPhoneLabel(label) && !isContactsLabel(label)
    }

    private fun isContactsLabel(label: String): Boolean {
        return label.contains("contact") ||
            label.contains("people") ||
            label.contains("联系人") ||
            label.contains("通讯录") ||
            label.contains("聯絡人")
    }

    private fun isPhoneLabel(label: String): Boolean {
        return label.contains("phone") ||
            label.contains("dial") ||
            label.contains("call") ||
            label.contains("电话") ||
            label.contains("電話") ||
            label.contains("拨号") ||
            label.contains("撥號")
    }

    private class Diff(
        private val old: List<ContactItem>,
        private val new: List<ContactItem>,
        private val oldQuery: String,
        private val newQuery: String,
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = old.size
        override fun getNewListSize(): Int = new.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            old[oldItemPosition].id == new[newItemPosition].id
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldQuery == newQuery && old[oldItemPosition] == new[newItemPosition]
    }

    companion object {
        private const val PAYLOAD_CARD = "card"
        private val CONTACT_PACKAGES = arrayOf(
            "com.android.contacts",
            "com.google.android.contacts",
            "com.mediatek.contacts",
            "com.android.dialer",
            "com.oplus.contacts",
            "com.coloros.contacts",
        )
    }
}
