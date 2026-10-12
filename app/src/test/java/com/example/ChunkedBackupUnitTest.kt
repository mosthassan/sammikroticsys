package com.example

import com.example.data.sync.ChunkedBackupHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkedBackupUnitTest {

    @Test
    fun testGzipCompressionAndDecompression() {
        val originalText = "{\"name\":\"سام ميكروتك\",\"parties\":[{\"id\":\"P1\",\"name\":\"عميل تجريبي\"}]}".repeat(100)
        val compressed = ChunkedBackupHelper.compressGzip(originalText)
        assertTrue("Compressed data must be smaller than original", compressed.size < originalText.toByteArray().size)

        val decompressed = ChunkedBackupHelper.decompressGzip(compressed)
        assertEquals(originalText, decompressed)
    }

    @Test
    fun testChunkingAndReassembly() {
        val payload = ByteArray(1_200_000) { (it % 256).toByte() }
        val chunks = ChunkedBackupHelper.splitIntoChunks(payload)
        assertTrue("Payload > 1MB should be split into multiple chunks", chunks.size >= 3)

        // Verify each chunk is <= 500KB
        for (chunk in chunks) {
            assertTrue("Chunk size must be <= 500KB", chunk.toByteArray().size <= ChunkedBackupHelper.MAX_CHUNK_SIZE_BYTES)
        }

        val reassembled = ChunkedBackupHelper.assembleChunks(chunks)
        assertArrayEquals(payload, reassembled)
    }

    @Test
    fun testSha256VerificationAndCorruptionDetection() {
        val data = "Valid JSON accounting backup payload".toByteArray()
        val originalHash = ChunkedBackupHelper.computeSha256(data)
        assertEquals(64, originalHash.length)

        val corruptedData = "Corrupted JSON accounting backup payload".toByteArray()
        val corruptedHash = ChunkedBackupHelper.computeSha256(corruptedData)
        assertFalse(originalHash == corruptedHash)
    }

    @Test
    fun testPassphraseEncryptionAndDecryption() {
        val rawData = "Confidential double-entry ledger data".toByteArray()
        val passphrase = "SafePassword#2026"

        val encrypted = ChunkedBackupHelper.encryptWithPassphrase(rawData, passphrase)
        assertFalse("Ciphertext should differ from raw", rawData.contentEquals(encrypted))

        val decrypted = ChunkedBackupHelper.decryptWithPassphrase(encrypted, passphrase)
        assertArrayEquals("Decrypted data must match original", rawData, decrypted)
    }

    @Test(expected = Exception::class)
    fun testInvalidPassphraseFailsDecryption() {
        val rawData = "Confidential double-entry ledger data".toByteArray()
        val encrypted = ChunkedBackupHelper.encryptWithPassphrase(rawData, "CorrectPassword")
        ChunkedBackupHelper.decryptWithPassphrase(encrypted, "WrongPassword")
    }
}
