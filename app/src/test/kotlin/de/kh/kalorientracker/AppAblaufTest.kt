package de.kh.kalorientracker

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Spielt den Kernablauf ohne Handy durch: erster Start → Rechner ausfüllen → Ziel übernehmen →
 * Lebensmittel suchen → eintragen → im Tagebuch sehen → Eintrag bearbeiten.
 * Legt nebenbei Bildschirmfotos unter build/bildschirmfotos/ ab.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-rDE-w411dp-h891dp-xxhdpi")
class AppAblaufTest {
    companion object {
        init {
            // Datenbank direkt auf dem Haupt-Thread: Unter Robolectric liefe die Oberfläche sonst nach einer
            // Abfrage auf dem Hintergrund-Thread weiter, was es im echten Handy nicht gibt.
            de.kh.kalorientracker.data.AppDatabase.testAnpassung = { b ->
                val direkt = java.util.concurrent.Executor { it.run() }
                b.allowMainThreadQueries().setQueryExecutor(direkt).setTransactionExecutor(direkt)
            }
        }
    }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun foto(name: String) {
        rule.waitForIdle()
        val ordner = File(System.getProperty("bildschirmfotos") ?: "build/bildschirmfotos").apply { mkdirs() }
        // captureToImage wartet unter Robolectric auf ein Neuzeichnen, das nie kommt; daher direkt zeichnen.
        val bild = rule.runOnUiThread {
            val v = rule.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        }
        File(ordner, "$name.png").outputStream().use { bild.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun warteAufText(text: String) =
        rule.waitUntil(10_000) { rule.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun eingeben(label: String, wert: String) {
        rule.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextInput(wert)
    }

    private fun onboarding(mitFotos: Boolean) {
        warteAufText("Willkommen")
        if (mitFotos) foto("01-willkommen")

        rule.onNodeWithText("Mann").performClick()
        eingeben("Geburtsdatum", "01.05.1981")
        eingeben("Größe in cm", "180")
        eingeben("Gewicht in kg", "92,5")
        rule.onNodeWithText("Leicht aktiv", substring = true).performScrollTo().performClick()
        eingeben("Zielgewicht in kg", "85")
        rule.onNodeWithText("Ziel übernehmen").performScrollTo()
        if (mitFotos) foto("02-rechner-ergebnis")
        rule.onNodeWithText("Ziel übernehmen").performClick()

        rule.waitUntil(10_000) {
            rule.onAllNodes(hasContentDescription("Eintrag hinzufügen")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun ersterStartBisEintrag() {
        onboarding(mitFotos = true)

        // Tagebuch
        foto("03-tagebuch-leer")

        rule.onNodeWithContentDescription("Eintrag hinzufügen").performClick()
        warteAufText("Lebensmittel suchen")
        rule.onNode(hasSetTextAction()).performTextInput("apfel")
        warteAufText("52 kcal")
        foto("04-suche-apfel")

        rule.onAllNodesWithText("Apfel").onFirst().performClick()
        warteAufText("Schnell eintragen")
        foto("05-apfel-detail")
        rule.onNodeWithText("Eintragen").performClick()

        warteAufText("Apfel")
        warteAufText("78")
        foto("06-tagebuch-mit-apfel")

        // Bearbeiten: Eintrag antippen, Menge 2, speichern
        rule.onAllNodesWithText("Apfel").onFirst().performClick()
        warteAufText("Speichern")
        foto("07-eintrag-bearbeiten")
    }
}
