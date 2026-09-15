package gd.app.quicksearch.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ReplacementSpan
import androidx.core.content.ContextCompat
import gd.app.quicksearch.R
import gd.app.quicksearch.search.SearchText

/** ColorOS MatchBg: rounded pill behind the matched query in result text. */
internal object SearchMatchHighlight {

    fun apply(context: Context, text: CharSequence, query: String): CharSequence {
        val ranges = SearchText.matchRanges(text, query)
        if (ranges.isEmpty()) {
            return text
        }
        val color = ContextCompat.getColor(context, R.color.search_match_highlight)
        val radius = context.resources.getDimension(R.dimen.search_match_highlight_radius)
        val spanned = SpannableString(text)
        for (range in ranges) {
            spanned.setSpan(
                RoundBgSpan(color, radius),
                range.first,
                range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        return spanned
    }

    private class RoundBgSpan(
        private val color: Int,
        private val radius: Float,
    ) : ReplacementSpan() {
        private val rect = RectF()

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?,
        ): Int {
            if (fm != null) {
                val metrics = paint.fontMetrics
                fm.ascent = metrics.ascent.toInt()
                fm.descent = metrics.descent.toInt()
                fm.top = metrics.top.toInt()
                fm.bottom = metrics.bottom.toInt()
                fm.leading = metrics.leading.toInt()
            }
            return paint.measureText(text, start, end).toInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint,
        ) {
            val width = paint.measureText(text, start, end)
            val textColor = paint.color
            rect.set(x, top.toFloat(), x + width, bottom.toFloat())
            paint.color = color
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.color = textColor
            canvas.drawText(text!!, start, end, x, y.toFloat(), paint)
        }
    }
}
