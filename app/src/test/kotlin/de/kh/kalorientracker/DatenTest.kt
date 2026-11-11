package de.kh.kalorientracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.kh.kalorientracker.data.AppDatabase
import de.kh.kalorientracker.data.FoodEntity
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.data.OffTreffer
import de.kh.kalorientracker.data.OpenFoodFacts
import de.kh.kalorientracker.data.ProfileEntity
import de.kh.kalorientracker.data.ServingEntity
import de.kh.kalorientracker.data.Sicherung
import de.kh.kalorientracker.data.StartdatenImport
import de.kh.kalorientracker.core.SuchText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** Datenlogik ohne Bildschirm: Suche, Portionen, Sicherung. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DatenTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun neueDb() = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()

    private fun food(id: String, name: String, beliebtheit: Int = 0, source: String = "off") = FoodEntity(
        id = id, source = source, barcode = id.removePrefix("off:").takeIf { source == "off" }, name = name,
        nameNorm = SuchText.normalisiere(name), brand = null, unitBase = "g", kcal = 50.0, fett = 1.0, gesFett = null,
        kh = 5.0, zucker = null, ballaststoffe = null, eiweiss = 3.0, salz = null, beliebtheit = beliebtheit,
    )

    private val profil = ProfileEntity(
        geschlecht = "MANN", geburtsdatum = "1981-05-01", groesseCm = 180.0, pal = 1.4, ziel = "ABNEHMEN",
        zielgewichtKg = 85.0, tempoKgProWoche = 0.5, startGewichtKg = 92.5, startDatum = "2026-11-11",
        tageszielKcal = 2060, fettProzent = 30.0, khProzent = 50.0, eiweissProzent = 20.0, updatedAt = 0,
    )

    @Test
    fun sucheMitZweiWoertern_findetAuchSelteneTreffer() = runBlocking {
        val db = neueDb()
        // 150 beliebte "Milch …"-Produkte drängen den einen Hafer-Treffer aus den ersten 120
        db.food().fuegeEin((1..150).map { food("off:${1000 + it}", "Milch Sorte $it", beliebtheit = 1000) })
        db.food().fuegeEin(listOf(food("off:42", "Haferdrink Milch Alternative", beliebtheit = 1)))
        val treffer = KalorienRepository(db).suche("milch hafer")
        assertEquals(listOf("off:42"), treffer.map { it.id })
    }

    @Test
    fun onlineSuche_behaeltEigenePortionUndBeliebtheit() = runBlocking {
        val db = neueDb()
        val off = object : OpenFoodFacts() {
            override suspend fun suche(eingabe: String) = listOf(
                OffTreffer(food("off:4001", "Orangensaft", beliebtheit = 0), listOf(ServingEntity(foodId = "off:4001", label = "100 g", gramm = 100.0))),
            )
        }
        val repo = KalorienRepository(db, off)
        db.food().fuegeEin(listOf(food("off:4001", "Orangensaft", beliebtheit = 777)))
        db.food().fuegeServingsEin(listOf(ServingEntity(foodId = "off:4001", label = "100 g", gramm = 100.0)))
        repo.neuePortion("off:4001", "Glas 250 ml", 250.0)

        repo.onlineSuche("orangensaft")

        val portionen = repo.portionen("off:4001").map { it.label }.sorted()
        assertEquals(listOf("100 g", "Glas 250 ml"), portionen)
        assertEquals(777, repo.lebensmittel("off:4001")!!.beliebtheit)
    }

    @Test
    fun sicherung_exportierenUndAufNeuemHandyEinlesen() = runBlocking {
        val alt = neueDb()
        StartdatenImport(ctx, alt).fallsLeer()
        alt.profile().speichere(profil)
        val repoAlt = KalorienRepository(alt)
        val eigenes = food("user:1", "Omas Kuchen", source = "user")
        repoAlt.legeProduktAn(eigenes, "Stück" to 120.0)
        repoAlt.eintragen(eigenes, "Stück", 120.0, 2.0, LocalDateTime.of(2026, 11, 10, 15, 0), 4)
        repoAlt.speichereGewicht(91.0, LocalDate.of(2026, 11, 10))
        repoAlt.setzeFavorit("user:1", true)

        val datei = ByteArrayOutputStream()
        Sicherung(ctx, alt).exportiere(datei)

        val neu = neueDb()
        StartdatenImport(ctx, neu).fallsLeer()
        val daten = Sicherung(ctx, neu).importiere(ByteArrayInputStream(datei.toByteArray()))
        assertEquals(1, daten.eintraege.size)

        val repoNeu = KalorienRepository(neu)
        assertNotNull(neu.profile().lade())
        assertEquals(1, neu.diary().eintraegeAm("2026-11-10").first().size)
        assertEquals(listOf("100 g", "Stück"), repoNeu.portionen("user:1").map { it.label }.sorted())
        assertEquals(setOf("user:1"), neu.favorite().ladeIds().toSet())
        assertEquals(91.0, neu.body().ladeLetztesGewicht("APP")!!.value, 0.01)
    }

    @Test
    fun sicherung_wirdNachNeuinstallationAutomatischEingelesen() = runBlocking {
        File(ctx.filesDir, "sicherung").deleteRecursively()
        val alt = neueDb()
        alt.profile().speichere(profil)
        KalorienRepository(alt).speichereGewicht(90.0, LocalDate.of(2026, 11, 9))
        Sicherung(ctx, alt).schreibe()

        val neu = neueDb()
        assertTrue(Sicherung(ctx, neu).wiederherstellenFallsNoetig())
        assertNotNull(neu.profile().lade())
        // Mit Profil passiert beim nächsten Start nichts mehr
        assertFalse(Sicherung(ctx, neu).wiederherstellenFallsNoetig())
    }

    @Test
    fun portionsnamenAusHerstellerAngabe() {
        assertEquals("Becher", OpenFoodFacts.portionsName("1 Becher (150 g)"))
        assertEquals("Riegel", OpenFoodFacts.portionsName("1 Riegel = 25g"))
        assertEquals("2 Scheiben", OpenFoodFacts.portionsName("2 Scheiben (50 g)"))
        assertEquals("Portion", OpenFoodFacts.portionsName("30 g"))
        assertEquals("Portion", OpenFoodFacts.portionsName(null))
        assertEquals(150.0, OpenFoodFacts.grammAus("1 Becher (150 g)")!!, 0.01)
        assertEquals(250.0, OpenFoodFacts.grammAus("250ml")!!, 0.01)
    }

    @Test
    fun katalogprodukt_bekommtPortionsnamenNachgeladen() = runBlocking {
        val db = neueDb()
        db.food().fuegeEin(listOf(food("off:777", "Quark-Creme Salted Caramel")))
        db.food().fuegeServingsEin(
            listOf(
                ServingEntity(foodId = "off:777", label = "100 g", gramm = 100.0),
                ServingEntity(foodId = "off:777", label = "Portion", gramm = 150.0, isDefault = true),
            ),
        )
        val off = object : OpenFoodFacts() {
            override suspend fun produkt(ean: String) = OffTreffer(
                food("off:777", "Quark-Creme Salted Caramel"),
                listOf(
                    ServingEntity(foodId = "off:777", label = "100 g", gramm = 100.0),
                    ServingEntity(foodId = "off:777", label = "Becher", gramm = 150.0, isDefault = true),
                ),
            )
        }
        val repo = KalorienRepository(db, off)
        assertTrue(repo.portionenNachladen(repo.lebensmittel("off:777")!!))
        assertEquals(listOf("100 g", "Becher"), repo.portionen("off:777").map { it.label }.sorted())
        assertFalse(repo.portionenNachladen(repo.lebensmittel("off:777")!!)) // nur einmal je Start
    }

    @Test
    fun gescanntesProdukt_lerntSuchbegriffUndNamen() = runBlocking {
        val db = neueDb()
        val off = object : OpenFoodFacts() {
            override suspend fun produkt(ean: String) = OffTreffer(food("off:4711", "Quark-Creme Salted Caramel"), emptyList())
            override suspend fun suche(eingabe: String) = listOf(OffTreffer(food("off:4711", "Quark-Creme Salted Caramel"), emptyList()))
        }
        val repo = KalorienRepository(db, off)
        assertTrue(repo.suche("zero quark").isEmpty())
        repo.barcode("4711")
        repo.lerneSuchbegriff("off:4711", "zero quark")
        assertEquals(listOf("off:4711"), repo.suche("zero quark").map { it.id })

        repo.benenneUm("off:4711", "Zero Quark Karamell")
        repo.onlineSuche("quark") // darf den eigenen Namen nicht überschreiben
        val f = repo.lebensmittel("off:4711")!!
        assertEquals("Zero Quark Karamell", f.name)
        assertEquals(listOf("off:4711"), repo.suche("salted caramel").map { it.id })
    }

    @Test
    fun onlineGefunden_wirdBeimNaechstenMalLokalGefunden() = runBlocking {
        val db = neueDb()
        val off = object : OpenFoodFacts() {
            override suspend fun suche(eingabe: String) = listOf(OffTreffer(food("off:99", "Proteinriegel Cookie Dough"), emptyList()))
        }
        val repo = KalorienRepository(db, off)
        assertTrue(repo.suche("eiweissriegel").isEmpty())
        val online = repo.onlineSuche("eiweissriegel")!!
        repo.lerneSuchbegriff(online.first().id, "eiweissriegel")
        assertEquals(listOf("off:99"), repo.suche("eiweissriegel").map { it.id })
    }
}
