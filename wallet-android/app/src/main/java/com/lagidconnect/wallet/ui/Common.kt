package com.lagidconnect.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagidconnect.wallet.ui.theme.Amber
import com.lagidconnect.wallet.ui.theme.AmberDark
import com.lagidconnect.wallet.ui.theme.Bad
import com.lagidconnect.wallet.ui.theme.Card
import com.lagidconnect.wallet.ui.theme.Ink
import com.lagidconnect.wallet.ui.theme.Line
import com.lagidconnect.wallet.ui.theme.Muted
import com.lagidconnect.wallet.ui.theme.Navy
import com.lagidconnect.wallet.ui.theme.Paper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Navy)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Paper,
                )
            }
        } else {
            Box(Modifier.size(16.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Paper,
        )
    }
}

@Composable
fun Eyebrow(text: String, color: androidx.compose.ui.graphics.Color = AmberDark) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Amber,
            contentColor = Ink,
            disabledContainerColor = Line,
            disabledContentColor = Muted,
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Ink,
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Ink)
    }
}

@Composable
fun ErrorText(message: String?) {
    if (message.isNullOrBlank()) return
    Text(
        message,
        color = Bad,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(18.dp))
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
fun Tag(text: String, background: androidx.compose.ui.graphics.Color = Paper) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Ink,
        modifier = Modifier
            .background(background, RoundedCornerShape(8.dp))
            .border(1.dp, Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

// ---------- text helpers ----------

val CLAIM_SHORT = mapOf(
    "lagos_resident" to "Lagos resident",
    "name_match" to "Name matches",
    "full_name" to "Full name",
    "age_over_18" to "Over 18",
    "lga" to "Local government area",
    "photo" to "Photo",
    "phone_verified" to "Phone verified",
)

fun claimShort(id: String): String = CLAIM_SHORT[id] ?: id

private val timeFmt = DateTimeFormatter.ofPattern("d MMM, HH:mm")
private val clockFmt = DateTimeFormatter.ofPattern("HH:mm")

fun formatTime(iso: String?): String = runCatching {
    timeFmt.format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
}.getOrDefault("")

fun formatClock(iso: String?): String = runCatching {
    clockFmt.format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
}.getOrDefault("")

fun categoryLabel(category: String?): String = when (category) {
    "estate" -> "Estate"
    "landlord" -> "Landlord"
    "employer" -> "Employer"
    "event" -> "Event"
    else -> "Business"
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Ink,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun EmptyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Muted,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
