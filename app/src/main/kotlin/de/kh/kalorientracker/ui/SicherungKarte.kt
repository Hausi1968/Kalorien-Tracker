package de.kh.kalorientracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.kh.kalorientracker.R
import de.kh.kalorientracker.data.Sicherung
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Eigene Daten als Datei speichern (z. B. in Google Drive oder Downloads) und wieder einlesen.
 * Androids automatische Sicherung läuft zusätzlich im Hintergrund.
 */
@Composable
fun SicherungKarte(sicherung: Sicherung, melde: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val textGespeichert = stringResource(R.string.sicherung_gespeichert)
    val textEingelesen = stringResource(R.string.sicherung_eingelesen)
    val textFehler = stringResource(R.string.sicherung_fehler)

    val speichern = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { context.contentResolver.openOutputStream(uri)!!.use { sicherung.exportiere(it) } }
                .onSuccess { melde(textGespeichert) }
                .onFailure { melde(textFehler) }
        }
    }
    val einlesen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { context.contentResolver.openInputStream(uri)!!.use { sicherung.importiere(it) } }
                .onSuccess { d -> melde(textEingelesen.format(d.eintraege.size)) }
                .onFailure { melde(textFehler) }
        }
    }

    Karte(titel = stringResource(R.string.sicherung_titel)) {
        Text(stringResource(R.string.sicherung_text), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
        Spacer(Modifier.height(Spacing.m))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            OutlinedButton(onClick = { speichern.launch("Kalorien-Sicherung-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.sicherung_speichern))
            }
            OutlinedButton(onClick = { einlesen.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.sicherung_einlesen))
            }
        }
    }
}
