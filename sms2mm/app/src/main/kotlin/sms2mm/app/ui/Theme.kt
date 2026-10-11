package sms2mm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val Background = Color(0xFF060910)
    val Surface = Color(0xFF0F1522)
    val SurfaceHigh = Color(0xFF172036)
    val Outline = Color(0xFF243150)
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

/** Translucent "glass" card: dark surface, 1 dp cyan → violet hairline. */
fun androidx.compose.ui.Modifier.glass(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(22.dp),
    alpha: Float = 0.78f,
): androidx.compose.ui.Modifier = this
    .then(androidx.compose.ui.Modifier.clip(shape))
    .background(Palette.Surface.copy(alpha = alpha))
    .border(1.dp, Brush.linearGradient(listOf(Palette.Cyan.copy(alpha = 0.55f), Palette.Violet.copy(alpha = 0.35f), Palette.Outline)), shape)

/** Near-black background with soft cyan and violet glows. */
@Composable
fun GlowBackground(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(Palette.Cyan.copy(alpha = 0.18f), Color.Transparent), center = Offset(size.width * 0.15f, size.height * 0.02f), radius = size.width * 0.9f),
                    radius = size.width * 0.9f, center = Offset(size.width * 0.15f, size.height * 0.02f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(Palette.Violet.copy(alpha = 0.14f), Color.Transparent), center = Offset(size.width * 0.95f, size.height * 0.18f), radius = size.width * 0.8f),
                    radius = size.width * 0.8f, center = Offset(size.width * 0.95f, size.height * 0.18f),
                )
            },
    ) { content() }
}

/** Animated ring: share of this month's captured amount already recorded. */
@Composable
fun ProgressRing(fraction: Float, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier, center: @Composable () -> Unit = {}) {
    val animated by androidx.compose.animation.core.animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = androidx.compose.animation.core.tween(900),
        label = "ring",
    )
    androidx.compose.foundation.layout.Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.fillMaxSize()) {
            val stroke = Stroke(width = size.minDimension * 0.11f, cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(Palette.Outline, -90f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
            drawArc(
                Brush.sweepGradient(listOf(Palette.Cyan, Palette.Violet, Palette.Cyan)),
                -90f, 360f * animated, false, topLeft = Offset(inset, inset), size = arcSize, style = stroke,
            )
        }
        center()
    }
}
