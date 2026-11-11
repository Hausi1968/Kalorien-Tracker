package de.kh.kalorientracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Woher ein Wert zählt: nur App-Einträge, nur Health Connect (z. B. Samsung Health) oder beides. */
enum class Quelle { APP, HC, BEIDE }

data class QuellenWahl(val gewicht: Quelle = Quelle.APP, val verbrannt: Quelle = Quelle.APP) {
    val brauchtHealthConnect: Boolean get() = gewicht != Quelle.APP || verbrannt != Quelle.APP
}

/** Merkt sich die Wahl des Nutzers (auf dem Handy, in den App-Einstellungen). */
class Quellen(context: Context? = null) {
    private val prefs = context?.getSharedPreferences("quellen", Context.MODE_PRIVATE)
    private val _wahl = MutableStateFlow(
        QuellenWahl(
            gewicht = lies("gewicht"),
            verbrannt = lies("verbrannt"),
        ),
    )
    val wahl: StateFlow<QuellenWahl> = _wahl

    private fun lies(key: String) = prefs?.getString(key, null)?.let { runCatching { Quelle.valueOf(it) }.getOrNull() } ?: Quelle.APP

    fun setze(neu: QuellenWahl) {
        prefs?.edit()?.putString("gewicht", neu.gewicht.name)?.putString("verbrannt", neu.verbrannt.name)?.apply()
        _wahl.value = neu
    }
}
