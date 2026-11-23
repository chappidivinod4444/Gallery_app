package app.gallery.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A photo or video kept in the Vault. */
data class VaultItem(
    val id: String,
    val name: String,
    val mime: String,
    val date: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val originalPath: String,
) {
    val isVideo get() = mime.startsWith("video")
}

/**
 * The Vault: hidden photos and videos, encrypted inside the app's private storage.
 *
 * - Files are encrypted with a random 256-bit key ([VaultCrypto], AES-GCM).
 * - That key is itself encrypted by a key that lives in the phone's secure hardware
 *   (Android Keystore) and can't be copied out of the phone.
 * - The list of items (names, dates) is encrypted too.
 * - Hidden items are removed from the phone's normal gallery and from other apps.
 */
object Vault {
    private const val KEK_ALIAS = "gallery_vault_kek"
    private lateinit var dir: File
    private lateinit var tempDir: File
    private lateinit var app: Context
    private var key: ByteArray? = null

    val items = MutableStateFlow<List<VaultItem>>(emptyList())
    /** The vault must be unlocked (fingerprint / face / screen lock) every time it's opened. */
    val unlocked = MutableStateFlow(false)

    private val thumbs = LruCache<String, Bitmap>(120)

    fun init(c: Context) {
        app = c.applicationContext
        dir = File(app.filesDir, "vault").apply { mkdirs() }
        tempDir = File(app.cacheDir, "vault_play").apply { mkdirs() }
        // Never leave decrypted copies behind.
        tempDir.listFiles()?.forEach { it.delete() }
    }

    fun lock() {
        unlocked.value = false
        thumbs.evictAll()
        tempDir.listFiles()?.forEach { it.delete() }
    }

    /** Call after the person has proved it's them. */
    fun open() {
        dataKey()
        items.value = loadIndex()
        unlocked.value = true
    }

    // ---- keys

    private fun kek(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEK_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEK_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun dataKey(): ByteArray {
        key?.let { return it }
        val prefs = app.getSharedPreferences("vault", Context.MODE_PRIVATE)
        val stored = prefs.getString("dk", null)
        val k = if (stored != null) {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, kek(), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
            c.doFinal(blob.copyOfRange(12, blob.size))
        } else {
            val fresh = VaultCrypto.newKey()
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, kek())
            val wrapped = c.iv + c.doFinal(fresh)
            prefs.edit().putString("dk", Base64.encodeToString(wrapped, Base64.NO_WRAP)).commit()
            fresh
        }
        key = k
        return k
    }

    // ---- index

    private fun indexFile() = File(dir, "index.bin")

    private fun loadIndex(): List<VaultItem> {
        val f = indexFile()
        if (!f.exists()) return emptyList()
        val json = ByteArrayOutputStream().also { out -> f.inputStream().use { VaultCrypto.decrypt(dataKey(), it, out) } }.toString("UTF-8")
        val a = JSONArray(json)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            VaultItem(
                o.getString("id"), o.getString("name"), o.getString("mime"), o.getLong("date"), o.getLong("size"),
                o.optInt("w"), o.optInt("h"), o.optLong("dur"), o.optString("path"),
            )
        }.sortedByDescending { it.date }
    }

    private fun saveIndex(list: List<VaultItem>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("name", it.name).put("mime", it.mime).put("date", it.date).put("size", it.size)
                    .put("w", it.width).put("h", it.height).put("dur", it.durationMs).put("path", it.originalPath)
            )
        }
        val tmp = File(dir, "index.tmp")
        tmp.outputStream().use { VaultCrypto.encrypt(dataKey(), ByteArrayInputStream(a.toString().toByteArray()), it) }
        tmp.renameTo(indexFile())
        items.value = list.sortedByDescending { it.date }
    }

    private fun dataFile(id: String) = File(dir, "$id.bin")
    private fun thumbFile(id: String) = File(dir, "$id.th")

    // ---- hide / restore

    /** Encrypts copies of [list] into the vault. The originals must then be deleted by the caller. */
    fun hide(list: List<Media>): List<Media> {
        val k = dataKey()
        val done = ArrayList<Media>()
        val current = items.value.toMutableList()
        for (m in list) {
            val id = UUID.randomUUID().toString()
            try {
                app.contentResolver.openInputStream(m.uri)!!.use { input ->
                    dataFile(id).outputStream().use { VaultCrypto.encrypt(k, input, it) }
                }
                runCatching {
                    val th = app.contentResolver.loadThumbnail(m.uri, Size(512, 512), null)
                    val jpg = ByteArrayOutputStream().also { th.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
                    thumbFile(id).outputStream().use { VaultCrypto.encrypt(k, ByteArrayInputStream(jpg), it) }
                }
                current += VaultItem(id, m.name, m.mime, m.date, m.size, m.width, m.height, m.durationMs, m.relativePath)
                done += m
            } catch (t: Throwable) {
                dataFile(id).delete(); thumbFile(id).delete()
            }
        }
        saveIndex(current)
        return done
    }

    /** Puts items back into the normal gallery (decrypted) and removes them from the vault. */
    fun restore(list: List<VaultItem>): Int {
        var ok = 0
        val remaining = items.value.toMutableList()
        for (v in list) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, v.name)
                put(MediaStore.MediaColumns.MIME_TYPE, v.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, restorePath(v))
                put(MediaStore.MediaColumns.DATE_TAKEN, v.date)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val coll = if (v.isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri: Uri = app.contentResolver.insert(coll, values) ?: continue
            try {
                dataFile(v.id).inputStream().use { input ->
                    app.contentResolver.openOutputStream(uri)!!.use { VaultCrypto.decrypt(dataKey(), input, it) }
                }
                app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                dataFile(v.id).delete(); thumbFile(v.id).delete()
                remaining.removeAll { it.id == v.id }
                ok++
            } catch (t: Throwable) {
                app.contentResolver.delete(uri, null, null)
            }
        }
        saveIndex(remaining)
        MediaRepo.refreshSoon()
        return ok
    }

    private fun restorePath(v: VaultItem): String {
        val p = v.originalPath.ifBlank { "Pictures/Restored/" }
        val top = p.substringBefore('/')
        val allowed = if (v.isVideo) setOf("DCIM", "Movies", "Pictures") else setOf("DCIM", "Pictures")
        return if (top in allowed) p else (if (v.isVideo) "Movies/" else "Pictures/") + p
    }

    fun deleteForever(list: List<VaultItem>) {
        list.forEach { dataFile(it.id).delete(); thumbFile(it.id).delete(); thumbs.remove(it.id) }
        saveIndex(items.value.filter { v -> list.none { it.id == v.id } })
    }

    // ---- viewing

    fun thumbnail(id: String): Bitmap? {
        thumbs.get(id)?.let { return it }
        val f = thumbFile(id)
        if (!f.exists()) return null
        val bytes = ByteArrayOutputStream().also { out -> f.inputStream().use { VaultCrypto.decrypt(dataKey(), it, out) } }.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { thumbs.put(id, it) }
    }

    /** The full picture, decrypted in memory only. */
    fun image(v: VaultItem, maxSide: Int = 3000): Bitmap? {
        val bytes = ByteArrayOutputStream().also { out -> dataFile(v.id).inputStream().use { VaultCrypto.decrypt(dataKey(), it, out) } }.toByteArray()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return rotateByExif(bmp, bytes)
    }

    private fun rotateByExif(bmp: Bitmap, bytes: ByteArray): Bitmap {
        val deg = runCatching { androidx.exifinterface.media.ExifInterface(ByteArrayInputStream(bytes)).rotationDegrees }.getOrDefault(0)
        if (deg == 0) return bmp
        val m = android.graphics.Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /** Videos need a file to play from: decrypted into the app's private cache, deleted when you leave. */
    fun videoFile(v: VaultItem): File {
        val out = File(tempDir, v.id + "." + v.name.substringAfterLast('.', "mp4"))
        if (!out.exists()) {
            dataFile(v.id).inputStream().use { input -> out.outputStream().use { VaultCrypto.decrypt(dataKey(), input, it) } }
        }
        return out
    }

    fun clearTemp() {
        tempDir.listFiles()?.forEach { it.delete() }
    }
}
