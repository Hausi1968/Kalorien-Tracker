package de.kh.kalorientracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import de.kh.kalorientracker.KalorienApp
import de.kh.kalorientracker.MainActivity
import de.kh.kalorientracker.R
import de.kh.kalorientracker.core.Format
import de.kh.kalorientracker.core.Tagesbilanz
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

/** Startbildschirm-Widget: wie viele kcal heute noch übrig sind. Antippen öffnet die App. */
class RestWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val ergebnis = goAsync()
        val app = context.applicationContext as KalorienApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bilanz = runCatching { app.container.repo.tag(LocalDate.now()).first() }.getOrNull()
                    ?.takeIf { it.profil != null }?.bilanz
                zeichne(context, bilanz)
            } finally {
                ergebnis.finish()
            }
        }
    }

    companion object {
        /** Alle Widgets neu zeichnen; tut nichts, wenn keins auf dem Startbildschirm liegt. */
        fun zeichne(context: Context, bilanz: Tagesbilanz?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RestWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, ansicht(context, bilanz))
        }

        private fun ansicht(context: Context, b: Tagesbilanz?): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_rest)
            val oeffnen = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_flaeche, oeffnen)
            if (b == null) {
                v.setTextViewText(R.id.widget_titel, context.getString(R.string.widget_heute_uebrig))
                v.setTextViewText(R.id.widget_wert, "–")
                v.setTextViewText(R.id.widget_zeile, context.getString(R.string.widget_kein_ziel))
                return v
            }
            v.setTextViewText(
                R.id.widget_titel,
                context.getString(if (b.zuViel) R.string.widget_heute_zu_viel else R.string.widget_heute_uebrig),
            )
            v.setTextViewText(R.id.widget_wert, context.getString(R.string.einheit_kcal, Format.kcal(abs(b.uebrig))))
            v.setTextColor(
                R.id.widget_wert,
                context.getColor(if (b.zuViel) R.color.widget_warnung else R.color.widget_text),
            )
            v.setTextViewText(
                R.id.widget_zeile,
                context.getString(R.string.widget_zeile, Format.kcal(b.ziel), Format.kcal(b.gegessen)),
            )
            return v
        }
    }
}
