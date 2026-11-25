package com.lagidconnect.verifier.face


import com.google.mlkit.vision.face.Face
import kotlin.math.abs
import kotlin.random.Random

/**
 * Active liveness: the person must blink and turn their head in a random direction, then face
 * the camera again. The frame captured at the end is the one used for matching.
 *
 * This stops a printed photo or a still image on a phone. It does NOT stop a well-made video
 * replay; for that, add a passive anti-spoof model later.
 */
class LivenessChecker(random: Random = Random.Default) {

    enum class Step { BLINK, TURN_LEFT, TURN_RIGHT, LOOK_STRAIGHT }

    sealed interface State {
        data class Prompt(val message: String, val completedSteps: Int, val totalSteps: Int) : State
        data class Passed(val face: Face) : State
        data class Failed(val reason: String) : State
    }

    private val steps: List<Step> = listOf(
        Step.BLINK,
        if (random.nextBoolean()) Step.TURN_LEFT else Step.TURN_RIGHT,
    ).shuffled(random) + Step.LOOK_STRAIGHT

    private var stepIndex = 0
    private var trackingId: Int? = null
    private var startedAt = -1L
    private var lastSeenAt = -1L
    private var eyesSeenOpen = false
    private var framesWithoutEyeData = 0
    private var finished = false

    val totalSteps: Int get() = steps.size

    fun onFrame(faces: List<Face>, frameWidth: Int, nowMs: Long): State {
        if (finished) return State.Failed("Check already finished.")
        if (startedAt < 0) startedAt = nowMs
        if (nowMs - startedAt > FaceConfig.LIVENESS_TIMEOUT_MS) {
            finished = true
            return State.Failed("Took too long. Please try again.")
        }

        when {
            faces.isEmpty() -> {
                // Losing the face for more than a moment restarts the sequence, so nobody can
                // swap faces between challenges.
                if (trackingId != null && lastSeenAt > 0 && nowMs - lastSeenAt > 1_500) restart()
                return prompt("Place your face inside the oval")
            }
            faces.size > 1 -> return prompt("Only one person should be in view")
        }

        val face = faces.first()
        lastSeenAt = nowMs

        val id = face.trackingId
        if (trackingId == null) {
            trackingId = id
        } else if (id != null && id != trackingId) {
            restart()
            trackingId = id
            return prompt("Face changed. Starting again")
        }

        if (face.boundingBox.width() < frameWidth * FaceConfig.MIN_FACE_WIDTH_RATIO) {
            return prompt("Move closer to the camera")
        }

        when (steps[stepIndex]) {
            Step.BLINK -> handleBlink(face)
            Step.TURN_LEFT -> if (userYaw(face) >= FaceConfig.TURN_ANGLE_DEG) advance()
            Step.TURN_RIGHT -> if (userYaw(face) <= -FaceConfig.TURN_ANGLE_DEG) advance()
            Step.LOOK_STRAIGHT -> if (isFrontal(face)) {
                finished = true
                return State.Passed(face)
            }
        }
        return prompt(null)
    }

    private fun handleBlink(face: Face) {
        val l = face.leftEyeOpenProbability
        val r = face.rightEyeOpenProbability
        if (l == null || r == null) {
            // Some devices never report eye probabilities. Rather than lock people out, skip the
            // blink after ~2 seconds of missing data; the head turn still has to pass.
            if (++framesWithoutEyeData > 60) advance()
            return
        }
        if (l > 0.6f && r > 0.6f) eyesSeenOpen = true
        if (eyesSeenOpen && l < 0.25f && r < 0.25f) advance()
    }

    /** Positive = person turned to their left (see FaceConfig.YAW_SIGN_FOR_USER_LEFT). */
    private fun userYaw(face: Face) = face.headEulerAngleY * FaceConfig.YAW_SIGN_FOR_USER_LEFT

    private fun isFrontal(face: Face): Boolean {
        val leftOpen = face.leftEyeOpenProbability?.let { it > 0.5f } ?: true
        val rightOpen = face.rightEyeOpenProbability?.let { it > 0.5f } ?: true
        val eyesOpen = leftOpen && rightOpen
        return abs(face.headEulerAngleY) < 10f &&
            abs(face.headEulerAngleX) < 12f &&
            abs(face.headEulerAngleZ) < 12f &&
            eyesOpen
    }

    private fun advance() {
        stepIndex = (stepIndex + 1).coerceAtMost(steps.lastIndex)
    }

    private fun restart() {
        stepIndex = 0
        eyesSeenOpen = false
        framesWithoutEyeData = 0
        trackingId = null
    }

    private fun prompt(override: String?): State.Prompt {
        val text = override ?: when (steps[stepIndex]) {
            Step.BLINK -> "Blink slowly"
            Step.TURN_LEFT -> "Turn your head to your left"
            Step.TURN_RIGHT -> "Turn your head to your right"
            Step.LOOK_STRAIGHT -> "Now look straight at the camera"
        }
        return State.Prompt(text, stepIndex, steps.size)
    }
}
