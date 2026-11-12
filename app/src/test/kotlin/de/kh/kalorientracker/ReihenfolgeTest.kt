package de.kh.kalorientracker

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.kh.kalorientracker.ui.Reihenfolge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReihenfolgeTest {
    private val app = ApplicationProvider.getApplicationContext<KalorienApp>()

    @After fun zurueck() = Reihenfolge.setze(false)

    @Test fun abendOben_drehtDieMahlzeitenUm_undBleibtGemerkt() {
        Reihenfolge.lade(app)
        assertEquals("Frühstück", Reihenfolge.sortiert(Reihenfolge.abendOben.value).first().name)
        Reihenfolge.setze(true)
        val liste = Reihenfolge.sortiert(Reihenfolge.abendOben.value)
        assertEquals("Snack 3", liste.first().name)
        assertEquals("Abendessen", liste[1].name)
        assertEquals("Frühstück", liste.last().name)
        Reihenfolge.lade(app) // wie nach einem Neustart
        assertTrue(Reihenfolge.abendOben.value)
    }
}
