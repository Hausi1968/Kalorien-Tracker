package de.kh.kalorientracker.core

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Deutsche Zahlenformate: 1.350 kcal, 0,8 g. */
object Format {
    private val de = Locale.GERMANY

    fun kcal(x: Double): String = NumberFormat.getIntegerInstance(de).format(x.roundToInt())
    fun kcal(x: Int): String = NumberFormat.getIntegerInstance(de).format(x)

    /** Gramm: unter 10 mit einer Nachkommastelle, sonst ganzzahlig; null = "k. A." */
    fun gramm(x: Double?): String {
        if (x == null) return "k. A."
        val nf = NumberFormat.getNumberInstance(de)
        nf.maximumFractionDigits = if (abs(x) < 10) 1 else 0
        nf.minimumFractionDigits = 0
        return nf.format(x)
    }

    fun dezimal(x: Double, stellen: Int = 1): String {
        val nf = NumberFormat.getNumberInstance(de)
        nf.maximumFractionDigits = stellen
        nf.minimumFractionDigits = 0
        return nf.format(x)
    }

    /** Liest deutsche oder englische Dezimalzahlen ("92,5" oder "92.5"). */
    fun parseDezimal(s: String): Double? = s.trim().replace(" ", "").replace(',', '.').toDoubleOrNull()
}
