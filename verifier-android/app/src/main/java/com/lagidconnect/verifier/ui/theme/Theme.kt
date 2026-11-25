package com.lagidconnect.verifier.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Same palette as the pitch deck
val Navy = Color(0xFF0F1B2D)
val NavySoft = Color(0xFF16263D)
val Amber = Color(0xFFF2A541)
val AmberDark = Color(0xFF9A5A08)
val Paper = Color(0xFFF5F3EE)
val Card = Color(0xFFFBFAF6)
val Line = Color(0xFFE0DACD)
val Ink = Color(0xFF14213D)
val Muted = Color(0xFF4A5568)
val OnNavyMuted = Color(0xFFC5D0DE)
val Good = Color(0xFF1E7A5A)
val Bad = Color(0xFFB3261E)

private val colors = lightColorScheme(
    primary = Navy,
    onPrimary = Paper,
    secondary = Amber,
    onSecondary = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = Muted,
    outline = Line,
    error = Bad,
)

private val type = Typography(
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
    ),
)

@Composable
fun WalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
