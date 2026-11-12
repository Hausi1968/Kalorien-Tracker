package de.kh.kalorientracker.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.kh.kalorientracker.R
import de.kh.kalorientracker.core.MahlzeitSlot
import de.kh.kalorientracker.core.Mahlzeiten
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ob im Tagebuch das Abendessen oben steht (Frühstück unten) oder wie gewohnt umgekehrt. */
object Reihenfolge {
    private var prefs: android.content.SharedPreferences? = null
    private val _abendOben = MutableStateFlow(false)
    val abendOben: StateFlow<Boolean> = _abendOben

    fun lade(context: Context) {
        prefs = context.getSharedPreferences("anzeige", Context.MODE_PRIVATE)
        _abendOben.value = prefs?.getBoolean("abendOben", false) ?: false
    }

    fun setze(abendOben: Boolean) {
        prefs?.edit()?.putBoolean("abendOben", abendOben)?.apply()
        _abendOben.value = abendOben
    }

    fun sortiert(abendOben: Boolean): List<MahlzeitSlot> =
        if (abendOben) Mahlzeiten.STANDARD.reversed() else Mahlzeiten.STANDARD
}

/** Die Mahlzeiten in der Reihenfolge, die der Nutzer gewählt hat. */
@Composable
fun mahlzeitenSortiert(): List<MahlzeitSlot> {
    val abendOben by Reihenfolge.abendOben.collectAsState()
    return Reihenfolge.sortiert(abendOben)
}

@Composable
fun ReihenfolgeKarte() {
    val abendOben by Reihenfolge.abendOben.collectAsState()
    Karte(titel = stringResource(R.string.reihenfolge_titel)) {
        Text(stringResource(R.string.reihenfolge_text), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
            SegmentedButton(
                selected = !abendOben,
                onClick = { Reihenfolge.setze(false) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(stringResource(R.string.reihenfolge_fruehstueck_oben)) }
            SegmentedButton(
                selected = abendOben,
                onClick = { Reihenfolge.setze(true) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(stringResource(R.string.reihenfolge_abend_oben)) }
        }
    }
}
