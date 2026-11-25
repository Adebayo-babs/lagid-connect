package com.identiko.wavelock

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * CameraX analyzer. Once a QR comes into view it watches for
 * [WaveLock.WATCH_MS], collecting frames, then reports.
 * Callbacks run on the analysis thread.
 */
internal class WaveAnalyzer(
    private val onWatching: (progress: Float) -> Unit,
    private val onResult: (WaveScanResult) -> Unit,
    private val onTooFewFrames: () -> Unit,
) : ImageAnalysis.Analyzer {

    private val stitcher = WaveStitcher()
    private var startedAt = 0L

    @Volatile
    private var paused = false

    /** Start looking for a new QR. */
    fun rearm() {
        stitcher.reset()
        startedAt = 0L
        paused = false
    }

    override fun analyze(image: ImageProxy) {
        try {
            if (!paused) process(image)
        } finally {
            image.close()
        }
    }

    private fun process(image: ImageProxy) {
        val w = image.width
        val h = image.height
        val frame = GridSampler.sample(lumaOf(image), w, h)
        val now = SystemClock.elapsedRealtime()

        if (startedAt == 0L) {
            if (frame == null) return
            startedAt = now
        }
        if (frame != null) stitcher.add(frame)

        val elapsed = now - startedAt
        val progress = elapsed.toFloat() / WaveLock.WATCH_MS
        onWatching(progress.coerceAtMost(1f))
        if (elapsed < WaveLock.WATCH_MS) return

        paused = true
        if (stitcher.count < WaveLock.MIN_FRAMES) {
            onTooFewFrames()
        } else {
            onResult(stitcher.evaluate())
        }
    }

    /** Copy the Y (brightness) plane without row padding. */
    private fun lumaOf(image: ImageProxy): ByteArray {
        val plane = image.planes[0]
        val buf = plane.buffer
        val w = image.width
        val h = image.height
        val stride = plane.rowStride
        val out = ByteArray(w * h)
        if (stride == w) {
            buf.position(0)
            buf.get(out, 0, w * h)
        } else {
            for (row in 0 until h) {
                buf.position(row * stride)
                buf.get(out, row * w, w)
            }
        }
        return out
    }
}