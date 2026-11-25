package com.lagidconnect.wallet.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lagidconnect.wallet.data.GrantItem
import com.lagidconnect.wallet.ui.theme.Bad
import com.lagidconnect.wallet.ui.theme.Ink
import com.lagidconnect.wallet.ui.theme.Muted

@Composable
fun ActivityScreen(vm: WalletViewModel, onBack: () -> Unit) {
    val state by vm.home.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadActivity() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Activity", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(20.dp)) {
            item { Column {
                Text(
                    "Every time your LagID was checked, and what was shared.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ErrorText(state.error)
                if (state.activity.isEmpty()) EmptyNote("Nothing yet.")
            } }
            items(state.activity, key = { it.id }) { ActivityRow(it) }
        }
    }
}

@Composable
fun GrantsScreen(vm: WalletViewModel, onBack: () -> Unit) {
    val state by vm.home.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<GrantItem?>(null) }
    LaunchedEffect(Unit) { vm.loadGrants() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Connected businesses", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(20.dp)) {
            item { Column {
                Text(
                    "Businesses you’ve approved before. Remove one and it " +
                        "has to ask you again next time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                ErrorText(state.error)
                if (state.grants.isEmpty()) EmptyNote("No connected businesses.")
            } }
            items(state.grants, key = { it.id }) { grant ->
                Panel(Modifier.padding(vertical = 5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                grant.business ?: "A business",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                grant.claims.joinToString(" · ") { claimShort(it) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted,
                            )
                            Text(
                                "Since ${formatTime(grant.since)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted,
                            )
                        }
                        TextButton(onClick = { confirm = grant }) {
                            Text("Remove", color = Bad)
                        }
                    }
                }
            }
        }
    }

    confirm?.let { grant ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Remove ${grant.business ?: "this business"}?") },
            text = {
                Text("They won’t be able to verify you again until you approve a new request.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.revoke(grant)
                    confirm = null
                }) { Text("Remove", color = Bad) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) {
                    Text("Keep", color = Ink)
                }
            },
        )
    }
}
