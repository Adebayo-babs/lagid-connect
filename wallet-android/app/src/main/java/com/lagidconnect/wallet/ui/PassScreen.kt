package com.lagidconnect.wallet.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.identiko.wavelock.WaveQr
import com.lagidconnect.wallet.ui.theme.Amber
import com.lagidconnect.wallet.ui.theme.Line
import com.lagidconnect.wallet.ui.theme.Muted
import com.lagidconnect.wallet.ui.theme.Navy
import com.lagidconnect.wallet.ui.theme.OnNavyMuted
import com.lagidconnect.wallet.ui.theme.Paper
import kotlinx.coroutines.delay

/**
 * Shows a single-use gate pass as a wave-locked QR.
 * The code lasts 30 seconds and is replaced just before it
 * expires. Screenshots are blocked while this is open.
 */
@Composable
fun PassScreen(vm: WalletViewModel, onBack: () -> Unit, onRelink: () -> Unit) {
    val state by vm.pass.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Block screenshots and screen recording on this screen
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // First pass, then keep the clock ticking
    LaunchedEffect(Unit) {
        vm.refreshPass()
        while (true) {
            delay(250)
            now = System.currentTimeMillis()
        }
    }

    // Swap in a new code 3 seconds before this one expires
    val left = ((state.expiresAtMs - now) / 1000).coerceAtLeast(0)
    LaunchedEffect(state.code, left <= 3) {
        if (state.code != null && left <= 3 && !state.loading) vm.refreshPass()
    }

    DisposableEffect(Unit) { onDispose { vm.clearPass() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Navy)
    ) {
        TopBar("My LagID pass", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                home.name.ifBlank { "Resident" },
                style = MaterialTheme.typography.headlineMedium,
                color = Paper,
            )
            Text(
                "LagID •••• ${home.lagIdLast4}",
                style = MaterialTheme.typography.bodyLarge,
                color = OnNavyMuted,
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Paper, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val code = state.code
                when {
                    state.needsEnrolment -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(
                            "Your face isn’t enrolled on this phone yet. " +
                                "Link again to add it.",
                            textAlign = TextAlign.Center,
                        )
                        PrimaryButton(text = "Link again", onClick = onRelink)
                    }
                    code != null -> WaveQr(
                        text = code,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.error != null -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        ErrorText(state.error)
                        SecondaryButton(text = "Try again", onClick = vm::refreshPass)
                    }
                    else -> CircularProgressIndicator(
                        color = Navy,
                        modifier = Modifier
                            .padding(48.dp)
                            .size(40.dp),
                    )
                }
            }

            if (state.code != null) {
                LinearProgressIndicator(
                    progress = { (left / 30f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Amber,
                    trackColor = Line,
                )
                Text(
                    "New code in ${left}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnNavyMuted,
                )
            }

            Text(
                "Show this at the gate. It works once, only while the " +
                    "wave moves, and the guard’s scanner checks your face.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnNavyMuted,
                textAlign = TextAlign.Center,
            )
            Text(
                "Screenshots are blocked on this screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
        }
    }
}
