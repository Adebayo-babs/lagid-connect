package com.lagidconnect.wallet.data

import kotlinx.serialization.Serializable

// Shapes match the Connect API's wallet routes.

@Serializable
data class LinkStart(
    val linkId: String,
    val expiresIn: Int = 600,
    val firstName: String? = null,
    val phoneMasked: String? = null,
    val devCode: String? = null,
)

@Serializable
data class LinkResult(
    val residentId: String,
    val deviceId: String,
    val displayName: String? = null,
    val lagIdLast4: String? = null,
)

@Serializable
data class Me(
    val residentId: String,
    val displayName: String? = null,
    val lagIdLast4: String? = null,
    val phoneMasked: String? = null,
    val linkedAt: String? = null,
    val deviceId: String,
)

@Serializable
data class BusinessInfo(
    val id: String,
    val name: String,
    val category: String? = null,
    val verified: Boolean = false,
    val logoUrl: String? = null,
)

@Serializable
data class ClaimItem(
    val id: String,
    val label: String,
    val required: Boolean = false,
)

@Serializable
data class ConsentRequest(
    val id: String,
    val status: String,
    val purpose: String,
    val business: BusinessInfo? = null,
    val claims: List<ClaimItem> = emptyList(),
    val approvedClaims: List<String> = emptyList(),
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val decidedAt: String? = null,
)

@Serializable
data class RequestList(val requests: List<ConsentRequest> = emptyList())

@Serializable
data class RequestWrapper(val request: ConsentRequest)

@Serializable
data class ActivityItem(
    val id: String,
    val type: String,
    val business: String? = null,
    val requestId: String? = null,
    val result: String? = null,
    val claims: List<String>? = null,
    val purpose: String? = null,
    val at: String,
)

@Serializable
data class ActivityList(val activity: List<ActivityItem> = emptyList())

@Serializable
data class GrantItem(
    val id: String,
    val business: String? = null,
    val claims: List<String> = emptyList(),
    val since: String? = null,
)

@Serializable
data class GrantList(val grants: List<GrantItem> = emptyList())

@Serializable
data class GatePass(
    val code: String,
    val expiresIn: Int = 30,
    val expiresAt: String? = null,
)

@Serializable
data class ApiErrorBody(val error: ApiErrorDetail? = null)

@Serializable
data class ApiErrorDetail(
    val code: String = "error",
    val message: String = "Something went wrong",
)

class ApiException(
    val status: Int,
    val code: String,
    override val message: String,
) : Exception(message)
