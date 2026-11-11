package de.kh.kalorientracker.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import de.kh.kalorientracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Eine neuere Version auf GitHub. */
data class NeueVersion(val version: String, val notizen: String, val apkUrl: String)

/**
 * Updates "aus der Luft": liest latest.json aus dem GitHub-Zweig "updates",
 * lädt die APK herunter und übergibt sie dem Android-Installer. Der Nutzer bestätigt immer selbst.
 * Updates passen nur, wenn sie mit demselben Schlüssel signiert sind wie die installierte App.
 */
class Aktualisierung(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences("aktualisierung", Context.MODE_PRIVATE)

    val aktuelleVersion: String get() = BuildConfig.VERSION_NAME

    /** Höchstens einmal am Tag automatisch prüfen; [immer] für den Knopf im Profil. */
    suspend fun pruefe(immer: Boolean = false): NeueVersion? {
        val jetzt = System.currentTimeMillis()
        if (!immer && jetzt - prefs.getLong("zuletzt", 0) < 20 * 60 * 60 * 1000L) return null
        val text = hole("$QUELLE/latest.json?t=${jetzt / 60_000}")
        prefs.edit().putLong("zuletzt", jetzt).apply()
        val r = json.parseToJsonElement(text).jsonObject
        val code = (r["versionCode"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: return null
        val version = (r["version"] as? JsonPrimitive)?.contentOrNull ?: return null
        if (code <= BuildConfig.VERSION_CODE) return null
        val apk = (r["apk"] as? JsonPrimitive)?.contentOrNull ?: return null
        val notizen = (r["notizen"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return NeueVersion(version, notizen, if (apk.startsWith("http")) apk else "$QUELLE/$apk")
    }

    /** Lädt die APK in den Cache. [fortschritt] bekommt 0..1. */
    suspend fun lade(v: NeueVersion, fortschritt: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val ordner = File(context.cacheDir, "updates").apply { mkdirs() }
        ordner.listFiles()?.forEach { it.delete() }
        val ziel = File(ordner, "Kalorien-Tracker-${v.version}.apk")
        var c = URL(v.apkUrl).openConnection() as HttpURLConnection
        // GitHub leitet Downloads weiter; HttpURLConnection folgt Weiterleitungen nur innerhalb von https
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "KalorienTracker-Android/${aktuelleVersion}")
        if (c.responseCode in 300..399) {
            val ort = c.getHeaderField("Location")
            c.disconnect()
            c = URL(ort).openConnection() as HttpURLConnection
        }
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            val gesamt = c.contentLengthLong.takeIf { it > 0 }
            c.inputStream.use { ein ->
                ziel.outputStream().use { aus ->
                    val puffer = ByteArray(64 * 1024)
                    var gelesen = 0L
                    while (true) {
                        val n = ein.read(puffer)
                        if (n < 0) break
                        aus.write(puffer, 0, n)
                        gelesen += n
                        if (gesamt != null) fortschritt((gelesen.toFloat() / gesamt).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        ziel
    }

    /** Darf die App Updates installieren? Sonst einmalig in den Einstellungen erlauben. */
    fun darfInstallieren(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun oeffneErlaubnis() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun installiere(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.dateien", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private suspend fun hole(adresse: String): String = withContext(Dispatchers.IO) {
        val c = URL(adresse).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "KalorienTracker-Android/${aktuelleVersion}")
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val REPO = "Hausi1968/Kalorien-Tracker"

        /** Update-Infos liegen im Zweig "updates" des Repositorys: latest.json + APK-Dateien. */
        const val QUELLE = "https://raw.githubusercontent.com/$REPO/updates"

        /** "0.10.0" > "0.9.2"; Zusätze wie "-beta" werden ignoriert. */
        fun istNeuer(kandidat: String, aktuell: String): Boolean {
            fun teile(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = teile(kandidat)
            val b = teile(aktuell)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
