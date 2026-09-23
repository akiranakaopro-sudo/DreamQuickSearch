package gd.app.quicksearch.ui.home

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.COUIRecyclerView
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Field

/**
 * COUIRecyclerView whose [scrollTo] actually writes View scroll offsets.
 *
 * AOSP RecyclerView.scrollTo is a no-op. On generic Android 14, [View.mScrollY]
 * is filtered from [Class.getDeclaredField], so ViewNative falls back to
 * [View.scrollTo] and the spring never appears. This subclass writes the fields
 * via HiddenApiBypass (same pattern as DreamRecorder BrowseCouiRecyclerView).
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
        clipChildren = false
        setIsUseNativeOverScroll(false)
        setOverScrollEnable(true)
        setEnableVibrator(true)
    }

    override fun startNestedScroll(axes: Int): Boolean = false

    override fun startNestedScroll(axes: Int, type: Int): Boolean = false

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
    ): Boolean = false

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int,
    ): Boolean = false

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
    ): Boolean = false

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
    ): Boolean = false

    override fun scrollTo(x: Int, y: Int) {
        applyScrollOffsets(x, y)
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        if (getScrollX() != scrollX || getScrollY() != scrollY) {
            applyScrollOffsets(scrollX, scrollY)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_TOUCHSCREEN == 0) {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
        }
        return super.onTouchEvent(event)
    }

    private fun applyScrollOffsets(x: Int, y: Int) {
        if (getScrollX() == x && getScrollY() == y) {
            return
        }
        val sx = SCROLL_X ?: return
        val sy = SCROLL_Y ?: return
        try {
            val oldX = sx.getInt(this)
            val oldY = sy.getInt(this)
            if (oldX == x && oldY == y) {
                return
            }
            sx.setInt(this, x)
            sy.setInt(this, y)
            onScrollChanged(x, y, oldX, oldY)
            if (!awakenScrollBars()) {
                postInvalidateOnAnimation()
            }
        } catch (_: Throwable) {
            // Spring is skipped if the runtime still blocks the field write.
        }
    }

    companion object {
        private const val TAG = "SearchResultsRV"
        private val SCROLL_X: Field?
        private val SCROLL_Y: Field?

        init {
            var x: Field? = null
            var y: Field? = null
            try {
                for (field in HiddenApiBypass.getInstanceFields(View::class.java)) {
                    when (field.name) {
                        "mScrollX" -> x = field
                        "mScrollY" -> y = field
                    }
                }
                x?.isAccessible = true
                y?.isAccessible = true
            } catch (t: Throwable) {
                Log.e(TAG, "resolve View scroll fields failed", t)
            }
            SCROLL_X = x
            SCROLL_Y = y
        }
    }
}
