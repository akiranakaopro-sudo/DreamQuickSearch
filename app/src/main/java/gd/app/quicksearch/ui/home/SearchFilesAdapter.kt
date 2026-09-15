package gd.app.quicksearch.ui.home

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.itemview.COUIBaseListItemView
import com.coui.appcompat.itemview.COUIBaseListItemViewHolder
import gd.app.quicksearch.R
import gd.app.quicksearch.search.files.FileItem

class SearchFilesAdapter(
    private val onFileClicked: (FileItem) -> Unit,
) : RecyclerView.Adapter<COUIBaseListItemViewHolder>() {

    private var icon: Drawable? = null
    private val items = ArrayList<FileItem>()

    init {
        setHasStableIds(true)
    }

    fun submit(files: List<FileItem>) {
        val old = ArrayList(items)
        items.clear()
        items.addAll(files)
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
        val file = items[position]
        val item = holder.itemView as COUIBaseListItemView
        item.setTitle(file.name)
        item.setSummary(file.path.ifEmpty { file.mime })
        item.setIcon(iconFor(item))
        item.setOnClickListener { onFileClicked(file) }
        SearchCategoryCard.bindCorners(holder, itemCount, position)
    }

    private fun iconFor(item: COUIBaseListItemView): Drawable? {
        icon?.let { return it }
        val pm = item.context.packageManager
        for (pkg in FILE_PACKAGES) {
            val loaded = runCatching { pm.getApplicationIcon(pkg) }.getOrNull() ?: continue
            icon = loaded
            return loaded
        }
        return null
    }

    private class Diff(
        private val old: List<FileItem>,
        private val new: List<FileItem>,
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
        private val FILE_PACKAGES = arrayOf(
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.coloros.filemanager",
            "com.oplus.filemanager",
            "com.oneplus.filemanager",
        )
    }
}
