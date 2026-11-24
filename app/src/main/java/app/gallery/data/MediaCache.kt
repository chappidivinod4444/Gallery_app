package app.gallery.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** Saves the photo list in the app's private storage so the next start shows it instantly. */
object MediaCache {
    private const val VERSION = 2

    private fun file(c: Context) = File(c.noBackupFilesDir, "media_index.bin")

    fun write(c: Context, list: List<Media>) {
        runCatching {
            val tmp = File(c.noBackupFilesDir, "media_index.tmp")
            DataOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { o ->
                o.writeInt(VERSION); o.writeInt(list.size)
                for (m in list) {
                    o.writeLong(m.id); o.writeBoolean(m.isVideo); o.writeUTF(m.name); o.writeUTF(m.mime)
                    o.writeLong(m.date); o.writeLong(m.size); o.writeInt(m.width); o.writeInt(m.height)
                    o.writeLong(m.durationMs); o.writeLong(m.bucketId); o.writeUTF(m.bucket); o.writeUTF(m.relativePath)
                }
            }
            tmp.renameTo(file(c))
        }
    }

    fun read(c: Context): List<Media>? = runCatching {
        val f = file(c)
        if (!f.exists()) return null
        DataInputStream(BufferedInputStream(f.inputStream(), 1 shl 16)).use { i ->
            if (i.readInt() != VERSION) return null
            val n = i.readInt()
            val images = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val videos = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val out = ArrayList<Media>(n)
            repeat(n) {
                val id = i.readLong(); val video = i.readBoolean()
                out += Media(
                    id = id, uri = ContentUris.withAppendedId(if (video) videos else images, id),
                    name = i.readUTF(), mime = i.readUTF(), date = i.readLong(), size = i.readLong(),
                    width = i.readInt(), height = i.readInt(), durationMs = i.readLong(), bucketId = i.readLong(),
                    bucket = i.readUTF(), relativePath = i.readUTF(), isVideo = video,
                )
            }
            out
        }
    }.getOrNull()
}

/** Finds pictures in folders other apps hid from galleries (a ".nomedia" file, or a name starting with "."). */
object HiddenScan {
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "dng", "avif")
    private val videoExt = setOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v", "ts")
    /** Folders full of other apps' small preview copies: not worth showing. */
    private val skip = setOf(".thumbnails", ".thumbs", ".cache", "cache", ".trash", ".trashed", ".pending", ".git", ".gs_fs0", ".statuses_cache")

    fun allowed() = Environment.isExternalStorageManager()

    fun scan(limit: Int = 30_000): List<Media> {
        val root = Environment.getExternalStorageDirectory()
        val out = ArrayList<Media>()
        fun visit(dir: File, hiddenAbove: Boolean, depth: Int) {
            if (depth > 9 || out.size >= limit) return
            val list = dir.listFiles() ?: return
            val hidden = hiddenAbove || dir.name.startsWith(".") || list.any { it.name == ".nomedia" }
            val rel = dir.path.removePrefix(root.path).trimStart('/') + "/"
            for (f in list) {
                if (f.isDirectory) {
                    val n = f.name.lowercase()
                    if (n in skip || n.startsWith(".trashed")) continue
                    if (depth == 0 && n == "android") {
                        // Only Android/media (WhatsApp and friends) is readable and useful.
                        File(f, "media").takeIf { it.isDirectory }?.let { visit(it, hidden, depth + 2) }
                        continue
                    }
                    visit(f, hidden, depth + 1)
                } else if (hidden) {
                    val ext = f.extension.lowercase()
                    val video = ext in videoExt
                    if (!video && ext !in imageExt) continue
                    if (f.length() < 2048) continue
                    out += Media(
                        id = -(1_000_000_000L + (f.path.hashCode().toLong() and 0x7FFFFFFF)),
                        uri = Uri.fromFile(f), name = f.name,
                        mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: if (video) "video/*" else "image/*",
                        date = f.lastModified(), size = f.length(), width = 0, height = 0, durationMs = 0,
                        bucketId = -(dir.path.hashCode().toLong() and 0x7FFFFFFF) - 1, bucket = dir.name.ifBlank { "Hidden" },
                        relativePath = rel, isVideo = video,
                    )
                }
            }
        }
        runCatching { visit(root, false, 0) }
        out.sortByDescending { it.date }
        return out
    }
}
