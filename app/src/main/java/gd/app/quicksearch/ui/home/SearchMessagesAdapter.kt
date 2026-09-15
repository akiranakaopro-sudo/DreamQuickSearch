package gd.app.quicksearch.ui.home

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.itemview.COUIBaseListItemView
import com.coui.appcompat.itemview.COUIBaseListItemViewHolder
import gd.app.quicksearch.R
import gd.app.quicksearch.search.messages.MessageItem

class SearchMessagesAdapter(
    private val onMessageClicked: (MessageItem) -> Unit,
) : RecyclerView.Adapter<COUIBaseListItemViewHolder>() {

    private var icon: Drawable? = null
    private val items = ArrayList<MessageItem>()
    private var query = ""

    init {
        setHasStableIds(true)
    }

    fun submit(messages: List<MessageItem>, query: String) {
        val oldQuery = this.query
        val old = ArrayList(items)
        this.query = query
        items.clear()
        items.addAll(messages)
        DiffUtil.calculateDiff(Diff(old, items, oldQuery, query), false).dispatchUpdatesTo(this)
        if (items.isNotEmpty()) {
            notifyItemRangeChanged(0, items.size, PAYLOAD_CARD)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): COUIBaseListItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_app, parent, false)
        val iconSize = parent.resources.getDimensionPixelSize(R.dimen.search_app_icon_size)
        val item = view as COUIBaseListItemView
        item.iconView.layoutParams = item.iconView.layoutParams.apply {
            width = iconSize
            height = iconSize
        }
        item.setIconStyle(COUIBaseListItemView.ROUND)
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
        val message = items[position]
        val item = holder.itemView as COUIBaseListItemView
        item.setTitle(SearchCategoryCard.highlighted(item, message.sender, query))
        item.setSummary(SearchCategoryCard.highlighted(item, message.body, query))
        item.setIcon(iconFor(item))
        item.setOnClickListener { onMessageClicked(message) }
        SearchCategoryCard.bindCorners(holder, itemCount, position)
    }

    private fun iconFor(item: COUIBaseListItemView): Drawable? {
        icon?.let { return it }
        val pm = item.context.packageManager
        for (pkg in MESSAGE_PACKAGES) {
            val loaded = runCatching { pm.getApplicationIcon(pkg) }.getOrNull() ?: continue
            icon = loaded
            return loaded
        }
        return null
    }

    private class Diff(
        private val old: List<MessageItem>,
        private val new: List<MessageItem>,
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
        private val MESSAGE_PACKAGES = arrayOf(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.android.messaging",
            "com.oplus.mms",
            "com.coloros.mms",
        )
    }
}
