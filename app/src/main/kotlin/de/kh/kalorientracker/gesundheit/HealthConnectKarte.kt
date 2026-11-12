package de.kh.kalorientracker.gesundheit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kh.kalorientracker.R
import de.kh.kalorientracker.data.Quelle
import de.kh.kalorientracker.ui.HinweisBox
import de.kh.kalorientracker.ui.Karte
import de.kh.kalorientracker.ui.PrimaerButton
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Profil-Karte: Samsung Health über Health Connect verbinden und festlegen, welche Werte zählen. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectKarte(hc: HealthConnect, onMeldung: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wahl by hc.repoQuellen().wahl.collectAsStateWithLifecycle()
    var verbunden by remember { mutableStateOf<Boolean?>(null) }
    var neuPruefen by remember { mutableIntStateOf(0) }
    val status = remember(neuPruefen) { hc.status() }
    LaunchedEffect(neuPruefen) { verbunden = hc.hatBerechtigung() }

    val textAbgleich = stringResource(R.string.hc_abgeglichen)
    val textKeineErlaubnis = stringResource(R.string.hc_keine_erlaubnis)
    fun abgleichen() = scope.launch {
        val n = runCatching { hc.abgleichen() }.getOrNull()
        onMeldung(if (n == null) textKeineErlaubnis else textAbgleich.format(n))
    }
    val anfrage = rememberLauncherForActivityResult(hc.berechtigungAnfragen()) { erteilt ->
        neuPruefen++
        if (erteilt.containsAll(hc.berechtigungen)) abgleichen()
    }

    Karte(titel = stringResource(R.string.hc_titel)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.hc_erklaerung), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
            when (status) {
                HealthConnect.Status.NICHT_VERFUEGBAR -> HinweisBox(stringResource(R.string.hc_nicht_verfuegbar), warnung = true)
                HealthConnect.Status.INSTALLIEREN -> {
                    HinweisBox(stringResource(R.string.hc_installieren_hinweis))
                    PrimaerButton(stringResource(R.string.hc_installieren), {
                        runCatching { context.startActivity(hc.installierenIntent()) }
                    })
                }
                HealthConnect.Status.BEREIT -> {
                    QuellenWahl(stringResource(R.string.hc_gewicht), wahl.gewicht) { hc.repoQuellen().setze(wahl.copy(gewicht = it)); abgleichen() }
                    QuellenWahl(stringResource(R.string.hc_verbrannt), wahl.verbrannt) { hc.repoQuellen().setze(wahl.copy(verbrannt = it)); abgleichen() }
                    if (wahl.verbrannt != de.kh.kalorientracker.data.Quelle.APP) {
                        AnteilRegler(wahl.hcAnteil) { hc.repoQuellen().setze(hc.repoQuellen().wahl.value.copy(hcAnteil = it)) }
                    }
                    if (wahl.brauchtHealthConnect) {
                        if (verbunden == false) {
                            HinweisBox(stringResource(R.string.hc_erlaubnis_hinweis))
                            PrimaerButton(stringResource(R.string.hc_verbinden), { anfrage.launch(hc.berechtigungen) })
                        } else if (verbunden == true) {
                            Text(stringResource(R.string.hc_verbunden), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            val stand by hc.stand.collectAsStateWithLifecycle()
                            stand?.let { s ->
                                val zeit = s.zeit.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                                val kcal = s.heuteAktivKcal
                                Text(
                                    if (kcal == null) stringResource(R.string.hc_stand_zeit, zeit)
                                    else stringResource(
                                        R.string.hc_stand, zeit, de.kh.kalorientracker.core.Format.kcal(kcal),
                                        wahl.hcAnteil, de.kh.kalorientracker.core.Format.kcal(kcal * wahl.hcAnteil / 100.0),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (kcal != null) {
                                    Text(stringResource(R.string.hc_stand_hinweis), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
                                }
                            }
                            OutlinedButton(onClick = { abgleichen() }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.hc_jetzt_abgleichen))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuellenWahl(titel: String, aktuell: Quelle, onWahl: (Quelle) -> Unit) {
    val optionen = listOf(Quelle.APP to R.string.hc_quelle_app, Quelle.HC to R.string.hc_quelle_hc, Quelle.BEIDE to R.string.hc_quelle_beide)
    Text(titel, style = MaterialTheme.typography.titleSmall)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        optionen.forEachIndexed { i, (q, label) ->
            SegmentedButton(
                selected = aktuell == q,
                onClick = { if (aktuell != q) onWahl(q) },
                shape = SegmentedButtonDefaults.itemShape(i, optionen.size),
            ) { Text(stringResource(label), maxLines = 1) }
        }
    }
}

/** Schieberegler: wie viel Prozent der von der Uhr gemeldeten kcal zählen (in 10er-Schritten). */
@androidx.compose.runtime.Composable
private fun AnteilRegler(anteil: Int, onAnteil: (Int) -> Unit) {
    var wert by androidx.compose.runtime.remember(anteil) { androidx.compose.runtime.mutableFloatStateOf(anteil.toFloat()) }
    Text(stringResource(R.string.hc_anteil, wert.toInt()), style = MaterialTheme.typography.titleSmall)
    androidx.compose.material3.Slider(
        value = wert, onValueChange = { wert = (it / 10).toInt() * 10f },
        onValueChangeFinished = { onAnteil(wert.toInt()) },
        valueRange = 0f..100f, steps = 9,
    )
    Text(stringResource(R.string.hc_anteil_hinweis), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
}
