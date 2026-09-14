package gd.app.quicksearch.ui.home

import android.app.Activity
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.view.doOnAttach
import com.coui.appcompat.R as CouiR
import com.oplus.graphics.OplusBlurParam
import com.oplus.view.ViewRootManager
import gd.app.quicksearch.databinding.ActivitySearchHomeBinding

class SearchHomeBackdrop(
    private val activity: Activity,
    private val binding: ActivitySearchHomeBinding,
) {
    private var blurManager: ViewRootManager? = null

    fun apply() {
        showSystemWallpaper()
        applyWindowBlur()
        binding.blurLayer.doOnAttach { view ->
            applyCompositorBlur(view)
            applyWallpaperBlur()
        }
    }

    fun release() {
        blurManager = null
        binding.blurLayer.background = null
        binding.blurBackdrop.setImageDrawable(null)
        if (Build.VERSION.SDK_INT >= 31) {
            binding.blurBackdrop.setRenderEffect(null)
        }
    }

    private fun showSystemWallpaper() {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
    }

    private fun applyWindowBlur() {
        if (Build.VERSION.SDK_INT < 31) {
            return
        }
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        activity.window.attributes = activity.window.attributes.apply {
            blurBehindRadius = WINDOW_BLUR_RADIUS_PX
        }
        activity.window.setBackgroundBlurRadius(WINDOW_BLUR_RADIUS_PX)
    }

    private fun applyCompositorBlur(target: View): Boolean {
        val manager = ViewRootManager(target)
        val drawable = manager.backgroundBlurDrawable ?: return false
        val radius = target.resources.getDimensionPixelSize(CouiR.dimen.coui_list_dialog_background_blur_radius)
        val params = OplusBlurParam().apply {
            setBlurType(OplusBlurParam.BLUR_TYPE_FAST_KAWASE)
            setMaterialParams(
                OplusBlurParam.BLUR_BLEND_MODE_OVERLAY,
                floatArrayOf(0f, 0f, 0f, 0.18f),
                floatArrayOf(0f, 0f, 0f, 0.42f),
            )
        }
        manager.setBlurParams(params)
        manager.setBlurRadius(radius)
        manager.setColor(0x33000000)
        target.background = drawable
        blurManager = manager
        return true
    }

    private fun applyWallpaperBlur() {
        val bitmap = loadWallpaperBitmap()
        if (bitmap == null) {
            binding.blurBackdrop.visibility = View.GONE
            return
        }
        binding.blurBackdrop.setImageBitmap(bitmap)
        binding.blurBackdrop.visibility = View.VISIBLE
        if (Build.VERSION.SDK_INT >= 31) {
            binding.blurBackdrop.setRenderEffect(
                RenderEffect.createBlurEffect(
                    WALLPAPER_BLUR_RADIUS_PX,
                    WALLPAPER_BLUR_RADIUS_PX,
                    Shader.TileMode.CLAMP,
                ),
            )
        }
    }

    private fun loadWallpaperBitmap(): Bitmap? {
        val manager = WallpaperManager.getInstance(activity)
        decodeWallpaperFile(manager)?.let { return it }
        val drawable = runCatching { manager.peekFastDrawable() }.getOrNull()
            ?: runCatching { manager.peekDrawable() }.getOrNull()
            ?: runCatching { manager.fastDrawable }.getOrNull()
            ?: runCatching { manager.drawable }.getOrNull()
        return drawable?.let(::drawableToBlurSource)
    }

    private fun decodeWallpaperFile(manager: WallpaperManager): Bitmap? {
        return runCatching {
            manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { file ->
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 8
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFileDescriptor(file.fileDescriptor, null, options)
            }
        }.getOrNull()
    }

    private fun drawableToBlurSource(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return scaleForBlur(drawable.bitmap)
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: binding.blurBackdrop.width.takeIf { it > 0 } ?: return null
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: binding.blurBackdrop.height.takeIf { it > 0 } ?: return null
        val sample = sampleSize(width, height)
        val bitmap = Bitmap.createBitmap(
            (width / sample).coerceAtLeast(1),
            (height / sample).coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun scaleForBlur(source: Bitmap): Bitmap {
        val sample = sampleSize(source.width, source.height)
        if (sample <= 1) {
            return source
        }
        return Bitmap.createScaledBitmap(
            source,
            (source.width / sample).coerceAtLeast(1),
            (source.height / sample).coerceAtLeast(1),
            true,
        )
    }

    private fun sampleSize(width: Int, height: Int): Int {
        val maxEdge = maxOf(width, height).coerceAtLeast(1)
        var sample = 1
        while (maxEdge / sample > MAX_BLUR_SOURCE_EDGE_PX) {
            sample *= 2
        }
        return sample
    }

    companion object {
        private const val WINDOW_BLUR_RADIUS_PX = 128
        private const val WALLPAPER_BLUR_RADIUS_PX = 28f
        private const val MAX_BLUR_SOURCE_EDGE_PX = 96
    }
}
