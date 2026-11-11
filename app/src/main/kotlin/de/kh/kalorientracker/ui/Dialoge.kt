package de.kh.kalorientracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import de.kh.kalorientracker.R
import de.kh.kalorientracker.core.Format
import de.kh.kalorientracker.ui.theme.Spacing

private fun zahl(s: String) = Format.parseDezimal(s)?.takeIf { it >= 0 && it < 20_000 }
private fun nurZahl(s: String) = s.filter { it.isDigit() || it == ',' || it == '.' }.take(7)

@Composable
private fun ZahlFeld(wert: String, onWert: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = wert, onValueChange = { onWert(nurZahl(it)) }, label = { Text(label) }, singleLine = true,
        isError = wert.isNotBlank() && zahl(wert) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier,
    )
}

/** Kalorien direkt eintragen (S07c): ohne Lebensmittel, nur kcal und optional Makros. */
@Composable
fun KalorienDirektDialog(
    onAbbrechen: () -> Unit,
    onSpeichern: (bezeichnung: String, kcal: Double, fett: Double?, kh: Double?, eiweiss: Double?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var fett by remember { mutableStateOf("") }
    var kh by remember { mutableStateOf("") }
    var eiweiss by remember { mutableStateOf("") }
    val standardName = stringResource(R.string.kalorien_direkt_standard)
    val k = zahl(kcal)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(stringResource(R.string.kalorien_direkt)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                    label = { Text(stringResource(R.string.bezeichnung_optional)) },
                    placeholder = { Text(standardName) },
                )
                ZahlFeld(kcal, { kcal = it }, stringResource(R.string.brennwert_kcal), Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    ZahlFeld(fett, { fett = it }, stringResource(R.string.kurz_fett), Modifier.weight(1f))
                    ZahlFeld(kh, { kh = it }, stringResource(R.string.kurz_kh), Modifier.weight(1f))
                    ZahlFeld(eiweiss, { eiweiss = it }, stringResource(R.string.kurz_eiweiss), Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = k != null, onClick = {
                k?.let { onSpeichern(name.trim().ifEmpty { standardName }, it, zahl(fett), zahl(kh), zahl(eiweiss)) }
            }) { Text(stringResource(R.string.eintragen)) }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.abbrechen)) } },
    )
}

/** Aktivität mit verbrannten kcal eintragen (vereinfacht; Katalog mit MET-Werten folgt). */
@Composable
fun AktivitaetDialog(onAbbrechen: () -> Unit, onSpeichern: (bezeichnung: String, kcal: Double, minuten: Double?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var minuten by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    val k = zahl(kcal)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(stringResource(R.string.aktivitaet_eintragen)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                    label = { Text(stringResource(R.string.aktivitaet_name)) },
                    placeholder = { Text(stringResource(R.string.aktivitaet_beispiel)) },
                )
                ZahlFeld(minuten, { minuten = it }, stringResource(R.string.minuten_optional), Modifier.fillMaxWidth())
                ZahlFeld(kcal, { kcal = it }, stringResource(R.string.verbrannte_kcal), Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = k != null && name.isNotBlank(), onClick = {
                k?.let { onSpeichern(name.trim(), it, zahl(minuten)) }
            }) { Text(stringResource(R.string.speichern)) }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.abbrechen)) } },
    )
}

/** Vorlagen für die Makro-Verteilung (Fett, Kohlenhydrate, Eiweiß in %). */
private val MAKRO_VORLAGEN = listOf(
    R.string.makro_vorlage_ausgewogen to Triple(30.0, 50.0, 20.0),
    R.string.makro_vorlage_eiweiss to Triple(30.0, 40.0, 30.0),
    R.string.makro_vorlage_wenig_kh to Triple(45.0, 25.0, 30.0),
)

/**
 * Tagesziel selbst festlegen oder automatisch lassen, dazu die Makro-Anteile in Prozent.
 * onSpeichern bekommt kcal = null, wenn das Ziel automatisch berechnet werden soll.
 */
@Composable
fun ZielAnpassenDialog(
    kcalBisher: Int,
    manuellBisher: Boolean,
    makroBisher: Triple<Double, Double, Double>,
    onAbbrechen: () -> Unit,
    onSpeichern: (kcal: Int?, fett: Double, kh: Double, eiweiss: Double) -> Unit,
) {
    var manuell by remember { mutableStateOf(manuellBisher) }
    var kcal by remember { mutableStateOf(kcalBisher.toString()) }
    var fett by remember { mutableStateOf(Format.dezimal(makroBisher.first)) }
    var kh by remember { mutableStateOf(Format.dezimal(makroBisher.second)) }
    var eiweiss by remember { mutableStateOf(Format.dezimal(makroBisher.third)) }
    val k = zahl(kcal)?.takeIf { it in 800.0..6000.0 }
    val f = zahl(fett); val c = zahl(kh); val e = zahl(eiweiss)
    val summe = (f ?: 0.0) + (c ?: 0.0) + (e ?: 0.0)
    val summeOk = f != null && c != null && e != null && kotlin.math.abs(summe - 100.0) < 0.5
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(stringResource(R.string.ziel_anpassen)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.ziel_selbst_festlegen), Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = manuell, onCheckedChange = { manuell = it })
                }
                if (manuell) {
                    ZahlFeld(kcal, { kcal = it }, stringResource(R.string.tagesziel_kcal), Modifier.fillMaxWidth())
                    if (kcal.isNotBlank() && k == null) {
                        Text(stringResource(R.string.tagesziel_grenzen), style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                    } else if (k != null && k < 1200) {
                        Text(stringResource(R.string.tagesziel_niedrig), style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                    }
                } else {
                    Text(stringResource(R.string.ziel_automatisch_text), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.makro_verteilung), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    MAKRO_VORLAGEN.forEach { (name, werte) ->
                        androidx.compose.material3.AssistChip(
                            onClick = {
                                fett = Format.dezimal(werte.first); kh = Format.dezimal(werte.second); eiweiss = Format.dezimal(werte.third)
                            },
                            label = { Text(stringResource(name), maxLines = 1) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    ZahlFeld(fett, { fett = it }, stringResource(R.string.prozent_fett), Modifier.weight(1f))
                    ZahlFeld(kh, { kh = it }, stringResource(R.string.prozent_kh), Modifier.weight(1f))
                    ZahlFeld(eiweiss, { eiweiss = it }, stringResource(R.string.prozent_eiweiss), Modifier.weight(1f))
                }
                Text(
                    stringResource(R.string.makro_summe, Format.dezimal(summe)),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = if (summeOk) androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    else androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = summeOk && (!manuell || k != null), onClick = {
                onSpeichern(if (manuell) k!!.toInt() else null, f!!, c!!, e!!)
            }) { Text(stringResource(R.string.speichern)) }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.abbrechen)) } },
    )
}
