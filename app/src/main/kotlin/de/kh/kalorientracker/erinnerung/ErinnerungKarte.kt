@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package de.kh.kalorientracker.erinnerung

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kh.kalorientracker.R
import de.kh.kalorientracker.ui.Karte
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Profil: Erinnerungen an Mahlzeiten und ans Wiegen ein- und ausschalten, Uhrzeit wählen. */
@Composable
fun ErinnerungKarte(erinnerungen: Erinnerungen, melde: (String) -> Unit) {
    val liste by erinnerungen.liste.collectAsStateWithLifecycle()
    var zeitFuer by remember { mutableStateOf<Erinnerung?>(null) }
    var wartend by remember { mutableStateOf<Erinnerung?>(null) }
    val textAus = stringResource(R.string.erinnerung_erlaubnis_fehlt)
    val erlaubnis = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        wartend?.let { if (ok) erinnerungen.setze(it) else melde(textAus) }
        wartend = null
    }

    fun schalte(e: Erinnerung, an: Boolean) {
        val neu = e.copy(an = an)
        if (an && Build.VERSION.SDK_INT >= 33) {
            wartend = neu
            erlaubnis.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            erinnerungen.setze(neu)
        }
    }

    Karte(titel = stringResource(R.string.erinnerung_titel)) {
        Text(stringResource(R.string.erinnerung_text), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
        liste.forEach { e ->
            Row(Modifier.fillMaxWidth().padding(top = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(name(e.art)), style = MaterialTheme.typography.bodyLarge)
                    AssistChip(
                        onClick = { zeitFuer = e },
                        label = { Text(e.zeit.format(DateTimeFormatter.ofPattern("HH:mm"))) },
                        leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = stringResource(R.string.uhrzeit_waehlen)) },
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Switch(checked = e.an, onCheckedChange = { schalte(e, it) })
            }
        }
    }

    zeitFuer?.let { e ->
        val state = rememberTimePickerState(initialHour = e.zeit.hour, initialMinute = e.zeit.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { zeitFuer = null },
            title = { Text(stringResource(name(e.art))) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    erinnerungen.setze(e.copy(zeit = LocalTime.of(state.hour, state.minute)))
                    zeitFuer = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { zeitFuer = null }) { Text(stringResource(R.string.abbrechen)) } },
        )
    }
}

private fun name(a: Art): Int = when (a) {
    Art.FRUEHSTUECK -> R.string.erinnerung_fruehstueck
    Art.MITTAG -> R.string.erinnerung_mittag
    Art.ABEND -> R.string.erinnerung_abend
    Art.WIEGEN -> R.string.erinnerung_wiegen
}
