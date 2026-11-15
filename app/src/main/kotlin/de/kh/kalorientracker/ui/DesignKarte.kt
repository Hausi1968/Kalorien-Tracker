package de.kh.kalorientracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import de.kh.kalorientracker.R
import de.kh.kalorientracker.ui.theme.Akzent
import de.kh.kalorientracker.ui.theme.Design
import de.kh.kalorientracker.ui.theme.Modus
import de.kh.kalorientracker.ui.theme.Spacing

/** Hell/dunkel und Akzentfarbe wählen. Wirkt sofort. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesignKarte() {
    val modus by Design.modus.collectAsState()
    val akzent by Design.akzent.collectAsState()
    Karte(titel = stringResource(R.string.design_titel)) {
        Text(stringResource(R.string.design_modus), style = MaterialTheme.typography.labelLarge)
        val modi = listOf(Modus.HANDY to R.string.design_wie_handy, Modus.HELL to R.string.design_hell, Modus.DUNKEL to R.string.design_dunkel)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
            modi.forEachIndexed { i, (m, text) ->
                SegmentedButton(
                    selected = modus == m,
                    onClick = { Design.setze(modus = m) },
                    shape = SegmentedButtonDefaults.itemShape(i, modi.size),
                ) { Text(stringResource(text), maxLines = 1) }
            }
        }
        Text(stringResource(R.string.design_farbe), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.m))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Akzent.entries.forEach { a ->
                FilterChip(
                    selected = akzent == a,
                    onClick = { Design.setze(akzent = a) },
                    label = { Text(stringResource(name(a))) },
                    leadingIcon = { Box(Modifier.size(16.dp).background(a.hell.primary, CircleShape)) },
                )
            }
        }
    }
}

private fun name(a: Akzent) = when (a) {
    Akzent.TUERKIS -> R.string.farbe_tuerkis
    Akzent.BLAU -> R.string.farbe_blau
    Akzent.LILA -> R.string.farbe_lila
    Akzent.ORANGE -> R.string.farbe_orange
    Akzent.ROSA -> R.string.farbe_rosa
}
