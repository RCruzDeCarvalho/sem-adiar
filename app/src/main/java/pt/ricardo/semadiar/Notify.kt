package pt.ricardo.semadiar

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Corre de hora a hora: resumo da manhã, lembretes ao longo do dia, cópia e actualizações. */
class NagWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        Nagger.run(applicationContext)
        Backup.daily(applicationContext)
        Updater.notifyIfNew(applicationContext)
        Achievements.notifyNew(applicationContext)
        kotlinx.coroutines.runBlocking { TodayWidget.refresh(applicationContext) }
        return Result.success()
    }

    companion object {
        fun schedule(c: Context) {
            val req = PeriodicWorkRequestBuilder<NagWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("lembretes", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

object Nagger {
    const val CH = "lembretes"
    const val CH_TIMER = "temporizador"
    private const val ID_MORNING = 1
    private const val ID_NAG = 2
    const val ID_TIMER = 3
    const val ID_UPDATE = 5

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Lembretes de tarefas", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_TIMER, "Fim dos 10 minutos", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun state(c: Context) = c.applicationContext.getSharedPreferences("lembretes_estado", Context.MODE_PRIVATE)

    fun canNotify(c: Context) =
        Build.VERSION.SDK_INT < 33 || granted(c, Manifest.permission.POST_NOTIFICATIONS)

    /** Intervalo entre lembretes, em minutos, conforme a intensidade escolhida. */
    fun gapMinutes(mode: Int) = when (mode) { 0 -> 0; 1 -> 180; else -> 60 }

    fun run(c: Context, now: LocalDateTime = LocalDateTime.now()) {
        if (!canNotify(c)) return
        val h = now.hour
        if (h < Settings.startHour(c) || h >= Settings.endHour(c)) return
        val pend = Focus.pending(Store.tasks(c))
        val st = state(c)
        val day = now.toLocalDate().toString()
        val ms = System.currentTimeMillis()

        // Primeira vez no dia: o resumo da manhã
        if (st.getString("manha", "") != day) {
            st.edit().putString("manha", day).putLong("ultimo", ms).apply()
            if (pend.isNotEmpty()) morning(c, pend)
            return
        }
        if (pend.isEmpty()) return

        when (val mode = Settings.mode(c)) {
            0 -> if (h >= 18 && st.getString("tarde", "") != day) {
                st.edit().putString("tarde", day).putLong("ultimo", ms).apply()
                nag(c, pend.first(), pend.size)
            }
            else -> {
                val last = st.getLong("ultimo", 0L)
                if (ms - last >= (gapMinutes(mode) - 10) * 60_000L) {
                    st.edit().putLong("ultimo", ms).apply()
                    nag(c, pend.first(), pend.size)
                }
            }
        }
    }

    private fun openApp(c: Context, code: Int, extra: Pair<String, String>? = null): PendingIntent {
        val i = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (extra != null) i.putExtra(extra.first, extra.second)
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun broadcast(c: Context, code: Int, action: String, id: String): PendingIntent =
        PendingIntent.getBroadcast(
            c, code,
            Intent(c, ActionReceiver::class.java).setAction(action).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    @SuppressLint("MissingPermission")
    fun morning(c: Context, pend: List<Task>) {
        val max = Settings.focusMax(c)
        val top = pend.take(max)
        val more = pend.size - top.size
        val lines = top.joinToString("\n") { "• " + it.title }
        val title = if (pend.size == 1) "☀️ Hoje há só uma coisa" else "☀️ Para hoje: ${pend.size} coisas"
        val text = top.joinToString(" · ") { it.title } + if (more > 0) " e mais $more" else ""
        channels(c)
        NotificationManagerCompat.from(c).notify(
            ID_MORNING,
            NotificationCompat.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFF3D4FA8.toInt())
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines + if (more > 0) "\n… e mais $more" else ""))
                .setContentIntent(openApp(c, 1))
                .setAutoCancel(true)
                .build()
        )
    }

    @SuppressLint("MissingPermission")
    fun nag(c: Context, t: Task, total: Int) {
        val step = t.nextStep
        val late = t.lateDays()
        val text = when {
            step != null -> "Próximo passo: $step"
            else -> "Que tal só 10 minutos? Começar é o mais difícil."
        }
        val sub = when {
            late > 0 -> "atrasada há $late ${if (late == 1) "dia" else "dias"}"
            total > 1 -> "e mais ${total - 1} para hoje"
            else -> null
        }
        val b = NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFF3D4FA8.toInt())
            .setContentTitle(t.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(c, 20, "open" to t.id))
            .setAutoCancel(true)
            .addAction(0, "✓ Feito", broadcast(c, 21, ActionReceiver.DONE, t.id))
            .addAction(0, "▶ 10 min", openApp(c, 22, "start" to t.id))
        if (sub != null) b.setSubText(sub)
        // À terceira, adiar abre a app para decidir o que fazer
        if (t.postpones >= 2) b.addAction(0, "Adiar…", openApp(c, 23, "stuck" to t.id))
        else b.addAction(0, "Amanhã", broadcast(c, 24, ActionReceiver.TOMORROW, t.id))
        channels(c)
        NotificationManagerCompat.from(c).notify(ID_NAG, b.build())
    }

    fun cancelNag(c: Context) {
        NotificationManagerCompat.from(c).cancel(ID_NAG)
    }

    @SuppressLint("MissingPermission")
    fun timerEnd(c: Context, id: String) {
        if (!canNotify(c)) return
        val t = Store.get(c, id) ?: return
        channels(c)
        NotificationManagerCompat.from(c).notify(
            ID_TIMER,
            NotificationCompat.Builder(c, CH_TIMER)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFF3D4FA8.toInt())
                .setContentTitle("⏰ 10 minutos! Boa!")
                .setContentText("${t.title} · continuas ou ficas por aqui?")
                .setContentIntent(openApp(c, 30))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        )
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        when (i.action) {
            DONE -> Actions.complete(c, id)
            TOMORROW -> Actions.postpone(c, id, today().plusDays(1))
        }
        Nagger.cancelNag(c)
    }

    companion object {
        const val DONE = "pt.ricardo.semadiar.FEITO"
        const val TOMORROW = "pt.ricardo.semadiar.AMANHA"
    }
}

/** O bloco de 10 minutos. Guarda o fim para sobreviver a fechar a app. */
object Timer {
    data class State(val id: String, val start: Long, val end: Long)

    private fun p(c: Context) = c.applicationContext.getSharedPreferences("temporizador", Context.MODE_PRIVATE)

    fun state(c: Context): State? {
        val id = p(c).getString("id", null) ?: return null
        return State(id, p(c).getLong("inicio", 0L), p(c).getLong("fim", 0L))
    }

    fun start(c: Context, id: String, minutes: Int = 10): State {
        val now = System.currentTimeMillis()
        val prev = state(c)
        // Conta um bloco novo (recomeçar um que ainda está a correr não conta)
        if (prev == null || prev.id != id || prev.end <= now) { Stats.inc(c, "blocos"); Stats.addDay(c) }
        // Se já estava a contar nesta tarefa, "mais 10" continua a partir de agora
        val begin = if (prev != null && prev.id == id) prev.start else now
        val end = now + minutes * 60_000L
        p(c).edit().putString("id", id).putLong("inicio", begin).putLong("fim", end).commit()
        val req = OneTimeWorkRequestBuilder<TimerWorker>()
            .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf("id" to id, "fim" to end))
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork("temporizador", ExistingWorkPolicy.REPLACE, req)
        Nagger.cancelNag(c)
        return State(id, begin, end)
    }

    /** Pára e devolve os minutos trabalhados. */
    fun stop(c: Context): Int {
        val s = state(c) ?: return 0
        WorkManager.getInstance(c).cancelUniqueWork("temporizador")
        NotificationManagerCompat.from(c).cancel(Nagger.ID_TIMER)
        p(c).edit().clear().commit()
        val until = minOf(System.currentTimeMillis(), s.end)
        val m = ((until - s.start) / 60_000L).toInt().coerceAtLeast(1)
        Actions.addMinutes(c, s.id, m)
        return m
    }
}

class TimerWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.success()
        val s = Timer.state(applicationContext)
        if (s != null && s.id == id && s.end == inputData.getLong("fim", 0L)) Nagger.timerEnd(applicationContext, id)
        return Result.success()
    }
}
