package sms2mm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Clean light palette: white cards on soft grey, dark text, one blue accent. */
object Palette {
    val Background = Color(0xFFF6F7F9)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceHigh = Color(0xFFEEF1F5)
    val Outline = Color(0xFFE2E6EC)
    val Text = Color(0xFF111827)
    val TextDim = Color(0xFF5B6474)
    val Accent = Color(0xFF2563EB)
    val AccentSoft = Color(0xFFE8EFFD)
    val Green = Color(0xFF16A34A)
    val Amber = Color(0xFFD97706)
    val Red = Color(0xFFDC2626)
}

private val colors = lightColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    primaryContainer = Palette.AccentSoft,
    onPrimaryContainer = Palette.Accent,
    secondary = Palette.Accent,
    onSecondary = Color.White,
    tertiary = Palette.Green,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface,
    surfaceContainerHighest = Palette.SurfaceHigh,
    surfaceContainerLow = Palette.Surface,
    surfaceContainerLowest = Palette.Surface,
    outline = Palette.Outline,
    outlineVariant = Palette.Outline,
    error = Palette.Red,
    secondaryContainer = Palette.AccentSoft,
    onSecondaryContainer = Palette.Accent,
)

private val type = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium),
    )
}

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
)

/** Big amount style used on the summary and transaction cards. */
val AmountStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, color = Palette.Text)

@Composable
fun Sms2mmTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colors, typography = type, shapes = shapes, content = content)

/** White card: soft shadow, 1 dp light-grey border. */
fun androidx.compose.ui.Modifier.card(
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
): androidx.compose.ui.Modifier = this
    .shadow(1.dp, shape, clip = false, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
    .then(androidx.compose.ui.Modifier.clip(shape))
    .background(Palette.Surface)
    .border(1.dp, Palette.Outline, shape)

/** Plain light background. */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize().background(Palette.Background),
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
            val stroke = Stroke(width = size.minDimension * 0.10f, cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(Palette.SurfaceHigh, -90f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
            drawArc(Palette.Accent, -90f, 360f * animated, false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
        }
        center()
    }
}
