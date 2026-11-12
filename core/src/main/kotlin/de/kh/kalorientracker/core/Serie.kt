package de.kh.kalorientracker.core

import java.time.LocalDate

/** Wie viele Tage in Folge etwas eingetragen wurde. */
object Serie {
    /**
     * Zählt rückwärts ab heute. Ist heute noch leer, zählt die Serie ab gestern weiter,
     * damit sie morgens nicht schon „gerissen“ aussieht.
     */
    fun laenge(tage: Set<LocalDate>, heute: LocalDate): Int {
        var d = if (heute in tage) heute else heute.minusDays(1)
        var n = 0
        while (d in tage) { n++; d = d.minusDays(1) }
        return n
    }
}
