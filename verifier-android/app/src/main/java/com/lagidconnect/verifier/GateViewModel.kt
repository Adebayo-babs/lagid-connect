package com.lagidconnect.verifier

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.face.Face
import com.identiko.wavelock.WaveScanResult
import com.lagidconnect.verifier.face.FaceConfig
import com.lagidconnect.verifier.face.FaceEmbedder
import com.lagidconnect.verifier.face.FaceException
import com.lagidconnect.verifier.face.FaceMatcher
import com.lagidconnect.verifier.face.FaceProcessor
import com.lagidconnect.verifier.face.FaceTemplate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the gate is in checking one person. */
sealed interface GateStep {
    data object Scanning : GateStep
    data class Busy(val message: String) : GateStep
    data class FaceCheck(val pass: VerifyReply, val attempt: Int) : GateStep
    data class Result(
        val granted: Boolean,
        val name: String?,
        val lagIdLast4: String?,
        val reason: String,
        val score: Float? = null,
    ) : GateStep
}

class GateViewModel(app: Application) : AndroidViewModel(app) {

    private val api = GateApi(BuildConfig.API_BASE_URL, BuildConfig.GATE_API_KEY)
    val configured: Boolean get() = api.configured

    // Your FaceMatch engine, loaded on first use
    private val faces: FaceProcessor by lazy {
        FaceProcessor(FaceEmbedder(app, FaceConfig.MODEL_ASSET))
    }

    private val _step = MutableStateFlow<GateStep>(GateStep.Scanning)
    val step: StateFlow<GateStep> = _step.asStateFlow()

    private var resetJob: Job? = null
    private var attempts = 0

    /** The wave scanner finished watching a QR. */
    fun onScanned(result: WaveScanResult) {
        val code = result.text
        if (!result.isLive || code == null) {
            // Never sent to the server: there is no live pass
            show(
                GateStep.Result(
                    granted = false,
                    name = null,
                    lagIdLast4 = null,
                    reason = if (code != null && !result.moved)
                        "Photo or screenshot: no moving wave"
                    else "Not a readable LagID pass",
                )
            )
            return
        }

        _step.value = GateStep.Busy("Checking the pass…")
        viewModelScope.launch {
            try {
                val pass = api.verify(code)
                if (pass.valid && pass.passId != null && pass.faceTemplate != null) {
                    attempts += 1
                    _step.value = GateStep.FaceCheck(pass, attempts)
                } else {
                    show(
                        GateStep.Result(
                            granted = false,
                            name = pass.name,
                            lagIdLast4 = pass.lagIdLast4,
                            reason = pass.reason ?: "Pass refused",
                        )
                    )
                }
            } catch (e: GateException) {
                show(GateStep.Result(false, null, null, e.message ?: "Error"))
            }
        }
    }

    /**
     * Liveness passed. Compare the live face with the template
     * the resident enrolled on their phone.
     */
    fun onFace(pass: VerifyReply, frame: Bitmap, face: Face) {
        _step.value = GateStep.Busy("Matching face…")
        viewModelScope.launch {
            val score = try {
                val (_, live) = faces.embedDetected(frame, face)
                val enrolled = FaceTemplate.decode(pass.faceTemplate!!)
                FaceMatcher.similarity(live, enrolled)
            } catch (e: FaceException) {
                finish(pass, false, e.message ?: "Face could not be read", null)
                return@launch
            } catch (e: IllegalArgumentException) {
                finish(pass, false, "Face template not compatible", null)
                return@launch
            }

            val granted = score >= FaceConfig.MATCH_THRESHOLD
            finish(
                pass,
                granted,
                if (granted) "Face matched" else "Face did not match",
                score,
            )
        }
    }

    /** Liveness failed or the guard cancelled: deny and close the pass. */
    fun onFaceFailed(pass: VerifyReply, reason: String) {
        viewModelScope.launch { finish(pass, false, reason, null) }
    }

    private suspend fun finish(pass: VerifyReply, granted: Boolean, reason: String, score: Float?) {
        try {
            api.report(pass.passId!!, granted, reason, score ?: 0f)
        } catch (_: GateException) {
            // The gate still decides; the monitor just misses this one
        }
        show(GateStep.Result(granted, pass.name, pass.lagIdLast4, reason, score))
    }

    private fun show(result: GateStep.Result) {
        _step.value = result
        resetJob?.cancel()
        resetJob = viewModelScope.launch {
            delay(6000)
            next()
        }
    }

    /** Back to scanning for the next person. */
    fun next() {
        resetJob?.cancel()
        _step.value = GateStep.Scanning
    }
}
