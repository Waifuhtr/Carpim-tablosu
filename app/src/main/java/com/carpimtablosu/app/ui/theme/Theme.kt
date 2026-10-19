package com.carpimtablosu.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Maskotun lacivert zemini ve sarı vurgularından türetilen koyu tema. */
object Palette {
    val Bg = Color(0xFF121A2E)
    val Surface = Color(0xFF1B2741)
    val SurfaceHigh = Color(0xFF243352)
    val Outline = Color(0xFF34446B)
    val Text = Color(0xFFF4F1EA)
    val TextDim = Color(0xFFA9B4CC)

    val Amber = Color(0xFFF5B731)
    val AmberDark = Color(0xFFC48A12)
    val Green = Color(0xFF3DD68C)
    val GreenDark = Color(0xFF23A866)
    val Red = Color(0xFFFF5C6C)
    val RedDark = Color(0xFFC93A4A)
    val Blue = Color(0xFF5AB8FF)
    val BlueDark = Color(0xFF2E86CC)
    val Purple = Color(0xFFB18CFF)
    val Locked = Color(0xFF3A4766)
    val LockedDark = Color(0xFF283453)
    val Flame = Color(0xFFFF8A3D)

    /** Ünite başlıkları için dönen renkler. */
    val Units = listOf(
        Color(0xFF3DD68C) to Color(0xFF23A866),
        Color(0xFF5AB8FF) to Color(0xFF2E86CC),
        Color(0xFFB18CFF) to Color(0xFF7F5BD6),
        Color(0xFFFF8A3D) to Color(0xFFCC6420),
        Color(0xFFFF5C9A) to Color(0xFFCC3A72),
        Color(0xFF2EC4C4) to Color(0xFF1C9696),
    )
}

private val scheme = darkColorScheme(
    primary = Palette.Amber,
    onPrimary = Color(0xFF231900),
    secondary = Palette.Green,
    onSecondary = Color(0xFF00210F),
    tertiary = Palette.Blue,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHigh,
    outline = Palette.Outline,
    error = Palette.Red,
)

private val typography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp),
)

@Composable
fun CarpimTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
