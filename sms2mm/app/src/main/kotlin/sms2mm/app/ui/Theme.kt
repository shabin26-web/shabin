package sms2mm.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Calm dark palette with a cyan → violet accent. */
object Palette {
    val Background = Color(0xFF0A0E16)
    val Surface = Color(0xFF121826)
    val SurfaceHigh = Color(0xFF1A2233)
    val Outline = Color(0xFF263049)
    val Text = Color(0xFFE6EAF2)
    val TextDim = Color(0xFF8B94A8)
    val Cyan = Color(0xFF22D3EE)
    val Violet = Color(0xFFA78BFA)
    val Green = Color(0xFF34D399)
    val Amber = Color(0xFFFBBF24)
    val Red = Color(0xFFF87171)

    val Accent: Brush get() = Brush.linearGradient(listOf(Cyan, Violet))
}

private val colors = darkColorScheme(
    primary = Palette.Cyan,
    onPrimary = Color(0xFF00222A),
    secondary = Palette.Violet,
    onSecondary = Color(0xFF1C1033),
    tertiary = Palette.Green,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHigh,
    surfaceContainerLow = Palette.Surface,
    outline = Palette.Outline,
    outlineVariant = Palette.Outline,
    error = Palette.Red,
    secondaryContainer = Color(0xFF173A45),
    onSecondaryContainer = Palette.Cyan,
)

private val type = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(letterSpacing = 1.2.sp),
    )
}

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

/** Big amount style used on the summary and transaction cards. */
val AmountStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)

@Composable
fun Sms2mmTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colors, typography = type, shapes = shapes, content = content)
