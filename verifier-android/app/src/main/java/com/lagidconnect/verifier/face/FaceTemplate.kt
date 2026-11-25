package com.lagidconnect.verifier.face

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * A face embedding as text, so it can be stored and sent.
 * Float32, little-endian, then base64 (no line breaks).
 * The server keeps only sha256(this text).
 */
object FaceTemplate {

    fun encode(embedding: FloatArray): String {
        val buf = ByteBuffer
            .allocate(embedding.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buf.putFloat(it) }
        return Base64.encodeToString(buf.array(), Base64.NO_WRAP)
    }

    fun decode(text: String): FloatArray {
        val bytes = Base64.decode(text, Base64.DEFAULT)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buf.getFloat() }
    }

    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
