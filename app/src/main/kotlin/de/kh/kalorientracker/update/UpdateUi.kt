package de.kh.kalorientracker.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.kh.kalorientracker.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/** Fragt, ob die neue Version installiert werden soll, lädt sie und startet den Android-Installer. */
@Composable
fun UpdateDialog(aktualisierung: Aktualisierung, neu: NeueVersion, onSchliessen: () -> Unit) {
    val scope = rememberCoroutineScope()
    var fortschritt by remember { mutableStateOf<Float?>(null) }
    var fehler by remember { mutableStateOf(false) }
    var datei by remember { mutableStateOf<File?>(null) }
    var erlaubnisFehlt by remember { mutableStateOf(false) }
    var ladeJob by remember { mutableStateOf<Job?>(null) }

    fun installieren(f: File) {
        if (aktualisierung.darfInstallieren()) {
            aktualisierung.installiere(f)
            onSchliessen()
        } else {
            erlaubnisFehlt = true
            aktualisierung.oeffneErlaubnis()
        }
    }

    fun abbrechen() {
        ladeJob?.cancel()
        ladeJob = null
        onSchliessen()
    }

    AlertDialog(
        onDismissRequest = { if (fortschritt == null) onSchliessen() },
        title = { Text(stringResource(R.string.update_titel, neu.version)) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.update_text, aktualisierung.aktuelleVersion))
                if (neu.notizen.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(neu.notizen, style = MaterialTheme.typography.bodySmall)
                }
                fortschritt?.let {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                }
                if (erlaubnisFehlt) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.update_erlaubnis), color = MaterialTheme.colorScheme.error)
                }
                if (fehler) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.update_fehler), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = fortschritt == null || datei != null,
                onClick = {
                    val vorhanden = datei
                    if (vorhanden != null) { installieren(vorhanden); return@TextButton }
                    fehler = false
                    fortschritt = 0f
                    ladeJob = scope.launch {
                        try {
                            val f = aktualisierung.lade(neu) { p -> fortschritt = p }
                            datei = f
                            fortschritt = 1f
                            installieren(f)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            fehler = true
                            fortschritt = null
                        }
                    }
                },
            ) { Text(stringResource(if (datei != null) R.string.update_installieren else R.string.update_jetzt)) }
        },
        dismissButton = {
            // Während des Downloads heißt der Knopf "Abbrechen" und stoppt ihn sofort
            TextButton(onClick = ::abbrechen) {
                Text(stringResource(if (fortschritt != null && datei == null) R.string.abbrechen else R.string.update_spaeter))
            }
        },
    )
}
