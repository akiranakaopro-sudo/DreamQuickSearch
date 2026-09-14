package gd.app.quicksearch.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import gd.app.quicksearch.R

class SectionHeaderAdapter : RecyclerView.Adapter<SectionHeaderAdapter.Holder>() {

    private var title: String? = null

    fun show(text: String) {
        val previous = title
        title = text
        when {
            previous == null -> notifyItemInserted(0)
            previous != text -> notifyItemChanged(0)
        }
    }

    fun hide() {
        if (title == null) {
            return
        }
        title = null
        notifyItemRemoved(0)
    }

    override fun getItemCount(): Int = if (title == null) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_section, parent, false)
        return Holder(view as TextView)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.title.text = title
    }

    class Holder(val title: TextView) : RecyclerView.ViewHolder(title)
}
