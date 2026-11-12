package de.kh.kalorientracker.erinnerung

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.kh.kalorientracker.KalorienApp
import de.kh.kalorientracker.MainActivity
import de.kh.kalorientracker.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Wird vom Wecker-Dienst geweckt: Prüft, ob die Erinnerung noch nötig ist (Mahlzeit schon eingetragen?
 * Heute schon gewogen?), zeigt sie sonst an und plant die nächste für morgen. Nach einem Neustart des
 * Handys werden alle Erinnerungen neu eingeplant.
 */
class ErinnerungEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? KalorienApp ?: return
        val erinnerungen = app.container.erinnerungen
        if (intent.action != Erinnerungen.AKTION) { erinnerungen.planeAlle(); return }
        val art = intent.getStringExtra(Erinnerungen.EXTRA_ART)?.let { runCatching { Art.valueOf(it) }.getOrNull() } ?: return
        val warten = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val e = erinnerungen.erinnerung(art)
                if (e.an) {
                    val heute = LocalDate.now()
                    val noetig = runCatching {
                        if (art.slotId != null) !app.container.repo.hatEintrag(heute, art.slotId)
                        else !app.container.repo.heuteGewogen()
                    }.getOrDefault(true)
                    if (noetig) zeige(context, art)
                }
                erinnerungen.plane(e) // nächste für morgen
            } finally {
                warten.finish()
            }
        }
    }

    private fun zeige(context: Context, art: Art) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val (titel, text) = when (art) {
            Art.FRUEHSTUECK -> R.string.erinnerung_fruehstueck_titel to R.string.erinnerung_mahlzeit_text
            Art.MITTAG -> R.string.erinnerung_mittag_titel to R.string.erinnerung_mahlzeit_text
            Art.ABEND -> R.string.erinnerung_abend_titel to R.string.erinnerung_mahlzeit_text
            Art.WIEGEN -> R.string.erinnerung_wiegen_titel to R.string.erinnerung_wiegen_text
        }
        val oeffnen = PendingIntent.getActivity(
            context, 100 + art.ordinal,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, Erinnerungen.KANAL)
            .setSmallIcon(R.drawable.ic_erinnerung)
            .setContentTitle(context.getString(titel))
            .setContentText(context.getString(text))
            .setContentIntent(oeffnen)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(200 + art.ordinal, n) }
    }
}
