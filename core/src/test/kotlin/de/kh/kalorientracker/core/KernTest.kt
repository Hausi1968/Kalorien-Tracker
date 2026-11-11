package de.kh.kalorientracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class BedarfsrechnerTest {
    private val heute = LocalDate.of(2026, 11, 11)
    private val mann45 = Koerperdaten(Geschlecht.MANN, LocalDate.of(1981, 5, 1), 180.0, 92.5, 1.4)

    @Test fun grundumsatzMifflinMann() {
        // 10*92,5 + 6,25*180 - 5*45 + 5 = 1830
        assertEquals(1830.0, Bedarfsrechner.grundumsatz(mann45, heute), 0.001)
    }

    @Test fun grundumsatzMifflinFrau() {
        val frau = mann45.copy(geschlecht = Geschlecht.FRAU, gewichtKg = 70.0, groesseCm = 165.0)
        // 700 + 1031,25 - 225 - 161 = 1345,25
        assertEquals(1345.25, Bedarfsrechner.grundumsatz(frau, heute), 0.001)
    }

    @Test fun abnehmenHalbesKiloProWoche() {
        val r = Bedarfsrechner.berechne(mann45, Zielvorgabe(Ziel.ABNEHMEN, 90.0, 0.5), heute)
        assertEquals(1830, r.grundumsatz)
        assertEquals(2562, r.gesamtbedarf)
        assertEquals(2060, r.tagesziel) // 2562 - 500, auf 10 gerundet
        assertEquals(5, r.wochenBisZiel)
        assertEquals(LocalDate.of(2026, 12, 16), r.zieldatum)
        assertTrue(r.warnungen.isEmpty())
    }

    @Test fun zuAggressivWirdAufGrundumsatzAngehoben() {
        val sitzend = mann45.copy(pal = 1.2)
        val r = Bedarfsrechner.berechne(sitzend, Zielvorgabe(Ziel.ABNEHMEN, 80.0, 0.75), heute)
        // 1830*1,2 = 2196; -750 = 1446 < 1830 -> 1830
        assertEquals(1830, r.tagesziel)
        assertTrue(Warnung.UNTER_GRUNDUMSATZ in r.warnungen)
    }

    @Test fun minimumFrau() {
        val klein = Koerperdaten(Geschlecht.FRAU, LocalDate.of(1950, 1, 1), 150.0, 45.0, 1.2)
        val r = Bedarfsrechner.berechne(klein, Zielvorgabe(Ziel.ABNEHMEN, 42.0, 0.5), heute)
        assertTrue(r.tagesziel >= Bedarfsrechner.MINIMUM_FRAU)
        assertTrue(Warnung.UNTER_MINIMUM in r.warnungen)
    }

    @Test fun haltenOhneDefizit() {
        val r = Bedarfsrechner.berechne(mann45, Zielvorgabe(Ziel.HALTEN, null, 0.5), heute)
        assertEquals(0, r.defizit / 10)
        assertNull(r.zieldatum)
    }

    @Test fun zielgewichtUnpassend() {
        val r = Bedarfsrechner.berechne(mann45, Zielvorgabe(Ziel.ABNEHMEN, 95.0, 0.5), heute)
        assertTrue(Warnung.ZIELGEWICHT_UNPASSEND in r.warnungen)
        assertNull(r.zieldatum)
    }
}

class NaehrwerteTest {
    @Test fun makroProzentWieImScreenshot() {
        // 1.500 kcal, 30 / 52,9 / 17,1 % -> 50 / 198 / 64 g
        val g = MakroAnteile(30.0, 52.9, 17.1).inGramm(1500.0)
        assertEquals(50, g.fett)
        assertEquals(198, g.kh)
        assertEquals(64, g.eiweiss)
    }

    @Test fun portionUmrechnen() {
        val quark = Naehrwerte(kcal = 49.0, fett = 0.2, kh = 4.0, eiweiss = 7.4)
        val becher = quark.fuerGramm(400.0)
        assertEquals(196.0, becher.kcal, 0.001)
        assertEquals(29.6, becher.eiweiss!!, 0.001)
    }

    @Test fun summeMitFehlendenWerten() {
        val a = Naehrwerte(100.0, fett = 1.0)
        val b = Naehrwerte(50.0, fett = null, zucker = 2.0)
        val s = a + b
        assertEquals(150.0, s.kcal, 0.001)
        assertEquals(1.0, s.fett!!, 0.001)
        assertEquals(2.0, s.zucker!!, 0.001)
        assertNull(s.eiweiss)
    }

    @Test fun plausibilitaet() {
        assertTrue(Naehrwerte(130.0, fett = 0.3, kh = 28.2, eiweiss = 2.7, ballaststoffe = 0.4).plausibel())
        // kJ statt kcal eingetragen
        assertFalse(Naehrwerte(544.0, fett = 0.3, kh = 28.2, eiweiss = 2.7).plausibel())
    }

    @Test fun bilanz() {
        val b = Tagesbilanz(ziel = 1500, gegessen = 196, verbrannt = 46)
        assertEquals(1350, b.uebrig)
        assertFalse(b.zuViel)
        assertTrue(Tagesbilanz(1500, 1700, 0).zuViel)
    }

    @Test fun kilojoule() {
        assertEquals(6276, Naehrwerte.kj(1500.0))
    }
}

class MahlzeitenTest {
    @Test fun slotNachUhrzeit() {
        assertEquals("Frühstück", Mahlzeiten.slotFuer(LocalTime.of(7, 30)).name)
        assertEquals("Mittagessen", Mahlzeiten.slotFuer(LocalTime.of(12, 7)).name)
        assertEquals("Snack 2", Mahlzeiten.slotFuer(LocalTime.of(13, 46)).name)
        assertEquals("Snack 3", Mahlzeiten.slotFuer(LocalTime.of(23, 59)).name)
        // nach Mitternacht vor 4 Uhr: letzter Slot
        assertEquals("Snack 3", Mahlzeiten.slotFuer(LocalTime.of(1, 15)).name)
    }

    @Test fun fenstertext() {
        assertEquals("11:00–13:00", Mahlzeiten.fensterText(Mahlzeiten.STANDARD[2]))
        assertEquals("20:00–00:00", Mahlzeiten.fensterText(Mahlzeiten.STANDARD[5]))
    }
}

class SucheTest {
    @Test fun normalisieren() {
        assertEquals("kaese gouda", SuchText.normalisiere("Käse, Gouda!"))
        assertEquals("strasse", SuchText.normalisiere("Straße"))
        assertEquals("creme brulee", SuchText.normalisiere("Crème brûlée"))
    }

    @Test fun ftsAbfrage() {
        assertEquals("reis* gek*", SuchText.ftsAbfrage("Reis gek"))
        assertEquals("", SuchText.ftsAbfrage("r"))
    }

    @Test fun rankingZuletztGegessenZuerst() {
        val k = listOf(
            Ranking.Kandidat("a", "Reis (gekocht)", true, 10, null, false),
            Ranking.Kandidat("b", "Basmati Reis", false, 500, 2, false),
            Ranking.Kandidat("c", "Reiswaffeln", false, 900, null, true),
        )
        assertEquals(listOf("b", "c", "a"), Ranking.sortiere("reis", k).map { it.id })
    }
}

class FormatTest {
    @Test fun deutsch() {
        assertEquals("1.350", Format.kcal(1350))
        assertEquals("0,8", Format.gramm(0.8))
        assertEquals("30", Format.gramm(29.6))
        assertEquals("k. A.", Format.gramm(null))
        assertEquals(92.5, Format.parseDezimal("92,5")!!, 0.0)
    }
}
