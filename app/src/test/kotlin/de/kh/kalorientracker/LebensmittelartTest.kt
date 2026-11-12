package de.kh.kalorientracker

import de.kh.kalorientracker.data.Lebensmittelart
import de.kh.kalorientracker.data.Lebensmittelart.*
import de.kh.kalorientracker.data.lebensmittelart
import org.junit.Assert.assertEquals
import org.junit.Test

class LebensmittelartTest {
    private fun pruefe(name: String, art: Lebensmittelart?) = assertEquals(name, art, lebensmittelart(name))

    @Test
    fun artenAusDemNamen() {
        pruefe("Apfel", OBST)
        pruefe("Apfelsaft", GETRAENK)
        pruefe("Apfelkuchen", KUCHEN)
        pruefe("Käsekuchen", KUCHEN)
        pruefe("Gouda jung", MILCH)
        pruefe("Milchreis", REIS)
        pruefe("Reis gekocht", REIS)
        pruefe("Vanilleeis", EIS)
        pruefe("Eisbein", FLEISCH)
        pruefe("Weintrauben", OBST)
        pruefe("Rotwein trocken", ALKOHOL)
        pruefe("Schweineschnitzel", FLEISCH)
        pruefe("Erdnussbutter", NUESSE)
        pruefe("Wassermelone", OBST)
        pruefe("Ei gekocht", EI)
        pruefe("Hähnchenbrust gebraten", FLEISCH)
        pruefe("Brokkoli", GEMUESE)
        pruefe("Vollkornbrot", BROT)
        pruefe("Cappuccino", HEISSGETRAENK)
        pruefe("Olivenöl", OEL)
        pruefe("Lachsfilet", FISCH)
        pruefe("Spaghetti Bolognese", NUDELN)
        pruefe("Xyz", null)
        assertEquals(REZEPT, lebensmittelart("Omas Auflauf", "rezept"))
    }
}
