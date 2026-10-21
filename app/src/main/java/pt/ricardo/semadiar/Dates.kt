package pt.ricardo.semadiar

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object Dates {
    val weekdays = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")
    private val months = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    private val monthsFull = listOf(
        "janeiro", "fevereiro", "março", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"
    )

    fun weekday(d: LocalDate) = weekdays[d.dayOfWeek.value - 1]

    /** "hoje", "amanhã", "sexta", "24 out", "há 3 dias" */
    fun short(d: LocalDate, t: LocalDate = today()): String {
        val diff = d.toEpochDay() - t.toEpochDay()
        return when {
            diff == 0L -> "hoje"
            diff == 1L -> "amanhã"
            diff == -1L -> "ontem"
            diff < 0 -> "há ${-diff} dias"
            diff < 7 -> weekday(d)
            else -> "${d.dayOfMonth} ${months[d.monthValue - 1]}"
        }
    }

    /** "sexta, 24 de outubro" */
    fun long(d: LocalDate, t: LocalDate = today()): String {
        val base = "${weekday(d)}, ${d.dayOfMonth} de ${monthsFull[d.monthValue - 1]}"
        val diff = d.toEpochDay() - t.toEpochDay()
        return when (diff) {
            0L -> "hoje ($base)"
            1L -> "amanhã ($base)"
            else -> base
        }
    }

    fun dayMonth(d: LocalDate) = "${d.dayOfMonth} ${months[d.monthValue - 1]}"

    // Atalhos de datas
    fun thisWeek(t: LocalDate = today()): LocalDate =
        if (t.dayOfWeek.value <= 5) t.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        else t.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    fun nextWeek(t: LocalDate = today()): LocalDate = t.with(TemporalAdjusters.next(DayOfWeek.MONDAY))

    fun thisMonth(t: LocalDate = today()): LocalDate =
        if (t.dayOfMonth < 25) t.withDayOfMonth(25) else t.with(TemporalAdjusters.lastDayOfMonth())

    fun defaultDue(c: Context): LocalDate = today().plusDays(Settings.defaultDays(c).toLong())

    data class Quick(val label: String, val date: LocalDate)

    fun quick(t: LocalDate = today()): List<Quick> = listOf(
        Quick("Hoje", t),
        Quick("Amanhã", t.plusDays(1)),
        Quick("Esta semana", thisWeek(t)),
        Quick("Próxima semana", nextWeek(t)),
        Quick("Este mês", thisMonth(t))
    ).distinctBy { it.date }

    // ---------- Perceber datas escritas no texto ----------

    data class Parsed(val title: String, val date: LocalDate?, val phrase: String?)

    private const val PRE = "(?:(?:até ao|até à|até|ate|para o|para a|para|pra|na|no|em)\\s+)?"
    private const val END = "(?=$|[\\s.,;!?])"

    private val numbers = mapOf(
        "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2, "três" to 3, "tres" to 3,
        "quatro" to 4, "cinco" to 5, "seis" to 6, "sete" to 7, "oito" to 8, "nove" to 9, "dez" to 10
    )

    private val dayNames = mapOf(
        "segunda" to DayOfWeek.MONDAY, "terça" to DayOfWeek.TUESDAY, "terca" to DayOfWeek.TUESDAY,
        "quarta" to DayOfWeek.WEDNESDAY, "quinta" to DayOfWeek.THURSDAY, "sexta" to DayOfWeek.FRIDAY,
        "sábado" to DayOfWeek.SATURDAY, "sabado" to DayOfWeek.SATURDAY, "domingo" to DayOfWeek.SUNDAY
    )

    private class Rule(pattern: String, val f: (MatchResult, LocalDate) -> LocalDate?) {
        val re = Regex("(?:^|\\s)$pattern$END", RegexOption.IGNORE_CASE)
    }

    private val rules = listOf(
        Rule("${PRE}depois de amanh[ãa]") { _, t -> t.plusDays(2) },
        Rule("${PRE}amanh[ãa]") { _, t -> t.plusDays(1) },
        Rule("${PRE}hoje") { _, t -> t },
        Rule("${PRE}esta semana") { _, t -> thisWeek(t) },
        Rule("${PRE}(?:pr[óo]xima semana|semana que vem)") { _, t -> nextWeek(t) },
        Rule("${PRE}(?:este m[êe]s|fim do m[êe]s|final do m[êe]s)") { _, t -> thisMonth(t) },
        Rule("(?:daqui a|dentro de|em)\\s+(\\d{1,3}|uma|um|duas|dois|tr[êe]s|quatro|cinco|seis|sete|oito|nove|dez)\\s+(dias?|semanas?)") { m, t ->
            val w = m.groupValues[1].lowercase()
            val n = w.toIntOrNull() ?: numbers[w]
            val mult = if (m.groupValues[2].lowercase().startsWith("semana")) 7 else 1
            if (n == null) null else t.plusDays((n * mult).toLong())
        },
        Rule("${PRE}dia\\s+(\\d{1,2})") { m, t ->
            val d = m.groupValues[1].toInt()
            var month = t.withDayOfMonth(1)
            if (d < t.dayOfMonth) month = month.plusMonths(1)
            when {
                d !in 1..31 -> null
                d > month.lengthOfMonth() -> month.with(TemporalAdjusters.lastDayOfMonth())
                else -> month.withDayOfMonth(d)
            }
        },
        Rule("${PRE}(?:pr[óo]xim[ao]\\s+)?(segunda|ter[çc]a|quarta|quinta|sexta|s[áa]bado|domingo)(?:-feira)?") { m, t ->
            val dow = dayNames[m.groupValues[1].lowercase()]
            if (dow == null) null else t.with(TemporalAdjusters.next(dow))
        }
    )

    fun parse(text: String, t: LocalDate = today()): Parsed {
        for (r in rules) {
            val m = r.re.find(text) ?: continue
            val d = r.f(m, t) ?: continue
            val title = (text.substring(0, m.range.first) + " " + text.substring(m.range.last + 1))
                .replace(Regex("\\s+"), " ")
                .trim()
                .trim(',', ';', '-', ' ')
            return Parsed(title.replaceFirstChar { it.uppercase() }, d, m.value.trim())
        }
        return Parsed(text.trim(), null, null)
    }
}
