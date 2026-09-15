package gd.app.quicksearch.ui.home

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import gd.app.quicksearch.R
import gd.app.quicksearch.search.files.FileItem

/** Type glyphs for file rows; images (and videos) can swap in a MediaStore thumb. */
internal object SearchFileIcons {

    fun placeholder(context: Context, file: FileItem): Drawable {
        return ContextCompat.getDrawable(context, iconRes(file))!!
    }

    fun wantsThumbnail(file: FileItem): Boolean {
        val kind = kind(file)
        return kind == Kind.IMAGE || kind == Kind.VIDEO
    }

    fun loadThumbnail(context: Context, file: FileItem, sizePx: Int): Bitmap? {
        if (sizePx <= 0) {
            return null
        }
        val resolver = context.contentResolver
        val raw = if (Build.VERSION.SDK_INT >= 29) {
            loadModern(resolver, file, sizePx)
        } else {
            loadLegacy(resolver, file, sizePx)
        } ?: return null
        if (raw.width == sizePx && raw.height == sizePx) {
            return raw
        }
        val cropped = ThumbnailUtils.extractThumbnail(raw, sizePx, sizePx)
        if (cropped !== raw) {
            raw.recycle()
        }
        return cropped
    }

    private fun loadModern(resolver: ContentResolver, file: FileItem, sizePx: Int): Bitmap? {
        val size = Size(sizePx, sizePx)
        loadThumbnail(resolver, file.uri, size)?.let { return it }
        val id = runCatching { ContentUris.parseId(file.uri) }.getOrDefault(-1L)
        if (id < 0L) {
            return null
        }
        val collection = if (kind(file) == Kind.VIDEO) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return loadThumbnail(resolver, ContentUris.withAppendedId(collection, id), size)
    }

    private fun loadThumbnail(resolver: ContentResolver, uri: Uri, size: Size): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.loadThumbnail(uri, size, null)
            } else {
                null
            }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun loadLegacy(resolver: ContentResolver, file: FileItem, sizePx: Int): Bitmap? {
        val id = runCatching { ContentUris.parseId(file.uri) }.getOrDefault(-1L)
        if (id >= 0L) {
            val options = BitmapFactory.Options()
            val fromStore = if (kind(file) == Kind.VIDEO) {
                MediaStore.Video.Thumbnails.getThumbnail(
                    resolver,
                    id,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    options,
                )
            } else {
                MediaStore.Images.Thumbnails.getThumbnail(
                    resolver,
                    id,
                    MediaStore.Images.Thumbnails.MINI_KIND,
                    options,
                )
            }
            if (fromStore != null) {
                return fromStore
            }
        }
        return decodeSampled(resolver, file.uri, sizePx)
    }

    private fun decodeSampled(resolver: ContentResolver, uri: Uri, sizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, sizePx)
        }
        return runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    }

    private fun sampleSize(width: Int, height: Int, sizePx: Int): Int {
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / (sample * 2) >= sizePx) {
            sample *= 2
        }
        return sample
    }

    private fun iconRes(file: FileItem): Int {
        return when (kind(file)) {
            Kind.IMAGE -> R.drawable.ic_file_image
            Kind.VIDEO -> R.drawable.ic_file_video
            Kind.AUDIO -> R.drawable.ic_file_audio
            Kind.DOCUMENT -> R.drawable.ic_file_document
            Kind.APK -> R.drawable.ic_file_apk
            Kind.OTHER -> R.drawable.ic_file_generic
        }
    }

    private fun kind(file: FileItem): Kind {
        val mime = file.mime.lowercase()
        val ext = file.name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") || ext in IMAGE_EXT -> Kind.IMAGE
            mime.startsWith("video/") || ext in VIDEO_EXT -> Kind.VIDEO
            mime.startsWith("audio/") || ext in AUDIO_EXT -> Kind.AUDIO
            mime == "application/vnd.android.package-archive" || ext == "apk" -> Kind.APK
            mime == "application/pdf" ||
                mime.startsWith("text/") ||
                mime.contains("officedocument") ||
                mime.contains("msword") ||
                mime.contains("ms-excel") ||
                mime.contains("ms-powerpoint") ||
                ext in DOCUMENT_EXT -> Kind.DOCUMENT
            else -> Kind.OTHER
        }
    }

    private enum class Kind { IMAGE, VIDEO, AUDIO, DOCUMENT, APK, OTHER }

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "dng")
    private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v")
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "amr", "wma")
    private val DOCUMENT_EXT = setOf(
        "pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "rtf",
    )
}
