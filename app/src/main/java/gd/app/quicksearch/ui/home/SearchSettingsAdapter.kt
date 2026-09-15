package gd.app.quicksearch.ui.home

import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.itemview.COUIBaseListItemView
import com.coui.appcompat.itemview.COUIBaseListItemViewHolder
import gd.app.quicksearch.R
import gd.app.quicksearch.search.settings.SettingItem

class SearchSettingsAdapter(
    private val onSettingClicked: (SettingItem) -> Unit,
) : RecyclerView.Adapter<COUIBaseListItemViewHolder>() {

    private val icons = LruCache<String, Drawable>(64)
    private val items = ArrayList<SettingItem>()

    init {
        setHasStableIds(true)
    }

    fun submit(settings: List<SettingItem>) {
        val old = ArrayList(items)
        items.clear()
        items.addAll(settings)
        DiffUtil.calculateDiff(Diff(old, items), false).dispatchUpdatesTo(this)
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
        val setting = items[position]
        val item = holder.itemView as COUIBaseListItemView
        item.setTitle(setting.label)
        item.setIcon(iconFor(item, setting))
        item.setOnClickListener { onSettingClicked(setting) }
        SearchCategoryCard.bindCorners(holder, itemCount, position)
    }

    private fun iconFor(item: COUIBaseListItemView, setting: SettingItem): Drawable? {
        icons.get(setting.id)?.let { return it }
        val icon = loadIcon(item, setting)
        if (icon != null) {
            icons.put(setting.id, icon)
        }
        return icon
    }

    private fun loadIcon(item: COUIBaseListItemView, setting: SettingItem): Drawable? {
        if (setting.iconRes != 0 && !setting.iconPackage.isNullOrEmpty()) {
            runCatching {
                val pkg = item.context.createPackageContext(setting.iconPackage, 0)
                ContextCompat.getDrawable(pkg, setting.iconRes)
            }.getOrNull()?.let { return it }
        }
        val pm = item.context.packageManager
        setting.targetPackage?.let { pkg ->
            runCatching { pm.getApplicationIcon(pkg) }.getOrNull()?.let { return it }
        }
        return runCatching { pm.getApplicationIcon("com.android.settings") }.getOrNull()
    }

    private class Diff(
        private val old: List<SettingItem>,
        private val new: List<SettingItem>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = old.size
        override fun getNewListSize(): Int = new.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            old[oldItemPosition].id == new[newItemPosition].id
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            old[oldItemPosition] == new[newItemPosition]
    }

    companion object {
        private const val PAYLOAD_CARD = "card"
    }
}
