package de.kh.kalorientracker.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** Eine Meldung mit optionalem "Rückgängig", die auf der nächsten Seite (Tagebuch) angezeigt wird. */
class Meldung(val text: String, val rueckgaengig: (suspend () -> Unit)? = null)

/**
 * Briefkasten für Meldungen über Seitenwechsel hinweg: Die Detailseite trägt ein oder löscht,
 * springt zurück, und das Tagebuch zeigt "… gelöscht – Rückgängig".
 */
object Meldungen {
    val offen = MutableStateFlow<Meldung?>(null)
    fun zeige(m: Meldung) { offen.value = m }
}
