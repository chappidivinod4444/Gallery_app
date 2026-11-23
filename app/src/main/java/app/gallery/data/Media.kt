package app.gallery.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One photo or video on the phone. */
data class Media(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    /** When it was taken (or added), in milliseconds. */
    val date: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucketId: Long,
    val bucket: String,
    val relativePath: String,
    val isVideo: Boolean,
) {
    val day: LocalDate get() = Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).toLocalDate()
    val isGif get() = mime == "image/gif"
    val isScreenshot get() = bucket.contains("screenshot", true) || relativePath.contains("screenshot", true)
}

/** A folder of photos (what the phone calls a "bucket"). */
data class Album(val id: Long, val name: String, val cover: Media, val count: Int, val path: String)

/** Reads every photo and video through Android's media index and keeps it up to date. */
object MediaRepo {
    val all = MutableStateFlow<List<Media>>(emptyList())
    val trash = MutableStateFlow<List<Media>>(emptyList())
    val loaded = MutableStateFlow(false)

    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pending: Job? = null

    fun init(context: Context) {
        app = context.applicationContext
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refreshSoon()
        }
        app.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        app.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    /** Several changes often arrive together (a burst of photos): wait a moment and reload once. */
    fun refreshSoon() {
        pending?.cancel()
        pending = scope.launch {
            delay(400)
            refresh()
        }
    }

    fun refresh() {
        scope.launch {
            all.value = query(trashed = false)
            loaded.value = true
            trash.value = query(trashed = true)
        }
    }

    private fun query(trashed: Boolean): List<Media> {
        val out = ArrayList<Media>(4096)
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.ORIENTATION,
        )
        val args = Bundle().apply {
            putString(
                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)",
            )
            putStringArray(
                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                ),
            )
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE)
        }
        try {
            app.contentResolver.query(collection, projection, args, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val iMod = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val iDur = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val iBucketName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val iPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val iType = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val iOrient = c.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val video = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val base = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    val taken = c.getLong(iTaken)
                    val rotated = c.getInt(iOrient) % 180 != 0
                    val w = c.getInt(iW)
                    val h = c.getInt(iH)
                    out += Media(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        name = c.getString(iName) ?: "",
                        mime = c.getString(iMime) ?: if (video) "video/*" else "image/*",
                        date = if (taken > 0) taken else c.getLong(iMod) * 1000,
                        size = c.getLong(iSize),
                        width = if (rotated) h else w,
                        height = if (rotated) w else h,
                        durationMs = c.getLong(iDur),
                        bucketId = c.getLong(iBucket),
                        bucket = c.getString(iBucketName) ?: "Other",
                        relativePath = c.getString(iPath) ?: "",
                        isVideo = video,
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted yet.
        }
        out.sortByDescending { it.date }
        return out
    }

    fun albums(list: List<Media>): List<Album> =
        list.groupBy { it.bucketId }.map { (id, items) ->
            Album(id, items.first().bucket, items.first(), items.size, items.first().relativePath)
        }.sortedWith(
            compareByDescending<Album> { it.name.equals("Camera", true) }
                .thenByDescending { it.name.contains("screenshot", true) }
                .thenByDescending { it.cover.date }
        )
}
