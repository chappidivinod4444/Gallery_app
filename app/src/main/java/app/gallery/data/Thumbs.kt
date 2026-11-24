package app.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import java.io.File
import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options

/**
 * A small preview of a photo or video. Uses Android's own thumbnail cache, which is
 * much faster than decoding the full picture and also works for videos.
 */
data class Thumb(val uri: Uri, val px: Int = 384)

class ThumbFetcher(private val data: Thumb, private val context: Context) : Fetcher {
    override suspend fun fetch(): FetchResult {
        // 1. Our own small-preview cache: reading a tiny saved JPEG is several times faster than asking Android.
        val cached = ThumbCache.file(context, data)
        if (cached != null && cached.exists()) {
            val o = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            BitmapFactory.decodeFile(cached.path, o)?.let { return ImageFetchResult(it.asImage(), true, DataSource.DISK) }
        }
        // 2. Android's thumbnail (or, for files in hidden folders, one made from the file).
        val bmp = if (data.uri.scheme == "file") {
            val f = File(data.uri.path!!)
            val size = Size(data.px, data.px)
            val video = f.extension.lowercase() in setOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v", "ts")
            if (video) ThumbnailUtils.createVideoThumbnail(f, size, null) else ThumbnailUtils.createImageThumbnail(f, size, null)
        } else context.contentResolver.loadThumbnail(data.uri, Size(data.px, data.px), null)
        if (cached != null) ThumbCache.save(cached, bmp)
        return ImageFetchResult(image = bmp.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val context: Context) : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher = ThumbFetcher(data, context)
    }
}

/** Saved small previews for the grid (in the app's cache folder; Android may clear it any time). */
object ThumbCache {
    private const val MAX_BYTES = 300L * 1024 * 1024

    fun file(c: Context, t: Thumb): File? {
        if (t.px > 400) return null
        val dir = File(c.cacheDir, "thumbs")
        return File(dir, "${t.uri.toString().hashCode().toUInt()}_${t.uri.lastPathSegment}_${t.px}.jpg")
    }

    fun save(f: File, bmp: Bitmap) {
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            tmp.renameTo(f)
        }
    }

    /** Keeps the cache under its size limit by removing the oldest previews. */
    fun trim(c: Context) {
        runCatching {
            val files = File(c.cacheDir, "thumbs").listFiles() ?: return
            var total = files.sumOf { it.length() }
            if (total <= MAX_BYTES) return
            for (f in files.sortedBy { it.lastModified() }) {
                total -= f.length(); f.delete()
                if (total <= MAX_BYTES * 3 / 4) break
            }
        }
    }
}

class ThumbKeyer : Keyer<Thumb> {
    override fun key(data: Thumb, options: Options): String = "thumb:${data.uri}:${data.px}"
}

/** A vault item's thumbnail: decrypted in memory, never written to any cache on disk. */
data class VaultThumb(val id: String)

class VaultThumbFetcher(private val data: VaultThumb) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bmp = Vault.thumbnail(data.id) ?: error("No thumbnail")
        return ImageFetchResult(image = bmp.asImage(), isSampled = true, dataSource = DataSource.MEMORY)
    }

    class Factory : Fetcher.Factory<VaultThumb> {
        override fun create(data: VaultThumb, options: Options, imageLoader: ImageLoader): Fetcher = VaultThumbFetcher(data)
    }
}

class VaultThumbKeyer : Keyer<VaultThumb> {
    override fun key(data: VaultThumb, options: Options): String = "vault:${data.id}"
}
