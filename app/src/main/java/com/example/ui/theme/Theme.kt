package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

object StudioColors {
    val Background = Color(0xFF0B0F17)
    val Surface = Color(0xFF111722)
    val Border = Color(0xFF1E293B)
    val BorderSubtle = Color(0xFF334155)
    val Amber = Color(0xFFF59E0B)
    val AmberBright = Color(0xFFFBBF24)
    val AmberSoft = Color(0xFFFCD34D)
    val AmberCream = Color(0xFFFEF3C7)
    val Emerald = Color(0xFF059669)
    val EmeraldBright = Color(0xFF34D399)
    val TextPrimary = Color(0xFFF1F5F9)
    val TextBody = Color(0xFFCBD5E1)
    val TextSecondary = Color(0xFF94A3B8)
    val BarInactive = Color(0xFF334155)
    val SlateActive = Color(0xFF334155)
    val SlateButton = Color(0xFF0F172A)
    val ErrorRed = Color(0xFFEF4444)
}

val StudioSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.noto_sans_sinhala, FontWeight.Normal)
)

val StudioMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal)
)

private val StudioColorScheme = darkColorScheme(
    primary = StudioColors.Amber,
    onPrimary = Color(0xFF020617),
    secondary = StudioColors.Emerald,
    onSecondary = Color.White,
    background = StudioColors.Background,
    onBackground = StudioColors.TextPrimary,
    surface = StudioColors.Surface,
    onSurface = StudioColors.TextPrimary,
    surfaceVariant = StudioColors.SlateButton,
    onSurfaceVariant = StudioColors.TextSecondary,
    outline = StudioColors.Border
)

val StudioTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = StudioSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = StudioSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = StudioSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = StudioSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = StudioMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum"
    )
)

@Composable
fun SwaraStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = StudioTypography,
        content = content
    )
}
