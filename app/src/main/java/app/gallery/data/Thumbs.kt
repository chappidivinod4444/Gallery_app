package app.gallery.data

import android.content.Context
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
        val bmp = context.contentResolver.loadThumbnail(data.uri, Size(data.px, data.px), null)
        return ImageFetchResult(image = bmp.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val context: Context) : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher = ThumbFetcher(data, context)
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
