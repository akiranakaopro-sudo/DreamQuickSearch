package gd.app.quicksearch.ui.home

import android.text.TextUtils
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.coui.appcompat.cardlist.COUICardListHelper
import com.coui.appcompat.itemview.COUIBaseListItemView
import com.coui.appcompat.itemview.COUIBaseListItemViewHolder
import gd.app.quicksearch.R

/**
 * ColorOS Global Search paints each category (Apps, Notes, …) as one frosted
 * card: translucent white fill, grouped COUI corners, light text on wallpaper.
 */
internal object SearchCategoryCard {

    fun style(item: COUIBaseListItemView) {
        val color = ContextCompat.getColor(item.context, R.color.search_category_card)
        COUICardListHelper.refreshCardBg(item.rootItemView, color)
        item.setTitleColor(ContextCompat.getColorStateList(item.context, R.color.search_category_title))
        item.setSummaryColor(ContextCompat.getColorStateList(item.context, R.color.search_category_summary))
        oneLine(item.findViewById(android.R.id.title))
        oneLine(item.findViewById(android.R.id.summary))
    }

    fun bindCorners(holder: COUIBaseListItemViewHolder, itemCount: Int, position: Int) {
        holder.setCornerType(COUICardListHelper.getPositionInGroup(itemCount, position))
    }

    fun highlighted(item: COUIBaseListItemView, text: CharSequence, query: String): CharSequence {
        return SearchMatchHighlight.apply(item.context, singleLine(text), query)
    }

    private fun oneLine(text: TextView?) {
        text ?: return
        text.maxLines = 1
        text.ellipsize = TextUtils.TruncateAt.END
    }

    /** MMS/notes bodies keep newlines; ColorOS shows that content as one ellipsized row. */
    private fun singleLine(text: CharSequence): CharSequence {
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            if (c == '\n' || c == '\r' || c == '\t') {
                break
            }
            i++
        }
        if (i == n) {
            return text
        }
        val out = StringBuilder(n)
        var pendingSpace = false
        for (j in 0 until n) {
            val c = text[j]
            if (c == '\n' || c == '\r' || c == '\t' || c == ' ') {
                if (out.isNotEmpty()) {
                    pendingSpace = true
                }
            } else {
                if (pendingSpace) {
                    out.append(' ')
                    pendingSpace = false
                }
                out.append(c)
            }
        }
        return out
    }
}
