package de.kh.kalorientracker.ui.foto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kh.kalorientracker.R
import de.kh.kalorientracker.data.KiSchluessel
import de.kh.kalorientracker.ui.Karte
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing

/** Profil: Zugangsschlüssel für die Foto-Erkennung eintragen, ändern oder entfernen. */
@Composable
fun SchluesselKarte(schluessel: KiSchluessel, melde: (String) -> Unit) {
    val wert by schluessel.wert.collectAsStateWithLifecycle()
    var bearbeiten by remember { mutableStateOf(false) }
    var eingabe by remember { mutableStateOf("") }
    val textGespeichert = stringResource(R.string.schluessel_gespeichert)
    val textEntfernt = stringResource(R.string.schluessel_entfernt)
    val gueltig = eingabe.trim().startsWith("sk-ant-") && eingabe.trim().length > 20

    Karte(titel = stringResource(R.string.schluessel_titel)) {
        Text(stringResource(R.string.schluessel_text), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
        Spacer(Modifier.height(Spacing.m))
        if (bearbeiten || wert.isBlank()) {
            OutlinedTextField(
                value = eingabe, onValueChange = { eingabe = it.trim().take(200) },
                label = { Text(stringResource(R.string.schluessel_feld)) },
                placeholder = { Text("sk-ant-…") },
                singleLine = true,
                isError = eingabe.isNotEmpty() && !gueltig,
                supportingText = { if (eingabe.isNotEmpty() && !gueltig) Text(stringResource(R.string.schluessel_ungueltig)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedButton(enabled = gueltig, onClick = {
                    schluessel.setze(eingabe); eingabe = ""; bearbeiten = false; melde(textGespeichert)
                }) { Text(stringResource(R.string.speichern)) }
                if (bearbeiten) TextButton(onClick = { bearbeiten = false; eingabe = "" }) { Text(stringResource(R.string.abbrechen)) }
            }
        } else {
            Text(stringResource(R.string.schluessel_hinterlegt, "…" + wert.takeLast(4)), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedButton(onClick = { bearbeiten = true }) { Text(stringResource(R.string.schluessel_aendern)) }
                TextButton(onClick = { schluessel.setze(""); melde(textEntfernt) }) { Text(stringResource(R.string.schluessel_entfernen)) }
            }
        }
    }
}
