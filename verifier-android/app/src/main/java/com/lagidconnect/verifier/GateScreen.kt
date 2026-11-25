package com.lagidconnect.verifier

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.identiko.wavelock.WaveScanStatus
import com.identiko.wavelock.WaveScanner
import com.identiko.wavelock.rememberWaveScannerState
import com.lagidconnect.verifier.face.FaceCapture
import com.lagidconnect.verifier.ui.theme.Amber
import com.lagidconnect.verifier.ui.theme.Bad
import com.lagidconnect.verifier.ui.theme.Good
import com.lagidconnect.verifier.ui.theme.Ink
import com.lagidconnect.verifier.ui.theme.Navy
import com.lagidconnect.verifier.ui.theme.OnNavyMuted
import com.lagidconnect.verifier.ui.theme.Paper

private val GoodBg = Color(0xFFD9F2E6)
private val BadBg = Color(0xFFFBE1DE)

@Composable
fun GateScreen(vm: GateViewModel = viewModel()) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCamera = it }
    LaunchedEffect(Unit) {
        if (!hasCamera) ask.launch(Manifest.permission.CAMERA)
    }

    val step by vm.step.collectAsStateWithLifecycle()

    when {
        !vm.configured -> Message(
            "Gate key missing",
            "Add lagid.gateKey=lc_test_... to verifier-android/local.properties, then run again.",
        )
        !hasCamera -> Message("Camera needed", "Allow the camera to scan passes.")
        else -> when (val s = step) {
            GateStep.Scanning -> ScanView(vm)
            is GateStep.Busy -> Message("One moment", s.message, busy = true)
            is GateStep.FaceCheck -> FaceCapture(
                key = s.attempt,
                onPassed = { frame, face -> vm.onFace(s.pass, frame, face) },
                onFailed = { reason -> vm.onFaceFailed(s.pass, "Face check failed: $reason") },
                onCancel = { vm.onFaceFailed(s.pass, "Cancelled at the gate") },
            )
            is GateStep.Result -> ResultView(s, onNext = vm::next)
        }
    }
}

@Composable
private fun ScanView(vm: GateViewModel) {
    // A fresh scanner for every person
    val scanner = rememberWaveScannerState()
    val status = scanner.status

    LaunchedEffect(status) {
        if (status is WaveScanStatus.Done) vm.onScanned(status.result)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Navy)
            .statusBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "LAGID GATE",
            style = MaterialTheme.typography.labelSmall,
            color = Amber,
        )
        Text(
            "Scan the resident’s pass",
            style = MaterialTheme.typography.headlineMedium,
            color = Paper,
        )

        Box(
            Modifier
                .fillMaxWidth()
                .background(Paper, RoundedCornerShape(20.dp))
                .padding(10.dp)
        ) {
            WaveScanner(
                state = scanner,
                modifier = Modifier.fillMaxWidth(),
                previewHeight = 340.dp,
            )
        }

        val (title, detail) = when (status) {
            WaveScanStatus.Looking ->
                "Ready" to "Point the camera at the wave QR in the LagID Wallet."
            is WaveScanStatus.Watching ->
                "Watching the wave…" to "Hold steady for 2 seconds."
            WaveScanStatus.TooFewFrames ->
                "Try again" to "Couldn’t see the QR clearly."
            is WaveScanStatus.CameraError -> "Camera" to status.message
            is WaveScanStatus.Done -> "Read" to "Checking the pass…"
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Paper)
        Text(detail, style = MaterialTheme.typography.bodyLarge, color = OnNavyMuted)

        if (status is WaveScanStatus.Watching) {
            LinearProgressIndicator(
                progress = { status.progress },
                modifier = Modifier.fillMaxWidth(),
                color = Amber,
                trackColor = Color(0x33FFFFFF),
            )
        }
        if (status == WaveScanStatus.TooFewFrames) {
            Button(
                onClick = { scanner.scanAgain() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Amber,
                    contentColor = Ink,
                ),
            ) { Text("Scan again") }
        }
    }
}

@Composable
private fun ResultView(r: GateStep.Result, onNext: () -> Unit) {
    val fg = if (r.granted) Good else Bad
    Column(
        Modifier
            .fillMaxSize()
            .background(if (r.granted) GoodBg else BadBg)
            .clickable(onClick = onNext)
            .statusBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            if (r.granted) Icons.Filled.CheckCircle else Icons.Filled.Close,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(120.dp),
        )
        Text(
            if (r.granted) "ACCESS GRANTED" else "ACCESS DENIED",
            color = fg,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        r.name?.let { name ->
            Box(
                Modifier
                    .size(96.dp)
                    .background(Navy, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initials(name),
                    color = Amber,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                name,
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
                textAlign = TextAlign.Center,
            )
        }
        r.lagIdLast4?.let {
            Text("LagID •••• $it", style = MaterialTheme.typography.bodyLarge, color = Ink)
        }
        Text(
            r.reason,
            style = MaterialTheme.typography.titleMedium,
            color = fg,
            textAlign = TextAlign.Center,
        )
        r.score?.let {
            Text(
                "Face similarity %.2f".format(it),
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Tap for the next person", style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}

@Composable
private fun Message(title: String, detail: String, busy: Boolean = false) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Navy)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (busy) CircularProgressIndicator(color = Amber)
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Paper)
        Text(
            detail,
            style = MaterialTheme.typography.bodyLarge,
            color = OnNavyMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private fun initials(name: String) =
    name.split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }
