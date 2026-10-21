package pt.ricardo.semadiar

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Nagger.channels(this)
        NagWorker.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 && !granted(this, Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        incoming.value = intent
        setContent { AppTheme { App(incoming) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incoming.value = intent
    }
}

private enum class Tab(val label: String) { HOJE("Hoje"), PROXIMAS("Próximas"), FEITAS("Feitas"), DEFINICOES("Definições") }

@Composable
private fun App(incoming: MutableState<Intent?>) {
    val c = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    var version by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        Store.invalidate()
        version++
        onPauseOrDispose { }
    }
    val tasks = remember(version) { Store.tasks(c) }
    fun refresh() { version++ }
    fun task(id: String?) = id?.let { i -> tasks.firstOrNull { it.id == i } }

    var tab by rememberSaveable { mutableStateOf(Tab.HOJE) }
    var capture by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var postponeId by rememberSaveable { mutableStateOf<String?>(null) }
    var stuckId by rememberSaveable { mutableStateOf<String?>(null) }
    var breakdownId by rememberSaveable { mutableStateOf<String?>(null) }
    var breakdownFresh by rememberSaveable { mutableStateOf(false) }
    var timer by remember { mutableStateOf(Timer.state(c)) }
    var confetti by remember { mutableIntStateOf(0) }

    fun toast(msg: String) {
        scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(msg) }
    }

    fun celebrate(t: Task?) {
        confetti++
        toast(t?.let { Cheer.forTask(it) } ?: "Feito! 🎉")
    }

    fun complete(id: String) {
        val t = Store.get(c, id)
        Actions.complete(c, id)
        refresh()
        celebrate(t)
        if (openId == id) openId = null
    }

    fun requestPostpone(id: String) {
        val t = Store.get(c, id) ?: return
        if (t.postpones >= 2) stuckId = id else postponeId = id
    }

    fun start(id: String) {
        timer = Timer.start(c, id)
        openId = null
    }

    fun toggleStep(id: String, i: Int) {
        val before = Store.get(c, id)
        if (Actions.toggleStep(c, id, i)) {
            refresh()
            celebrate(before)
        } else refresh()
    }

    val ops = Ops(
        open = { openId = it },
        complete = { complete(it) },
        postpone = { requestPostpone(it) },
        start = { start(it) }
    )

    // Pedidos vindos das notificações e do widget
    LaunchedEffect(incoming.value) {
        val i = incoming.value ?: return@LaunchedEffect
        Store.invalidate(); refresh()
        when {
            i.getBooleanExtra("capture", false) -> { tab = Tab.HOJE; capture = true }
            i.getStringExtra("start") != null -> start(i.getStringExtra("start")!!)
            i.getStringExtra("stuck") != null -> stuckId = i.getStringExtra("stuck")
            i.getStringExtra("open") != null -> openId = i.getStringExtra("open")
        }
        if (i.extras?.isEmpty == false) Nagger.cancelNag(c)
        incoming.value = null
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = cs.background,
            snackbarHost = {
                SnackbarHost(snack) { d ->
                    Snackbar(d, shape = RoundedCornerShape(18.dp), containerColor = cs.inverseSurface, contentColor = cs.inverseOnSurface)
                }
            },
            floatingActionButton = {
                if (tab == Tab.HOJE || tab == Tab.PROXIMAS) {
                    ExtendedFloatingActionButton(
                        onClick = { capture = true },
                        icon = { Icon(Icons.Filled.Add, null) },
                        text = { Text("Nova") },
                        containerColor = cs.primary,
                        contentColor = cs.onPrimary,
                        shape = RoundedCornerShape(22.dp)
                    )
                }
            },
            bottomBar = {
                NavigationBar(containerColor = cs.surfaceContainerLow) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                Icon(
                                    when (t) {
                                        Tab.HOJE -> Icons.Filled.Home
                                        Tab.PROXIMAS -> Icons.Filled.DateRange
                                        Tab.FEITAS -> Icons.Filled.CheckCircle
                                        Tab.DEFINICOES -> Icons.Filled.Settings
                                    },
                                    null
                                )
                            },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = cs.secondaryContainer)
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    Tab.HOJE -> TodayScreen(tasks, Settings.focusMax(c), ops)
                    Tab.PROXIMAS -> UpcomingScreen(tasks, ops)
                    Tab.FEITAS -> DoneScreen(tasks, onUndo = { Actions.undo(c, it); refresh() }, onOpen = { openId = it })
                    Tab.DEFINICOES -> SettingsScreen(onDataChanged = { Store.invalidate(); refresh() })
                }
            }
        }
        Confetti(confetti)
    }

    if (capture) CaptureSheet(
        onSave = { t ->
            Store.add(c, t); refresh(); capture = false
            toast(if (t.due == today()) "Apontado para hoje ✓" else "Apontado para ${Dates.short(t.due)} ✓")
        },
        onDismiss = { capture = false }
    )

    task(openId)?.let { t ->
        TaskSheet(
            t = t,
            onDismiss = { openId = null; refresh() },
            onComplete = { complete(t.id) },
            onStart = { start(t.id) },
            onPostpone = { requestPostpone(t.id) },
            onBreakdown = { breakdownFresh = false; breakdownId = t.id },
            onToggleStep = { toggleStep(t.id, it) },
            onChanged = { refresh() }
        )
    }

    task(postponeId)?.let { t ->
        PostponeSheet(
            t,
            onPick = { d: LocalDate ->
                Actions.postpone(c, t.id, d); refresh(); postponeId = null
                toast("Fica para ${Dates.short(d)}. Combinado.")
            },
            onDismiss = { postponeId = null }
        )
    }

    task(stuckId)?.let { t ->
        StuckDialog(
            t,
            onBreakdown = { stuckId = null; breakdownFresh = true; breakdownId = t.id },
            onAskPerson = {
                stuckId = null
                Store.edit(c, t.id) { it.copy(postpones = 1) }; refresh()
                Breakdown.askPerson(c, t)
            },
            onDrop = {
                stuckId = null; Actions.drop(c, t.id); refresh(); openId = null
                toast("Largada. Às vezes é a melhor decisão 🍃")
            },
            onPostponeAnyway = { stuckId = null; postponeId = t.id },
            onDismiss = { stuckId = null }
        )
    }

    task(breakdownId)?.let { t ->
        BreakdownDialog(
            t,
            onToggleStep = { toggleStep(t.id, it) },
            onChanged = { refresh() },
            onDone = {
                if (breakdownFresh) {
                    Actions.fresh(c, t.id)
                    toast(t.nextStep?.let { "Para hoje, só isto: $it" } ?: "Fica para hoje, com um recomeço")
                    openId = null
                }
                breakdownId = null; breakdownFresh = false; refresh()
            }
        )
    }

    timer?.let { s ->
        val t = task(s.id)
        if (t == null) LaunchedEffect(s) { Timer.stop(c); timer = null }
        else TimerScreen(
            t, s.end,
            onStepDone = {
                val i = t.steps.indexOfFirst { !it.done }
                if (i >= 0) {
                    val finished = Actions.toggleStep(c, t.id, i)
                    refresh()
                    if (finished) { Timer.stop(c); timer = null; refresh(); celebrate(t) }
                    else toast("Passo feito ✓")
                }
            },
            onFinishTask = { Timer.stop(c); timer = null; complete(t.id) },
            onMore = { timer = Timer.start(c, t.id) },
            onStop = {
                val m = Timer.stop(c); timer = null; refresh()
                toast("Trabalhaste $m min nisto. Começar já é muito 💪")
            }
        )
    }
}
