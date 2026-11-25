package com.lagidconnect.wallet.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the Connect API's /v1/wallet routes.
 * Every call after linking carries a fresh device token.
 */
class WalletApi(
    private val baseUrl: String,
    private val keys: DeviceKeys,
    private val session: Session,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val jsonType = "application/json".toMediaType()

    // ---------------- linking ----------------

    suspend fun linkStart(lagId: String, phone: String): LinkStart {
        val body = buildJsonObject {
            put("lagId", lagId)
            put("phone", phone)
        }
        return post("/v1/wallet/link/start", body, auth = false)
    }

    /** Makes a new device key, then registers it. */
    suspend fun linkComplete(
        linkId: String,
        code: String,
        faceScore: Double,
        templateHash: String,
    ): LinkResult {
        keys.createKey()
        val jwk = keys.publicJwk()
        val body = buildJsonObject {
            put("linkId", linkId)
            put("code", code)
            put("platform", "android")
            put("publicKey", JsonObject(jwk.mapValues { JsonPrimitive(it.value) }))
            put("faceMatch", buildJsonObject {
                put("passed", true)
                put("score", faceScore)
                put("templateHash", templateHash)
            })
        }
        return post("/v1/wallet/link/complete", body, auth = false)
    }

    // ---------------- signed-in calls ----------------

    suspend fun me(): Me = get("/v1/wallet/me")

    suspend fun requests(status: String? = null): List<ConsentRequest> {
        val path = if (status == null) "/v1/wallet/requests"
        else "/v1/wallet/requests?status=$status"
        return get<RequestList>(path).requests
    }

    suspend fun decide(
        request: ConsentRequest,
        approve: Boolean,
        claims: List<String>,
    ): ConsentRequest {
        val decision = if (approve) "approve" else "decline"
        val chosen = if (approve) claims.sorted() else emptyList()
        val signature = keys.signDecision(request.id, decision, chosen)

        val body = buildJsonObject {
            put("decision", decision)
            put("signature", signature)
            put("claims", JsonArray(chosen.map { JsonPrimitive(it) }))
            // The server keeps only a hash of the LagID, so
            // the phone sends it for a fresh registry check.
            if (approve) put("lagId", session.lagId)
        }
        return post<RequestWrapper>(
            "/v1/wallet/requests/${request.id}/decision",
            body,
        ).request
    }

    /** A single-use gate pass code, valid for 30 seconds. */
    suspend fun pass(template: String): GatePass {
        val body = buildJsonObject {
            put("lagId", session.lagId)
            put("template", template)
        }
        return post("/v1/wallet/pass", body)
    }

    suspend fun activity(): List<ActivityItem> =
        get<ActivityList>("/v1/wallet/activity").activity

    suspend fun grants(): List<GrantItem> =
        get<GrantList>("/v1/wallet/grants").grants

    suspend fun revoke(grantId: String) {
        send<JsonObject>(
            Request.Builder()
                .url(baseUrl + "/v1/wallet/grants/$grantId")
                .delete()
                .withAuth()
                .build()
        )
    }

    // ---------------- plumbing ----------------

    private fun Request.Builder.withAuth(): Request.Builder {
        val deviceId = session.deviceId
            ?: throw ApiException(401, "not_linked", "Wallet not linked")
        return header("Authorization", "Bearer " + keys.deviceToken(deviceId))
    }

    private suspend inline fun <reified T> get(path: String): T =
        send(Request.Builder().url(baseUrl + path).get().withAuth().build())

    private suspend inline fun <reified T> post(
        path: String,
        body: JsonObject,
        auth: Boolean = true,
    ): T {
        val builder = Request.Builder()
            .url(baseUrl + path)
            .post(body.toString().toRequestBody(jsonType))
        if (auth) builder.withAuth()
        return send(builder.build())
    }

    private suspend inline fun <reified T> send(request: Request): T =
        withContext(Dispatchers.IO) {
            val response = try {
                http.newCall(request).execute()
            } catch (e: IOException) {
                throw ApiException(
                    0,
                    "network",
                    "Can't reach the server. Check your connection."
                )
            }
            response.use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    val err = runCatching {
                        json.decodeFromString<ApiErrorBody>(text).error
                    }.getOrNull()
                    throw ApiException(
                        it.code,
                        err?.code ?: "http_${it.code}",
                        err?.message ?: "Request failed (${it.code})",
                    )
                }
                json.decodeFromString<T>(text.ifBlank { "{}" })
            }
        }
}
