package de.kh.kalorientracker.erinnerung

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Arten von Erinnerungen. [slotId] = Mahlzeit, die schon eingetragen sein darf (dann keine Erinnerung). */
enum class Art(val standard: LocalTime, val slotId: Int?) {
    FRUEHSTUECK(LocalTime.of(9, 30), 1),
    MITTAG(LocalTime.of(13, 30), 3),
    ABEND(LocalTime.of(19, 30), 5),
    WIEGEN(LocalTime.of(7, 30), null),
}

data class Erinnerung(val art: Art, val an: Boolean, val zeit: LocalTime)

/**
 * Merkt sich die Erinnerungen (in den App-Einstellungen) und plant sie beim Wecker-Dienst von Android ein.
 * Die Zeiten sind ungefähr (Android bündelt Wecker, um Akku zu sparen), dafür braucht die App keine Sonderrechte.
 */
class Erinnerungen(private val context: Context) {
    private val prefs = context.getSharedPreferences("erinnerungen", Context.MODE_PRIVATE)
    private val _liste = MutableStateFlow(lies())
    val liste: StateFlow<List<Erinnerung>> = _liste

    private fun lies() = Art.entries.map { a ->
        Erinnerung(
            a,
            prefs.getBoolean("${a.name}_an", false),
            prefs.getString("${a.name}_zeit", null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: a.standard,
        )
    }

    fun setze(e: Erinnerung) {
        prefs.edit().putBoolean("${e.art.name}_an", e.an).putString("${e.art.name}_zeit", e.zeit.toString()).apply()
        _liste.value = lies()
        plane(e)
    }

    fun erinnerung(art: Art): Erinnerung = _liste.value.first { it.art == art }

    /** Alle eingeschalteten Erinnerungen neu einplanen (App-Start, Neustart des Handys, nach dem Auslösen). */
    fun planeAlle() = _liste.value.forEach { plane(it) }

    fun plane(e: Erinnerung) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(e.art)
        am.cancel(pi)
        if (!e.an) return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, naechsterZeitpunkt(e.zeit), pi)
    }

    private fun pendingIntent(art: Art): PendingIntent = PendingIntent.getBroadcast(
        context, art.ordinal,
        Intent(context, ErinnerungEmpfaenger::class.java).setAction(AKTION).putExtra(EXTRA_ART, art.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun kanalAnlegen() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(KANAL, context.getString(de.kh.kalorientracker.R.string.erinnerung_kanal), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    companion object {
        const val AKTION = "de.kh.kalorientracker.ERINNERUNG"
        const val EXTRA_ART = "art"
        const val KANAL = "erinnerungen"

        /** Nächster Zeitpunkt für die Uhrzeit: heute, wenn sie noch kommt, sonst morgen. */
        fun naechsterZeitpunkt(zeit: LocalTime, jetzt: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): Long {
            var t = LocalDate.from(jetzt).atTime(zeit)
            if (!t.isAfter(jetzt.plusSeconds(30))) t = t.plusDays(1)
            return t.atZone(zone).toInstant().toEpochMilli()
        }
    }
}
