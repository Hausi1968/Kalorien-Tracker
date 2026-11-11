package de.kh.kalorientracker.core

/**
 * Vitamine, Mineralstoffe und weitere Angaben, die nicht jedes Produkt hat.
 * Werte immer in Gramm (wie bei Open Food Facts), Anzeige wählt mg oder µg.
 * Referenzmengen (NRV) nach EU-Lebensmittelinformationsverordnung, Anhang XIII.
 */
object Mikros {
    enum class Gruppe { WEITERE, VITAMINE, MINERALSTOFFE }

    /** [schluessel] wie bei Open Food Facts ("vitamin-c" -> nutriments["vitamin-c_100g"]). */
    data class Stoff(val schluessel: String, val name: String, val gruppe: Gruppe, val referenzGramm: Double?)

    val ALLE = listOf(
        Stoff("cholesterol", "Cholesterin", Gruppe.WEITERE, null),
        Stoff("alcohol", "Alkohol", Gruppe.WEITERE, null),
        Stoff("vitamin-a", "Vitamin A", Gruppe.VITAMINE, 800e-6),
        Stoff("vitamin-b1", "Vitamin B1", Gruppe.VITAMINE, 1.1e-3),
        Stoff("vitamin-b2", "Vitamin B2", Gruppe.VITAMINE, 1.4e-3),
        Stoff("vitamin-pp", "Niacin (B3)", Gruppe.VITAMINE, 16e-3),
        Stoff("pantothenic-acid", "Pantothensäure (B5)", Gruppe.VITAMINE, 6e-3),
        Stoff("vitamin-b6", "Vitamin B6", Gruppe.VITAMINE, 1.4e-3),
        Stoff("biotin", "Biotin (B7)", Gruppe.VITAMINE, 50e-6),
        Stoff("vitamin-b9", "Folsäure (B9)", Gruppe.VITAMINE, 200e-6),
        Stoff("vitamin-b12", "Vitamin B12", Gruppe.VITAMINE, 2.5e-6),
        Stoff("vitamin-c", "Vitamin C", Gruppe.VITAMINE, 80e-3),
        Stoff("vitamin-d", "Vitamin D", Gruppe.VITAMINE, 5e-6),
        Stoff("vitamin-e", "Vitamin E", Gruppe.VITAMINE, 12e-3),
        Stoff("vitamin-k", "Vitamin K", Gruppe.VITAMINE, 75e-6),
        Stoff("calcium", "Calcium", Gruppe.MINERALSTOFFE, 800e-3),
        Stoff("chloride", "Chlorid", Gruppe.MINERALSTOFFE, 800e-3),
        Stoff("chromium", "Chrom", Gruppe.MINERALSTOFFE, 40e-6),
        Stoff("iron", "Eisen", Gruppe.MINERALSTOFFE, 14e-3),
        Stoff("fluoride", "Fluorid", Gruppe.MINERALSTOFFE, 3.5e-3),
        Stoff("iodine", "Jod", Gruppe.MINERALSTOFFE, 150e-6),
        Stoff("potassium", "Kalium", Gruppe.MINERALSTOFFE, 2000e-3),
        Stoff("copper", "Kupfer", Gruppe.MINERALSTOFFE, 1e-3),
        Stoff("magnesium", "Magnesium", Gruppe.MINERALSTOFFE, 375e-3),
        Stoff("manganese", "Mangan", Gruppe.MINERALSTOFFE, 2e-3),
        Stoff("molybdenum", "Molybdän", Gruppe.MINERALSTOFFE, 50e-6),
        Stoff("phosphorus", "Phosphor", Gruppe.MINERALSTOFFE, 700e-3),
        Stoff("selenium", "Selen", Gruppe.MINERALSTOFFE, 55e-6),
        Stoff("zinc", "Zink", Gruppe.MINERALSTOFFE, 10e-3),
    )
    private val NACH_SCHLUESSEL = ALLE.associateBy { it.schluessel }
    fun stoff(schluessel: String): Stoff? = NACH_SCHLUESSEL[schluessel]

    /** Kompakt speichern: "vitamin-c=0.0046;iron=0.0002". */
    fun kodiere(werte: Map<String, Double>): String? =
        werte.filter { it.key in NACH_SCHLUESSEL && it.value >= 0 }
            .takeIf { it.isNotEmpty() }
            ?.entries?.joinToString(";") { "${it.key}=${it.value}" }

    fun lese(text: String?): Map<String, Double> =
        text.orEmpty().split(';').mapNotNull { teil ->
            val (k, v) = teil.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            v.toDoubleOrNull()?.let { k to it }
        }.toMap()

    fun mal(werte: Map<String, Double>, faktor: Double): Map<String, Double> = werte.mapValues { it.value * faktor }

    fun summe(liste: List<Map<String, Double>>): Map<String, Double> =
        liste.flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }

    /** Lesbare Menge: 0,0046 g -> "4,6 mg", 0,00000085 g -> "0,85 µg". Alkohol in g. */
    fun anzeige(gramm: Double): String = when {
        gramm == 0.0 -> "0 g"
        gramm >= 1.0 -> Format.dezimal(gramm, 1) + " g"
        gramm >= 1e-3 -> Format.dezimal(gramm * 1e3, 1) + " mg"
        else -> Format.dezimal(gramm * 1e6, 1) + " µg"
    }

    /** Anteil an der Referenzmenge in Prozent, null ohne Referenz. */
    fun prozent(stoff: Stoff, gramm: Double): Int? = stoff.referenzGramm?.let { Math.round(gramm / it * 100).toInt() }
}
