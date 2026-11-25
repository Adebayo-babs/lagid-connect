package com.identiko.wavelock

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.decoder.Decoder
import com.google.zxing.qrcode.detector.Detector
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** One camera frame, read as a grid of QR squares. */
internal class FrameGrid(
    val bits: BitMatrix,
    val singleRead: String?,
)

/** Finds the QR in a camera frame and samples its squares. */
internal object GridSampler {
    private val hints = mapOf(DecodeHintType.TRY_HARDER to true)

    /**
     * [y] is the camera's brightness plane, [w] x [h].
     * Returns null if no QR of the expected size is in view.
     */
    fun sample(y: ByteArray, w: Int, h: Int): FrameGrid? {
        val src = PlanarYUVLuminanceSource(
            y, w, h, 0, 0, w, h, false,
        )
        val bits = detect(BinaryBitmap(HybridBinarizer(src)))
            ?: detect(BinaryBitmap(GlobalHistogramBinarizer(src)))
            ?: return null
        // Decoder changes the grid it reads, so give it a copy.
        return FrameGrid(bits, tryDecode(bits.clone()))
    }

    private fun detect(bitmap: BinaryBitmap): BitMatrix? = try {
        Detector(bitmap.blackMatrix).detect(hints).bits
            .takeIf { it.width == WaveLock.SIZE }
    } catch (e: ReaderException) {
        null
    }

    /**
     * What any normal scanner would get from this one grid.
     * Note: this changes [bits]; pass a copy to keep it.
     */
    fun tryDecode(bits: BitMatrix): String? = try {
        Decoder().decode(bits).text
    } catch (e: ReaderException) {
        null
    }
}

/** What the scanner found after watching the QR. */
data class WaveScanResult(
    /** Camera frames used. */
    val frames: Int,
    /** Frames a normal scanner could read on their own. */
    val singleReads: Int,
    /** How many times the broken strip crossed the QR. */
    val sweeps: Float,
    val moved: Boolean,
    /** The text rebuilt from all frames, or null. */
    val text: String?,
) {
    /** True only for a live, moving wave QR that was rebuilt. */
    val isLive: Boolean get() = moved && text != null
}

/**
 * Collects frames of the moving QR, rebuilds the clean code
 * by majority vote, and checks that the scrambled strip moved.
 */
internal class WaveStitcher {
    private val n = WaveLock.SIZE
    private val frames = ArrayList<BooleanArray>()
    private var singleReads = 0

    val count get() = frames.size

    fun add(frame: FrameGrid) {
        frames += BooleanArray(n * n) { i ->
            frame.bits.get(i % n, i / n)
        }
        if (frame.singleRead != null) singleReads++
    }

    fun reset() {
        frames.clear()
        singleReads = 0
    }

    fun evaluate(): WaveScanResult {
        // Pass 1: plain majority vote for every square.
        val vote = BooleanArray(n * n) { i ->
            frames.count { it[i] } * 2 > frames.size
        }

        // Where was the wave in each frame?
        val angles = frames.map { waveAngle(it, vote) }

        // Pass 2: vote again, ignoring squares under the wave.
        val clean = cleanVote(angles, vote)
        val matrix = BitMatrix(n)
        for (i in clean.indices) {
            if (clean[i]) matrix.set(i % n, i / n)
        }
        val text = GridSampler.tryDecode(matrix)

        val seen = angles.filterNotNull()
        val sweeps = travelled(seen)
        val moved = seen.size >= frames.size * 0.6 &&
                sweeps >= 0.5f

        return WaveScanResult(
            frames = frames.size,
            singleReads = singleReads,
            sweeps = sweeps,
            moved = moved,
            text = text,
        )
    }

    /**
     * Where is the scrambled strip in one frame? The circular
     * mean of the columns that differ from the vote, as an
     * angle. Null if the frame has almost no scrambled squares.
     */
    private fun waveAngle(
        f: BooleanArray,
        vote: BooleanArray,
    ): Double? {
        var sx = 0.0
        var sy = 0.0
        var k = 0
        for (r in 0 until n) for (c in 0 until n) {
            val i = r * n + c
            if (WaveLock.isFixed(r, c) || f[i] == vote[i]) continue
            val a = 2 * PI * c / n
            sx += cos(a)
            sy += sin(a)
            k++
        }
        return if (k > n) atan2(sy, sx) else null
    }

    private fun cleanVote(
        angles: List<Double?>,
        vote: BooleanArray,
    ): BooleanArray {
        val skip = WaveLock.BAND_FRACTION * n / 2 + 2
        val centres = angles.map { a ->
            a?.let { ((it / (2 * PI) * n) % n + n) % n }
        }
        return BooleanArray(n * n) { i ->
            val r = i / n
            val c = i % n
            var dark = 0
            var used = 0
            for (j in frames.indices) {
                val cc = centres[j]
                if (cc != null && !WaveLock.isFixed(r, c)) {
                    var d = ((c - cc) % n + n) % n
                    if (d > n / 2) d -= n
                    if (abs(d) < skip) continue
                }
                used++
                if (frames[j][i]) dark++
            }
            if (used == 0) vote[i] else dark * 2 > used
        }
    }

    /** Total distance the strip moved, in full sweeps. */
    private fun travelled(angles: List<Double>): Float {
        var total = 0.0
        for (i in 1 until angles.size) {
            var d = angles[i] - angles[i - 1]
            while (d > PI) d -= 2 * PI
            while (d < -PI) d += 2 * PI
            total += abs(d)
        }
        return (total / (2 * PI)).toFloat()
    }
}