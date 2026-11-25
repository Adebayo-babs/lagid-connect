package com.lagidconnect.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lagidconnect.wallet.data.ActivityItem
import com.lagidconnect.wallet.data.ConsentRequest
import com.lagidconnect.wallet.ui.theme.Amber
import com.lagidconnect.wallet.ui.theme.Bad
import com.lagidconnect.wallet.ui.theme.Good
import com.lagidconnect.wallet.ui.theme.Ink
import com.lagidconnect.wallet.ui.theme.Line
import com.lagidconnect.wallet.ui.theme.Muted
import com.lagidconnect.wallet.ui.theme.Navy
import com.lagidconnect.wallet.ui.theme.NavySoft
import com.lagidconnect.wallet.ui.theme.OnNavyMuted
import com.lagidconnect.wallet.ui.theme.Paper
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    vm: WalletViewModel,
    onOpenRequest: (String) -> Unit,
    onOpenActivity: () -> Unit,
    onOpenGrants: () -> Unit,
    onOpenPass: () -> Unit,
) {
    val state by vm.home.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.refreshHome()
        // Check for new requests every 4 seconds
        while (true) {
            delay(4000)
            vm.pollPending()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            IdCard(
                name = state.name,
                last4 = state.lagIdLast4,
                onRefresh = vm::refreshHome,
            )
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
                PrimaryButton(text = "Show my pass", onClick = onOpenPass)
                ErrorText(state.error)
                SectionTitle("Waiting for you")
                if (state.pending.isEmpty()) {
                    EmptyNote("No requests right now. When a business asks to verify you, it shows up here.")
                }
            }
        }

        items(state.pending, key = { it.id }) { request ->
            PendingCard(
                request = request,
                onClick = { onOpenRequest(request.id) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
            )
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Recent activity", Modifier.weight(1f))
                    Text(
                        "See all",
                        style = MaterialTheme.typography.labelLarge,
                        color = Ink,
                        modifier = Modifier
                            .clickable(onClick = onOpenActivity)
                            .padding(top = 20.dp, bottom = 8.dp, start = 8.dp),
                    )
                }
                if (state.activity.isEmpty()) EmptyNote("Nothing yet.")
                state.activity.take(5).forEach { ActivityRow(it) }

                Spacer(Modifier.height(16.dp))
                Panel(Modifier.clickable(onClick = onOpenGrants)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Ink)
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text("Connected businesses", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "See who can verify you, and remove access",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdCard(name: String, last4: String, onRefresh: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Navy)
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("LagID Wallet", color = Amber)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Paper)
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(NavySoft, RoundedCornerShape(20.dp))
                .border(1.dp, Amber.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "LAGOS STATE RESIDENT",
                style = MaterialTheme.typography.labelSmall,
                color = OnNavyMuted,
            )
            Text(
                name.ifBlank { "Resident" },
                style = MaterialTheme.typography.headlineMedium,
                color = Paper,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "LagID •••• $last4",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnNavyMuted,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Amber,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    " Verified",
                    style = MaterialTheme.typography.labelLarge,
                    color = Amber,
                )
            }
        }
    }
}

@Composable
private fun PendingCard(
    request: ConsentRequest,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(Amber, CircleShape)
            )
            Text(
                request.business?.name ?: "A business",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
            )
            Text(
                "until ${formatClock(request.expiresAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
        }
        Text(request.purpose, style = MaterialTheme.typography.bodyLarge)
        Text(
            "Wants ${request.claims.size} detail" +
                if (request.claims.size == 1) "" else "s",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )
    }
}

@Composable
fun ActivityRow(item: ActivityItem) {
    val (text, color) = activityText(item)
    Column {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .background(color, CircleShape)
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = Ink)
            item.claims?.takeIf { it.isNotEmpty() }?.let { claims ->
                Text(
                    claims.joinToString(" · ") { claimShort(it) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            Text(
                formatTime(item.at),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Line)
    )
    }
}

fun activityText(item: ActivityItem): Pair<String, androidx.compose.ui.graphics.Color> {
    val who = item.business ?: "A business"
    return when (item.type) {
        "wallet.linked" -> "Wallet linked to this phone" to Good
        "request.created" ->
            "$who asked: ${item.purpose ?: "verification"}" to Amber
        "request.approved" -> {
            val n = item.claims?.size ?: 0
            "You shared $n detail${if (n == 1) "" else "s"} with $who" to Good
        }
        "request.declined" -> "You declined $who" to Bad
        "request.expired" -> "Request from $who expired" to Muted
        "grant.revoked" -> "You removed $who’s access" to Bad
        "gate.result" -> "Gate check at $who" to Navy
        else -> item.type to Muted
    }
}
