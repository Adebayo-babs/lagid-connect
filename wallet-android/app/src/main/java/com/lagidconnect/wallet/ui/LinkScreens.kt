package com.lagidconnect.wallet.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lagidconnect.wallet.face.FaceCapture
import com.lagidconnect.wallet.ui.theme.Amber
import com.lagidconnect.wallet.ui.theme.Good
import com.lagidconnect.wallet.ui.theme.Muted
import com.lagidconnect.wallet.ui.theme.Navy
import com.lagidconnect.wallet.ui.theme.OnNavyMuted
import com.lagidconnect.wallet.ui.theme.Paper

// ---------------- 1. LagID and phone ----------------

@Composable
fun WelcomeScreen(vm: WalletViewModel, onCodeSent: () -> Unit) {
    val state by vm.link.collectAsStateWithLifecycle()
    var lagId by remember { mutableStateOf(state.lagId) }
    var phone by remember { mutableStateOf(state.phone) }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Navy)
                .statusBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Eyebrow("LagID Connect", color = Amber)
            Text(
                "Your LagID, on your phone, under your control.",
                style = MaterialTheme.typography.headlineMedium,
                color = Paper,
            )
            Text(
                "Approve who can check who you are. See every check.",
                style = MaterialTheme.typography.bodyLarge,
                color = OnNavyMuted,
            )
            Spacer(Modifier.height(12.dp))
        }

        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Link your LagID", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = lagId,
                onValueChange = { lagId = it; vm.clearLinkError() },
                label = { Text("LagID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it; vm.clearLinkError() },
                label = { Text("Phone number on your LagID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorText(state.error)
            PrimaryButton(
                text = "Send code",
                busy = state.busy,
                onClick = { vm.startLink(lagId, phone, onCodeSent) },
            )
            Text(
                "We’ll check these against the Lagos resident register.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
        }
    }
}

// ---------------- 2. One-time code ----------------

@Composable
fun CodeScreen(vm: WalletViewModel, onBack: () -> Unit, onNext: () -> Unit) {
    val state by vm.link.collectAsStateWithLifecycle()
    val start = state.start

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Enter code", onBack = onBack)
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "We sent a 6-digit code to ${start?.phoneMasked ?: "your phone"}.",
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedTextField(
                value = state.code,
                onValueChange = vm::setCode,
                label = { Text("Code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            // The sandbox has no SMS, so the demo shows it here
            start?.devCode?.let { code ->
                Panel {
                    Eyebrow("Demo mode")
                    Text(
                        "No SMS in the sandbox. Your code is $code.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            ErrorText(state.error)
            PrimaryButton(
                text = "Continue",
                enabled = state.code.length == 6,
                onClick = { vm.resetFace(); onNext() },
            )
        }
    }
}

// ---------------- 3. Face enrolment ----------------

@Composable
fun FaceScreen(vm: WalletViewModel, onBack: () -> Unit, onLinked: () -> Unit) {
    val state by vm.link.collectAsStateWithLifecycle()

    // Live camera with your FaceMatch liveness check:
    // blink, turn, look straight. The final frame is enrolled.
    if (state.face == FaceStatus.Capturing) {
        FaceCapture(
            key = state.attempt,
            onPassed = { frame, face -> vm.enrolFace(frame, face, onLinked) },
            onFailed = { message -> vm.faceCaptureFailed(message) },
            onCancel = { vm.resetFace() },
        )
        return
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale",
    )

    Column(Modifier.fillMaxSize()) {
        TopBar("Face check", onBack = if (state.busy) null else onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .size(200.dp)
                    .scale(if (state.face == FaceStatus.Checking) scale else 1f)
                    .border(
                        4.dp,
                        if (state.face == FaceStatus.Passed) Good else Amber,
                        CircleShape,
                    )
                    .background(Navy, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.face == FaceStatus.Passed) Icons.Filled.CheckCircle
                    else Icons.Filled.Face,
                    contentDescription = null,
                    tint = if (state.face == FaceStatus.Passed) Good else Paper,
                    modifier = Modifier.size(88.dp),
                )
            }

            Text(
                when (state.face) {
                    FaceStatus.Idle, FaceStatus.Capturing ->
                        "Next: a quick live check. You’ll blink and turn your head."
                    FaceStatus.Checking -> "Saving your face template and linking…"
                    FaceStatus.Passed -> "Done. Linking your wallet…"
                    FaceStatus.Failed -> "That didn’t work."
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            ErrorText(state.error)

            when (state.face) {
                FaceStatus.Idle -> PrimaryButton(
                    text = "Start face check",
                    onClick = vm::startFaceCapture,
                )
                FaceStatus.Failed -> {
                    PrimaryButton(
                        text = "Try again",
                        onClick = vm::startFaceCapture,
                    )
                    SecondaryButton(text = "Back to code", onClick = onBack)
                }
                else -> Unit
            }

            Text(
                "Your face template stays on this phone. LagID Connect " +
                    "keeps only a fingerprint of it, and the gate uses it " +
                    "to make sure the pass is shown by you.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
