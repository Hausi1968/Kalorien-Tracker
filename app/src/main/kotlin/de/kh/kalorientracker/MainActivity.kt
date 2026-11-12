package de.kh.kalorientracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        // Verbrannte kcal und Gewicht aus Health Connect: jedes Mal, wenn die App sichtbar wird,
        // und alle 15 Minuten, solange sie offen ist (Health Connect gibt Daten nur im Vordergrund heraus)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    container.gesundheitAbgleichenWennFaellig()
                    delay(15 * 60 * 1000L)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Eigene Daten für Androids automatische Sicherung bereitlegen
        (application as KalorienApp).container.sichere()
    }
}
