package gd.app.quicksearch.ui.home

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.COUIRecyclerView

/**
 * COUI stores the rubber-band in [View.mScrollY], but hardware rendering never
 * sees that field write ([RecyclerView.scrollTo] is a no-op). Draw the spring
 * offset onto the canvas instead so the list moves inside its own bounds.
 */
class SearchResultsRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : COUIRecyclerView(context, attrs, defStyleAttr) {

    init {
        overScrollMode = View.OVER_SCROLL_ALWAYS
        isNestedScrollingEnabled = false
        clipToPadding = false
        clipChildren = true
        setOverScrollEnable(true)
        setEnableVibrator(true)
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val dx = -scrollX.toFloat()
        val dy = -scrollY.toFloat()
        if (dx == 0f && dy == 0f) {
            super.dispatchDraw(canvas)
            return
        }
        val save = canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.translate(dx, dy)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_TOUCHSCREEN == 0) {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
        }
        return super.onTouchEvent(event)
    }
}
