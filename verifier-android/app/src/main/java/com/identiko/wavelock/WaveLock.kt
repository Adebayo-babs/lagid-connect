package com.identiko.wavelock

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import kotlin.math.abs
import kotlin.math.sin

/**
 * Settings shared by the staff phone (display) and the gate
 * validator (camera). Both sides must agree on them.
 */
internal object WaveLock {
    /** QR version 5 = 37 x 37 squares, ~84 bytes at level M. */
    const val VERSION = 5
    const val SIZE = 17 + 4 * VERSION

    /** The wave scrambles this share of the columns. */
    const val BAND_FRACTION = 0.40f

    /** One full sweep across the QR takes this long. */
    const val SWEEP_MS = 1700L

    /** The scrambled squares change this often. */
    const val NOISE_MS = 70L

    /** How long the validator watches before deciding. */
    const val WATCH_MS = 2000L

    /** Fewer usable frames than this and the scan is retried. */
    const val MIN_FRAMES = 8

    /**
     * Squares the wave never touches: finder patterns,
     * separators, format info, timing lines and the
     * alignment pattern. Scanners need these to find the QR.
     */
    val fixedMask: BooleanArray by lazy { functionModules(SIZE) }

    fun isFixed(r: Int, c: Int) = fixedMask[r * SIZE + c]

    /** Wavy centre of the band for one row at time [t] (ms). */
    fun bandCentre(row: Int, t: Long): Float {
        val n = SIZE.toFloat()
        val wobble = sin(row * 0.33 + t / 260.0).toFloat() * 1.3f
        val x = t * n / SWEEP_MS + wobble
        return ((x % n) + n) % n
    }

    /** True if column [c] is inside the band centred at [cx]. */
    fun inBand(c: Int, cx: Float): Boolean {
        val n = SIZE.toFloat()
        var d = ((c - cx) % n + n) % n
        if (d > n / 2) d -= n
        return abs(d) < BAND_FRACTION * n / 2
    }

    private fun functionModules(n: Int): BooleanArray {
        val m = BooleanArray(n * n)
        fun mark(r: Int, c: Int) {
            if (r in 0 until n && c in 0 until n) {
                m[r * n + c] = true
            }
        }
        for (r in 0 until n) for (c in 0 until n) {
            val topLeft = r < 9 && c < 9
            val topRight = r < 9 && c >= n - 8
            val bottomLeft = r >= n - 8 && c < 9
            val timing = r == 6 || c == 6
            if (topLeft || topRight || bottomLeft || timing) {
                mark(r, c)
            }
        }
        // Versions 2..6 have one alignment pattern at (n-7, n-7).
        val a = n - 7
        for (r in a - 2..a + 2) for (c in a - 2..a + 2) mark(r, c)
        return m
    }
}

/** The plain QR squares for one pass, before the wave. */
internal class PassMatrix(text: String) {
    val size: Int
    private val dark: BooleanArray

    init {
        val hints = mapOf(
            EncodeHintType.QR_VERSION to WaveLock.VERSION,
        )
        val qr = Encoder.encode(
            text, ErrorCorrectionLevel.M, hints,
        )
        val m = qr.matrix
        size = m.width
        require(size == WaveLock.SIZE) {
            "Unexpected QR size $size"
        }
        dark = BooleanArray(size * size) { i ->
            m.get(i % size, i / size).toInt() == 1
        }
    }

    fun isDark(r: Int, c: Int) = dark[r * size + c]
}

/**
 * Works out which squares are dark in each animation frame.
 * Inside the wave band, data squares are replaced with random
 * values that change every [WaveLock.NOISE_MS].
 */
internal class WaveAnimator(private val qr: PassMatrix) {
    private val n = qr.size
    private var noise = BooleanArray(n * n)
    private var noiseAt = Long.MIN_VALUE
    private val rnd = java.security.SecureRandom()

    /** Call once per frame before reading [isDark]. */
    fun tick(t: Long) {
        if (t - noiseAt >= WaveLock.NOISE_MS) {
            noise = BooleanArray(n * n) { rnd.nextBoolean() }
            noiseAt = t
        }
    }

    fun isDark(
        r: Int,
        c: Int,
        t: Long,
        waveOn: Boolean,
    ): Boolean {
        if (!waveOn || WaveLock.isFixed(r, c)) {
            return qr.isDark(r, c)
        }
        val cx = WaveLock.bandCentre(r, t)
        return if (WaveLock.inBand(c, cx)) noise[r * n + c]
        else qr.isDark(r, c)
    }
}