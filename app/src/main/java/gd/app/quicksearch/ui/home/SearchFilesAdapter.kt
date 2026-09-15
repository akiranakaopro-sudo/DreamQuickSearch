package gd.app.quicksearch.ui.home

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.itemview.COUIBaseListItemView
import com.coui.appcompat.itemview.COUIBaseListItemViewHolder
import gd.app.quicksearch.R
import gd.app.quicksearch.search.files.FileItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class SearchFilesAdapter(
    private val onFileClicked: (FileItem) -> Unit,
) : RecyclerView.Adapter<COUIBaseListItemViewHolder>() {

    private val main = Handler(Looper.getMainLooper())
    private val thumbs = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "file-thumbs").apply { isDaemon = true }
    }
    private val cache = object : LruCache<String, Drawable>(THUMB_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Drawable): Int {
            val bitmap = (value as? BitmapDrawable)?.bitmap ?: return 1
            return bitmap.byteCount.coerceAtLeast(1)
        }
    }
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val items = ArrayList<FileItem>()
    private var query = ""
    private var iconSizePx = 0

    init {
        setHasStableIds(true)
    }

    fun submit(files: List<FileItem>, query: String) {
        val oldQuery = this.query
        val old = ArrayList(items)
        this.query = query
        items.clear()
        items.addAll(files)
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
        iconSizePx = iconSize
        val item = view as COUIBaseListItemView
        item.iconView.layoutParams = item.iconView.layoutParams.apply {
            width = iconSize
            height = iconSize
        }
        item.iconView.scaleType = ImageView.ScaleType.CENTER_CROP
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
        if (payloads.size == 1 && payloads[0] == PAYLOAD_THUMB) {
            bindIcon(holder.itemView as COUIBaseListItemView, items[position])
            return
        }
        bind(holder, position)
    }

    private fun bind(holder: COUIBaseListItemViewHolder, position: Int) {
        val file = items[position]
        val item = holder.itemView as COUIBaseListItemView
        item.setTitle(SearchCategoryCard.highlighted(item, file.name, query))
        item.setSummary(SearchCategoryCard.highlighted(item, file.path.ifEmpty { file.mime }, query))
        bindIcon(item, file)
        item.setOnClickListener { onFileClicked(file) }
        SearchCategoryCard.bindCorners(holder, itemCount, position)
    }

    private fun bindIcon(item: COUIBaseListItemView, file: FileItem) {
        cache.get(file.id)?.let { thumb ->
            item.setIcon(thumb)
            return
        }
        item.setIcon(SearchFileIcons.placeholder(item.context, file))
        if (!SearchFileIcons.wantsThumbnail(file) || failed.contains(file.id) || !loading.add(file.id)) {
            return
        }
        val sizePx = iconSizePx
        val id = file.id
        val app = item.context.applicationContext
        thumbs.execute {
            val bitmap = SearchFileIcons.loadThumbnail(app, file, sizePx)
            if (bitmap == null) {
                failed.add(id)
                loading.remove(id)
                return@execute
            }
            val drawable = BitmapDrawable(app.resources, bitmap)
            cache.put(id, drawable)
            loading.remove(id)
            main.post {
                val position = items.indexOfFirst { it.id == id }
                if (position >= 0) {
                    notifyItemChanged(position, PAYLOAD_THUMB)
                }
            }
        }
    }

    private class Diff(
        private val old: List<FileItem>,
        private val new: List<FileItem>,
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
        private const val PAYLOAD_THUMB = "thumb"
        private const val THUMB_CACHE_BYTES = 2 * 1024 * 1024
    }
}
