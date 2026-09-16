package gd.app.quicksearch.ui.home

import android.app.Activity
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnAttach
import com.oplus.graphics.OplusBlurParam
import com.oplus.view.ViewRootManager
import gd.app.quicksearch.R
import java.lang.reflect.Method
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SearchHomeBackdrop(
    private val activity: Activity,
    private val backdropView: ImageView,
    private val blurLayer: View,
) {
    private val wallpaperManager = WallpaperManager.getInstance(activity)
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "wallpaper-frost").apply { isDaemon = true }
    }
    private var colorsListener: Any? = null
    private var blurDrawable: Drawable? = null
    @Volatile
    private var usingCompositor = false
    @Volatile
    private var released = false

    fun apply() {
        showSystemWallpaper()
        blurLayer.background = null
        if (Build.VERSION.SDK_INT >= 27) {
            colorsListener = WallpaperPaletteApi.listen(
                wallpaperManager,
                Handler(Looper.getMainLooper()),
                ::onWallpaperChanged,
            )
        }
        blurLayer.doOnAttach { view ->
            view.post {
                if (!released && frostWallpaperInCompositor(view)) {
                    usingCompositor = true
                    backdropView.setImageDrawable(null)
                    if (Build.VERSION.SDK_INT >= 31) {
                        backdropView.setRenderEffect(null)
                    }
                } else if (!released) {
                    loadFrostedWallpaper()
                }
            }
        }
    }

    fun release() {
        released = true
        usingCompositor = false
        if (Build.VERSION.SDK_INT >= 27) {
            WallpaperPaletteApi.stopListening(wallpaperManager, colorsListener)
        }
        colorsListener = null
        blurDrawable = null
        worker.shutdownNow()
        blurLayer.background = null
        backdropView.setImageDrawable(null)
        if (Build.VERSION.SDK_INT >= 31) {
            backdropView.setRenderEffect(null)
        }
    }

    private fun onWallpaperChanged() {
        if (!usingCompositor) {
            loadFrostedWallpaper()
        }
    }

    private fun showSystemWallpaper() {
        activity.window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER or
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
        )
        activity.window.setBackgroundDrawableResource(android.R.color.transparent)
        if (Build.VERSION.SDK_INT >= 31) {
            activity.window.attributes = activity.window.attributes.apply {
                blurBehindRadius = WINDOW_BLUR_RADIUS_PX
            }
            activity.window.setBackgroundBlurRadius(WINDOW_BLUR_RADIUS_PX)
        }
    }

    private fun frostWallpaperInCompositor(target: View): Boolean {
        return runCatching {
            val radius = target.resources.getDimensionPixelSize(R.dimen.search_home_blur_radius)
            val drawable = createBlurDrawable(target) ?: return false
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
            true
        }.getOrDefault(false)
    }

    private fun createBlurDrawable(target: View): Drawable? {
        val fromManager = ViewRootManager(target).backgroundBlurDrawable
        if (fromManager != null) {
            return fromManager
        }
        val viewRootImpl = call(target, "getViewRootImpl") ?: return null
        return call(viewRootImpl, "createBackgroundBlurDrawable") as? Drawable
    }

    private fun loadFrostedWallpaper() {
        if (released) {
            return
        }
        val width = backdropView.width
        val height = backdropView.height
        runCatching {
            worker.execute {
                val bitmap = loadWallpaperBitmap(width, height)?.takeIf { it.hasVisibleColor() }
                val palette = if (Build.VERSION.SDK_INT >= 27) {
                    WallpaperPaletteApi.read(wallpaperManager)
                } else {
                    null
                }
                backdropView.post {
                    if (!released) {
                        showFrostedWallpaper(bitmap, palette)
                    }
                }
            }
        }
    }

    private fun showFrostedWallpaper(bitmap: Bitmap?, palette: IntArray?) {
        when {
            bitmap != null -> {
                backdropView.setImageBitmap(bitmap)
                blurLayer.background = null
            }
            palette != null -> {
                backdropView.setImageDrawable(null)
                blurLayer.background = WallpaperPaletteFrostDrawable(palette).apply {
                    alpha = PALETTE_OVERLAY_ALPHA
                }
            }
            else -> {
                backdropView.setImageDrawable(null)
                blurLayer.background = ColorDrawable(FROST_FALLBACK_OVERLAY)
            }
        }
        backdropView.visibility = View.VISIBLE
        if (Build.VERSION.SDK_INT >= 31) {
            val blur = bitmap?.let {
                RenderEffect.createBlurEffect(
                    WALLPAPER_BLUR_RADIUS_PX,
                    WALLPAPER_BLUR_RADIUS_PX,
                    Shader.TileMode.CLAMP,
                )
            }
            backdropView.setRenderEffect(blur)
        }
    }

    private fun loadWallpaperBitmap(targetWidth: Int, targetHeight: Int): Bitmap? {
        decodeWallpaperFile(wallpaperManager)?.let { return it }
        val drawable = runCatching { wallpaperManager.peekFastDrawable() }.getOrNull()
            ?: runCatching { wallpaperManager.peekDrawable() }.getOrNull()
            ?: runCatching { wallpaperManager.fastDrawable }.getOrNull()
            ?: runCatching { wallpaperManager.drawable }.getOrNull()
            ?: runCatching { @Suppress("DEPRECATION") activity.wallpaper }.getOrNull()
        return drawable?.let { drawableToBlurSource(it, targetWidth, targetHeight) }
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

    private fun drawableToBlurSource(
        drawable: Drawable,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return scaleForBlur(drawable.bitmap)
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 }
            ?: targetWidth.takeIf { it > 0 }
            ?: return null
        val height = drawable.intrinsicHeight.takeIf { it > 0 }
            ?: targetHeight.takeIf { it > 0 }
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

    private fun call(
        target: Any?,
        name: String,
        types: Array<Class<*>> = emptyArray(),
        vararg args: Any?,
    ): Any? {
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
        private const val PALETTE_OVERLAY_ALPHA = 156
        private const val FROST_FALLBACK_OVERLAY = 0x99071923.toInt()
    }
}

@RequiresApi(27)
private object WallpaperPaletteApi {
    fun read(manager: WallpaperManager): IntArray? {
        return runCatching {
            manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.let(::palette)
        }.getOrNull()
    }

    fun listen(
        manager: WallpaperManager,
        handler: Handler,
        onChanged: () -> Unit,
    ): Any? {
        return runCatching {
            val listener = WallpaperManager.OnColorsChangedListener { _, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                    onChanged()
                }
            }
            manager.addOnColorsChangedListener(listener, handler)
            listener
        }.getOrNull()
    }

    fun stopListening(manager: WallpaperManager, listener: Any?) {
        val colorsListener = listener as? WallpaperManager.OnColorsChangedListener ?: return
        runCatching { manager.removeOnColorsChangedListener(colorsListener) }
    }

    private fun palette(colors: android.app.WallpaperColors): IntArray {
        val primary = colors.primaryColor.toArgb()
        return intArrayOf(
            primary,
            colors.secondaryColor?.toArgb() ?: primary,
            colors.tertiaryColor?.toArgb() ?: primary,
        )
    }
}

private class WallpaperPaletteFrostDrawable(
    palette: IntArray,
) : Drawable() {
    private val primary = palette[0]
    private val secondary = palette.getOrElse(1) { primary }
    private val tertiary = palette.getOrElse(2) { secondary }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private var backgroundShader: Shader? = null
    private var glowShaders: List<Shader> = emptyList()
    private var drawableAlpha = 255

    override fun onBoundsChange(bounds: Rect) {
        val width = bounds.width().toFloat().coerceAtLeast(1f)
        val height = bounds.height().toFloat().coerceAtLeast(1f)
        backgroundShader = LinearGradient(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            intArrayOf(darken(primary, 0.58f), darken(secondary, 0.7f), darken(tertiary, 0.82f)),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP,
        )
        glowShaders = listOf(
            glow(bounds.left + width * 0.28f, bounds.top + height * 0.14f, width * 0.52f, primary),
            glow(bounds.left + width * 0.88f, bounds.top + height * 0.34f, width * 0.42f, secondary),
            glow(bounds.left + width * 0.18f, bounds.top + height * 0.7f, width * 0.56f, tertiary),
        )
    }

    override fun draw(canvas: Canvas) {
        paint.alpha = drawableAlpha
        paint.shader = backgroundShader
        canvas.drawRect(bounds, paint)
        glowShaders.forEach { shader ->
            paint.shader = shader
            canvas.drawRect(bounds, paint)
        }
        paint.shader = null
        paint.color = 0x24000000
        canvas.drawRect(bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun glow(centerX: Float, centerY: Float, radius: Float, color: Int): Shader {
        return RadialGradient(
            centerX,
            centerY,
            radius.coerceAtLeast(1f),
            intArrayOf(
                ColorUtils.setAlphaComponent(color, 92),
                ColorUtils.setAlphaComponent(color, 34),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.46f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private fun darken(color: Int, amount: Float): Int {
        return ColorUtils.blendARGB(color, FROST_BASE, amount)
    }

    companion object {
        private const val FROST_BASE = 0xFF071923.toInt()
    }
}
