package gd.app.quicksearch.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import gd.app.quicksearch.R

/**
 * Category title row. Like ColorOS, the row also carries a "More" button that
 * opens the whole category when the section has more hits than it shows inline.
 */
class SectionHeaderAdapter(
    private val onMoreClicked: () -> Unit = {},
) : RecyclerView.Adapter<SectionHeaderAdapter.Holder>() {

    private var title: String? = null
    private var hasMore = false

    fun show(text: String, hasMore: Boolean) {
        val previousTitle = title
        val previousMore = this.hasMore
        title = text
        this.hasMore = hasMore
        when {
            previousTitle == null -> notifyItemInserted(0)
            previousTitle != text || previousMore != hasMore -> notifyItemChanged(0)
        }
    }

    fun hide() {
        if (title == null) {
            return
        }
        title = null
        hasMore = false
        notifyItemRemoved(0)
    }

    override fun getItemCount(): Int = if (title == null) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_section, parent, false)
        val holder = Holder(view)
        holder.more.setOnClickListener { onMoreClicked() }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.title.text = title
        holder.more.visibility = if (hasMore) View.VISIBLE else View.GONE
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.section_title)
        val more: TextView = view.findViewById(R.id.section_more)
    }
}
