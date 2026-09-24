package gd.app.quicksearch.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import gd.app.quicksearch.R

/** Last row of the results list: "Swipe up to exit". */
class ExitHintAdapter : RecyclerView.Adapter<ExitHintAdapter.Holder>() {

    private var visible = false

    fun show() {
        if (visible) {
            return
        }
        visible = true
        notifyItemInserted(0)
    }

    fun hide() {
        if (!visible) {
            return
        }
        visible = false
        notifyItemRemoved(0)
    }

    override fun getItemCount(): Int = if (visible) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_exit_hint, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.label.setText(R.string.search_swipe_up_to_exit)
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.exit_hint_label)
    }
}
