package com.lagidconnect.wallet.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lagidconnect.wallet.WalletApp
import com.lagidconnect.wallet.data.ActivityItem
import com.lagidconnect.wallet.data.ApiException
import com.lagidconnect.wallet.data.ConsentRequest
import com.lagidconnect.wallet.data.GrantItem
import com.lagidconnect.wallet.data.LinkStart
import com.lagidconnect.wallet.face.FaceException
import com.lagidconnect.wallet.face.FaceTemplate
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FaceStatus { Idle, Capturing, Checking, Passed, Failed }

data class PassUi(
    val code: String? = null,
    val expiresAtMs: Long = 0L,
    val loading: Boolean = false,
    val error: String? = null,
    val needsEnrolment: Boolean = false,
)

data class LinkUi(
    val lagId: String = "",
    val phone: String = "",
    val start: LinkStart? = null,
    val code: String = "",
    val face: FaceStatus = FaceStatus.Idle,
    val attempt: Int = 0,
    val busy: Boolean = false,
    val error: String? = null,
)

data class HomeUi(
    val name: String = "",
    val lagIdLast4: String = "",
    val pending: List<ConsentRequest> = emptyList(),
    val recent: List<ConsentRequest> = emptyList(),
    val activity: List<ActivityItem> = emptyList(),
    val grants: List<GrantItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class WalletViewModel(app: Application) : AndroidViewModel(app) {

    private val wallet = app as WalletApp
    private val api = wallet.api
    private val session = wallet.session
    private val keys = wallet.keys

    private val _link = MutableStateFlow(LinkUi())
    val link: StateFlow<LinkUi> = _link.asStateFlow()

    private val _home = MutableStateFlow(
        HomeUi(
            name = session.displayName.orEmpty(),
            lagIdLast4 = session.lagIdLast4.orEmpty(),
        )
    )
    val home: StateFlow<HomeUi> = _home.asStateFlow()

    /** Set when the server no longer knows this device. */
    private val _signedOut = MutableStateFlow(false)
    val signedOut: StateFlow<Boolean> = _signedOut.asStateFlow()

    val isLinked: Boolean
        get() = session.isLinked && keys.hasKey()

    // ===================== linking =====================

    fun startLink(lagId: String, phone: String, onSent: () -> Unit) {
        val id = lagId.trim().uppercase()
        val tel = phone.trim()
        if (id.isEmpty() || tel.isEmpty()) {
            _link.update { it.copy(error = "Enter your LagID and phone number") }
            return
        }
        _link.update { it.copy(lagId = id, phone = tel, busy = true, error = null) }
        viewModelScope.launch {
            try {
                val start = api.linkStart(id, tel)
                _link.update { it.copy(start = start, busy = false, code = "") }
                onSent()
            } catch (e: ApiException) {
                _link.update { it.copy(busy = false, error = friendly(e)) }
            }
        }
    }

    fun setCode(code: String) {
        _link.update { it.copy(code = code.filter(Char::isDigit).take(6), error = null) }
    }

    /** Opens the camera for the liveness check. */
    fun startFaceCapture() {
        _link.update {
            it.copy(
                face = FaceStatus.Capturing,
                attempt = it.attempt + 1,
                error = null,
            )
        }
    }

    fun faceCaptureFailed(message: String) {
        _link.update { it.copy(face = FaceStatus.Failed, busy = false, error = message) }
    }

    /**
     * Liveness passed on [frame]. Runs the FaceMatch engine
     * (align + MobileFaceNet) to make the face template, keeps
     * the template on this phone, sends only its hash, and
     * finishes linking.
     */
    fun enrolFace(frame: Bitmap, face: Face, onLinked: () -> Unit) {
        val state = _link.value
        val start = state.start ?: return
        _link.update { it.copy(face = FaceStatus.Checking, busy = true, error = null) }

        viewModelScope.launch {
            val template = try {
                val (_, embedding) = wallet.faceProcessor.embedDetected(frame, face)
                FaceTemplate.encode(embedding)
            } catch (e: FaceException) {
                faceCaptureFailed(e.message ?: "Face check didn’t work. Try again.")
                return@launch
            }

            try {
                val result = api.linkComplete(
                    start.linkId,
                    state.code,
                    faceScore = 1.0,
                    templateHash = FaceTemplate.sha256Hex(template),
                )
                session.save(result, state.lagId)
                session.faceTemplate = template
                _link.update { it.copy(face = FaceStatus.Passed) }
                _home.update {
                    it.copy(
                        name = result.displayName.orEmpty(),
                        lagIdLast4 = result.lagIdLast4.orEmpty(),
                    )
                }
                _signedOut.value = false
                _link.value = LinkUi()
                onLinked()
            } catch (e: ApiException) {
                keys.deleteKey()
                _link.update {
                    it.copy(busy = false, face = FaceStatus.Failed, error = friendly(e))
                }
            }
        }
    }

    fun clearLinkError() = _link.update { it.copy(error = null) }

    fun resetFace() = _link.update {
        it.copy(face = FaceStatus.Idle, busy = false, error = null)
    }

    // ===================== home =====================

    fun refreshHome() {
        _home.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            guarded {
                val all = api.requests()
                val activity = api.activity()
                _home.update {
                    it.copy(
                        pending = all.filter { r -> r.status == "pending" },
                        recent = all.filter { r -> r.status != "pending" },
                        activity = activity,
                        loading = false,
                    )
                }
            }
            _home.update { it.copy(loading = false) }
        }
    }

    /** Quiet poll so new requests pop up during the demo. */
    fun pollPending() {
        viewModelScope.launch {
            guarded(silent = true) {
                val pending = api.requests(status = "pending")
                _home.update { it.copy(pending = pending) }
            }
        }
    }

    fun requestById(id: String): ConsentRequest? =
        _home.value.pending.firstOrNull { it.id == id }
            ?: _home.value.recent.firstOrNull { it.id == id }

    fun decide(
        request: ConsentRequest,
        approve: Boolean,
        claims: List<String>,
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                api.decide(request, approve, claims)
                onDone(null)
                refreshHome()
            } catch (e: ApiException) {
                if (e.status == 401 && e.code == "device_not_active") signOut()
                onDone(friendly(e))
                refreshHome()
            }
        }
    }

    // ===================== gate pass =====================

    private val _pass = MutableStateFlow(PassUi())
    val pass: StateFlow<PassUi> = _pass.asStateFlow()

    /** Fetches a fresh single-use pass code (30 seconds). */
    fun refreshPass() {
        val template = session.faceTemplate
        if (template == null) {
            _pass.value = PassUi(needsEnrolment = true)
            return
        }
        _pass.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val p = api.pass(template)
                _pass.value = PassUi(
                    code = p.code,
                    expiresAtMs = System.currentTimeMillis() + p.expiresIn * 1000L,
                )
            } catch (e: ApiException) {
                when {
                    e.status == 401 && e.code == "device_not_active" -> signOut()
                    e.code == "face_not_enrolled" ||
                        e.code == "face_template_mismatch" ->
                        _pass.value = PassUi(needsEnrolment = true)
                    else -> _pass.update {
                        it.copy(code = null, loading = false, error = friendly(e))
                    }
                }
            }
        }
    }

    fun clearPass() {
        _pass.value = PassUi()
    }

    // ===================== activity & grants =====================

    fun loadActivity() {
        viewModelScope.launch {
            guarded {
                val activity = api.activity()
                _home.update { it.copy(activity = activity) }
            }
        }
    }

    fun loadGrants() {
        viewModelScope.launch {
            guarded {
                val grants = api.grants()
                _home.update { it.copy(grants = grants) }
            }
        }
    }

    fun revoke(grant: GrantItem) {
        viewModelScope.launch {
            guarded {
                api.revoke(grant.id)
                _home.update { it.copy(grants = it.grants - grant) }
                loadActivity()
            }
        }
    }

    // ===================== session =====================

    fun signOut() {
        keys.deleteKey()
        session.clear()
        _home.value = HomeUi()
        _signedOut.value = true
    }

    private suspend fun guarded(silent: Boolean = false, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: ApiException) {
            if (e.status == 401 && e.code in setOf("device_not_active", "not_linked")) {
                signOut()
            } else if (!silent) {
                _home.update { it.copy(error = friendly(e)) }
            }
        }
    }

    private fun friendly(e: ApiException): String = when (e.code) {
        "resident_not_found" -> "We couldn’t find that LagID."
        "phone_mismatch" -> "That phone number isn’t the one on your LagID record."
        "record_inactive" -> "This LagID is not active."
        "record_unverified" -> "This LagID record isn’t verified yet."
        "wrong_code" -> "That code isn’t right. Go back and check it."
        "code_expired", "link_not_found" -> "The code expired. Start again."
        "too_many_attempts" -> "Too many tries. Start again."
        "face_match_failed" -> "Face check didn’t pass."
        "request_closed" -> "This request was already closed."
        "network" -> e.message
        else -> e.message
    }
}
