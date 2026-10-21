package pt.ricardo.semadiar

import android.app.Activity
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

private val encouragements = listOf(
    "Começar era o mais difícil. Já está.",
    "Não tem de ficar perfeito, só tem de avançar.",
    "Só estes minutos. Mais nada.",
    "Um passo de cada vez.",
    "Estás a fazer. Isso é o que conta."
)

@Composable
fun TimerScreen(
    t: Task,
    end: Long,
    onStepDone: () -> Unit,
    onFinishTask: () -> Unit,
    onMore: () -> Unit,
    onStop: () -> Unit
) {
    val c = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val total = 10 * 60_000L
    val left = (end - now).coerceAtLeast(0L)
    val over = left == 0L
    val phrase = remember(end) { encouragements.random() }
    var rang by remember(end) { mutableStateOf(false) }

    LaunchedEffect(end) {
        while (System.currentTimeMillis() < end) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
        if (!rang) { rang = true; ring(c) }
    }
    // Ecrã sempre ligado enquanto conta
    DisposableEffect(Unit) {
        val w = (c as? Activity)?.window
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val pulse by rememberInfiniteTransition(label = "p").animateFloat(
        0.35f, 0.8f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "a"
    )

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Palette.timer)) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (over) "ACABARAM OS 10 MINUTOS" else "SÓ 10 MINUTOS",
                    color = Palette.Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    t.title, color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp, textAlign = TextAlign.Center, lineHeight = 30.sp
                )
                Spacer(Modifier.weight(1f))

                // Anel do tempo
                Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val sw = 16.dp.toPx()
                        val inset = sw / 2
                        val arc = Size(size.width - sw, size.height - sw)
                        drawArc(Color.White.copy(alpha = 0.10f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
                        val sweep = 360f * (left.toFloat() / total).coerceIn(0f, 1f)
                        drawArc(
                            if (over) Palette.Amber.copy(alpha = pulse) else Palette.Amber,
                            -90f, if (over) 360f else sweep, false, Offset(inset, inset), arc,
                            style = Stroke(sw, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (over) {
                            Text("🎉", fontSize = 54.sp)
                            Text("Conseguiste!", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            val s = (left + 999) / 1000
                            Text(
                                "%d:%02d".format(s / 60, s % 60),
                                color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    if (over) "Continuas mais um bocadinho ou ficas por aqui? As duas respostas estão certas." else phrase,
                    color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center, fontSize = 15.sp
                )
                Spacer(Modifier.weight(1f))

                t.nextStep?.let { step ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.10f)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("AGORA", color = Palette.AmberSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            Text(step, color = Color.White, fontSize = 17.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = onStepDone,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Palette.IndigoDeep),
                            shape = RoundedCornerShape(50)
                        ) { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Feito") }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                if (over) {
                    Button(
                        onClick = onMore,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Amber, contentColor = Color(0xFF2E1C00)),
                        shape = RoundedCornerShape(50)
                    ) { Text("Mais 10 minutos", fontSize = 16.sp) }
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onFinishTask,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) { Text("Acabei a tarefa") }
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) { Text(if (over) "Fico por aqui" else "Parar") }
                }
                Spacer(Modifier.height(4.dp))
                if (!over) TextButton(onClick = onMore) { Text("Recomeçar os 10 minutos", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp) }
            }
        }
    }
}

private fun ring(c: android.content.Context) {
    try {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31)
            c.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") c.getSystemService(Vibrator::class.java)
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 500), -1))
    } catch (_: Exception) { }
    try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80).startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
    } catch (_: Exception) { }
}
