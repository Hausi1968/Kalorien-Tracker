package de.kh.kalorientracker

import android.graphics.Bitmap
import androidx.compose.foundation.verticalScroll
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.kh.kalorientracker.data.AppDatabase
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.data.ProfileEntity
import de.kh.kalorientracker.data.StartdatenImport
import de.kh.kalorientracker.ui.ProfilScreen
import de.kh.kalorientracker.ui.rezepte.RezeptEditorScreen
import de.kh.kalorientracker.ui.theme.KalorienTheme
import de.kh.kalorientracker.update.Aktualisierung
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Einzelne Bildschirme mit Auswahlfenstern, ohne die ganze App drumherum (die Navigation bringt
 * Robolectric bei Fenstern aus dem Takt). Datenbank im Speicher mit den echten Startdaten.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class BildschirmTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
        .allowMainThreadQueries().setQueryExecutor { it.run() }.setTransactionExecutor { it.run() }.build()
    private val repo = KalorienRepository(db)

    init {
        runBlocking {
            StartdatenImport(ctx, db).fallsLeer()
            db.profile().speichere(
                ProfileEntity(
                    geschlecht = "MANN", geburtsdatum = "1981-05-01", groesseCm = 180.0, pal = 1.4, ziel = "ABNEHMEN",
                    zielgewichtKg = 85.0, tempoKgProWoche = 0.5, startGewichtKg = 92.5, startDatum = "2026-11-11",
                    tageszielKcal = 2060, fettProzent = 30.0, khProzent = 50.0, eiweissProzent = 20.0, updatedAt = 0,
                ),
            )
        }
    }

    private fun foto(name: String) {
        rule.waitForIdle()
        val ordner = File(System.getProperty("bildschirmfotos") ?: "build/bildschirmfotos").apply { mkdirs() }
        val bild = rule.runOnUiThread {
            val v = rule.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        }
        File(ordner, "$name.png").outputStream().use { bild.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun warteAufText(text: String) =
        rule.waitUntil(10_000) { rule.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    // Kleiner Bildschirm: Eingabefelder in Fenstern kommen unter Robolectric bei Handy-Größe nie zur Ruhe
    // (nur im Test; schlichte Material-Fenster mit Textfeld zeigen dasselbe).
    @Config(qualifiers = "de-rDE")
    @Test fun zielUndMakrosSelbstFestlegen() {
        rule.setContent { KalorienTheme { ProfilScreen(repo, Aktualisierung(ctx), {}, {}) } }
        rule.onNodeWithText("Ziel und Makros anpassen").performScrollTo().performClick()
        warteAufText("Tagesziel selbst festlegen")
        rule.onNodeWithText("Eiweißreich").performClick()
        foto("11-ziel-anpassen")
        rule.onNodeWithText("Speichern").performClick()
        rule.waitForIdle()
        val p = runBlocking { db.profile().lade() }!!
        assertEquals(30.0, p.eiweissProzent, 0.01)
        assertEquals(40.0, p.khProzent, 0.01)
        assertEquals(false, p.zielManuell)
    }

    private fun beispielTag(): java.time.LocalDate = runBlocking {
        val tag = java.time.LocalDate.now()
        suspend fun lm(n: String) = repo.suche(n).first()
        repo.eintragen(lm("haferflocken"), "100 g", 100.0, 0.8, tag.atTime(7, 30), 1)
        repo.eintragen(lm("apfel"), "100 g", 100.0, 1.5, tag.atTime(10, 0), 2)
        repo.eintragen(lm("vollkornbrot"), "100 g", 100.0, 1.2, tag.atTime(12, 15), 3)
        repo.eintragen(lm("gouda"), "100 g", 100.0, 0.6, tag.atTime(12, 15), 3)
        repo.eintragen(lm("nudeln"), "100 g", 100.0, 1.25, tag.atTime(18, 30), 5)
        tag
    }

    @Test fun tagesuebersichtKalorienUndNaehrwerte() {
        val tag = beispielTag()
        rule.setContent { KalorienTheme { de.kh.kalorientracker.ui.uebersicht.UebersichtScreen(repo, tag, {}, {}) } }
        warteAufText("Einträge mit viel")
        foto("12-uebersicht-kalorien")
        rule.onNodeWithText("Nährwerte").performClick()
        warteAufText("Weitere Nährwerte")
        foto("13-uebersicht-naehrwerte")
    }

    @Test fun eintraegeAuswaehlenUndVerschieben() {
        val tag = beispielTag()
        var fertig = false
        rule.setContent { KalorienTheme { de.kh.kalorientracker.ui.tagebuch.AuswahlScreen(repo, tag, null, onFertig = { fertig = true }) } }
        warteAufText("Mittagessen")
        rule.onNodeWithText("Mittagessen").performClick() // ganze Mahlzeit auswählen
        foto("14-auswahl")
        val mittag = runBlocking { db.diary().eintraegeAm(tag.toString()).first().filter { it.slotId == 3 } }
        runBlocking { repo.verschiebe(mittag, tag.minusDays(1), 5) }
        val gestern = runBlocking { db.diary().eintraegeAm(tag.minusDays(1).toString()).first() }
        assertEquals(2, gestern.size)
        assertEquals(setOf(5), gestern.map { it.slotId }.toSet())
    }

    @Test fun rezeptAusZweiZutaten() {
        var gespeichert: String? = null
        rule.setContent { KalorienTheme { RezeptEditorScreen(repo, null, onZurueck = {}, onGespeichert = { gespeichert = it }) } }
        rule.onNode(hasSetTextAction() and hasText("Name des Gerichts")).performTextInput("Haferbrei mit Apfel")

        fun zutat(suche: String, name: String, gramm: String) {
            rule.onNodeWithText("Zutat hinzufügen").performScrollTo().performClick()
            warteAufText("Was hast du gegessen?")
            rule.onNode(hasSetTextAction()).performTextInput(suche)
            warteAufText(name)
            rule.onAllNodesWithText(name).onFirst().performClick()
            warteAufText("Hinzufügen")
            if (name == "Apfel") foto("08b-zutat-menge")
            rule.onNode(hasSetTextAction()).performTextClearance()
            rule.onNode(hasSetTextAction()).performTextInput(gramm)
            rule.onNodeWithText("Hinzufügen").performClick()
        }
        zutat("hafer", "Haferflocken", "80")
        zutat("apfel", "Apfel", "150")
        warteAufText("Pro Portion")
        foto("08-rezept-editor")
        rule.onNodeWithText("Speichern").performScrollTo().performClick()
        rule.waitForIdle()

        val id = assertNotNull(gespeichert).let { gespeichert!! }
        val f = runBlocking { repo.lebensmittel(id) }!!
        val zutaten = runBlocking { repo.rezeptZutaten(id) }
        assertEquals(2, zutaten.size)
        val hafer = runBlocking { repo.suche("haferflocken").first { it.name == "Haferflocken" } }
        val apfel = runBlocking { repo.suche("apfel").first { it.name == "Apfel" } }
        val erwartetKcal = (hafer.kcal * 0.8 + apfel.kcal * 1.5) / 230.0 * 100.0
        assertEquals(erwartetKcal, f.kcal, 0.01)
        assertEquals(115.0, runBlocking { repo.portionen(id) }.first { it.isDefault }.gramm, 0.01) // 230 g / 2 Portionen
    }

    @Test fun katalogEinlesenUndSuchen() {
        val start = System.currentTimeMillis()
        runBlocking { de.kh.kalorientracker.data.KatalogImport(ctx, db).fallsNoetig() }
        val dauer = System.currentTimeMillis() - start
        val anzahl = runBlocking { db.food().anzahl() }
        println("KATALOG $anzahl Einträge in $dauer ms")
        org.junit.Assert.assertTrue(anzahl > 50_000)
        // Grundnahrungsmittel bleiben vorne, auch wenn es viele Markenprodukte gibt
        val t = System.currentTimeMillis()
        val treffer = runBlocking { repo.suche("haferflocken") }
        println("SUCHE ${treffer.size} Treffer in ${System.currentTimeMillis() - t} ms: " + treffer.take(5).joinToString { it.name + "/" + it.brand })
        assertEquals("start:haferflocken", treffer.first().id)
        val nutella = runBlocking { repo.suche("nutella") }
        println("NUTELLA " + nutella.take(3).joinToString { it.name + "/" + it.brand })
        // Zweiter Aufruf liest nichts neu ein
        runBlocking { de.kh.kalorientracker.data.KatalogImport(ctx, db).fallsNoetig() }
        assertEquals(anzahl, runBlocking { db.food().anzahl() })
    }

    @Test fun quellenWahlFuerGewichtUndVerbrannt() = runBlocking {
        val quellen = de.kh.kalorientracker.data.Quellen()
        val r = KalorienRepository(db, quellen = quellen)
        val heute = java.time.LocalDate.now()
        r.speichereGewicht(92.0, heute)
        r.speichereAktivitaet("Radfahren", 300.0, 45.0, heute)
        r.uebernehmeHealthConnect(heute.minusDays(14), mapOf(heute to (System.currentTimeMillis() to 90.5)), mapOf(heute to 420.0))
        suspend fun verbranntS() = r.tag(heute).first().verbrannt
        suspend fun gewicht() = r.ladeLetztesGewicht()

        assertEquals(92.0, gewicht()!!, 0.01)
        assertEquals(300.0, verbranntS(), 0.01)

        quellen.setze(de.kh.kalorientracker.data.QuellenWahl(de.kh.kalorientracker.data.Quelle.HC, de.kh.kalorientracker.data.Quelle.HC))
        assertEquals(90.5, gewicht()!!, 0.01)
        assertEquals(420.0, verbranntS(), 0.01)

        quellen.setze(de.kh.kalorientracker.data.QuellenWahl(de.kh.kalorientracker.data.Quelle.BEIDE, de.kh.kalorientracker.data.Quelle.BEIDE))
        assertEquals(720.0, verbranntS(), 0.01)

        // Erneuter Abgleich ersetzt die alten Werte statt sie zu verdoppeln
        r.uebernehmeHealthConnect(heute.minusDays(14), emptyMap(), mapOf(heute to 500.0))
        assertEquals(800.0, verbranntS(), 0.01)
    }

    @Test fun mahlzeitPerFotoPruefenUndEintragen() {
        // Testbild: ein "Teller" in Farbe, gespeichert wie ein echtes Foto
        val fotos = de.kh.kalorientracker.data.Fotos(ctx)
        val name = "test-teller.jpg"
        fotos.datei(name).outputStream().use { out ->
            val b = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(b).apply {
                drawColor(android.graphics.Color.rgb(230, 225, 215))
                drawCircle(320f, 240f, 200f, android.graphics.Paint().apply { color = android.graphics.Color.WHITE })
                drawCircle(260f, 220f, 70f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(245, 240, 225) })
                drawCircle(390f, 230f, 60f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(200, 140, 80) })
                drawCircle(320f, 330f, 50f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(60, 140, 60) })
            }
            b.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        val schluessel = de.kh.kalorientracker.data.KiSchluessel().apply { setze("sk-ant-test-0000000000000000000000") }
        val erkenner = object : de.kh.kalorientracker.data.FotoErkenner {
            override val bereit = true
            override suspend fun erkenne(jpeg: ByteArray) = Result.success(
                listOf(
                    de.kh.kalorientracker.data.ErkanntesLebensmittel("Reis gekocht", 180.0),
                    de.kh.kalorientracker.data.ErkanntesLebensmittel("Hähnchenbrust gebraten", 120.0),
                    de.kh.kalorientracker.data.ErkanntesLebensmittel("Brokkoli", 90.0),
                    de.kh.kalorientracker.data.ErkanntesLebensmittel("Zauberbohnen", 30.0),
                ),
            )
        }
        val werkzeug = de.kh.kalorientracker.ui.foto.FotoWerkzeug(fotos, schluessel, erkenner)
        val tag = java.time.LocalDate.now()
        var fertig = false
        rule.setContent {
            KalorienTheme {
                de.kh.kalorientracker.ui.foto.FotoScreen(
                    repo, werkzeug, tag, 3, onZurueck = {}, onFertig = { fertig = true }, onSchluessel = {}, startFoto = name,
                )
            }
        }
        warteAufText("Bitte prüfen")
        warteAufText("Reis (gekocht)")
        foto("15-foto-pruefen")
        // Unbekanntes wird angezeigt, aber nicht eingetragen
        rule.onNodeWithText("„Zauberbohnen“ nicht gefunden").assertExists()
        // Menge beim Reis ändern: 180 → 150 g
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextClearance()
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("150")
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
            .performScrollToNode(hasText("3 Lebensmittel eintragen", substring = true))
        foto("16-foto-eintragen")
        rule.onNodeWithText("3 Lebensmittel eintragen", substring = true).performClick()
        rule.waitUntil(10_000) { fertig }
        val eintraege = runBlocking { db.diary().eintraegeAm(tag.toString()).first() }
        assertEquals(3, eintraege.size)
        assertEquals(setOf(name), eintraege.map { it.foto }.toSet())
        assertEquals(setOf(3), eintraege.map { it.slotId }.toSet())
        assertEquals(150.0, eintraege.first { it.label.startsWith("Reis") }.gramm!!, 0.01)
    }

}
