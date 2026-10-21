package pt.ricardo.semadiar

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

private data class WRow(val title: String, val sub: String, val late: Boolean)

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val tasks = Store.tasks(context)
        val pend = Focus.pending(tasks)
        val max = Settings.focusMax(context)
        val rows = pend.take(max).map { t ->
            val late = t.lateDays()
            WRow(
                title = t.title,
                sub = t.nextStep?.let { "→ $it" } ?: if (late > 0) "atrasada há $late d" else "",
                late = late > 0
            )
        }
        val more = (pend.size - rows.size).coerceAtLeast(0)
        val done = Focus.doneToday(tasks)
        val app = context.applicationContext
        provideContent { Content(app, rows, more, done) }
    }

    @Composable
    private fun Content(app: Context, rows: List<WRow>, more: Int, done: Int) {
        val bg = ColorProvider(day = Palette.Cream, night = Color(0xFF181A26))
        val ink = ColorProvider(day = Palette.Ink, night = Color(0xFFE7E8F0))
        val muted = ColorProvider(day = Color(0xFF5E6276), night = Color(0xFFB8BACB))
        val accent = ColorProvider(day = Palette.Indigo, night = Color(0xFFAAB6FF))
        val amber = ColorProvider(Palette.Amber, Palette.Amber)
        val late = ColorProvider(Palette.Late, Color(0xFFF08A84))

        val open = actionStartActivity(Intent(app, MainActivity::class.java))
        val add = actionStartActivity(
            Intent(app, MainActivity::class.java).setData(Uri.parse("semadiar://nova")).putExtra("capture", true)
        )

        Column(
            GlanceModifier.fillMaxSize().background(bg).cornerRadius(24.dp).padding(14.dp).clickable(open)
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Para hoje",
                    style = TextStyle(color = accent, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                    modifier = GlanceModifier.defaultWeight()
                )
                if (done > 0) Text("✓ $done  ", style = TextStyle(color = muted, fontSize = 12.sp))
                Box(
                    GlanceModifier.size(30.dp).cornerRadius(15.dp).background(accent).clickable(add),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", style = TextStyle(color = ColorProvider(Color.White, Color(0xFF14205A)), fontWeight = FontWeight.Bold, fontSize = 18.sp))
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            if (rows.isEmpty()) {
                Text(
                    if (done > 0) "Tudo feito por hoje 🎉" else "Nada para hoje 🌿",
                    style = TextStyle(color = ink, fontSize = 14.sp)
                )
            } else rows.forEachIndexed { i, r ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        GlanceModifier.size(8.dp).cornerRadius(4.dp)
                            .background(if (r.late) late else if (i == 0) amber else accent)
                    ) {}
                    Spacer(GlanceModifier.width(8.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(r.title, style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                        if (r.sub.isNotBlank()) Text(r.sub, style = TextStyle(color = if (r.late) late else muted, fontSize = 12.sp), maxLines = 1)
                    }
                }
            }
            if (more > 0) {
                Spacer(GlanceModifier.height(4.dp))
                Text("e mais $more", style = TextStyle(color = muted, fontSize = 12.sp))
            }
        }
    }

    companion object {
        suspend fun refresh(c: Context) {
            try { TodayWidget().updateAll(c) } catch (_: Exception) { }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
