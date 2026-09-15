package gd.app.quicksearch.ui.home

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
    }

    fun bindCorners(holder: COUIBaseListItemViewHolder, itemCount: Int, position: Int) {
        holder.setCornerType(COUICardListHelper.getPositionInGroup(itemCount, position))
    }

    fun highlighted(item: COUIBaseListItemView, text: CharSequence, query: String): CharSequence {
        return SearchMatchHighlight.apply(item.context, text, query)
    }
}
