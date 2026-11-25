package com.lagidconnect.wallet.face

import com.lagidconnect.wallet.BuildConfig

/** Settings for the face engine (from the FaceMatch app). */
object FaceConfig {
    const val MODEL_ASSET = "mobile_face_net.tflite"

    /** Cosine similarity at or above this counts as a match. */
    const val MATCH_THRESHOLD = 0.55f

    const val LIVENESS_TIMEOUT_MS = 25_000L
    const val MIN_FACE_WIDTH_RATIO = 0.30f
    const val TURN_ANGLE_DEG = 25f

    /** Flip to -1f if "turn left" passes when turning right. */
    const val YAW_SIGN_FOR_USER_LEFT = 1f

    val SHOW_DEBUG: Boolean = BuildConfig.DEBUG
}
