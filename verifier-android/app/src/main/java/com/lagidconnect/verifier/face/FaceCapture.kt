package com.lagidconnect.verifier.face

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

import com.lagidconnect.verifier.ui.theme.Amber
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetector
import java.util.concurrent.Executors

@Composable
fun FaceCapture(
    key: Int,
    onPassed: (Bitmap, Face) -> Unit,
    onFailed: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) onFailed("Camera permission is needed to verify a face.")
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Waiting for camera permission…")
        }
        return
    }

    // A new key (each attempt) gives a fresh camera session and a fresh, re-randomised challenge.
    androidx.compose.runtime.key(key) {
        LivenessCamera(onPassed, onFailed, onCancel)
    }
}

@Composable
private fun LivenessCamera(
    onPassed: (Bitmap, Face) -> Unit,
    onFailed: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val passed by rememberUpdatedState(onPassed)
    val failed by rememberUpdatedState(onFailed)

    val checker = remember { LivenessChecker() }
    var prompt by remember { mutableStateOf("Place your face inside the oval") }
    var progress by remember { mutableStateOf(0f) }
    var debugText by remember { mutableStateOf("") }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val detector = FaceProcessor.createLiveDetector()
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        val analyzer = LivenessAnalyzer(
            detector = detector,
            checker = checker,
            onState = { state, debug ->
                debugText = debug
                when (state) {
                    is LivenessChecker.State.Prompt -> {
                        prompt = state.message
                        progress = state.completedSteps.toFloat() / state.totalSteps
                    }
                    is LivenessChecker.State.Failed -> failed(state.reason)
                    is LivenessChecker.State.Passed -> {
                        prompt = "Hold still…"
                        progress = 1f
                    }
                }
            },
            onCaptured = { frame, face -> passed(frame, face) },
        )

        providerFuture.addListener({
            val p = providerFuture.get().also { provider = it }
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            try {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            } catch (e: Exception) {
                failed("Couldn't open the front camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            analyzer.stop()
            provider?.unbindAll()
            detector.close()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        OvalGuide(progress)

        Column(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                prompt,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color(0x99000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(0.6f),
                color = Amber,
                trackColor = Color(0x55FFFFFF),
            )
        }

        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (FaceConfig.SHOW_DEBUG && debugText.isNotEmpty()) {
                Text(
                    debugText,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(8.dp),
                )
            }
            OutlinedButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
        }
    }
}

/** Darkens everything outside an oval and draws a ring that fills as challenges complete. */
@Composable
private fun OvalGuide(progress: Float) {
    Canvas(
        Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val w = size.width * 0.72f
        val h = w * 1.3f
        val topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f)
        drawRect(Color(0xAA000000))
        drawOval(Color.Transparent, topLeft, GeoSize(w, h), blendMode = BlendMode.Clear)
        drawOval(Color.White.copy(alpha = 0.5f), topLeft, GeoSize(w, h), style = Stroke(3.dp.toPx()))
        drawArc(
            color = Amber,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = GeoSize(w, h),
            style = Stroke(6.dp.toPx()),
        )
    }
}

/**
 * Feeds camera frames to ML Kit and the liveness checker. When the check passes, converts that
 * exact frame to an upright Bitmap and hands it on together with the detected face.
 */
private class LivenessAnalyzer(
    private val detector: FaceDetector,
    private val checker: LivenessChecker,
    private val onState: (LivenessChecker.State, String) -> Unit,
    private val onCaptured: (Bitmap, Face) -> Unit,
) : ImageAnalysis.Analyzer {

    @Volatile
    private var done = false

    fun stop() {
        done = true
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (done || media == null) {
            proxy.close()
            return
        }
        val rotation = proxy.imageInfo.rotationDegrees
        val uprightWidth = if (rotation % 180 == 0) proxy.width else proxy.height

        // Listeners run on the main thread; the frame stays open until we've finished with it.
        detector.process(InputImage.fromMediaImage(media, rotation)).addOnCompleteListener { task ->
            try {
                if (done || !task.isSuccessful) return@addOnCompleteListener
                val faces = task.result
                val state = checker.onFrame(faces, uprightWidth, SystemClock.elapsedRealtime())
                onState(state, debugLine(faces, uprightWidth))
                when (state) {
                    is LivenessChecker.State.Passed -> {
                        done = true
                        val frame = ImageDecoding.rotate(proxy.toBitmap(), rotation.toFloat())
                        onCaptured(frame, state.face)
                    }
                    is LivenessChecker.State.Failed -> done = true
                    else -> Unit
                }
            } finally {
                proxy.close()
            }
        }
    }

    private fun debugLine(faces: List<Face>, frameWidth: Int): String {
        val f = faces.firstOrNull() ?: return "faces=0"
        return "faces=%d yaw=%.0f pitch=%.0f roll=%.0f\neyes L=%s R=%s width=%.2f id=%s".format(
            faces.size,
            f.headEulerAngleY, f.headEulerAngleX, f.headEulerAngleZ,
            f.leftEyeOpenProbability?.let { "%.2f".format(it) } ?: "n/a",
            f.rightEyeOpenProbability?.let { "%.2f".format(it) } ?: "n/a",
            f.boundingBox.width().toFloat() / frameWidth,
            f.trackingId?.toString() ?: "-",
        )
    }
}
