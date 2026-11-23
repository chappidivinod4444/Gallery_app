package app.gallery

import app.gallery.data.VaultCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class VaultCryptoTest {
    private val key = VaultCrypto.newKey()

    private fun enc(data: ByteArray) = ByteArrayOutputStream().also { VaultCrypto.encrypt(key, ByteArrayInputStream(data), it) }.toByteArray()
    private fun dec(data: ByteArray, k: ByteArray = key) = ByteArrayOutputStream().also { VaultCrypto.decrypt(k, ByteArrayInputStream(data), it) }.toByteArray()

    @Test
    fun roundTripSizes() {
        for (size in listOf(0, 1, 1000, VaultCrypto.CHUNK - 1, VaultCrypto.CHUNK, VaultCrypto.CHUNK + 1, VaultCrypto.CHUNK * 3, 1_000_003)) {
            val data = Random.nextBytes(size)
            assertArrayEquals("size $size", data, dec(enc(data)))
        }
    }

    @Test
    fun wrongKeyFails() = expectFailure { dec(enc(Random.nextBytes(5000)), VaultCrypto.newKey()) }

    @Test
    fun tamperedByteFails() {
        val e = enc(Random.nextBytes(200_000))
        e[e.size / 2] = (e[e.size / 2].toInt() xor 1).toByte()
        expectFailure { dec(e) }
    }

    @Test
    fun cutOffFails() {
        val e = enc(Random.nextBytes(VaultCrypto.CHUNK * 3))
        // Drop the last chunk exactly: the previous chunk isn't marked "last", so it must fail.
        val lastChunkSize = 1 + 4 + 12 + VaultCrypto.CHUNK + 16
        expectFailure { dec(e.copyOf(e.size - lastChunkSize)) }
    }

    @Test
    fun extraDataFails() = expectFailure { dec(enc(Random.nextBytes(100)) + byteArrayOf(1, 2, 3)) }

    private fun expectFailure(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            return
        }
        fail("Expected decryption to fail")
    }
}
