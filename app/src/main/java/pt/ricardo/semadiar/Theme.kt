package pt.ricardo.semadiar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Papel creme, tinta azul-índigo e um âmbar quente para o que é "agora". */
object Palette {
    val Indigo = Color(0xFF3D4FA8)
    val IndigoDeep = Color(0xFF2A3478)
    val IndigoNight = Color(0xFF1B2152)
    val Amber = Color(0xFFF0A23B)
    val AmberSoft = Color(0xFFFCE6C4)
    val Cream = Color(0xFFFAF7F2)
    val Sand = Color(0xFFEFE9DF)
    val Ink = Color(0xFF1F2233)

    val Ok = Color(0xFF3E9B6E)
    val Warn = Color(0xFFE0A030)
    val Late = Color(0xFFD0504A)
    val Lilac = Color(0xFF8C7BD1)

    val hero = Brush.linearGradient(listOf(Color(0xFF4A5CC0), IndigoDeep, IndigoNight))
    val timer = Brush.verticalGradient(listOf(Color(0xFF2E3A8C), IndigoNight, Color(0xFF12153A)))

    val confetti = listOf(Amber, Color(0xFF7FA8FF), Color(0xFFF07A6A), Ok, Lilac, Color(0xFFFFD166))
}

private val LightScheme = lightColorScheme(
    primary = Palette.Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2FA),
    onPrimaryContainer = Color(0xFF15204F),
    secondary = Palette.Amber,
    onSecondary = Color(0xFF3B2400),
    secondaryContainer = Palette.AmberSoft,
    onSecondaryContainer = Color(0xFF4A2E00),
    tertiary = Palette.Ok,
    background = Palette.Cream,
    onBackground = Palette.Ink,
    surface = Palette.Cream,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Sand,
    onSurfaceVariant = Color(0xFF5E6276),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F3ED),
    surfaceContainer = Color(0xFFF3EEE6),
    surfaceContainerHigh = Color(0xFFEDE7DE),
    surfaceContainerHighest = Color(0xFFE6DFD5),
    outline = Color(0xFFCBC6D6),
    outlineVariant = Color(0xFFE3DDD6),
    error = Palette.Late
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFAAB6FF),
    onPrimary = Color(0xFF14205A),
    primaryContainer = Color(0xFF2E3B86),
    onPrimaryContainer = Color(0xFFDDE2FA),
    secondary = Color(0xFFFFC27A),
    onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF5C3D10),
    onSecondaryContainer = Palette.AmberSoft,
    tertiary = Color(0xFF7FD1A6),
    background = Color(0xFF12131C),
    onBackground = Color(0xFFE7E8F0),
    surface = Color(0xFF12131C),
    onSurface = Color(0xFFE7E8F0),
    surfaceVariant = Color(0xFF262838),
    onSurfaceVariant = Color(0xFFB8BACB),
    surfaceContainerLowest = Color(0xFF181A26),
    surfaceContainerLow = Color(0xFF1B1D2A),
    surfaceContainer = Color(0xFF20222F),
    surfaceContainerHigh = Color(0xFF262838),
    surfaceContainerHighest = Color(0xFF2D3042),
    outline = Color(0xFF555A70),
    outlineVariant = Color(0xFF33364A),
    error = Color(0xFFF08A84)
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 36.sp),
        headlineMedium = t.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}

private class Piece(val x: Float, val vx: Float, val vy: Float, val color: Color, val spin: Float, val w: Float, val h: Float)

/** Uma chuva de confetis que cai uma vez quando [key] muda. */
@Composable
fun Confetti(key: Int, modifier: Modifier = Modifier) {
    if (key == 0) return
    val progress = remember(key) { Animatable(0f) }
    val pieces = remember(key) {
        List(90) {
            val a = Random.nextFloat() * Math.PI.toFloat()
            Piece(
                x = 0.5f + (Random.nextFloat() - 0.5f) * 0.3f,
                vx = cos(a) * (0.25f + Random.nextFloat() * 0.45f),
                vy = -(0.6f + Random.nextFloat() * 0.7f) * sin(a).coerceAtLeast(0.4f),
                color = Palette.confetti.random(),
                spin = Random.nextFloat() * 720f - 360f,
                w = 8f + Random.nextFloat() * 10f,
                h = 5f + Random.nextFloat() * 6f
            )
        }
    }
    LaunchedEffect(key) { progress.animateTo(1f, tween(2200, easing = LinearEasing)) }
    val p = progress.value
    if (p >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        pieces.forEach { pc ->
            val t = p * 2.2f
            val x = (pc.x + pc.vx * t) * size.width
            val y = (0.45f + pc.vy * t + 0.9f * t * t) * size.height
            if (y > size.height) return@forEach
            rotate(pc.spin * p, Offset(x, y)) {
                drawRect(
                    pc.color.copy(alpha = (1f - p * 0.6f)),
                    topLeft = Offset(x - pc.w / 2, y - pc.h / 2),
                    size = Size(pc.w, pc.h)
                )
            }
        }
    }
}
