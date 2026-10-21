package pt.ricardo.semadiar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate

// ---------------------------------------------------------------- Escolher data

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickDate(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val min = today().toEpochDay() * DAY_MS
    val st = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDay() * DAY_MS,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= min
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / DAY_MS)) }
                onDismiss()
            }) { Text("Escolher") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    ) { DatePicker(st, showModeToggle = false) }
}

// ---------------------------------------------------------------- Nova tarefa

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureSheet(onSave: (Task) -> Unit, onDismiss: () -> Unit) {
    val c = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    var step by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf(false) }
    val parsed = Dates.parse(text)
    val fallback = Dates.defaultDue(c)
    val due = chosen ?: parsed.date ?: fallback
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun save() {
        if (text.isBlank()) return
        val title = if (chosen == null && parsed.date != null && parsed.title.isNotBlank()) parsed.title else text.trim()
        onSave(Task(title = title.replaceFirstChar { it.uppercase() }, due = due, steps = if (step.isBlank()) emptyList() else listOf(Step(step.trim()))))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface
    ) {
        // Cabeçalho fixo, sempre visível mesmo com o teclado aberto
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("O que tens de fazer?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            val can = text.isNotBlank()
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(if (can) cs.primary else cs.surfaceContainerHighest)
                    .clickable(enabled = can) { save() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check, "Guardar",
                    tint = if (can) cs.onPrimary else cs.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 20.dp)
        ) {
            Text("Podes escrever a data no texto: \"ligar à Ana na sexta\".", color = cs.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("Ex.: marcar o dentista amanhã") },
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                textStyle = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(14.dp))
            SectionLabel("Para quando")
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Dates.quick().forEach { q ->
                    FilterChip(
                        selected = due == q.date,
                        onClick = { chosen = q.date },
                        label = { Text(q.label) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = cs.primary, selectedLabelColor = cs.onPrimary
                        )
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { picking = true },
                    label = { Text("Escolher…") },
                    leadingIcon = { Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp)) },
                    shape = RoundedCornerShape(50)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.secondaryContainer).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📅", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        chosen != null -> "Fica para ${Dates.long(due)}"
                        parsed.date != null -> "Percebi: ${Dates.long(due)}"
                        else -> "Se não escolheres, fica para ${Dates.long(due)}"
                    },
                    color = cs.onSecondaryContainer,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = step,
                onValueChange = { step = it },
                modifier = Modifier.fillMaxWidth().keepVisible(),
                label = { Text("Primeiro passo pequenino (opcional)") },
                placeholder = { Text("Ex.: procurar o número") },
                shape = RoundedCornerShape(18.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { save() },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(50)
            ) { Text("Guardar", fontSize = 16.sp) }
        }
    }
    if (picking) PickDate(due, { chosen = it }, { picking = false })
}

// ---------------------------------------------------------------- Ficha da tarefa

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSheet(
    t: Task,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onStart: () -> Unit,
    onPostpone: () -> Unit,
    onBreakdown: () -> Unit,
    onToggleStep: (Int) -> Unit,
    onChanged: () -> Unit
) {
    val c = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var title by remember(t.id) { mutableStateOf(t.title) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = {
            if (title.isNotBlank() && title != t.title) Store.edit(c, t.id) { it.copy(title = title.trim()) }
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.titleLarge,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { TaskBadges(t) }
            }
            Spacer(Modifier.height(12.dp))

            // Data
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerLow)
                    .clickable { picking = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.DateRange, null, tint = cs.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (t.active) "Para" else "Era para", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    Text(Dates.long(t.due).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                }
                if (t.active && t.due.isAfter(today())) {
                    TextButton(onClick = { Actions.setDate(c, t.id, today()); onChanged() }) { Text("Fazer hoje") }
                } else Text("Mudar", color = cs.primary, fontWeight = FontWeight.SemiBold)
            }
            val info = buildList {
                val age = t.ageDays()
                add(if (age == 0) "apontada hoje" else "apontada há $age ${if (age == 1) "dia" else "dias"}")
                if (t.minutes > 0) add("${t.minutes} min de trabalho")
            }
            Text(info.joinToString(" · "), fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, top = 6.dp))

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Passos", Modifier.weight(1f))
                TextButton(onClick = onBreakdown) { Text("✂️ Ajuda a partir") }
            }
            if (t.steps.isEmpty()) {
                Text(
                    "Uma tarefa grande assusta. Parte-a em passos que se façam em poucos minutos.",
                    color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            StepsEditor(t.steps, onToggle = onToggleStep, onChange = { Actions.setSteps(c, t.id, it); onChanged() })

            Spacer(Modifier.height(22.dp))
            if (t.active) {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Amber, contentColor = Color(0xFF2E1C00))
                ) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Começar 10 minutos", fontSize = 16.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onComplete, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(50)) {
                        Icon(Icons.Filled.Check, null); Spacer(Modifier.width(6.dp)); Text("Feito")
                    }
                    OutlinedButton(onClick = onPostpone, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(50)) {
                        Text("Adiar")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { Actions.drop(c, t.id); onChanged(); onDismiss() }) {
                        Text("Largar sem culpa", color = cs.onSurfaceVariant)
                    }
                    TextButton(onClick = { confirmDelete = true }) { Text("Apagar", color = Palette.Late) }
                }
            } else {
                Button(
                    onClick = { Actions.undo(c, t.id); onChanged() },
                    modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(50)
                ) { Text(if (t.doneAt > 0) "Afinal não está feita" else "Recuperar") }
            }
            if (confirmDelete) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Late.copy(alpha = 0.12f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Apagar de vez?", modifier = Modifier.weight(1f))
                    TextButton(onClick = { confirmDelete = false }) { Text("Não") }
                    TextButton(onClick = { Store.remove(c, t.id); onChanged(); onDismiss() }) { Text("Apagar", color = Palette.Late) }
                }
            }
        }
    }
    if (picking) PickDate(t.due, { Actions.setDate(c, t.id, it); onChanged() }, { picking = false })
}

// ---------------------------------------------------------------- Adiar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostponeSheet(t: Task, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var picking by remember { mutableStateOf(false) }
    val d = today()
    val options = listOf(
        "Amanhã" to d.plusDays(1),
        "Daqui a 3 dias" to d.plusDays(3),
        "Próxima segunda" to Dates.nextWeek(d),
        "Daqui a 2 semanas" to d.plusDays(14)
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = cs.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Adiar para quando?", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                when (t.postpones) {
                    0 -> "Tudo bem adiar, mas com uma data a sério."
                    1 -> "Já é a segunda vez. Escolhe um dia em que consigas mesmo."
                    else -> "Já adiaste ${t.postpones} vezes."
                },
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            options.forEach { (label, date) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp))
                        .background(cs.surfaceContainerLow).clickable { onPick(date) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${Dates.weekday(date)}, ${Dates.dayMonth(date)}", color = cs.onSurfaceVariant)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp))
                    .border(1.dp, cs.outlineVariant, RoundedCornerShape(18.dp)).clickable { picking = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.DateRange, null, tint = cs.primary)
                Spacer(Modifier.width(10.dp))
                Text("Escolher dia…", color = cs.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (picking) PickDate(d.plusDays(1), onPick, { picking = false })
}

// ---------------------------------------------------------------- À terceira

@Composable
fun StuckDialog(
    t: Task,
    onBreakdown: () -> Unit,
    onAskPerson: () -> Unit,
    onDrop: () -> Unit,
    onPostponeAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(30.dp), color = cs.surface) {
            Column(Modifier.padding(24.dp)) {
                Box(
                    Modifier.size(60.dp).clip(CircleShape).background(Palette.AmberSoft),
                    contentAlignment = Alignment.Center
                ) { Text("🤔", fontSize = 30.sp) }
                Spacer(Modifier.height(14.dp))
                Text("Esta já foi adiada ${t.postpones} vezes", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Quando uma tarefa se arrasta assim, normalmente é grande demais, falta alguma coisa, ou já não interessa. O que fazemos?",
                    color = cs.onSurfaceVariant, fontSize = 14.sp
                )
                Spacer(Modifier.height(10.dp))
                Text("“${t.title}”", fontWeight = FontWeight.SemiBold, color = cs.primary)
                Spacer(Modifier.height(16.dp))
                StuckOption("✂️", "Partir em passos pequenos", "O primeiro passo de 2 minutos", true, onBreakdown)
                StuckOption("🙋", "Pedir ajuda a alguém", "Envia uma mensagem já escrita", false, onAskPerson)
                StuckOption("🍃", "Largar sem culpa", "Se já não importa, deixa ir", false, onDrop)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onPostponeAnyway, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Adiar mesmo assim", color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StuckOption(emoji: String, title: String, sub: String, main: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp))
            .background(if (main) cs.primary else cs.surfaceContainerLow)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (main) cs.onPrimary else cs.onSurface)
            Text(sub, fontSize = 12.sp, color = if (main) cs.onPrimary.copy(alpha = 0.8f) else cs.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- Partir em passos

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BreakdownDialog(t: Task, onToggleStep: (Int) -> Unit, onChanged: () -> Unit, onDone: () -> Unit) {
    val c = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var pasteMsg by remember { mutableStateOf<String?>(null) }
    var sentToAi by remember { mutableStateOf(false) }
    val suggestions = Breakdown.suggest(t.title).filter { s -> t.steps.none { it.text.equals(s, true) } }

    fun setSteps(l: List<Step>) { Actions.setSteps(c, t.id, l); onChanged() }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = cs.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.Close, "Fechar") }
                    Text("Partir em passos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Button(onClick = onDone, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 20.dp)) { Text("Pronto") }
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    Text(t.title, style = MaterialTheme.typography.headlineSmall, color = cs.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "O truque é o primeiro passo ser ridiculamente pequeno. Abrir um site, encontrar um papel, escrever uma frase.",
                        color = cs.onSurfaceVariant, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    StepsEditor(t.steps, onToggle = onToggleStep, onChange = { setSteps(it) })

                    if (suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerLow).padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💡", fontSize = 20.sp)
                                Spacer(Modifier.width(8.dp))
                                Text("Sugestões para começar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            }
                            Text("Toca para juntar aos passos.", fontSize = 13.sp, color = cs.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                suggestions.forEach { s ->
                                    Row(
                                        Modifier.clip(RoundedCornerShape(50)).background(cs.surfaceContainerLowest)
                                            .border(1.dp, cs.outlineVariant, RoundedCornerShape(50))
                                            .clickable { setSteps(t.steps + Step(s)) }
                                            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp), tint = cs.primary)
                                        Spacer(Modifier.width(4.dp))
                                        Text(s, fontSize = 14.sp)
                                    }
                                }
                            }
                            if (suggestions.size > 1) TextButton(onClick = { setSteps(t.steps + suggestions.map { Step(it) }) }) {
                                Text("Juntar todas")
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.hero).padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✨", fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("Pedir ajuda à IA", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "1. Envio a tarefa, já com o pedido escrito, para a app de IA que tiveres (Gemini, ChatGPT, Claude…).\n" +
                                "2. Copia a resposta.\n3. Volta aqui e toca em Colar resposta.",
                            color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { Breakdown.askAi(c, t); sentToAi = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Palette.IndigoDeep),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f)
                            ) { Text("Enviar à IA") }
                            Button(
                                onClick = {
                                    val steps = Breakdown.clipboard(c)?.let { Breakdown.parse(it) }.orEmpty()
                                    pasteMsg = if (steps.isEmpty()) {
                                        "Não encontrei passos no que copiaste. Copia só a lista da resposta."
                                    } else {
                                        setSteps(t.steps.filter { it.done } + steps.map { Step(it) })
                                        "Juntei ${steps.size} passos ✓"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (sentToAi) Palette.Amber else Color.White.copy(alpha = 0.18f),
                                    contentColor = if (sentToAi) Color(0xFF2E1C00) else Color.White
                                ),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f)
                            ) { Text("Colar resposta") }
                        }
                        pasteMsg?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, color = Palette.AmberSoft, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}
