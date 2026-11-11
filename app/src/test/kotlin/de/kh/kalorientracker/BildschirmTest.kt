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
}
