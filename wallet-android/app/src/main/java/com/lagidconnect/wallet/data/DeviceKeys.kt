package com.lagidconnect.wallet.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * The phone's own P-256 key, kept in the Android Keystore.
 * The private key can't be exported; we only ask the
 * Keystore to sign things with it.
 */
class DeviceKeys {

    private val keyStore = KeyStore.getInstance(STORE).apply { load(null) }

    fun hasKey(): Boolean = keyStore.containsAlias(ALIAS)

    /** Makes a fresh key, replacing any old one. */
    fun createKey() {
        if (hasKey()) keyStore.deleteEntry(ALIAS)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        KeyPairGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_EC, STORE)
            .apply { initialize(spec) }
            .generateKeyPair()
    }

    fun deleteKey() {
        if (hasKey()) keyStore.deleteEntry(ALIAS)
    }

    /** Public key as a JWK the server can store. */
    fun publicJwk(): Map<String, String> {
        val pub = keyStore.getCertificate(ALIAS).publicKey as ECPublicKey
        return mapOf(
            "kty" to "EC",
            "crv" to "P-256",
            "x" to Jose.b64url(Jose.unsigned(pub.w.affineX)),
            "y" to Jose.b64url(Jose.unsigned(pub.w.affineY)),
        )
    }

    private fun signDer(data: ByteArray): ByteArray {
        val key = keyStore.getKey(ALIAS, null) as PrivateKey
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(key)
            update(data)
            sign()
        }
    }

    private fun now(): Long = System.currentTimeMillis() / 1000

    /**
     * Short-lived token proving "this request comes from
     * device [deviceId]". Sent as Authorization: Bearer.
     */
    fun deviceToken(deviceId: String, ttlSeconds: Long = 120): String {
        val header = buildJsonObject {
            put("alg", "ES256")
            put("typ", "JWT")
            put("kid", deviceId)
        }
        val iat = now()
        val payload = buildJsonObject {
            put("aud", WALLET_AUDIENCE)
            put("iat", iat)
            put("exp", iat + ttlSeconds)
        }
        return Jose.compact(header.toString(), payload.toString(), ::signDer)
    }

    /**
     * Signs the resident's decision on a request:
     *   { rid, decision, claims (sorted), iat }
     */
    fun signDecision(
        requestId: String,
        decision: String,
        claims: List<String>,
    ): String {
        val header = buildJsonObject { put("alg", "ES256") }
        val payload = buildJsonObject {
            put("rid", requestId)
            put("decision", decision)
            put("claims", JsonArray(claims.sorted().map { JsonPrimitive(it) }))
            put("iat", now())
        }
        return Jose.compact(header.toString(), payload.toString(), ::signDer)
    }

    companion object {
        private const val STORE = "AndroidKeyStore"
        private const val ALIAS = "lagid_connect_device"
        const val WALLET_AUDIENCE = "lagid-connect-wallet"
    }
}
