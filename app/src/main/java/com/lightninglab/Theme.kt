package com.lightninglab

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Storm cinema" identity: near-black blue surfaces, ONE accent (electric yellow = the bolt) used for primary actions
 * and selected states only, a cool arc-blue for glow/secondary info. Letter-spacing is kept at 0 in all text styles
 * because non-zero tracking breaks Persian/Arabic letter joining.
 */
object Storm {
    val bg = Color(0xFF080B11)
    val surface = Color(0xFF0F141D)
    val surfaceHi = Color(0xFF161D2A)
    val surfaceTop = Color(0xFF2A3447)
    val outline = Color(0xFF3A465C)
    val text = Color(0xFFE9EEF6)
    val muted = Color(0xFF8995AA)
    val bolt = Color(0xFFFFD43B)
    val onBolt = Color(0xFF171200)
    val boltDim = Color(0xFF3A3000)
    val arc = Color(0xFF6FB4FF)
    val selected = Color(0xFF211D0C)
}

private val Sans = FontFamily.SansSerif

val StormTypography = Typography(
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)

val StormShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LightningTheme(content: @Composable () -> Unit) {
    val cs = darkColorScheme(
        primary = Storm.bolt, onPrimary = Storm.onBolt, primaryContainer = Storm.boltDim, onPrimaryContainer = Storm.bolt,
        secondary = Storm.arc, onSecondary = Color(0xFF00203D),
        secondaryContainer = Storm.boltDim, onSecondaryContainer = Storm.bolt,
        tertiary = Storm.arc,
        background = Storm.bg, onBackground = Storm.text,
        surface = Storm.surface, onSurface = Storm.text,
        surfaceVariant = Storm.surfaceTop, onSurfaceVariant = Storm.muted,
        outline = Storm.outline, outlineVariant = Storm.outline,
        error = Color(0xFFFF6B6B)
    )
    MaterialTheme(colorScheme = cs, typography = StormTypography, shapes = StormShapes, content = content)
}
