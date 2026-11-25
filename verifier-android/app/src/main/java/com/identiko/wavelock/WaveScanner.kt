package com.identiko.wavelock

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

/** What the scanner is doing right now. */
sealed interface WaveScanStatus {
    /** Waiting for a QR to come into view. */
    data object Looking : WaveScanStatus

    /** QR in view; watching it. [progress] goes 0 to 1. */
    data class Watching(val progress: Float) : WaveScanStatus

    /** Couldn't see the QR clearly. Call scanAgain(). */
    data object TooFewFrames : WaveScanStatus

    /** Finished. Check result.isLive, then scanAgain(). */
    data class Done(val result: WaveScanResult) : WaveScanStatus

    /** The chosen camera couldn't be opened. */
    data class CameraError(val message: String) : WaveScanStatus
}

/** Holds the scanner's status. Use rememberWaveScannerState(). */
@Stable
class WaveScannerState internal constructor() {
    var status: WaveScanStatus by mutableStateOf(
        WaveScanStatus.Looking,
    )
        internal set

    internal var analyzer: WaveAnalyzer? = null

    /** Clear the last result and look for the next QR. */
    fun scanAgain() {
        analyzer?.rearm()
        status = WaveScanStatus.Looking
    }
}

@Composable
fun rememberWaveScannerState(): WaveScannerState =
    remember { WaveScannerState() }

/**
 * Camera view that reads a [WaveQr]. Watch [state] for the
 * result.
 *
 * The app must already have the CAMERA permission when this
 * is shown. The library declares it in its manifest; asking
 * the user for it is up to the app.
 *
 * @param previewHeight height of the camera view.
 * @param showCameraSwitch shows "Switch camera" when the
 *   device has more than one camera. The choice is saved.
 */
@Composable
fun WaveScanner(
    state: WaveScannerState,
    modifier: Modifier = Modifier,
    previewHeight: Dp = 300.dp,
    showCameraSwitch: Boolean = true,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val main = remember { ContextCompat.getMainExecutor(context) }

    val analyzer = remember {
        WaveAnalyzer(
            onWatching = { p ->
                main.execute {
                    state.status = WaveScanStatus.Watching(p)
                }
            },
            onResult = { r ->
                main.execute {
                    state.status = WaveScanStatus.Done(r)
                }
            },
            onTooFewFrames = {
                main.execute {
                    state.status = WaveScanStatus.TooFewFrames
                }
            },
        ).also { state.analyzer = it }
    }
    val worker = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { worker.shutdown() } }

    // Which camera to use. Saved across restarts.
    val prefs = remember {
        context.getSharedPreferences(
            "wavelock_scanner", Context.MODE_PRIVATE,
        )
    }
    var camIndex by remember {
        mutableIntStateOf(prefs.getInt("camera", 0))
    }
    var provider by remember {
        mutableStateOf<ProcessCameraProvider?>(null)
    }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ provider = future.get() }, main)
    }
    val cameras = provider?.availableCameraInfos.orEmpty()

    // Bind the chosen camera; runs again when it changes.
    LaunchedEffect(provider, camIndex) {
        val p = provider ?: return@LaunchedEffect
        val list = p.availableCameraInfos
        if (list.isEmpty()) {
            state.status = WaveScanStatus.CameraError(
                "No camera found.",
            )
            return@LaunchedEffect
        }
        val info = list[camIndex % list.size]

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val selector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1280, 720),
                    ResolutionStrategy
                        .FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(selector)
            .setBackpressureStrategy(
                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST,
            )
            .build()
            .also { it.setAnalyzer(worker, analyzer) }

        p.unbindAll()
        try {
            p.bindToLifecycle(
                owner, info.cameraSelector, preview, analysis,
            )
            state.scanAgain()
        } catch (e: Exception) {
            state.status = WaveScanStatus.CameraError(
                "This camera can't be used. Switch to another one.",
            )
        }
    }

    Column(modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight),
        )
        if (showCameraSwitch && cameras.size > 1) {
            val i = camIndex % cameras.size
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Camera ${i + 1} of ${cameras.size} · " +
                            facingName(cameras[i].lensFacing),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = {
                    camIndex = (i + 1) % cameras.size
                    prefs.edit().putInt("camera", camIndex).apply()
                }) { Text("Switch camera") }
            }
        }
    }
}

private fun facingName(facing: Int) = when (facing) {
    CameraSelector.LENS_FACING_BACK -> "Back"
    CameraSelector.LENS_FACING_FRONT -> "Front"
    else -> "Other"
}