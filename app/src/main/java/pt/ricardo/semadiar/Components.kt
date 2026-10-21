package pt.ricardo.semadiar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

/** Círculo para marcar como feito, com uma pequena animação antes de avisar. */
@Composable
fun RoundCheck(
    checked: Boolean,
    color: Color,
    size: Dp = 30.dp,
    onCheck: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val on = checked || pressed
    val bg by animateColorAsState(if (on) color else Color.Transparent, label = "bg")
    val sc by animateFloatAsState(if (pressed) 1.15f else 1f, spring(dampingRatio = 0.4f), label = "sc")
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(380)
            onCheck()
            pressed = false
        }
    }
    Box(
        Modifier.size(size).scale(sc).clip(CircleShape)
            .border(2.dp, color, CircleShape)
            .background(bg)
            .clickable { if (!checked) pressed = true else onCheck() },
        contentAlignment = Alignment.Center
    ) {
        if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

/** Pequena etiqueta arredondada. */
@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
}

/** Etiquetas de estado de uma tarefa: atraso, adiamentos, passos. */
@Composable
fun TaskBadges(t: Task, onDark: Boolean = false) {
    val late = t.lateDays()
    val items = buildList {
        if (late > 0) add(Triple("atrasada ${if (late == 1) "1 dia" else "$late dias"}", Palette.Late, Color.White))
        if (t.postpones > 0) add(
            Triple(
                "adiada ${t.postpones}×",
                if (onDark) Color.White.copy(alpha = 0.16f) else Palette.AmberSoft,
                if (onDark) Palette.AmberSoft else Color(0xFF7A4A00)
            )
        )
        if (t.steps.isNotEmpty()) add(
            Triple(
                "${t.stepsDone}/${t.steps.size} passos",
                if (onDark) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primaryContainer,
                if (onDark) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }
    if (items.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (txt, bg, fg) -> Pill(txt, bg, fg) }
    }
}

/** Título de secção, discreto e espaçado. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp
    )
}

/** Lista de passos editável: marcar, apagar, mudar de ordem e acrescentar. */
@Composable
fun StepsEditor(
    steps: List<Step>,
    onToggle: (Int) -> Unit,
    onChange: (List<Step>) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEachIndexed { i, s ->
            val isNext = !s.done && steps.indexOfFirst { !it.done } == i
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (isNext) cs.secondaryContainer else cs.surfaceContainerLow)
                    .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundCheck(s.done, if (isNext) Palette.Amber else cs.primary, 24.dp) { onToggle(i) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    if (isNext) Text("próximo passo", fontSize = 11.sp, color = cs.onSecondaryContainer, fontWeight = FontWeight.Bold)
                    Text(
                        s.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (s.done) cs.onSurfaceVariant else cs.onSurface,
                        textDecoration = if (s.done) TextDecoration.LineThrough else null
                    )
                }
                if (i > 0) IconButton(onClick = {
                    val l = steps.toMutableList(); val x = l.removeAt(i); l.add(i - 1, x); onChange(l)
                }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, "Subir", tint = cs.onSurfaceVariant)
                }
                if (i < steps.lastIndex) IconButton(onClick = {
                    val l = steps.toMutableList(); val x = l.removeAt(i); l.add(i + 1, x); onChange(l)
                }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Descer", tint = cs.onSurfaceVariant)
                }
                IconButton(onClick = { onChange(steps.filterIndexed { j, _ -> j != i }) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Close, "Apagar passo", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        fun addDraft() {
            if (draft.isNotBlank()) { onChange(steps + Step(draft.trim())); draft = "" }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth().keepVisible(),
            placeholder = { Text(if (steps.isEmpty()) "Escreve um passo pequenino…" else "Mais um passo…") },
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addDraft() }),
            trailingIcon = {
                IconButton(onClick = { addDraft() }, enabled = draft.isNotBlank()) { Icon(Icons.Filled.Add, "Acrescentar") }
            }
        )
    }
}

/** Quando o campo ganha foco, espera pelo teclado e desliza até ele ficar à vista. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun Modifier.keepVisible(): Modifier = composed {
    val req = remember { BringIntoViewRequester() }
    val imeVisible = WindowInsets.isImeVisible
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused, imeVisible) {
        if (focused) {
            delay(250)
            req.bringIntoView()
        }
    }
    this.bringIntoViewRequester(req).onFocusChanged { focused = it.isFocused }
}
