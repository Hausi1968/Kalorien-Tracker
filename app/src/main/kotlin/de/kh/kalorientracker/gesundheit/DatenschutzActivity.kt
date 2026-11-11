package de.kh.kalorientracker.gesundheit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.kh.kalorientracker.R
import de.kh.kalorientracker.ui.PrimaerButton
import de.kh.kalorientracker.ui.Seite
import de.kh.kalorientracker.ui.theme.KalorienTheme
import de.kh.kalorientracker.ui.theme.Spacing

/** Wird von Health Connect geöffnet, wenn jemand wissen will, wofür die App Gesundheitsdaten liest. */
class DatenschutzActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KalorienTheme {
                Seite(titel = stringResource(R.string.hc_datenschutz_titel)) { pad ->
                    Column(
                        Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(Spacing.l),
                        verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        Text(stringResource(R.string.hc_datenschutz_text), style = MaterialTheme.typography.bodyLarge)
                        PrimaerButton(stringResource(R.string.schliessen), { finish() })
                    }
                }
            }
        }
    }
}
