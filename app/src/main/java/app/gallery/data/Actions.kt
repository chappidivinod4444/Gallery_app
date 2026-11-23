package app.gallery.data

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/** Things you can do with photos: share, trash, delete, move, copy, rename, set as wallpaper. */
object Actions {

    fun share(c: Context, items: List<Media>) {
        if (items.isEmpty()) return
        val intent = if (items.size == 1) {
            Intent(Intent.ACTION_SEND).setType(items[0].mime).putExtra(Intent.EXTRA_STREAM, items[0].uri)
        } else {
            val types = items.map { it.mime.substringBefore('/') }.distinct()
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType(if (types.size == 1) "${types[0]}/*" else "*/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        c.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Android asks you to confirm; the files go to Trash for 30 days. */
    fun trashRequest(c: Context, items: List<Media>, trash: Boolean = true): PendingIntent =
        MediaStore.createTrashRequest(c.contentResolver, items.map { it.uri }, trash)

    /** Android asks you to confirm; the files are deleted for good. */
    fun deleteRequest(c: Context, items: List<Media>): PendingIntent =
        MediaStore.createDeleteRequest(c.contentResolver, items.map { it.uri })

    /** Android asks you to confirm before this app may change (move / rename) the files. */
    fun writeRequest(c: Context, items: List<Media>): PendingIntent =
        MediaStore.createWriteRequest(c.contentResolver, items.map { it.uri })

    /** After [writeRequest] was approved: moves files into another folder. */
    fun move(c: Context, items: List<Media>, relativePath: String): Int {
        var ok = 0
        val path = relativePath.trimEnd('/') + "/"
        for (m in items) {
            val v = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, fixPath(path, m.isVideo)) }
            if (runCatching { c.contentResolver.update(m.uri, v, null, null) }.getOrDefault(0) > 0) ok++
        }
        MediaRepo.refreshSoon()
        return ok
    }

    /** After [writeRequest] was approved: renames a file (keeps its extension). */
    fun rename(c: Context, m: Media, newName: String): Boolean {
        val ext = m.name.substringAfterLast('.', "")
        val clean = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val full = if (ext.isNotEmpty() && !clean.endsWith(".$ext", true)) "$clean.$ext" else clean
        val ok = runCatching {
            c.contentResolver.update(m.uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, full) }, null, null)
        }.getOrDefault(0) > 0
        MediaRepo.refreshSoon()
        return ok
    }

    /** Copies files into a folder (no permission pop-up needed: they become new files of this app). */
    fun copy(c: Context, items: List<Media>, relativePath: String): Int {
        var ok = 0
        for (m in items) {
            if (runCatching { copyOne(c, m, relativePath) }.getOrNull() != null) ok++
        }
        MediaRepo.refreshSoon()
        return ok
    }

    private fun copyOne(c: Context, m: Media, relativePath: String): Uri? {
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, m.name)
            put(MediaStore.MediaColumns.MIME_TYPE, m.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, fixPath(relativePath.trimEnd('/') + "/", m.isVideo))
            put(MediaStore.MediaColumns.DATE_TAKEN, m.date)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val coll = if (m.isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val out = c.contentResolver.insert(coll, v) ?: return null
        try {
            c.contentResolver.openInputStream(m.uri)!!.use { input ->
                c.contentResolver.openOutputStream(out)!!.use { input.copyTo(it, 256 * 1024) }
            }
            c.contentResolver.update(out, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return out
        } catch (t: Throwable) {
            c.contentResolver.delete(out, null, null)
            throw t
        }
    }

    /**
     * Android only lets photos live under Pictures/DCIM and videos under Movies/DCIM:
     * keep the chosen folder but put it under an allowed top folder if needed.
     */
    private fun fixPath(path: String, video: Boolean): String {
        val top = path.substringBefore('/')
        val allowed = if (video) setOf("DCIM", "Movies", "Pictures") else setOf("DCIM", "Pictures")
        return if (top in allowed) path else (if (video) "Movies/" else "Pictures/") + path
    }

    /** Saves a picture you edited as a new photo. Returns its address. */
    fun saveBitmap(c: Context, bmp: Bitmap, baseName: String, takenAt: Long?): Uri? {
        val name = baseName.substringBeforeLast('.') + "_edited_" + System.currentTimeMillis() / 1000 + ".jpg"
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Edited/")
            put(MediaStore.MediaColumns.DATE_TAKEN, takenAt ?: System.currentTimeMillis())
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = c.contentResolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v) ?: return null
        return try {
            c.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            c.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            MediaRepo.refreshSoon()
            uri
        } catch (t: Throwable) {
            c.contentResolver.delete(uri, null, null)
            null
        }
    }

    /** "Set as" (wallpaper, contact photo…) through Android's own screen: no extra permission. */
    fun setAs(c: Context, m: Media) {
        val i = Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(m.uri, m.mime)
            .putExtra("mimeType", m.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        c.startActivity(Intent.createChooser(i, "Set as").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Opens the picture in another app chosen by you. */
    fun openWith(c: Context, m: Media) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(m.uri, m.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        c.startActivity(Intent.createChooser(i, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
