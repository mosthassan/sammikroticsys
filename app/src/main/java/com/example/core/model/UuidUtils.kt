package com.example.core.model

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.UUID

object UuidUtils {
    private val random = SecureRandom()

    /**
     * Generates a time-ordered UUID (UUIDv7-compatible layout: 48-bit timestamp ms + random bits)
     * Ensuring natural sequential ordering in SQLite B-Trees.
     */
    fun newTimeOrderedId(): String {
        val now = System.currentTimeMillis()
        val randomBytes = ByteArray(10)
        random.nextBytes(randomBytes)

        val bb = ByteBuffer.allocate(16)
        // 48 bits of timestamp
        bb.putShort((now ushr 32).toShort())
        bb.putInt((now and 0xFFFFFFFFL).toInt())
        // Version 7 in high nibble of byte 6
        val b6 = (randomBytes[0].toInt() and 0x0F) or 0x70
        bb.put(b6.toByte())
        bb.put(randomBytes[1])
        // Variant in high 2 bits of byte 8 (RFC 4122: 10xxxxxx)
        val b8 = (randomBytes[2].toInt() and 0x3F) or 0x80
        bb.put(b8.toByte())
        for (i in 3 until 10) {
            bb.put(randomBytes[i])
        }
        bb.flip()
        val mostSig = bb.long
        val leastSig = bb.long
        return UUID(mostSig, leastSig).toString()
    }
}
