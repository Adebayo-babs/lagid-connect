package com.identiko.wavelock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Longest text that fits (QR version 5, level M). */
const val WAVE_QR_MAX_LENGTH = 84

/**
 * Shows [text] as a QR with a clear wave moving across it.
 * Inside the wave the data squares are scrambled, so no
 * single frame, photo or screenshot can be read. Only
 * [WaveScanner] can read it, by watching the wave move.
 *
 * Tip: block screenshots on the screen that shows this
 * (WindowManager.LayoutParams.FLAG_SECURE).
 *
 * @param waveOn false shows a plain QR (for demos only).
 */
@Composable
fun WaveQr(
    text: String,
    modifier: Modifier = Modifier,
    waveOn: Boolean = true,
    ink: Color = Color(0xFF0E1A2B),
) {
    require(text.length <= WAVE_QR_MAX_LENGTH) {
        "WaveQr text is ${text.length} chars; " +
                "max $WAVE_QR_MAX_LENGTH"
    }
    val matrix = remember(text) { PassMatrix(text) }
    val anim = remember(matrix) { WaveAnimator(matrix) }
    var t by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { t = it - start }
    }

    Canvas(modifier.aspectRatio(1f).background(Color.White)) {
        val now = t
        val n = matrix.size
        val quiet = 4
        val cell = size.width / (n + quiet * 2)
        val off = quiet * cell
        val sq = Size(cell + 0.6f, cell + 0.6f)

        anim.tick(now)
        for (r in 0 until n) for (c in 0 until n) {
            if (anim.isDark(r, c, now, waveOn)) {
                val at = Offset(off + c * cell, off + r * cell)
                drawRect(ink, at, sq)
            }
        }
        if (!waveOn) return@Canvas

        // Clear, colourless sheen with faint edges.
        val half = WaveLock.BAND_FRACTION * n / 2
        val edge = 1.5.dp.toPx()
        val line = ink.copy(alpha = 0.18f)
        for (r in 0 until n) {
            val cx = WaveLock.bandCentre(r, now)
            val y = off + r * cell
            for (shift in intArrayOf(-n, 0, n)) {
                val x0 = off + (cx + shift - half) * cell
                val x1 = off + (cx + shift + half) * cell
                if (x1 < 0f || x0 > size.width) continue
                val sheen = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.28f),
                    1f to Color.Transparent,
                    startX = x0,
                    endX = x1,
                )
                drawRect(sheen, Offset(x0, y), Size(x1 - x0, cell))
                drawRect(line, Offset(x0, y), Size(edge, cell))
                drawRect(
                    line, Offset(x1 - edge, y), Size(edge, cell),
                )
            }
        }
    }
}