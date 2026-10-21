package pt.ricardo.semadiar

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Contadores que só sobem. Nada se perde, mesmo que se apague ou desfaça uma tarefa. */
object Stats {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("estatisticas", Context.MODE_PRIVATE)

    fun get(c: Context, k: String) = p(c).getInt(k, 0)
    fun inc(c: Context, k: String) { p(c).edit().putInt(k, get(c, k) + 1).commit() }

    fun days(c: Context): Set<String> = p(c).getStringSet("dias", emptySet()) ?: emptySet()
    fun addDay(c: Context) {
        val d = days(c)
        val t = today().toString()
        if (t !in d) p(c).edit().putStringSet("dias", d + t).commit()
    }
}

object Achievements {
    class A(
        val id: String,
        val emoji: String,
        val title: String,
        val what: String,             // frase curta do que conta
        val unit: (Int) -> String,    // "3 blocos"
        val tiers: List<Int>,
        val value: (Context, List<Task>) -> Int
    )

    val tierNames = listOf("Bronze", "Prata", "Ouro", "Platina", "Diamante")
    val tierColors = listOf(
        Color(0xFFC27C4A), Color(0xFF9AA3B5), Color(0xFFE6B422), Color(0xFF6FB7C9), Color(0xFF8C7BD1)
    )

    private fun pl(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

    val all = listOf(
        A("blocos", "⏱️", "Arranque", "Começar um bloco de 10 minutos",
            { pl(it, "bloco de 10 minutos", "blocos de 10 minutos") }, listOf(1, 10, 25, 50, 100)
        ) { c, _ -> Stats.get(c, "blocos") },
        A("teimosas", "🐉", "Domadora de teimosas", "Acabar uma tarefa adiada 3 vezes ou pendurada há 2 semanas",
            { pl(it, "teimosa vencida", "teimosas vencidas") }, listOf(1, 3, 10, 25, 50)
        ) { c, _ -> Stats.get(c, "teimosas") },
        A("partidas", "✂️", "Passo a passo", "Partir uma tarefa e fazer o primeiro passo no mesmo dia",
            { pl(it, "tarefa partida e começada", "tarefas partidas e começadas") }, listOf(1, 5, 15, 30, 60)
        ) { c, _ -> Stats.get(c, "partidas") },
        A("largadas", "🍃", "Saber largar", "Largar sem culpa o que já não importa",
            { pl(it, "decisão", "decisões") }, listOf(1, 3, 10, 25, 50)
        ) { c, _ -> Stats.get(c, "largadas") },
        A("dias", "🌱", "Dias com obra", "Dias em que fizeste pelo menos uma coisa. Nunca se perdem",
            { pl(it, "dia", "dias") }, listOf(3, 10, 30, 75, 150)
        ) { c, _ -> Stats.days(c).size },
        A("feitas", "✅", "Uma de cada vez", "Tarefas acabadas",
            { pl(it, "tarefa feita", "tarefas feitas") }, listOf(1, 10, 50, 150, 300)
        ) { _, l -> l.count { it.doneAt > 0 } },
        A("foco", "🔥", "Horas de foco", "Tempo de trabalho nos blocos de 10 minutos",
            { if (it < 60) "$it min" else pl(it / 60, "hora", "horas") }, listOf(60, 300, 600, 1500, 3000)
        ) { _, l -> l.sumOf { it.minutes } }
    )

    data class Progress(val a: A, val value: Int, val tier: Int) {
        val next: Int? get() = a.tiers.getOrNull(tier)
        val prev: Int get() = if (tier == 0) 0 else a.tiers[tier - 1]
        val fraction: Float get() = next?.let { ((value - prev).toFloat() / (it - prev)).coerceIn(0f, 1f) } ?: 1f
    }

    data class Unlock(val a: A, val tier: Int)   // tier começa em 1

    fun progress(c: Context): List<Progress> {
        val tasks = Store.tasks(c)
        return all.map { a ->
            val v = a.value(c, tasks)
            Progress(a, v, a.tiers.count { v >= it })
        }
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("conquistas", Context.MODE_PRIVATE)

    /** Compara com o que já foi anunciado e devolve as medalhas novas. */
    @Synchronized
    fun evaluate(c: Context): List<Unlock> {
        val p = prefs(c)
        val ed = p.edit()
        val out = mutableListOf<Unlock>()
        progress(c).forEach { pr ->
            val seen = p.getInt(pr.a.id, 0)
            if (pr.tier > seen) {
                out += Unlock(pr.a, pr.tier)
                ed.putInt(pr.a.id, pr.tier)
            }
        }
        if (out.isNotEmpty()) ed.commit()
        return out
    }

    fun earned(c: Context) = progress(c).sumOf { it.tier }
    val max get() = all.sumOf { it.tiers.size }

    fun describe(u: Unlock) = "${tierNames[u.tier - 1]} · ${u.a.unit(u.a.tiers[u.tier - 1])}"

    /** Para medalhas ganhas fora da app (ex.: "Feito" numa notificação). */
    @SuppressLint("MissingPermission")
    fun notifyNew(c: Context) {
        val news = evaluate(c)
        if (news.isEmpty() || !Nagger.canNotify(c)) return
        val top = news.maxBy { it.tier }
        val pi = PendingIntent.getActivity(
            c, 60, Intent(c, MainActivity::class.java).putExtra("medals", true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        Nagger.channels(c)
        NotificationManagerCompat.from(c).notify(
            6,
            NotificationCompat.Builder(c, Nagger.CH)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFF3D4FA8.toInt())
                .setContentTitle("🏅 Nova medalha: ${top.a.title}")
                .setContentText(describe(top) + if (news.size > 1) " · e mais ${news.size - 1}" else "")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        )
    }
}

// ---------------------------------------------------------------- Ecrã

@Composable
fun Medal(emoji: String, tier: Int, size: Int = 64) {
    val cs = MaterialTheme.colorScheme
    val earned = tier > 0
    val color = if (earned) Achievements.tierColors[tier - 1] else cs.outline
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(
                if (earned) Brush.linearGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.12f)))
                else Brush.linearGradient(listOf(cs.surfaceContainerHighest, cs.surfaceContainerHighest))
            )
            .border((size / 16).dp, color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (size * 0.45f).sp, modifier = if (earned) Modifier else Modifier.alpha(0.35f))
    }
}

@Composable
fun MedalsScreen() {
    val c = androidx.compose.ui.platform.LocalContext.current
    val cs = MaterialTheme.colorScheme
    val list = Achievements.progress(c)
    val earned = list.sumOf { it.tier }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(top = 18.dp, bottom = 6.dp)) {
                SectionLabel("Sem pressas nem sequências")
                Spacer(Modifier.height(6.dp))
                Text("Medalhas", style = MaterialTheme.typography.displaySmall)
                Text("Contam o que te custa mais. Nada se perde, nunca.", color = cs.onSurfaceVariant)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.hero).padding(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$earned", color = Palette.Amber, fontSize = 48.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                        Text(" / ${Achievements.max}", color = Color.White.copy(alpha = 0.7f), fontSize = 18.sp, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Text(if (earned == 1) "medalha ganha" else "medalhas ganhas", color = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                    list.filter { it.tier > 0 }.sortedByDescending { it.tier }.take(3).forEach { Medal(it.a.emoji, it.tier, 44) }
                }
            }
        }
        items(list, key = { it.a.id }) { pr -> MedalCard(pr) }
    }
}

@Composable
private fun MedalCard(pr: Achievements.Progress) {
    val cs = MaterialTheme.colorScheme
    val a = pr.a
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerLow).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Medal(a.emoji, pr.tier, 60)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                if (pr.tier > 0) {
                    Spacer(Modifier.width(8.dp))
                    Pill(Achievements.tierNames[pr.tier - 1], Achievements.tierColors[pr.tier - 1].copy(alpha = 0.18f), Achievements.tierColors[pr.tier - 1].let {
                        if (pr.tier == 3) Color(0xFF8A6A00) else it
                    })
                }
            }
            Text(a.what, fontSize = 13.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            // Os 5 níveis em pontinhos
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                a.tiers.indices.forEach { i ->
                    Box(
                        Modifier.size(10.dp).clip(CircleShape)
                            .background(if (i < pr.tier) Achievements.tierColors[i] else cs.surfaceContainerHighest)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    pr.next?.let { "${a.unit(pr.value)} · próxima aos ${a.unit(it).substringBefore(' ')}" } ?: "${a.unit(pr.value)} · todas! 💎",
                    fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1
                )
            }
            if (pr.next != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { pr.fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                    color = Palette.Amber,
                    trackColor = cs.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

/** Janela de celebração de uma medalha nova. */
@Composable
fun UnlockDialog(news: List<Achievements.Unlock>, onClose: () -> Unit, onSeeAll: () -> Unit) {
    val top = news.maxBy { it.tier }
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(32.dp), color = cs.surface) {
            Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SectionLabel("Nova medalha", color = Achievements.tierColors[top.tier - 1].let { if (top.tier == 3) Color(0xFFB08A00) else it })
                Spacer(Modifier.height(16.dp))
                Medal(top.a.emoji, top.tier, 112)
                Spacer(Modifier.height(16.dp))
                Text(top.a.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(Achievements.describe(top), color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                if (news.size > 1) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        news.filter { it !== top }.take(4).forEach { Medal(it.a.emoji, it.tier, 36) }
                    }
                    Text("e mais ${news.size - 1}", fontSize = 13.sp, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = onClose, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Boa! 🎉") }
                androidx.compose.material3.TextButton(onClick = onSeeAll) { Text("Ver todas as medalhas") }
            }
        }
    }
}
