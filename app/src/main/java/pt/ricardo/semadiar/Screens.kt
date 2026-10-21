package pt.ricardo.semadiar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime

/** O que os ecrãs podem pedir à app. */
class Ops(
    val open: (String) -> Unit,
    val complete: (String) -> Unit,
    val postpone: (String) -> Unit,
    val start: (String) -> Unit
)

private val bottomPad = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 120.dp)

// ---------------------------------------------------------------- Hoje

@Composable
fun TodayScreen(tasks: List<Task>, focusMax: Int, ops: Ops) {
    val cs = MaterialTheme.colorScheme
    val t = today()
    val pend = Focus.pending(tasks, t)
    val focus = pend.take(focusMax)
    val rest = pend.drop(focusMax)
    val done = Focus.doneToday(tasks, t)
    var showRest by rememberSaveable { mutableStateOf(false) }
    val soon = tasks.filter { it.active && it.due.isAfter(t) }.sortedBy { it.due }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TodayHeader(pend.size, done) }

        if (pend.isEmpty()) item { EmptyToday(done, soon.firstOrNull()) }

        focus.firstOrNull()?.let { first ->
            item(key = "hero-" + first.id) { HeroCard(first, ops) }
        }
        items(focus.drop(1), key = { it.id }) { FocusCard(it, ops) }

        if (rest.isNotEmpty()) item {
            TextButton(onClick = { showRest = !showRest }) {
                Text(if (showRest) "Esconder as outras" else "Mais ${rest.size} para hoje", color = cs.primary)
                Icon(if (showRest) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = cs.primary)
            }
        }
        if (showRest) items(rest, key = { it.id }) { CompactRow(it, ops) }

        if (soon.isNotEmpty()) {
            item { Spacer(Modifier.height(4.dp)); SectionLabel("A seguir", color = cs.onSurfaceVariant) }
            items(soon.take(3), key = { "s" + it.id }) { CompactRow(it, ops) }
        }
    }
}

@Composable
private fun TodayHeader(pending: Int, done: Int) {
    val cs = MaterialTheme.colorScheme
    val d = today()
    val hello = when (LocalTime.now().hour) {
        in 5..12 -> "Bom dia"
        in 13..19 -> "Boa tarde"
        else -> "Boa noite"
    }
    val sub = when {
        pending == 0 && done > 0 -> "Fizeste tudo o que era para hoje."
        pending == 0 -> "Hoje está tudo calmo."
        pending == 1 -> "Só uma coisa para hoje."
        else -> "$pending coisas para hoje. Uma de cada vez."
    }
    Column(Modifier.padding(top = 18.dp, bottom = 4.dp)) {
        SectionLabel("${Dates.weekday(d)} · ${d.dayOfMonth} ${Dates.dayMonth(d).substringAfter(' ')}")
        Spacer(Modifier.height(6.dp))
        Text(hello, style = MaterialTheme.typography.displaySmall, color = cs.onBackground)
        Text(sub, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        if (done + pending > 0) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { done.toFloat() / (done + pending) },
                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)),
                    color = Palette.Amber,
                    trackColor = cs.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round
                )
                Spacer(Modifier.width(12.dp))
                Text("✓ $done feita${if (done == 1) "" else "s"}", fontSize = 13.sp, color = cs.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyToday(done: Int, next: Task?) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(if (done > 0) Palette.AmberSoft else cs.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Text(if (done > 0) "🎉" else "🌿", fontSize = 40.sp) }
        Spacer(Modifier.height(14.dp))
        Text(
            if (done > 0) "Tudo feito por hoje" else "Nada para hoje",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (done > 0) "Descansa, que mereces."
            else "Toca em Nova para apontar alguma coisa. Se não escolheres data, eu escolho por ti.",
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (next != null) {
            Spacer(Modifier.height(10.dp))
            Text("A seguir: ${next.title} (${Dates.short(next.due)})", color = cs.primary, fontSize = 13.sp)
        }
    }
}

/** A tarefa do "agora", em destaque. */
@Composable
private fun HeroCard(t: Task, ops: Ops) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Palette.hero)
            .clickable { ops.open(t.id) }.padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("AGORA", Palette.Amber, Color(0xFF3B2400))
            Spacer(Modifier.width(8.dp))
            TaskBadges(t, onDark = true)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            t.title,
            color = Color.White,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 31.sp
        )
        val step = t.nextStep
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.10f)).padding(14.dp)
        ) {
            Text(
                if (step != null) "PRÓXIMO PASSO" else "PARA COMEÇAR",
                color = Palette.AmberSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                step ?: "Só 10 minutos. Depois vês se continuas.",
                color = Color.White, fontSize = 17.sp, lineHeight = 22.sp
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { ops.start(t.id) },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Amber, contentColor = Color(0xFF2E1C00)),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("10 minutos", fontSize = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))
                    .clickable { ops.complete(t.id) },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Check, "Feito", tint = Color.White) }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { ops.postpone(t.id) }) {
                Text("Adiar", color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

/** As outras tarefas do foco. */
@Composable
private fun FocusCard(t: Task, ops: Ops) {
    val cs = MaterialTheme.colorScheme
    val late = t.lateDays() > 0
    val accent = if (late) Palette.Late else cs.primary
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(cs.surfaceContainerLowest)
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(24.dp))
            .clickable { ops.open(t.id) }
            .height(IntrinsicSize.Min)
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(accent))
        Row(Modifier.weight(1f).padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 2.dp)) { RoundCheck(false, accent) { ops.complete(t.id) } }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                t.nextStep?.let {
                    Spacer(Modifier.height(2.dp))
                    Text("→ $it", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { TaskBadges(t) }
                    SmallAction("▶ 10 min", cs.primary) { ops.start(t.id) }
                    Spacer(Modifier.width(6.dp))
                    SmallAction("Adiar", cs.onSurfaceVariant) { ops.postpone(t.id) }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/** Linha simples: círculo para marcar, título, data. */
@Composable
fun CompactRow(t: Task, ops: Ops, showDate: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val late = t.lateDays() > 0
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerLow)
            .clickable { ops.open(t.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundCheck(false, if (late) Palette.Late else cs.primary, 26.dp) { ops.complete(t.id) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            t.nextStep?.let { Text("→ $it", fontSize = 13.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (showDate) {
            Spacer(Modifier.width(8.dp))
            Pill(
                Dates.short(t.due),
                if (late) Palette.Late.copy(alpha = 0.14f) else cs.primaryContainer,
                if (late) Palette.Late else cs.onPrimaryContainer
            )
        }
    }
}

// ---------------------------------------------------------------- Próximas

@Composable
fun UpcomingScreen(tasks: List<Task>, ops: Ops) {
    val cs = MaterialTheme.colorScheme
    val t = today()
    val list = tasks.filter { it.active && it.due.isAfter(t) }.sortedWith(compareBy<Task> { it.due }.thenBy { it.created })
    val endOfWeek = t.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
    val nextWeekEnd = endOfWeek.plusDays(7)
    fun section(d: LocalDate) = when {
        d == t.plusDays(1) -> "Amanhã"
        !d.isAfter(endOfWeek) -> "Esta semana"
        !d.isAfter(nextWeekEnd) -> "Próxima semana"
        else -> "Mais tarde"
    }
    val groups = list.groupBy { section(it.due) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(top = 18.dp, bottom = 6.dp)) {
                SectionLabel("Planeado")
                Spacer(Modifier.height(6.dp))
                Text("Próximas", style = MaterialTheme.typography.displaySmall)
                Text(
                    if (list.isEmpty()) "Nada marcado para os próximos dias." else "Cada coisa tem o seu dia. Não há listas sem fim.",
                    color = cs.onSurfaceVariant
                )
            }
        }
        listOf("Amanhã", "Esta semana", "Próxima semana", "Mais tarde").forEach { name ->
            val g = groups[name] ?: return@forEach
            item(key = "h$name") {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(name)
                    Spacer(Modifier.width(8.dp))
                    Pill("${g.size}", cs.primaryContainer, cs.onPrimaryContainer)
                }
            }
            items(g, key = { it.id }) { CompactRow(it, ops) }
        }
    }
}

// ---------------------------------------------------------------- Feitas

@Composable
fun DoneScreen(tasks: List<Task>, onUndo: (String) -> Unit, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val t = today()
    val week = (6 downTo 0).map { t.minusDays(it.toLong()) }
    val done = tasks.filter { it.doneAt > 0 }.sortedByDescending { it.doneAt }
    val perDay = week.map { d -> done.count { localDay(it.doneAt) == d } }
    val total = perDay.sum()
    val dropped = tasks.filter { it.droppedAt > 0 && it.doneAt == 0L }.sortedByDescending { it.droppedAt }
    val minutes = tasks.sumOf { it.minutes }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(top = 18.dp, bottom = 6.dp)) {
                SectionLabel("O que já fizeste")
                Spacer(Modifier.height(6.dp))
                Text("Feitas", style = MaterialTheme.typography.displaySmall)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.hero).padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$total", color = Palette.Amber, fontSize = 52.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (total == 1) "tarefa feita\nnos últimos 7 dias" else "tarefas feitas\nnos últimos 7 dias",
                        color = Color.White, fontSize = 15.sp, lineHeight = 19.sp, modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                val max = (perDay.maxOrNull() ?: 0).coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    week.forEachIndexed { i, d ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            if (perDay[i] > 0) Text("${perDay[i]}", color = Color.White, fontSize = 11.sp)
                            Box(
                                Modifier.fillMaxWidth().height((6 + 52f * perDay[i] / max).dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (d == t) Palette.Amber else Color.White.copy(alpha = if (perDay[i] > 0) 0.55f else 0.15f))
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(Dates.weekday(d).take(3), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                }
                if (minutes > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text("⏱ ${minutes} minutos em blocos de 10", color = Palette.AmberSoft, fontSize = 13.sp)
                }
            }
        }
        if (done.isEmpty()) item {
            Text("Quando acabares alguma coisa, aparece aqui. Cada uma conta.", color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        }
        items(done.take(60), key = { "d" + it.id }) { d ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerLow)
                    .clickable { onOpen(d.id) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Palette.Ok), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.title, style = MaterialTheme.typography.bodyLarge)
                    Text(Dates.short(localDay(d.doneAt)), fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                TextButton(onClick = { onUndo(d.id) }) { Text("Desfazer", fontSize = 13.sp) }
            }
        }
        if (dropped.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Largadas sem culpa", color = cs.onSurfaceVariant)
                Text("Também é uma decisão. Se mudares de ideias, recupera-as.", fontSize = 13.sp, color = cs.onSurfaceVariant)
            }
            items(dropped.take(30), key = { "x" + it.id }) { d ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerLow)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(d.title, modifier = Modifier.weight(1f), color = cs.onSurfaceVariant)
                    TextButton(onClick = { onUndo(d.id) }) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Recuperar", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
