package gd.app.quicksearch.ui.home

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.cardlist.COUICardListHelper
import com.coui.appcompat.itemview.COUIBaseListItemView
import com.coui.appcompat.itemview.COUIBaseListItemViewHolder
import gd.app.quicksearch.R
import gd.app.quicksearch.search.calendar.CalendarItem

class SearchCalendarAdapter(
    private val onEventClicked: (CalendarItem) -> Unit,
) : RecyclerView.Adapter<COUIBaseListItemViewHolder>() {

    private var icon: Drawable? = null
    private val items = ArrayList<CalendarItem>()

    init {
        setHasStableIds(true)
    }

    fun submit(events: List<CalendarItem>) {
        val old = ArrayList(items)
        items.clear()
        items.addAll(events)
        DiffUtil.calculateDiff(Diff(old, items), false).dispatchUpdatesTo(this)
        if (items.isNotEmpty()) {
            notifyItemRangeChanged(0, items.size, PAYLOAD_CARD)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long {
        val item = items[position]
        return when {
            item.isTodo -> item.id xor 0x4000000000000000L
            item.fromDreamCalendar -> item.id
            else -> item.id xor Long.MIN_VALUE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): COUIBaseListItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_app, parent, false)
        val iconSize = parent.resources.getDimensionPixelSize(R.dimen.search_app_icon_size)
        val item = view as COUIBaseListItemView
        item.iconView.layoutParams = item.iconView.layoutParams.apply {
            width = iconSize
            height = iconSize
        }
        item.setIconStyle(COUIBaseListItemView.ROUND)
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
            holder.setCornerType(COUICardListHelper.getPositionInGroup(itemCount, position))
            return
        }
        bind(holder, position)
    }

    private fun bind(holder: COUIBaseListItemViewHolder, position: Int) {
        val event = items[position]
        val item = holder.itemView as COUIBaseListItemView
        item.setTitle(event.title)
        item.setSummary(event.subtitle(item.context))
        item.setIcon(iconFor(item))
        item.setOnClickListener { onEventClicked(event) }
        holder.setCornerType(COUICardListHelper.getPositionInGroup(itemCount, position))
    }

    private fun iconFor(item: COUIBaseListItemView): Drawable? {
        icon?.let { return it }
        val pm = item.context.packageManager
        for (pkg in CalendarItem.CALENDAR_PACKAGES) {
            val loaded = runCatching { pm.getApplicationIcon(pkg) }.getOrNull() ?: continue
            icon = loaded
            return loaded
        }
        return null
    }

    private class Diff(
        private val old: List<CalendarItem>,
        private val new: List<CalendarItem>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = old.size
        override fun getNewListSize(): Int = new.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = old[oldItemPosition]
            val newItem = new[newItemPosition]
            return oldItem.id == newItem.id &&
                oldItem.fromDreamCalendar == newItem.fromDreamCalendar &&
                oldItem.isTodo == newItem.isTodo
        }
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            old[oldItemPosition] == new[newItemPosition]
    }

    companion object {
        private const val PAYLOAD_CARD = "card"
    }
}
