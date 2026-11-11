package de.kh.kalorientracker.core

import java.text.Normalizer

/** Normalisiert Text für die Volltextsuche: klein, Umlaute ausgeschrieben, Sonderzeichen weg. */
object SuchText {
    fun normalisiere(s: String): String {
        val lower = s.lowercase()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        val ohneAkzente = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return ohneAkzente.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    /** Baut eine FTS-MATCH-Abfrage mit Präfixsuche je Wort: "reis gek" -> "reis* gek*". Leer, wenn zu kurz. */
    fun ftsAbfrage(eingabe: String): String {
        val woerter = normalisiere(eingabe).split(' ').filter { it.length >= 2 }
        return woerter.joinToString(" ") { "$it*" }
    }
}

/** Ranking der Treffer: zuletzt gegessen > Favorit > Name beginnt mit Suchwort > Grundnahrungsmittel > Beliebtheit. */
object Ranking {
    data class Kandidat(
        val id: String,
        val name: String,
        val istGrundnahrungsmittel: Boolean,
        val beliebtheit: Int,
        val zuletztGegessenVorTagen: Int?,
        val istFavorit: Boolean,
    )

    fun sortiere(eingabe: String, kandidaten: List<Kandidat>): List<Kandidat> {
        val erstesWort = SuchText.normalisiere(eingabe).substringBefore(' ')
        return kandidaten.sortedWith(
            compareByDescending<Kandidat> { k -> k.zuletztGegessenVorTagen?.let { 1000 - it.coerceAtMost(999) } ?: 0 }
                .thenByDescending { it.istFavorit }
                .thenByDescending { SuchText.normalisiere(it.name).startsWith(erstesWort) }
                .thenByDescending { it.istGrundnahrungsmittel }
                .thenByDescending { it.beliebtheit }
                .thenBy { it.name.length },
        )
    }
}
