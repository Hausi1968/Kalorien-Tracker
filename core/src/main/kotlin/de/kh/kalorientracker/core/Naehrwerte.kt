package de.kh.kalorientracker.core

import kotlin.math.roundToInt

/** Nährwerte; im Katalog pro 100 g/ml, im Tagebuch für die gegessene Menge. null = keine Angabe. */
data class Naehrwerte(
    val kcal: Double,
    val fett: Double? = null,
    val gesFett: Double? = null,
    val kh: Double? = null,
    val zucker: Double? = null,
    val ballaststoffe: Double? = null,
    val eiweiss: Double? = null,
    val salz: Double? = null,
) {
    fun fuerGramm(gramm: Double): Naehrwerte {
        val f = gramm / 100.0
        return Naehrwerte(
            kcal = kcal * f,
            fett = fett?.times(f), gesFett = gesFett?.times(f),
            kh = kh?.times(f), zucker = zucker?.times(f),
            ballaststoffe = ballaststoffe?.times(f), eiweiss = eiweiss?.times(f),
            salz = salz?.times(f),
        )
    }

    operator fun plus(o: Naehrwerte) = Naehrwerte(
        kcal = kcal + o.kcal,
        fett = summe(fett, o.fett), gesFett = summe(gesFett, o.gesFett),
        kh = summe(kh, o.kh), zucker = summe(zucker, o.zucker),
        ballaststoffe = summe(ballaststoffe, o.ballaststoffe), eiweiss = summe(eiweiss, o.eiweiss),
        salz = summe(salz, o.salz),
    )

    /** Prüft, ob kcal zu den Makros passen (±15 % bzw. ±20 kcal). Alkohol wird nicht berücksichtigt. */
    fun plausibel(): Boolean {
        val f = fett ?: return true
        val k = kh ?: return true
        val e = eiweiss ?: return true
        val berechnet = 9 * f + 4 * k + 4 * e + 2 * (ballaststoffe ?: 0.0)
        val toleranz = maxOf(20.0, kcal * 0.15)
        return kotlin.math.abs(berechnet - kcal) <= toleranz
    }

    companion object {
        val NULL = Naehrwerte(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        private fun summe(a: Double?, b: Double?): Double? = if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)
        fun kj(kcal: Double): Int = (kcal * 4.184).roundToInt()
    }
}

/** Makro-Ziele in % der Kalorien, umgerechnet in Gramm (Fett 9 kcal/g, KH und Eiweiß 4 kcal/g). */
data class MakroAnteile(val fettProzent: Double, val khProzent: Double, val eiweissProzent: Double) {
    init {
        require(kotlin.math.abs(fettProzent + khProzent + eiweissProzent - 100.0) < 0.5) { "Makro-Anteile müssen 100 % ergeben" }
    }

    fun inGramm(kcal: Double) = MakroGramm(
        fett = (kcal * fettProzent / 100.0 / 9.0).roundToInt(),
        kh = (kcal * khProzent / 100.0 / 4.0).roundToInt(),
        eiweiss = (kcal * eiweissProzent / 100.0 / 4.0).roundToInt(),
    )

    companion object {
        /** Ausgewogene Standardverteilung nach üblichen Empfehlungen (30/50/20). */
        val STANDARD = MakroAnteile(30.0, 50.0, 20.0)
    }
}

data class MakroGramm(val fett: Int, val kh: Int, val eiweiss: Int)

/** Tagesbilanz: Ziel − gegessen + verbrannt = übrig. */
data class Tagesbilanz(val ziel: Int, val gegessen: Int, val verbrannt: Int) {
    val uebrig: Int get() = ziel - gegessen + verbrannt
    val zuViel: Boolean get() = uebrig < 0
    /** Anteil gegessen am verfügbaren Budget (Ziel + verbrannt), 0..n */
    val anteil: Float get() = if (ziel + verbrannt <= 0) 0f else gegessen.toFloat() / (ziel + verbrannt)
}
