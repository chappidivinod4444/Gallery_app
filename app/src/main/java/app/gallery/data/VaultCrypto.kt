package app.gallery.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * File encryption for the Vault: AES-256-GCM in 64 KB chunks.
 *
 * Every chunk has its own random 12-byte nonce and 16-byte tag. The file's random id, the
 * chunk number and a "last chunk" flag are authenticated with each chunk, so chunks can't be
 * changed, swapped between files, reordered, dropped or cut off without decryption failing.
 *
 * Layout: "GVLT" | version (1) | file id (16) | chunks…
 * Chunk:  last flag (1) | plain length (4) | nonce (12) | ciphertext + tag
 */
object VaultCrypto {
    private val MAGIC = byteArrayOf('G'.code.toByte(), 'V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())
    private const val VERSION: Byte = 1
    const val CHUNK = 64 * 1024
    private val random = SecureRandom()

    fun newKey(): ByteArray = ByteArray(32).also { random.nextBytes(it) }

    private fun aad(fileId: ByteArray, index: Long, last: Boolean): ByteArray =
        ByteBuffer.allocate(fileId.size + 9).put(fileId).putLong(index).put(if (last) 1 else 0).array()

    fun encrypt(key: ByteArray, input: InputStream, output: OutputStream) {
        require(key.size == 32)
        val keySpec = SecretKeySpec(key, "AES")
        val out = DataOutputStream(output)
        val fileId = ByteArray(16).also { random.nextBytes(it) }
        out.write(MAGIC); out.writeByte(VERSION.toInt()); out.write(fileId)

        var current = readFully(input, CHUNK)
        var index = 0L
        while (true) {
            val next = if (current.size == CHUNK) readFully(input, CHUNK) else ByteArray(0)
            val last = next.isEmpty()
            val nonce = ByteArray(12).also { random.nextBytes(it) }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, nonce))
            c.updateAAD(aad(fileId, index, last))
            val ct = c.doFinal(current)
            out.writeByte(if (last) 1 else 0)
            out.writeInt(current.size)
            out.write(nonce)
            out.write(ct)
            if (last) break
            current = next
            index++
        }
        out.flush()
    }

    fun decrypt(key: ByteArray, input: InputStream, output: OutputStream) {
        require(key.size == 32)
        val keySpec = SecretKeySpec(key, "AES")
        val inp = DataInputStream(input)
        val magic = ByteArray(4).also { inp.readFully(it) }
        if (!magic.contentEquals(MAGIC)) throw IOException("Not a vault file")
        if (inp.readByte() != VERSION) throw IOException("Unknown vault version")
        val fileId = ByteArray(16).also { inp.readFully(it) }
        var index = 0L
        while (true) {
            val flag = try { inp.readByte().toInt() } catch (e: EOFException) { throw IOException("Vault file is cut off") }
            if (flag != 0 && flag != 1) throw IOException("Vault file is damaged")
            val last = flag == 1
            val len = inp.readInt()
            if (len < 0 || len > CHUNK) throw IOException("Vault file is damaged")
            val nonce = ByteArray(12).also { inp.readFully(it) }
            val ct = ByteArray(len + 16).also { inp.readFully(it) }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, nonce))
            c.updateAAD(aad(fileId, index, last))
            output.write(c.doFinal(ct)) // throws if anything was changed
            if (last) {
                if (inp.read() != -1) throw IOException("Unexpected data after the end")
                break
            }
            index++
        }
        output.flush()
    }

    private fun readFully(input: InputStream, max: Int): ByteArray {
        val buf = ByteArray(max)
        var n = 0
        while (n < max) {
            val r = input.read(buf, n, max - n)
            if (r < 0) break
            n += r
        }
        return if (n == max) buf else buf.copyOf(n)
    }
}
