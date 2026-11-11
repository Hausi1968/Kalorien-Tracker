package de.kh.kalorientracker.core

import java.time.LocalDate
import java.time.Period
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

enum class Geschlecht { MANN, FRAU }

enum class Ziel { ABNEHMEN, HALTEN, ZUNEHMEN }

/** Eingaben des Nutzers für den Bedarfsrechner. */
data class Koerperdaten(
    val geschlecht: Geschlecht,
    val geburtsdatum: LocalDate,
    val groesseCm: Double,
    val gewichtKg: Double,
    val pal: Double,
)

data class Zielvorgabe(
    val ziel: Ziel,
    val zielgewichtKg: Double?,
    /** kg pro Woche, z.B. 0.5; bei HALTEN ignoriert. */
    val tempoKgProWoche: Double,
)

enum class Warnung {
    /** Tagesziel wurde auf den Grundumsatz angehoben. */
    UNTER_GRUNDUMSATZ,
    /** Tagesziel wurde auf die Mindestmenge (1.200 / 1.500 kcal) angehoben. */
    UNTER_MINIMUM,
    /** Zielgewicht passt nicht zum Ziel (z.B. abnehmen, aber Zielgewicht höher). */
    ZIELGEWICHT_UNPASSEND,
}

data class Bedarfsergebnis(
    val grundumsatz: Int,
    val gesamtbedarf: Int,
    /** Positiv = Defizit, negativ = Überschuss. */
    val defizit: Int,
    val tagesziel: Int,
    val wochenBisZiel: Int?,
    val zieldatum: LocalDate?,
    val warnungen: Set<Warnung>,
)

/**
 * Rechnet Grundumsatz (Mifflin-St Jeor), Gesamtbedarf und Tagesziel.
 * 7.000 kcal entsprechen grob 1 kg Körperfett.
 */
object Bedarfsrechner {
    const val KCAL_PRO_KG = 7000.0
    const val MINIMUM_FRAU = 1200
    const val MINIMUM_MANN = 1500
    val PAL_STUFEN = listOf(1.2, 1.4, 1.6, 1.8, 2.0)
    val TEMPO_STUFEN = listOf(0.25, 0.5, 0.75)

    fun alter(geburtsdatum: LocalDate, heute: LocalDate): Int = Period.between(geburtsdatum, heute).years

    fun grundumsatz(k: Koerperdaten, heute: LocalDate): Double {
        val basis = 10.0 * k.gewichtKg + 6.25 * k.groesseCm - 5.0 * alter(k.geburtsdatum, heute)
        return basis + if (k.geschlecht == Geschlecht.MANN) 5.0 else -161.0
    }

    fun berechne(k: Koerperdaten, z: Zielvorgabe, heute: LocalDate): Bedarfsergebnis {
        require(k.groesseCm in 100.0..250.0) { "Größe außerhalb 100–250 cm" }
        require(k.gewichtKg in 30.0..350.0) { "Gewicht außerhalb 30–350 kg" }
        require(k.pal in 1.0..2.5) { "Aktivitätsfaktor außerhalb 1,0–2,5" }

        val bmr = grundumsatz(k, heute)
        val tdee = bmr * k.pal
        val tagesDifferenz = when (z.ziel) {
            Ziel.ABNEHMEN -> z.tempoKgProWoche * KCAL_PRO_KG / 7.0
            Ziel.ZUNEHMEN -> -z.tempoKgProWoche * KCAL_PRO_KG / 7.0
            Ziel.HALTEN -> 0.0
        }
        val warnungen = mutableSetOf<Warnung>()
        var ziel = tdee - tagesDifferenz
        if (z.ziel == Ziel.ABNEHMEN) {
            val minimum = if (k.geschlecht == Geschlecht.MANN) MINIMUM_MANN else MINIMUM_FRAU
            if (ziel < bmr) { ziel = bmr; warnungen += Warnung.UNTER_GRUNDUMSATZ }
            if (ziel < minimum) { ziel = minimum.toDouble(); warnungen += Warnung.UNTER_MINIMUM }
        }
        val tagesziel = rundeAuf10(ziel)
        val echtesDefizit = tdee - tagesziel

        var wochen: Int? = null
        var datum: LocalDate? = null
        val zg = z.zielgewichtKg
        if (zg != null && z.ziel != Ziel.HALTEN) {
            val differenz = if (z.ziel == Ziel.ABNEHMEN) k.gewichtKg - zg else zg - k.gewichtKg
            if (differenz <= 0.0) {
                warnungen += Warnung.ZIELGEWICHT_UNPASSEND
            } else {
                // Tatsächliches Tempo aus dem (evtl. angehobenen) Ziel
                val kgProWoche = kotlin.math.abs(echtesDefizit) * 7.0 / KCAL_PRO_KG
                if (kgProWoche > 0.0) {
                    wochen = ceil(differenz / kgProWoche).toInt()
                    datum = heute.plusWeeks(wochen.toLong())
                }
            }
        }
        return Bedarfsergebnis(
            grundumsatz = bmr.roundToInt(),
            gesamtbedarf = tdee.roundToInt(),
            defizit = echtesDefizit.roundToInt(),
            tagesziel = tagesziel,
            wochenBisZiel = wochen,
            zieldatum = datum,
            warnungen = warnungen,
        )
    }

    private fun rundeAuf10(x: Double): Int = (max(0.0, x) / 10.0).roundToInt() * 10
}
