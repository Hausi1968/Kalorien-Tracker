package de.kh.kalorientracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.kh.kalorientracker.ui.KalorienAppUi
import de.kh.kalorientracker.ui.theme.KalorienTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KalorienApp).container
        setContent {
            KalorienTheme {
                KalorienAppUi(container)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Eigene Daten für Androids automatische Sicherung bereitlegen
        (application as KalorienApp).container.sichere()
    }
}
