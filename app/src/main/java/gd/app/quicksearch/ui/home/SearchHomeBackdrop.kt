package gd.app.quicksearch.ui.home

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.annotation.RequiresApi
import com.oplus.graphics.OplusBlurParam
import com.oplus.view.ViewRootManager
import gd.app.quicksearch.R
import java.lang.reflect.Method

/**
 * ColorOS InCall-style frosted wallpaper for search (DreamDialer skill):
 *
 * 1. Prefer baking once with [FastBlur] (radius 25, scale 4), cache, paint on
 *    [backdropView] + scrim — no live compositor cost.
 * 2. If wallpaper pixels are unreadable (normal apps often cannot call
 *    WallpaperManager.getBitmap), fall back to FLAG_SHOW_WALLPAPER + one Oplus
 *    background-blur drawable so the frost still matches the live wallpaper.
 */
class SearchHomeBackdrop(
    private val activity: Activity,
    private val backdropView: ImageView,
    private val scrimView: View? = null,
    private val blurHost: View? = null,
) {
    private val wallpaperManager = WallpaperManager.getInstance(activity)
    private var colorsListener: Any? = null
    private var liveBlurDrawable: Drawable? = null
    @Volatile
    private var released = false
    private var usingLiveBlur = false

    fun apply() {
        applyCachedToWindow(activity)
        if (Build.VERSION.SDK_INT >= 27) {
            colorsListener = WallpaperPaletteApi.listen(
                wallpaperManager,
                Handler(Looper.getMainLooper()),
            ) {
                if (!released) {
                    invalidateAndReload()
                }
            }
        }
        paintBackdrop()
    }

    fun release() {
        released = true
        if (Build.VERSION.SDK_INT >= 27) {
            WallpaperPaletteApi.stopListening(wallpaperManager, colorsListener)
        }
        colorsListener = null
        liveBlurDrawable = null
        blurHost?.background = null
    }

    private fun paintBackdrop() {
        ensureBlurredWallpaper(activity.applicationContext)
        val blur = copyCachedBlur(activity)
        if (blur != null) {
            usingLiveBlur = false
            clearLiveWallpaperFlags()
            backdropView.setImageDrawable(blur)
            backdropView.visibility = View.VISIBLE
            blurHost?.background = null
            blurHost?.visibility = View.GONE
            scrimView?.setBackgroundResource(R.drawable.search_home_scrim)
            scrimView?.visibility = View.VISIBLE
            activity.window.setBackgroundDrawable(copyCachedBlur(activity))
            return
        }

        // Dialer bake unavailable — frosted wallpaper via public blur-behind APIs.
        usingLiveBlur = true
        enableLiveWallpaperFlags()
        backdropView.setImageDrawable(null)
        backdropView.visibility = View.GONE
        activity.findViewById<View>(R.id.search_home_root)?.background = null
        activity.findViewById<View>(R.id.category_root)?.background = null
        blurHost?.background = null
        blurHost?.visibility = View.GONE
        scrimView?.setBackgroundResource(R.drawable.search_home_scrim_soft)
        scrimView?.visibility = View.VISIBLE
        // Prefer Oplus background-blur drawable when the platform allows it;
        // otherwise FLAG_BLUR_BEHIND + setBackgroundBlurRadius already frosts.
        val host = blurHost
        if (host != null) {
            host.visibility = View.VISIBLE
            host.post {
                if (!released && usingLiveBlur) {
                    val ok = frostWallpaperInCompositor(host)
                    Log.i(TAG, "live frost drawable applied=$ok (window blur-behind always on)")
                }
            }
        }
    }

    private fun enableLiveWallpaperFlags() {
        activity.window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER or
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
        )
        activity.window.setBackgroundDrawableResource(android.R.color.transparent)
        if (Build.VERSION.SDK_INT >= 31) {
            activity.window.attributes = activity.window.attributes.apply {
                blurBehindRadius = LIVE_BLUR_RADIUS_PX
            }
            activity.window.setBackgroundBlurRadius(LIVE_BLUR_RADIUS_PX)
        }
    }

    private fun clearLiveWallpaperFlags() {
        activity.window.clearFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER or
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
        )
        if (Build.VERSION.SDK_INT >= 31) {
            activity.window.setBackgroundBlurRadius(0)
        }
    }

    private fun frostWallpaperInCompositor(target: View): Boolean {
        return runCatching {
            val radius = target.resources.getDimensionPixelSize(R.dimen.search_home_live_blur_radius)
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
            liveBlurDrawable = drawable
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

    private fun invalidateAndReload() {
        synchronized(BLUR_LOCK) {
            sCachedBlurredWallpaper = null
            clearWallpaperSourceCache(activity.applicationContext)
        }
        Thread({
            ensureBlurredWallpaper(activity.applicationContext)
            if (!released) {
                activity.runOnUiThread {
                    if (!released) {
                        paintBackdrop()
                    }
                }
            }
        }, "search-blur-reload").apply {
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }

    companion object {
        private const val TAG = "SearchHomeBackdrop"
        /** Same knobs as InCallActivity.loadBlurredWallpaperBackground. */
        private const val BLUR_RADIUS = 25
        private const val BLUR_SCALE = 4
        private const val MAX_WALLPAPER_EDGE_PX = 720
        private const val LIVE_BLUR_RADIUS_PX = 150
        private const val WALLPAPER_CACHE_NAME = "wallpaper.bin"

        private val BLUR_LOCK = Any()
        @Volatile
        private var sCachedBlurredWallpaper: Drawable? = null

        fun prefetch(context: Context?) {
            if (context == null || sCachedBlurredWallpaper != null) {
                return
            }
            Thread(
                { ensureBlurredWallpaper(context.applicationContext) },
                "search-blur-prefetch",
            ).apply {
                priority = Thread.NORM_PRIORITY - 1
                start()
            }
        }

        fun applyCachedToWindow(activity: Activity) {
            ensureBlurredWallpaper(activity.applicationContext)
            val blur = copyCachedBlur(activity)
            if (blur != null) {
                activity.window.setBackgroundDrawable(blur)
            } else {
                // Leave transparent so FLAG_SHOW_WALLPAPER + blur-behind can frost.
                activity.window.setBackgroundDrawableResource(android.R.color.transparent)
            }
        }

        private fun copyCachedBlur(context: Context): Drawable? {
            val cached = sCachedBlurredWallpaper
            if (cached is BitmapDrawable) {
                val bmp = cached.bitmap
                if (bmp != null && !bmp.isRecycled) {
                    return BitmapDrawable(context.resources, bmp)
                }
            }
            return cached
        }

        private fun ensureBlurredWallpaper(context: Context) {
            if (sCachedBlurredWallpaper != null) {
                return
            }
            synchronized(BLUR_LOCK) {
                if (sCachedBlurredWallpaper != null) {
                    return
                }
                val wallpaper = loadBlurredWallpaperBackground(context)
                if (wallpaper != null) {
                    sCachedBlurredWallpaper = wallpaper
                }
            }
        }

        private fun loadBlurredWallpaperBackground(context: Context): Drawable? {
            return runCatching {
                val wm = WallpaperManager.getInstance(context) ?: return null
                val wallpaperBitmap = loadWallpaperBitmap(wm, context)
                if (wallpaperBitmap == null) {
                    Log.w(TAG, "wallpaper bitmap unavailable; will use live frost fallback")
                    return null
                }
                if (wallpaperBitmap.width <= 0) {
                    return null
                }
                Log.i(TAG, "baking frost ${wallpaperBitmap.width}x${wallpaperBitmap.height}")
                val toBlur = if (wallpaperBitmap.config != Bitmap.Config.ARGB_8888) {
                    wallpaperBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: wallpaperBitmap
                } else {
                    wallpaperBitmap
                }
                val blurred = FastBlur.doBlur(toBlur, BLUR_RADIUS, BLUR_SCALE, false, 0)
                    ?: return BitmapDrawable(context.resources, wallpaperBitmap)
                Log.i(TAG, "frost ready ${blurred.width}x${blurred.height}")
                cacheWallpaperSource(context, toBlur)
                BitmapDrawable(context.resources, blurred)
            }.onFailure {
                Log.w(TAG, "loadBlurredWallpaperBackground failed", it)
            }.getOrNull()
        }

        /** Keep a private copy so later launches can bake without WallpaperManager. */
        private fun cacheWallpaperSource(context: Context, source: Bitmap) {
            runCatching {
                val out = java.io.File(context.filesDir, WALLPAPER_CACHE_NAME)
                java.io.FileOutputStream(out).use { fos ->
                    source.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                }
            }
        }

        private fun clearWallpaperSourceCache(context: Context) {
            runCatching {
                java.io.File(context.filesDir, WALLPAPER_CACHE_NAME).delete()
            }
        }

        private fun loadWallpaperBitmap(wm: WallpaperManager, context: Context): Bitmap? {
            decodeWallpaperFile(wm)?.let { return it }
            readWallpaperBitmap(wm)?.let { return it }
            val drawable = runCatching { wm.peekFastDrawable() }.getOrNull()
                ?: runCatching { wm.peekDrawable() }.getOrNull()
                ?: runCatching { wm.fastDrawable }.getOrNull()
                ?: runCatching { wm.drawable }.getOrNull()
            if (drawable is BitmapDrawable && drawable.bitmap != null && drawable.bitmap.hasVisibleColor()) {
                return drawable.bitmap
            }
            if (drawable != null) {
                val width = drawable.intrinsicWidth.takeIf { it > 0 }
                    ?: context.resources.displayMetrics.widthPixels
                val height = drawable.intrinsicHeight.takeIf { it > 0 }
                    ?: context.resources.displayMetrics.heightPixels
                val sample = sampleSize(width, height)
                val bitmap = Bitmap.createBitmap(
                    (width / sample).coerceAtLeast(1),
                    (height / sample).coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                drawable.draw(canvas)
                if (bitmap.hasVisibleColor()) {
                    return bitmap
                }
            }
            // Last resort: wallpaper.bin dropped into files/ (system builds use getBitmap).
            return decodeAppWallpaperCache(context)
        }

        /** WallpaperManager.getBitmap is @SystemApi; needs READ_WALLPAPER_INTERNAL. */
        private fun readWallpaperBitmap(wm: WallpaperManager): Bitmap? {
            return try {
                val method = WallpaperManager::class.java.getMethod("getBitmap")
                (method.invoke(wm) as? Bitmap)?.takeIf { it.width > 0 }
            } catch (se: SecurityException) {
                Log.w(TAG, "getBitmap blocked: ${se.message}")
                null
            } catch (t: Throwable) {
                val cause = t.cause
                if (cause is SecurityException) {
                    Log.w(TAG, "getBitmap blocked: ${cause.message}")
                } else {
                    Log.w(TAG, "getBitmap failed: ${t.message}")
                }
                null
            }
        }

        private fun decodeAppWallpaperCache(context: Context): Bitmap? {
            return runCatching {
                val file = java.io.File(context.filesDir, WALLPAPER_CACHE_NAME)
                if (!file.isFile || file.length() < 64L) {
                    return null
                }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
            }.getOrNull()
        }

        private fun decodeWallpaperFile(manager: WallpaperManager): Bitmap? {
            return try {
                manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { file ->
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 2
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeFileDescriptor(file.fileDescriptor, null, options)
                }
            } catch (se: SecurityException) {
                Log.w(TAG, "getWallpaperFile blocked: ${se.message}")
                null
            } catch (t: Throwable) {
                Log.w(TAG, "getWallpaperFile failed: ${t.message}")
                null
            }
        }

        private fun sampleSize(width: Int, height: Int): Int {
            val maxEdge = maxOf(width, height).coerceAtLeast(1)
            var sample = 1
            while (maxEdge / sample > MAX_WALLPAPER_EDGE_PX) {
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
                android.graphics.Color.red(pixel) +
                    android.graphics.Color.green(pixel) +
                    android.graphics.Color.blue(pixel) > 48
            }
        }
    }
}

@RequiresApi(27)
private object WallpaperPaletteApi {
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
}
