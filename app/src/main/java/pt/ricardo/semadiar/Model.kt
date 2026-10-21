package pt.ricardo.semadiar

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

const val DAY_MS = 86_400_000L

data class Step(val text: String, val done: Boolean = false)

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val due: LocalDate,
    val created: Long = System.currentTimeMillis(),
    val steps: List<Step> = emptyList(),
    val postpones: Int = 0,      // quantas vezes foi adiada
    val doneAt: Long = 0L,
    val droppedAt: Long = 0L,    // largada sem culpa
    val minutes: Int = 0         // minutos trabalhados nos blocos de 10
) {
    val active get() = doneAt == 0L && droppedAt == 0L
    val nextStep: String? get() = steps.firstOrNull { !it.done }?.text
    val stepsDone get() = steps.count { it.done }
}

fun today(): LocalDate = LocalDate.now()

/** Dias de atraso (positivo = atrasada). */
fun Task.lateDays(t: LocalDate = today()): Int = (t.toEpochDay() - due.toEpochDay()).toInt()

fun Task.isForToday(t: LocalDate = today()) = active && !due.isAfter(t)

fun Task.ageDays(now: Long = System.currentTimeMillis()): Int = ((now - created) / DAY_MS).toInt()

fun granted(c: Context, perm: String) =
    ContextCompat.checkSelfPermission(c, perm) == PackageManager.PERMISSION_GRANTED

object Store {
    @Volatile private var cache: List<Task>? = null

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences("dados", Context.MODE_PRIVATE)

    fun tasks(c: Context): List<Task> = cache ?: load(c).also { cache = it }

    fun invalidate() { cache = null }

    fun get(c: Context, id: String): Task? = tasks(c).firstOrNull { it.id == id }

    @Synchronized
    fun update(c: Context, f: (List<Task>) -> List<Task>) {
        val n = f(tasks(c))
        prefs(c).edit().putString("tasks", toJson(n).toString()).commit()
        cache = n
        Changes.notify(c)
    }

    fun add(c: Context, t: Task) = update(c) { it + t }
    fun edit(c: Context, id: String, f: (Task) -> Task) = update(c) { l -> l.map { if (it.id == id) f(it) else it } }
    fun remove(c: Context, id: String) = update(c) { l -> l.filterNot { it.id == id } }

    fun raw(c: Context): String = prefs(c).getString("tasks", null) ?: "[]"

    fun setRaw(c: Context, json: String) {
        prefs(c).edit().putString("tasks", json).commit()
        invalidate()
        Changes.notify(c)
    }

    private fun load(c: Context): List<Task> = try {
        fromJson(JSONArray(raw(c)))
    } catch (_: Exception) {
        emptyList()
    }

    fun toJson(list: List<Task>): JSONArray {
        val a = JSONArray()
        list.forEach { t ->
            val steps = JSONArray()
            t.steps.forEach { steps.put(JSONObject().put("t", it.text).put("d", it.done)) }
            a.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("due", t.due.toString())
                    .put("created", t.created)
                    .put("steps", steps)
                    .put("postpones", t.postpones)
                    .put("doneAt", t.doneAt)
                    .put("droppedAt", t.droppedAt)
                    .put("minutes", t.minutes)
            )
        }
        return a
    }

    fun fromJson(a: JSONArray): List<Task> = (0 until a.length()).mapNotNull { i ->
        try {
            val o = a.getJSONObject(i)
            val s = o.optJSONArray("steps") ?: JSONArray()
            Task(
                id = o.getString("id"),
                title = o.getString("title"),
                due = LocalDate.parse(o.getString("due")),
                created = o.optLong("created", System.currentTimeMillis()),
                steps = (0 until s.length()).map { j ->
                    val so = s.getJSONObject(j)
                    Step(so.optString("t"), so.optBoolean("d"))
                },
                postpones = o.optInt("postpones"),
                doneAt = o.optLong("doneAt"),
                droppedAt = o.optLong("droppedAt"),
                minutes = o.optInt("minutes")
            )
        } catch (_: Exception) {
            null
        }
    }
}

/** Definições escolhidas pela pessoa. */
object Settings {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("definicoes", Context.MODE_PRIVATE)

    /** 0 = suaves, 1 = normais, 2 = insistentes */
    fun mode(c: Context) = p(c).getInt("modo", 1)
    fun setMode(c: Context, v: Int) = p(c).edit().putInt("modo", v).apply()

    fun startHour(c: Context) = p(c).getInt("inicio", 9)
    fun setStartHour(c: Context, v: Int) = p(c).edit().putInt("inicio", v).apply()

    fun endHour(c: Context) = p(c).getInt("fim", 21)
    fun setEndHour(c: Context, v: Int) = p(c).edit().putInt("fim", v).apply()

    /** Prazo dado a uma tarefa quando não se escolhe data. */
    fun defaultDays(c: Context) = p(c).getInt("prazo", 3)
    fun setDefaultDays(c: Context, v: Int) = p(c).edit().putInt("prazo", v).apply()

    /** Quantas tarefas aparecem em destaque no "Hoje". */
    fun focusMax(c: Context) = p(c).getInt("foco", 3)
    fun setFocusMax(c: Context, v: Int) = p(c).edit().putInt("foco", v).apply()
}

/** As tarefas de hoje, por ordem: as mais atrasadas, depois as mais adiadas. */
object Focus {
    fun pending(list: List<Task>, t: LocalDate = today()): List<Task> =
        list.filter { it.isForToday(t) }
            .sortedWith(compareBy<Task> { it.due }.thenByDescending { it.postpones }.thenBy { it.created })

    fun doneToday(list: List<Task>, t: LocalDate = today()): Int =
        list.count { it.doneAt > 0 && localDay(it.doneAt) == t }
}

fun localDay(ms: Long): LocalDate =
    java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

/** Avisa o widget sempre que os dados mudam. */
object Changes {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    fun notify(c: Context) {
        val app = c.applicationContext
        scope.launch { TodayWidget.refresh(app) }
    }
}

/** As acções sobre tarefas, usadas pela app, pelas notificações e pelo widget. */
object Actions {
    fun complete(c: Context, id: String) {
        Store.edit(c, id) { it.copy(doneAt = System.currentTimeMillis(), steps = it.steps.map { s -> s.copy(done = true) }) }
        Nagger.cancelNag(c)
    }

    fun undo(c: Context, id: String) = Store.edit(c, id) { t ->
        t.copy(doneAt = 0L, droppedAt = 0L, due = if (t.due.isBefore(today())) today() else t.due)
    }

    fun postpone(c: Context, id: String, to: LocalDate) {
        Store.edit(c, id) { it.copy(due = to, postpones = it.postpones + 1) }
        Nagger.cancelNag(c)
    }

    /** Mudar a data. Para mais tarde conta como adiar, para mais cedo não. */
    fun setDate(c: Context, id: String, to: LocalDate) = Store.edit(c, id) { t ->
        if (to.isAfter(t.due) && !t.due.isAfter(today())) t.copy(due = to, postpones = t.postpones + 1)
        else t.copy(due = to)
    }

    fun drop(c: Context, id: String) {
        Store.edit(c, id) { it.copy(droppedAt = System.currentTimeMillis()) }
        Nagger.cancelNag(c)
    }

    /** Recomeçar com outra abordagem: fica para hoje e o contador volta a zero. */
    fun fresh(c: Context, id: String) = Store.edit(c, id) { it.copy(due = today(), postpones = 0) }

    /** Marca ou desmarca um passo. Devolve true se assim a tarefa ficou toda feita. */
    fun toggleStep(c: Context, id: String, index: Int): Boolean {
        var finished = false
        Store.edit(c, id) { t ->
            val steps = t.steps.mapIndexed { i, s -> if (i == index) s.copy(done = !s.done) else s }
            finished = steps.isNotEmpty() && steps.all { it.done }
            t.copy(steps = steps, doneAt = if (finished) System.currentTimeMillis() else 0L)
        }
        return finished
    }

    fun setSteps(c: Context, id: String, steps: List<Step>) = Store.edit(c, id) { it.copy(steps = steps) }

    fun addMinutes(c: Context, id: String, m: Int) {
        if (m > 0) Store.edit(c, id) { it.copy(minutes = it.minutes + m) }
    }
}

/** Mensagens para celebrar uma tarefa feita. */
object Cheer {
    private val plain = listOf(
        "Feito! 🎉", "Menos uma! ✨", "Boa! Já está 💪", "Riscada da lista ✓",
        "Isso! Uma de cada vez 🌱", "Feito e bem feito 🙌"
    )

    fun forTask(t: Task): String {
        val age = t.ageDays()
        return when {
            t.postpones >= 3 -> "Essa foi adiada ${t.postpones} vezes e venceste-a! 🏆"
            age >= 7 -> "Estava pendurada há $age dias. Já não está! 🎉"
            else -> plain.random()
        }
    }
}
