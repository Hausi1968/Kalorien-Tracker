@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package de.kh.kalorientracker.ui.tagebuch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kh.kalorientracker.R
import de.kh.kalorientracker.core.Format
import de.kh.kalorientracker.core.Mahlzeiten
import de.kh.kalorientracker.data.DiaryEntryEntity
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.ui.Karte
import de.kh.kalorientracker.ui.Meldung
import de.kh.kalorientracker.ui.Meldungen
import de.kh.kalorientracker.ui.Seite
import de.kh.kalorientracker.ui.aufHauptThread
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATUM_KURZ = DateTimeFormatter.ofPattern("EE, dd.MM.yy", Locale.GERMANY)

private enum class Ziel { VERSCHIEBEN, KOPIEREN }

/**
 * Einträge eines Tages auswählen (einzeln oder ganze Mahlzeit) und gesammelt
 * verschieben, kopieren oder löschen – in einen anderen Tag und/oder eine andere Mahlzeit.
 */
@Composable
fun AuswahlScreen(repo: KalorienRepository, tag: LocalDate, vorauswahl: String?, onFertig: () -> Unit) {
    val daten by remember(tag) { repo.tag(tag) }.collectAsStateWithLifecycle(initialValue = null)
    var gewaehlt by rememberSaveable { mutableStateOf(listOfNotNull(vorauswahl?.takeIf { it.isNotEmpty() })) }
    var dialog by remember { mutableStateOf<Ziel?>(null) }
    var arbeitet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val textVerschoben = stringResource(R.string.auswahl_verschoben)
    val textKopiert = stringResource(R.string.auswahl_kopiert)
    val textGeloescht = stringResource(R.string.auswahl_geloescht)

    val alle = daten?.eintraege.orEmpty()
    val auswahl = alle.filter { it.id in gewaehlt }

    fun fertig(aktion: suspend () -> Meldung) {
        if (arbeitet || auswahl.isEmpty()) return
        arbeitet = true
        scope.launch {
            Meldungen.zeige(aktion())
            aufHauptThread(onFertig)
        }
    }

    dialog?.let { art ->
        ZielDialog(
            titel = stringResource(if (art == Ziel.VERSCHIEBEN) R.string.auswahl_verschieben else R.string.auswahl_kopieren),
            startTag = tag,
            startSlot = auswahl.map { it.slotId }.distinct().singleOrNull() ?: 0,
            onAbbrechen = { dialog = null },
            onOk = { nachTag, slot ->
                dialog = null
                val n = auswahl.size
                if (art == Ziel.VERSCHIEBEN) {
                    fertig {
                        val vorher = auswahl
                        repo.verschiebe(vorher, nachTag, slot)
                        Meldung(textVerschoben.format(n)) { vorher.forEach { repo.wiederherstellen(it) } }
                    }
                } else {
                    fertig {
                        val kopien = repo.kopiereMahlzeit(auswahl, nachTag, slot)
                        Meldung(textKopiert.format(n)) { kopien.forEach { repo.loesche(it) } }
                    }
                }
            },
        )
    }

    val mahlzeitenReihe = de.kh.kalorientracker.ui.mahlzeitenSortiert()
    Seite(titel = tag.format(DATUM_KURZ), onZurueck = onFertig) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (daten != null && alle.isEmpty()) {
                    item { Text(stringResource(R.string.uebersicht_leer), color = AppTheme.colors.textMuted) }
                }
                items(mahlzeitenReihe.filter { s -> alle.any { it.slotId == s.id } }, key = { it.id }) { slot ->
                    val inSlot = alle.filter { it.slotId == slot.id }
                    val zahl = inSlot.count { it.id in gewaehlt }
                    Karte {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                gewaehlt = if (zahl == inSlot.size) gewaehlt - inSlot.map { it.id }.toSet()
                                else (gewaehlt + inSlot.map { it.id }).distinct()
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(slot.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TriStateCheckbox(
                                state = when (zahl) {
                                    0 -> ToggleableState.Off
                                    inSlot.size -> ToggleableState.On
                                    else -> ToggleableState.Indeterminate
                                },
                                onClick = null,
                            )
                        }
                        inSlot.forEach { e ->
                            HorizontalDivider(color = AppTheme.colors.border)
                            AuswahlZeile(e, e.id in gewaehlt) {
                                gewaehlt = if (e.id in gewaehlt) gewaehlt - e.id else gewaehlt + e.id
                            }
                        }
                    }
                }
            }
            BottomAppBar {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val aktiv = auswahl.isNotEmpty() && !arbeitet
                    AktionsKnopf(stringResource(R.string.auswahl_verschieben), Icons.AutoMirrored.Rounded.DriveFileMove, aktiv) { dialog = Ziel.VERSCHIEBEN }
                    AktionsKnopf(stringResource(R.string.auswahl_kopieren), Icons.Rounded.ContentCopy, aktiv) { dialog = Ziel.KOPIEREN }
                    AktionsKnopf(stringResource(R.string.loeschen), Icons.Rounded.Delete, aktiv) {
                        fertig {
                            val weg = auswahl
                            weg.forEach { repo.loesche(it) }
                            Meldung(textGeloescht.format(weg.size)) { weg.forEach { repo.wiederherstellen(it) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuswahlZeile(e: DiaryEntryEntity, an: Boolean, onUmschalten: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onUmschalten).padding(vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(e.label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    e.servingLabel?.let { "${Format.dezimal(e.quantity)} × $it" },
                    stringResource(R.string.einheit_kcal, Format.kcal(e.kcal)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted,
            )
        }
        Checkbox(checked = an, onCheckedChange = null)
    }
}

@Composable
private fun AktionsKnopf(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, aktiv: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = aktiv) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Zieltag und Mahlzeit wählen. Mahlzeit 0 = jede bleibt in ihrer bisherigen Mahlzeit. */
@Composable
private fun ZielDialog(titel: String, startTag: LocalDate, startSlot: Int, onAbbrechen: () -> Unit, onOk: (LocalDate, Int) -> Unit) {
    var tag by remember { mutableStateOf(startTag) }
    var slot by remember { mutableIntStateOf(startSlot) }
    var kalender by remember { mutableStateOf(false) }
    if (kalender) {
        val zustand = rememberDatePickerState(initialSelectedDateMillis = tag.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { kalender = false },
            confirmButton = {
                TextButton(onClick = {
                    zustand.selectedDateMillis?.let { tag = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    kalender = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { kalender = false }) { Text(stringResource(R.string.abbrechen)) } },
        ) { DatePicker(zustand) }
        return
    }
    val heute = LocalDate.now()
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(titel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.auswahl_tag), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FilterChip(tag == heute.minusDays(1), { tag = heute.minusDays(1) }, { Text(stringResource(R.string.gestern)) })
                    FilterChip(tag == heute, { tag = heute }, { Text(stringResource(R.string.heute)) })
                    FilterChip(tag == heute.plusDays(1), { tag = heute.plusDays(1) }, { Text(stringResource(R.string.morgen)) })
                }
                OutlinedButton(onClick = { kalender = true }, modifier = Modifier.fillMaxWidth()) { Text(tag.format(DATUM_KURZ)) }
                Text(stringResource(R.string.auswahl_mahlzeit), style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FilterChip(slot == 0, { slot = 0 }, { Text(stringResource(R.string.auswahl_wie_bisher)) })
                    Mahlzeiten.STANDARD.forEach { s -> FilterChip(slot == s.id, { slot = s.id }, { Text(s.name) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onOk(tag, slot) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.abbrechen)) } },
    )
}
