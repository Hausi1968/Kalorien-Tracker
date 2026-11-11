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
