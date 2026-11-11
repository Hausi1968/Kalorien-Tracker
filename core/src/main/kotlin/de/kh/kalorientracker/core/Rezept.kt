package de.kh.kalorientracker.core

/** Eine Zutat: Nährwerte pro 100 g/ml und die verwendete Menge in Gramm. */
data class RezeptZutat(val pro100: Naehrwerte, val gramm: Double)

/** Ergebnis: Summe des ganzen Rezepts, Werte pro 100 g und pro Portion. */
data class RezeptWerte(
    val gesamt: Naehrwerte,
    val gesamtGramm: Double,
    val pro100: Naehrwerte,
    val portionGramm: Double,
    val proPortion: Naehrwerte,
)

object RezeptRechner {
    /**
     * Rechnet die Zutaten zusammen. gesamtGramm kann vorgegeben werden (z. B. gewogenes Gewicht nach dem
     * Kochen, wenn Wasser verdampft ist); sonst ist es die Summe der Zutaten.
     */
    fun berechne(zutaten: List<RezeptZutat>, portionen: Int, gesamtGramm: Double? = null): RezeptWerte? {
        if (zutaten.isEmpty() || portionen < 1) return null
        val summe = zutaten.map { it.pro100.fuerGramm(it.gramm) }.reduce(Naehrwerte::plus)
        val gewicht = gesamtGramm?.takeIf { it > 0 } ?: zutaten.sumOf { it.gramm }
        if (gewicht <= 0) return null
        val pro100 = summe.fuerGramm(100.0 * 100.0 / gewicht)
        val portion = gewicht / portionen
        return RezeptWerte(summe, gewicht, pro100, portion, pro100.fuerGramm(portion))
    }
}
