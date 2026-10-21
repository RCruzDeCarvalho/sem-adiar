package pt.ricardo.semadiar

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(onDataChanged: () -> Unit) {
    val c = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var mode by remember { mutableIntStateOf(Settings.mode(c)) }
    var start by remember { mutableIntStateOf(Settings.startHour(c)) }
    var end by remember { mutableIntStateOf(Settings.endHour(c)) }
    var days by remember { mutableIntStateOf(Settings.defaultDays(c)) }
    var focus by remember { mutableIntStateOf(Settings.focusMax(c)) }
    var backupMsg by remember { mutableStateOf<String?>(null) }
    var backupTick by remember { mutableIntStateOf(0) }
    var update by remember { mutableStateOf(Updater.pending(c)) }
    var checking by remember { mutableStateOf(false) }
    var updMsg by remember { mutableStateOf<String?>(null) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            backupMsg = if (Backup.setTarget(c, uri)) "Cópia feita ✓ A partir de agora faço uma por dia." else "Não consegui escrever no ficheiro."
            backupTick++
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val n = Backup.restore(c, uri)
            backupMsg = if (n == null) "Esse ficheiro não é uma cópia do Sem Adiar." else "Recuperei $n tarefas ✓"
            onDataChanged()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 18.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionLabel("A tua maneira")
        Text("Definições", style = MaterialTheme.typography.displaySmall)

        Card("🔔", "Lembretes", "Ao longo do dia, enquanto houver coisas para hoje.") {
            Segmented(listOf("Suaves", "Normais", "Insistentes"), mode) { mode = it; Settings.setMode(c, it) }
            Text(
                when (mode) {
                    0 -> "Um resumo de manhã e um lembrete ao fim da tarde."
                    1 -> "Um resumo de manhã e depois de 3 em 3 horas."
                    else -> "Um resumo de manhã e depois de hora a hora."
                },
                fontSize = 13.sp, color = cs.onSurfaceVariant
            )
            Stepper("Começam às", "${start}h", { if (start > 5) { start--; Settings.setStartHour(c, start) } }, { if (start < end - 1) { start++; Settings.setStartHour(c, start) } })
            Stepper("Acabam às", "${end}h", { if (end > start + 1) { end--; Settings.setEndHour(c, end) } }, { if (end < 24) { end++; Settings.setEndHour(c, end) } })
        }

        Card("📅", "Sem data não há", "Se não escolheres data, quantos dias te dou?") {
            Segmented(listOf("1", "2", "3", "5", "7"), listOf(1, 2, 3, 5, 7).indexOf(days).coerceAtLeast(0)) {
                days = listOf(1, 2, 3, 5, 7)[it]; Settings.setDefaultDays(c, days)
            }
            Text("Uma nova tarefa fica para daqui a $days ${if (days == 1) "dia" else "dias"}.", fontSize = 13.sp, color = cs.onSurfaceVariant)
        }

        Card("🎯", "Foco de hoje", "Quantas tarefas vês em destaque. As outras ficam escondidas.") {
            Segmented(listOf("1", "3", "5"), listOf(1, 3, 5).indexOf(focus).let { if (it < 0) 1 else it }) {
                focus = listOf(1, 3, 5)[it]; Settings.setFocusMax(c, focus); onDataChanged()
            }
        }

        Card("☁️", "Cópia de segurança", "Num ficheiro à tua escolha, por exemplo no Google Drive.") {
            val last = remember(backupTick) { Backup.lastTime(c) }
            val has = remember(backupTick) { Backup.uri(c) != null }
            Text(
                if (has && last > 0) "Última cópia: " + SimpleDateFormat("d MMM, HH:mm", Locale("pt", "PT")).format(Date(last))
                else "Ainda não escolheste onde guardar.",
                fontSize = 13.sp, color = cs.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { create.launch("sem-adiar-copia.json") }, shape = RoundedCornerShape(50)) {
                    Text(if (has) "Mudar ficheiro" else "Escolher ficheiro")
                }
                if (has) OutlinedButton(onClick = {
                    val u = Backup.uri(c)
                    backupMsg = if (u != null && Backup.writeTo(c, u)) "Cópia feita ✓" else "Não consegui escrever no ficheiro."
                    backupTick++
                }, shape = RoundedCornerShape(50)) { Text("Copiar já") }
            }
            TextButton(onClick = { open.launch(arrayOf("application/json", "*/*")) }) { Text("Recuperar de uma cópia…") }
            backupMsg?.let { Text(it, fontSize = 13.sp, color = cs.primary) }
        }

        Card("✨", "Actualizações", "A app procura sozinha, mas podes ver já.") {
            val u = update
            if (u != null) {
                Text("Há uma versão nova: ${u.versionName}", fontWeight = FontWeight.SemiBold, color = cs.primary)
                OutlinedButton(onClick = { Updater.download(c, u) }, shape = RoundedCornerShape(50)) { Text("Descarregar") }
            } else {
                OutlinedButton(
                    onClick = {
                        checking = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { Updater.check(c, force = true) }
                            update = r; checking = false
                            updMsg = if (r == null) "Tens a versão mais recente ✓" else null
                        }
                    },
                    enabled = !checking,
                    shape = RoundedCornerShape(50)
                ) { Text(if (checking) "A procurar…" else "Procurar agora") }
                updMsg?.let { Text(it, fontSize = 13.sp, color = cs.onSurfaceVariant) }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LinkTile("💬", "Sugestões", Modifier.weight(1f)) {
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${BuildConfig.FEEDBACK_EMAIL}"))
                    .putExtra(Intent.EXTRA_SUBJECT, "Sem Adiar · sugestão (versão ${BuildConfig.VERSION_NAME})")
                try { c.startActivity(i) } catch (_: Exception) { }
            }
            LinkTile("☕", "Ko-fi", Modifier.weight(1f)) {
                try { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.KOFI))) } catch (_: Exception) { }
            }
        }
        Text(
            "Sem Adiar · versão ${BuildConfig.VERSION_NAME}",
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 12.sp, color = cs.onSurfaceVariant
        )
    }
}

@Composable
private fun Card(emoji: String, title: String, sub: String, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(cs.surfaceContainerLow).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(cs.surfaceContainerLowest), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(sub, fontSize = 13.sp, color = cs.onSurfaceVariant)
            }
        }
        content()
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(cs.surfaceContainerHighest).padding(4.dp)
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (on) cs.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(o, color = if (on) cs.onPrimary else cs.onSurface, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Box(Modifier.size(36.dp).clip(CircleShape).background(cs.surfaceContainerHighest).clickable(onClick = onMinus), contentAlignment = Alignment.Center) { Text("−", fontSize = 18.sp) }
        Text(value, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        Box(Modifier.size(36.dp).clip(CircleShape).background(cs.surfaceContainerHighest).clickable(onClick = onPlus), contentAlignment = Alignment.Center) { Text("+", fontSize = 18.sp) }
    }
}

@Composable
private fun LinkTile(emoji: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerLow).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}
