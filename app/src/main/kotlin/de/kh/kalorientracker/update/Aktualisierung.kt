package de.kh.kalorientracker.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import de.kh.kalorientracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Eine neuere Version auf GitHub. [sha256] und [groesse] prüfen, ob die Datei vollständig angekommen ist. */
data class NeueVersion(
    val version: String,
    val notizen: String,
    val apkUrl: String,
    val sha256: String? = null,
    val groesse: Long? = null,
)

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
        val sha = (r["sha256"] as? JsonPrimitive)?.contentOrNull?.lowercase()
        val groesse = (r["groesse"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        val url = apkAdresse(apk) ?: return null
        return NeueVersion(version, notizen, url, sha, groesse)
    }

    /** Lädt die APK in den Cache und prüft sie. [fortschritt] bekommt 0..1. Abbrechbar über die Coroutine. */
    suspend fun lade(v: NeueVersion, fortschritt: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val ordner = File(context.cacheDir, "updates").apply { mkdirs() }
        ordner.listFiles()?.forEach { it.delete() }
        val ziel = File(ordner, "Kalorien-Tracker-${v.version}.apk")
        var c = verbinde(v.apkUrl)
        // GitHub leitet Downloads weiter; nur zu https-Adressen folgen
        if (c.responseCode in 300..399) {
            val ort = c.getHeaderField("Location")
            c.disconnect()
            if (ort == null || !ort.startsWith("https://")) throw IOException("Weiterleitung ungültig")
            c = verbinde(ort)
        }
        val pruefer = MessageDigest.getInstance("SHA-256")
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            val gesamt = c.contentLengthLong.takeIf { it > 0 } ?: v.groesse
            c.inputStream.use { ein ->
                ziel.outputStream().use { aus ->
                    val puffer = ByteArray(64 * 1024)
                    var gelesen = 0L
                    while (true) {
                        ensureActive()
                        val n = ein.read(puffer)
                        if (n < 0) break
                        aus.write(puffer, 0, n)
                        pruefer.update(puffer, 0, n)
                        gelesen += n
                        if (gesamt != null) fortschritt((gelesen.toFloat() / gesamt).coerceIn(0f, 1f))
                    }
                }
            }
        } catch (e: Throwable) {
            ziel.delete()
            throw e
        } finally {
            c.disconnect()
        }
        val fehler = pruefeDatei(ziel, v, pruefer.digest().joinToString("") { "%02x".format(it) })
        if (fehler != null) {
            ziel.delete()
            throw IOException(fehler)
        }
        ziel
    }

    /** null = Datei in Ordnung, sonst der Grund. */
    private fun pruefeDatei(datei: File, v: NeueVersion, sha: String): String? {
        if (v.groesse != null && datei.length() != v.groesse) return "Größe stimmt nicht"
        if (v.sha256 != null && v.sha256 != sha) return "Prüfsumme stimmt nicht"
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val neu = pm.getPackageArchiveInfo(datei.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return "Keine gültige App-Datei"
        if (neu.packageName != context.packageName) return "Falsche App"
        @Suppress("DEPRECATION")
        val alt = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val neueSig = neu.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        val alteSig = alt.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        if (neueSig.isEmpty() || neueSig != alteSig) return "Falscher Schlüssel"
        return null
    }

    private fun verbinde(adresse: String): HttpURLConnection =
        (URL(adresse).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "KalorienTracker-Android/${aktuelleVersion}")
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

        /** Nur Dateien aus dem eigenen Update-Zweig oder den eigenen GitHub-Downloads zulassen. */
        fun apkAdresse(apk: String): String? = when {
            !apk.contains("://") && !apk.contains("..") && apk.endsWith(".apk") -> "$QUELLE/$apk"
            apk.startsWith("$QUELLE/") || apk.startsWith("https://github.com/$REPO/") -> apk
            else -> null
        }
    }
}
