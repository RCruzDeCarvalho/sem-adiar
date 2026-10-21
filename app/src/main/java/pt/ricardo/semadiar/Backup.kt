package pt.ricardo.semadiar

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cópia de segurança num ficheiro escolhido pela pessoa (ex.: no Google Drive),
 * pelo selector de ficheiros do Android. Não precisa de login nem de chaves.
 */
object Backup {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("backup", Context.MODE_PRIVATE)

    fun uri(c: Context): Uri? = prefs(c).getString("uri", null)?.let { Uri.parse(it) }
    fun lastTime(c: Context): Long = prefs(c).getLong("last", 0L)
    fun lastError(c: Context): String? = prefs(c).getString("error", null)

    private fun toJson(c: Context): String {
        val s = c.applicationContext.getSharedPreferences("definicoes", Context.MODE_PRIVATE)
        val settings = JSONObject()
        s.all.forEach { (k, v) -> if (v is Int) settings.put(k, v) }
        return JSONObject()
            .put("app", "sem-adiar")
            .put("version", 1)
            .put("exported", System.currentTimeMillis())
            .put("tasks", JSONArray(Store.raw(c)))
            .put("settings", settings)
            .put("extra", extras(c))
            .toString(2)
    }

    /** Medalhas e contadores, para não se perderem ao mudar de telemóvel. */
    private val extraPrefs = listOf("estatisticas", "conquistas")

    private fun extras(c: Context): JSONObject {
        val out = JSONObject()
        extraPrefs.forEach { name ->
            val o = JSONObject()
            c.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                when (v) {
                    is Int -> o.put(k, JSONObject().put("i", v))
                    is Set<*> -> o.put(k, JSONObject().put("set", JSONArray(v.map { it.toString() })))
                }
            }
            out.put(name, o)
        }
        return out
    }

    private fun restoreExtras(c: Context, o: JSONObject?) {
        if (o == null) return
        extraPrefs.forEach { name ->
            val p = o.optJSONObject(name) ?: return@forEach
            val ed = c.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            p.keys().forEach { k ->
                val v = p.getJSONObject(k)
                if (v.has("i")) ed.putInt(k, v.getInt("i"))
                if (v.has("set")) {
                    val a = v.getJSONArray("set")
                    ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                }
            }
            ed.commit()
        }
    }

    fun writeTo(c: Context, target: Uri): Boolean = try {
        c.contentResolver.openOutputStream(target, "wt")!!.use { it.write(toJson(c).toByteArray()) }
        prefs(c).edit().putLong("last", System.currentTimeMillis()).remove("error").commit()
        true
    } catch (e: Exception) {
        prefs(c).edit().putString("error", e.message ?: e.javaClass.simpleName).commit()
        false
    }

    fun setTarget(c: Context, target: Uri): Boolean {
        try {
            c.contentResolver.takePersistableUriPermission(
                target, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) { }
        prefs(c).edit().putString("uri", target.toString()).commit()
        return writeTo(c, target)
    }

    /** Uma cópia por dia, se houver ficheiro escolhido. */
    fun daily(c: Context) {
        val u = uri(c) ?: return
        if (System.currentTimeMillis() - lastTime(c) < 20 * 3_600_000L) return
        if (Store.tasks(c).isEmpty()) return
        writeTo(c, u)
    }

    /** Devolve o número de tarefas restauradas, ou null se o ficheiro não servir. */
    fun restore(c: Context, source: Uri): Int? {
        val text = try {
            c.contentResolver.openInputStream(source)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) { return null }
        val o = try { JSONObject(text) } catch (_: Exception) { return null }
        if (o.optString("app") != "sem-adiar") return null
        val tasks = o.optJSONArray("tasks") ?: return null
        Store.setRaw(c, tasks.toString())
        o.optJSONObject("settings")?.let { s ->
            val ed = c.applicationContext.getSharedPreferences("definicoes", Context.MODE_PRIVATE).edit()
            s.keys().forEach { k -> ed.putInt(k, s.optInt(k)) }
            ed.commit()
        }
        restoreExtras(c, o.optJSONObject("extra"))
        return Store.tasks(c).size
    }
}

/** Procura versões novas nas publicações do repositório (linha "versionCode=NN"). */
object Updater {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("actualizacoes", Context.MODE_PRIVATE)

    data class Update(val versionName: String, val versionCode: Int, val url: String)

    private val page get() = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest"

    fun pending(c: Context): Update? {
        val p = prefs(c)
        val code = p.getInt("code", 0)
        if (code <= BuildConfig.VERSION_CODE) return null
        return Update(p.getString("name", "") ?: "", code, p.getString("url", "") ?: "")
    }

    fun check(c: Context, force: Boolean = false): Update? {
        val p = prefs(c)
        if (!force && System.currentTimeMillis() - p.getLong("checked", 0L) < 6 * 3_600_000L) return pending(c)
        return try {
            val conn = (URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val body = o.optString("body")
            val code = Regex("versionCode=(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val name = Regex("versionName=([\\w.]+)").find(body)?.groupValues?.get(1) ?: o.optString("name")
            var url = ""
            o.optJSONArray("assets")?.let { a ->
                for (i in 0 until a.length()) {
                    val x = a.getJSONObject(i)
                    if (x.optString("name").endsWith(".apk")) url = x.optString("browser_download_url")
                }
            }
            p.edit().putLong("checked", System.currentTimeMillis())
                .putInt("code", code).putString("name", name).putString("url", url).apply()
            pending(c)
        } catch (_: Exception) {
            pending(c)
        }
    }

    fun download(c: Context, u: Update) {
        c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url.ifBlank { page })).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @SuppressLint("MissingPermission")
    fun notifyIfNew(c: Context) {
        val u = check(c) ?: return
        val p = prefs(c)
        if (p.getInt("notified", 0) >= u.versionCode || !Nagger.canNotify(c)) return
        val pi = PendingIntent.getActivity(
            c, 50, Intent(Intent.ACTION_VIEW, Uri.parse(u.url.ifBlank { page })),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        Nagger.channels(c)
        NotificationManagerCompat.from(c).notify(
            Nagger.ID_UPDATE,
            NotificationCompat.Builder(c, Nagger.CH)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("✨ Há uma versão nova do Sem Adiar")
                .setContentText("Toca para descarregar a versão ${u.versionName}")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        )
        p.edit().putInt("notified", u.versionCode).apply()
    }
}
