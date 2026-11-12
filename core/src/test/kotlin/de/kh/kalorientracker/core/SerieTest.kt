package de.kh.kalorientracker.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SerieTest {
    private val heute = LocalDate.of(2026, 11, 12)
    private fun tage(vararg minus: Long) = minus.map { heute.minusDays(it) }.toSet()

    @Test fun zaehltAbHeute() = assertEquals(3, Serie.laenge(tage(0, 1, 2, 4), heute))
    @Test fun heuteNochLeer_zaehltAbGestern() = assertEquals(2, Serie.laenge(tage(1, 2), heute))
    @Test fun gerissen() = assertEquals(0, Serie.laenge(tage(2, 3), heute))
}
