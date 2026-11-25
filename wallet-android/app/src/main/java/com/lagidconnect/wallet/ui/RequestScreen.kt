package com.lagidconnect.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagidconnect.wallet.data.ConsentRequest
import com.lagidconnect.wallet.ui.theme.Amber
import com.lagidconnect.wallet.ui.theme.Good
import com.lagidconnect.wallet.ui.theme.Ink
import com.lagidconnect.wallet.ui.theme.Muted
import com.lagidconnect.wallet.ui.theme.Navy
import com.lagidconnect.wallet.ui.theme.OnNavyMuted
import com.lagidconnect.wallet.ui.theme.Paper

@Composable
fun RequestScreen(
    vm: WalletViewModel,
    requestId: String,
    onBack: () -> Unit,
) {
    val request = remember(requestId) { vm.requestById(requestId) }

    if (request == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Request", onBack = onBack)
            Column(Modifier.padding(24.dp)) {
                EmptyNote("This request is no longer available.")
            }
        }
        return
    }

    // Every claim starts switched on; required ones stay on
    val chosen = remember(requestId) {
        mutableStateListOf<String>().apply {
            addAll(request.claims.map { it.id })
        }
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<String?>(null) }

    fun submit(approve: Boolean) {
        busy = true
        error = null
        vm.decide(request, approve, chosen.toList()) { err ->
            busy = false
            if (err != null) error = err
            else outcome = if (approve) "approved" else "declined"
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Verification request", onBack = onBack)

        if (outcome != null) {
            DoneView(request, outcome!!, chosen.size, onBack)
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Header(request)

            Column(Modifier.padding(20.dp)) {
                Text(
                    "Choose what to share",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                request.claims.forEach { claim ->
                    val on = claim.id in chosen
                    Panel(Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    claimShort(claim.id),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    claim.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Muted,
                                )
                                if (claim.required) {
                                    Text(
                                        "Required",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Ink,
                                    )
                                }
                            }
                            Switch(
                                checked = on,
                                enabled = !claim.required && !busy,
                                onCheckedChange = { checked ->
                                    if (checked) chosen.add(claim.id)
                                    else chosen.remove(claim.id)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = Navy,
                                    checkedThumbColor = Amber,
                                ),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "They get signed yes or no answers, checked against the " +
                            "Lagos register. Not a copy of your ID.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                ErrorText(error)
            }
        }

        Column(
            Modifier
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(
                text = "Approve and share ${chosen.size}",
                busy = busy,
                enabled = chosen.isNotEmpty(),
                onClick = { submit(approve = true) },
            )
            SecondaryButton(
                text = "Decline",
                enabled = !busy,
                onClick = { submit(approve = false) },
            )
        }
    }
}

@Composable
private fun Header(request: ConsentRequest) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Navy)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Eyebrow(categoryLabel(request.business?.category), color = Amber)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                request.business?.name ?: "A business",
                style = MaterialTheme.typography.headlineMedium,
                color = Paper,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (request.business?.verified == true) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Verified business",
                    tint = Amber,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(22.dp),
                )
            }
        }
        Text(
            "wants to verify you for:",
            style = MaterialTheme.typography.bodyMedium,
            color = OnNavyMuted,
        )
        Text(
            request.purpose,
            style = MaterialTheme.typography.titleLarge,
            color = Paper,
        )
        Text(
            "Answer by ${formatClock(request.expiresAt)}",
            style = MaterialTheme.typography.bodyMedium,
            color = OnNavyMuted,
        )
    }
}

@Composable
private fun DoneView(
    request: ConsentRequest,
    outcome: String,
    shared: Int,
    onBack: () -> Unit,
) {
    val approved = outcome == "approved"
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (approved) Good else Muted,
            modifier = Modifier.size(88.dp),
        )
        Text(
            if (approved) "Shared with ${request.business?.name}"
            else "Declined",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            if (approved) "You shared $shared detail${if (shared == 1) "" else "s"}. " +
                "It’s in your activity log, and you can remove access any time."
            else "${request.business?.name ?: "The business"} gets nothing.",
            style = MaterialTheme.typography.bodyLarge,
            color = Muted,
        )
        PrimaryButton(text = "Done", onClick = onBack)
    }
}
