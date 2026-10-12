package com.example

import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testChunkedCompressionAndReassembly() {
        val originalText = "{\"name\":\"سام ميكروتك\",\"parties\":[{\"id\":\"P1\",\"name\":\"عميل تجريبي\"}]}".repeat(100)
        val compressed = com.example.data.sync.ChunkedBackupHelper.compressGzip(originalText)
        assertTrue("Compressed data must be smaller than original", compressed.size < originalText.toByteArray().size)

        val decompressed = com.example.data.sync.ChunkedBackupHelper.decompressGzip(compressed)
        assertEquals(originalText, decompressed)

        val chunks = com.example.data.sync.ChunkedBackupHelper.splitIntoChunks(compressed)
        assertTrue(chunks.isNotEmpty())
        val reassembled = com.example.data.sync.ChunkedBackupHelper.assembleChunks(chunks)
        val decompressedFromChunks = com.example.data.sync.ChunkedBackupHelper.decompressGzip(reassembled)
        assertEquals(originalText, decompressedFromChunks)
    }

    @Test
    fun testPassphraseEncryptionIntegrity() {
        val rawData = "Confidential double-entry ledger data".toByteArray()
        val passphrase = "SafePassword#2026"

        val encrypted = com.example.data.sync.ChunkedBackupHelper.encryptWithPassphrase(rawData, passphrase)
        assertFalse("Ciphertext should differ from raw", rawData.contentEquals(encrypted))

        val decrypted = com.example.data.sync.ChunkedBackupHelper.decryptWithPassphrase(encrypted, passphrase)
        assertArrayEquals("Decrypted data must match original", rawData, decrypted)
    }
}
