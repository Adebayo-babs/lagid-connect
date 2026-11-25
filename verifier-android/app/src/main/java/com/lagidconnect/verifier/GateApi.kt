package com.lagidconnect.verifier

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class VerifyReply(
    val valid: Boolean = false,
    val reason: String? = null,
    val passId: String? = null,
    val name: String? = null,
    val lagIdLast4: String? = null,
    val faceTemplate: String? = null,
)

class GateException(message: String) : Exception(message)

/** Talks to the Connect API's /v1/gate routes. */
class GateApi(
    private val baseUrl: String,
    private val apiKey: String,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val type = "application/json".toMediaType()

    val configured: Boolean get() = apiKey.isNotBlank()

    /** Redeems a pass code (works once). */
    suspend fun verify(code: String): VerifyReply {
        val body = buildJsonObject { put("code", code) }
        return json.decodeFromString(post("/v1/gate/verify", body))
    }

    /** Reports the face-match outcome to the Gate Monitor. */
    suspend fun report(passId: String, granted: Boolean, reason: String, score: Float) {
        val body = buildJsonObject {
            put("passId", passId)
            put("granted", granted)
            put("reason", reason)
            put("faceScore", score.toDouble())
        }
        post("/v1/gate/results", body)
    }

    private suspend fun post(path: String, body: JsonObject): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody(type))
                .build()
            val response = try {
                http.newCall(request).execute()
            } catch (e: IOException) {
                throw GateException("Can't reach the server")
            }
            response.use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    throw GateException(
                        if (it.code == 401) "Gate key not valid. Check lagid.gateKey."
                        else "Server error (${it.code})"
                    )
                }
                text
            }
        }
}
