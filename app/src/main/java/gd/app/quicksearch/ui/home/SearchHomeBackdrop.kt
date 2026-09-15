package gd.app.quicksearch.ui.home

import android.app.Activity
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import com.oplus.graphics.OplusBlurParam
import com.oplus.view.ViewRootManager
import gd.app.quicksearch.R
import java.lang.reflect.Method

class SearchHomeBackdrop(
    private val activity: Activity,
    private val backdropView: ImageView,
    private val blurLayer: View,
) {
    private var blurDrawable: Drawable? = null

    fun apply() {
        showSystemWallpaper()
        blurLayer.doOnAttach { view ->
            view.post { frostWallpaperInCompositor(view) }
        }
        backdropView.doOnLayout {
            frostWallpaperBitmap()
        }
    }

    fun release() {
        blurDrawable = null
        blurLayer.background = null
        backdropView.setImageDrawable(null)
        if (Build.VERSION.SDK_INT >= 31) {
            backdropView.setRenderEffect(null)
        }
    }

    private fun showSystemWallpaper() {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        activity.window.setBackgroundDrawableResource(android.R.color.transparent)
        if (Build.VERSION.SDK_INT >= 31) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            activity.window.attributes = activity.window.attributes.apply {
                blurBehindRadius = WINDOW_BLUR_RADIUS_PX
            }
            activity.window.setBackgroundBlurRadius(WINDOW_BLUR_RADIUS_PX)
        }
    }

    private fun frostWallpaperInCompositor(target: View) {
        val radius = target.resources.getDimensionPixelSize(R.dimen.search_home_blur_radius)
        val drawable = createBlurDrawable(target) ?: return
        val params = OplusBlurParam().apply {
            setBlurType(OplusBlurParam.BLUR_TYPE_QUALITY_KAWASE)
            setMaterialParams(
                OplusBlurParam.BLUR_BLEND_MODE_OVERLAY,
                floatArrayOf(0f, 0f, 0f, 0.06f),
                floatArrayOf(0f, 0f, 0f, 0.16f),
            )
        }
        call(drawable, "setBlurRadius", arrayOf(Int::class.javaPrimitiveType!!), radius)
        call(drawable, "setColor", arrayOf(Int::class.javaPrimitiveType!!), 0x00000000)
        val wrapper = call(drawable, "getWrapper")
        val ext = call(wrapper, "getExtImpl")
        call(ext, "setBlurParams", arrayOf(OplusBlurParam::class.java), params)
        target.background = drawable
        blurDrawable = drawable
    }

    private fun createBlurDrawable(target: View): Drawable? {
        val fromManager = ViewRootManager(target).backgroundBlurDrawable
        if (fromManager != null) {
            return fromManager
        }
        val viewRootImpl = call(target, "getViewRootImpl") ?: return null
        return call(viewRootImpl, "createBackgroundBlurDrawable") as? Drawable
    }

    private fun frostWallpaperBitmap() {
        val bitmap = loadWallpaperBitmap()?.takeIf { it.hasVisibleColor() }
        if (bitmap == null) {
            backdropView.setImageDrawable(null)
            return
        }
        backdropView.setImageBitmap(bitmap)
        backdropView.visibility = View.VISIBLE
        if (Build.VERSION.SDK_INT >= 31) {
            backdropView.setRenderEffect(
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
            ?: runCatching { @Suppress("DEPRECATION") activity.wallpaper }.getOrNull()
        return drawable?.let(::drawableToBlurSource)
    }

    private fun decodeWallpaperFile(manager: WallpaperManager): Bitmap? {
        return runCatching {
            manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { file ->
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 4
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
        val width = drawable.intrinsicWidth.takeIf { it > 0 }
            ?: backdropView.width.takeIf { it > 0 }
            ?: return null
        val height = drawable.intrinsicHeight.takeIf { it > 0 }
            ?: backdropView.height.takeIf { it > 0 }
            ?: return null
        val sample = sampleSize(width, height)
        val bitmap = Bitmap.createBitmap(
            (width / sample).coerceAtLeast(1),
            (height / sample).coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
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

    private fun Bitmap.hasVisibleColor(): Boolean {
        if (width < 2 || height < 2) {
            return false
        }
        val samples = intArrayOf(
            getPixel(width / 2, height / 2),
            getPixel(width / 4, height / 4),
            getPixel(width * 3 / 4, height * 3 / 4),
        )
        return samples.any { pixel ->
            Color.red(pixel) + Color.green(pixel) + Color.blue(pixel) > 48
        }
    }

    private fun call(target: Any?, name: String, types: Array<Class<*>> = emptyArray(), vararg args: Any?): Any? {
        if (target == null) {
            return null
        }
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val method: Method = type.getDeclaredMethod(name, *types)
                method.isAccessible = true
                return method.invoke(target, *args)
            } catch (_: NoSuchMethodException) {
                type = type.superclass
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }

    companion object {
        private const val WINDOW_BLUR_RADIUS_PX = 150
        private const val WALLPAPER_BLUR_RADIUS_PX = 64f
        private const val MAX_BLUR_SOURCE_EDGE_PX = 320
    }
}
