package com.example.data.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Utility for compressing, chunking (<= 500KB per chunk), hashing (SHA-256),
 * and optionally encrypting (AES-256-GCM + PBKDF2) cloud backup payloads.
 */
object ChunkedBackupHelper {

    const val MAX_CHUNK_SIZE_BYTES = 500 * 1024 // 500 KB limit per chunk
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PBKDF2_ITERATIONS = 65536
    private const val SALT_LENGTH = 16

    /**
     * Compresses the raw UTF-8 JSON text using GZIP.
     */
    fun compressGzip(rawJson: String): ByteArray {
        val bytes = rawJson.toByteArray(Charsets.UTF_8)
        val byteOut = ByteArrayOutputStream()
        GZIPOutputStream(byteOut).use { it.write(bytes) }
        return byteOut.toByteArray()
    }

    /**
     * Decompresses GZIP-compressed bytes back to a UTF-8 string.
     */
    fun decompressGzip(compressedBytes: ByteArray): String {
        val byteIn = ByteArrayInputStream(compressedBytes)
        GZIPInputStream(byteIn).use { gzip ->
            return gzip.readBytes().toString(Charsets.UTF_8)
        }
    }

    /**
     * Calculates the SHA-256 hex string of the given byte array.
     */
    fun computeSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Splits byte array into Base64-encoded string chunks, each chunk <= MAX_CHUNK_SIZE_BYTES in encoded size.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun splitIntoChunks(data: ByteArray): List<String> {
        val base64String = Base64.encode(data)
        // Split string into chunks of max 400,000 characters (comfortably <= 500KB)
        val chunkSizeChars = 400_000
        val chunks = mutableListOf<String>()
        var offset = 0
        while (offset < base64String.length) {
            val end = (offset + chunkSizeChars).coerceAtMost(base64String.length)
            chunks.add(base64String.substring(offset, end))
            offset = end
        }
        return chunks
    }

    /**
     * Reassembles a list of Base64-encoded string chunks back into a raw byte array.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun assembleChunks(chunks: List<String>): ByteArray {
        val fullBase64 = chunks.joinToString("")
        return Base64.decode(fullBase64)
    }

    /**
     * Encrypts byte array using AES-256-GCM derived from a passphrase via PBKDF2.
     * Returns: salt (16 bytes) + IV (12 bytes) + ciphertext.
     */
    fun encryptWithPassphrase(data: ByteArray, passphrase: String): ByteArray {
        val random = java.security.SecureRandom()
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val iv = ByteArray(GCM_IV_LENGTH)
        random.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val cipherText = cipher.doFinal(data)

        val byteOut = ByteArrayOutputStream()
        byteOut.write(salt)
        byteOut.write(iv)
        byteOut.write(cipherText)
        return byteOut.toByteArray()
    }

    /**
     * Decrypts byte array using AES-256-GCM derived from a passphrase via PBKDF2.
     * Expects: salt (16 bytes) + IV (12 bytes) + ciphertext.
     */
    fun decryptWithPassphrase(encryptedData: ByteArray, passphrase: String): ByteArray {
        require(encryptedData.size > SALT_LENGTH + GCM_IV_LENGTH) { "Invalid encrypted data length" }

        val salt = encryptedData.copyOfRange(0, SALT_LENGTH)
        val iv = encryptedData.copyOfRange(SALT_LENGTH, SALT_LENGTH + GCM_IV_LENGTH)
        val cipherText = encryptedData.copyOfRange(SALT_LENGTH + GCM_IV_LENGTH, encryptedData.size)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(cipherText)
    }
}
