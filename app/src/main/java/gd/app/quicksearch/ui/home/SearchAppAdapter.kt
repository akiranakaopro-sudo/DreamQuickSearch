package gd.app.quicksearch.ui.home

import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.cardlist.COUICardListHelper
import com.coui.appcompat.itemview.COUIBaseListItemView
import com.coui.appcompat.itemview.COUIBaseListItemViewHolder
import gd.app.quicksearch.R
import gd.app.quicksearch.search.apps.InstalledApp

class SearchAppAdapter(
    private val onAppClicked: (InstalledApp) -> Unit,
) : RecyclerView.Adapter<COUIBaseListItemViewHolder>() {

    private val icons = LruCache<ComponentName, Drawable>(64)
    private val items = ArrayList<InstalledApp>()

    init {
        setHasStableIds(true)
    }

    fun submit(apps: List<InstalledApp>) {
        val old = ArrayList(items)
        items.clear()
        items.addAll(apps)
        DiffUtil.calculateDiff(Diff(old, items), false).dispatchUpdatesTo(this)
        if (items.isNotEmpty()) {
            notifyItemRangeChanged(0, items.size, PAYLOAD_CARD)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long = items[position].component.hashCode().toLong()

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
        bind(holder, position, fullBind = true)
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
        bind(holder, position, fullBind = true)
    }

    private fun bind(holder: COUIBaseListItemViewHolder, position: Int, fullBind: Boolean) {
        val app = items[position]
        val item = holder.itemView as COUIBaseListItemView
        if (fullBind) {
            item.setTitle(app.label)
            item.setIcon(iconFor(item, app.component))
            item.setOnClickListener { onAppClicked(app) }
        }
        holder.setCornerType(COUICardListHelper.getPositionInGroup(itemCount, position))
    }

    private fun iconFor(item: COUIBaseListItemView, component: ComponentName): Drawable? {
        icons.get(component)?.let { return it }
        val icon = runCatching { item.context.packageManager.getActivityIcon(component) }.getOrNull()
            ?: runCatching {
                item.context.packageManager.getApplicationIcon(component.packageName)
            }.getOrNull()
        if (icon != null) {
            icons.put(component, icon)
        }
        return icon
    }

    private class Diff(
        private val old: List<InstalledApp>,
        private val new: List<InstalledApp>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = old.size
        override fun getNewListSize(): Int = new.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            old[oldItemPosition].component == new[newItemPosition].component
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            old[oldItemPosition] == new[newItemPosition]
    }

    companion object {
        private const val PAYLOAD_CARD = "card"
    }
}
