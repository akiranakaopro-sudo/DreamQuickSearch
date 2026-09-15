package gd.app.quicksearch.ui.home

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.coui.appcompat.itemview.COUIBaseListItemView
import com.coui.appcompat.itemview.COUIBaseListItemViewHolder
import gd.app.quicksearch.R
import gd.app.quicksearch.search.contacts.ContactItem

class SearchContactsAdapter(
    private val onContactClicked: (ContactItem) -> Unit,
) : RecyclerView.Adapter<COUIBaseListItemViewHolder>() {

    private val icons = LruCache<Long, Drawable>(64)
    private val items = ArrayList<ContactItem>()

    init {
        setHasStableIds(true)
    }

    fun submit(contacts: List<ContactItem>) {
        val old = ArrayList(items)
        items.clear()
        items.addAll(contacts)
        DiffUtil.calculateDiff(Diff(old, items), false).dispatchUpdatesTo(this)
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
        item.setIconStyle(COUIBaseListItemView.CIRCLE)
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
        item.setTitle(contact.name)
        item.setSummary(contact.phone)
        item.setIcon(iconFor(item, contact))
        item.setOnClickListener { onContactClicked(contact) }
        SearchCategoryCard.bindCorners(holder, itemCount, position)
    }

    private fun iconFor(item: COUIBaseListItemView, contact: ContactItem): Drawable? {
        icons.get(contact.id)?.let { return it }
        val icon = loadPhoto(item, contact.photoUri) ?: defaultIcon(item)
        if (icon != null) {
            icons.put(contact.id, icon)
        }
        return icon
    }

    private fun loadPhoto(item: COUIBaseListItemView, photoUri: String?): Drawable? {
        if (photoUri.isNullOrEmpty()) {
            return null
        }
        return runCatching {
            item.context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()?.let { BitmapDrawable(item.resources, it) }
    }

    private fun defaultIcon(item: COUIBaseListItemView): Drawable? {
        val pm = item.context.packageManager
        for (pkg in CONTACT_PACKAGES) {
            runCatching { pm.getApplicationIcon(pkg) }.getOrNull()?.let { return it }
        }
        return null
    }

    private class Diff(
        private val old: List<ContactItem>,
        private val new: List<ContactItem>,
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
        private val CONTACT_PACKAGES = arrayOf(
            "com.android.contacts",
            "com.google.android.contacts",
            "com.oplus.contacts",
        )
    }
}
