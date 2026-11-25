package com.lagidconnect.wallet.data

import android.util.Base64
import java.math.BigInteger

/**
 * Small helpers for ES256 JWTs and JWS, written by hand
 * so the private key never leaves the Android Keystore.
 */
object Jose {

    fun b64url(bytes: ByteArray): String =
        Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )

    fun b64url(text: String): String = b64url(text.toByteArray(Charsets.UTF_8))

    /** A BigInteger as exactly [size] unsigned big-endian bytes. */
    fun unsigned(value: BigInteger, size: Int = 32): ByteArray {
        val raw = value.toByteArray()
        return when {
            raw.size == size -> raw
            raw.size > size -> raw.copyOfRange(raw.size - size, raw.size)
            else -> ByteArray(size - raw.size) + raw
        }
    }

    /**
     * Android signs ECDSA in DER:
     *   SEQUENCE { INTEGER r, INTEGER s }
     * JWS needs r and s as two 32-byte values back to back.
     */
    fun derToRaw(der: ByteArray, size: Int = 32): ByteArray {
        var i = 0
        require(der[i++].toInt() == 0x30) { "Not a DER sequence" }
        i += lengthBytes(der, i)

        fun readInt(): ByteArray {
            require(der[i++].toInt() == 0x02) { "Expected INTEGER" }
            val len = der[i++].toInt() and 0xFF
            val bytes = der.copyOfRange(i, i + len)
            i += len
            return unsigned(BigInteger(1, bytes), size)
        }

        val r = readInt()
        val s = readInt()
        return r + s
    }

    // Skips the DER length field, returns how many bytes it used
    private fun lengthBytes(der: ByteArray, at: Int): Int {
        val first = der[at].toInt() and 0xFF
        return if (first < 0x80) 1 else 1 + (first and 0x7F)
    }

    /**
     * Builds "<header>.<payload>.<signature>" where [sign]
     * returns a DER ECDSA signature of the input bytes.
     */
    fun compact(
        headerJson: String,
        payloadJson: String,
        sign: (ByteArray) -> ByteArray,
    ): String {
        val input = b64url(headerJson) + "." + b64url(payloadJson)
        val der = sign(input.toByteArray(Charsets.US_ASCII))
        return input + "." + b64url(derToRaw(der))
    }
}
