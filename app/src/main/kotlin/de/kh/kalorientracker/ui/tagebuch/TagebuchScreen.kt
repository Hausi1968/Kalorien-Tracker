@file:OptIn(ExperimentalMaterial3Api::class)

package de.kh.kalorientracker.ui.tagebuch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kh.kalorientracker.R
import de.kh.kalorientracker.core.Format
import de.kh.kalorientracker.core.MahlzeitSlot
import de.kh.kalorientracker.core.Mahlzeiten
import de.kh.kalorientracker.data.ActivityEntryEntity
import de.kh.kalorientracker.data.DiaryEntryEntity
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.ui.AktivitaetDialog
import de.kh.kalorientracker.ui.BilanzGleichung
import de.kh.kalorientracker.ui.Initiale
import de.kh.kalorientracker.ui.Karte
import de.kh.kalorientracker.ui.MakroDreier
import de.kh.kalorientracker.ui.Seite
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val DE = Locale.GERMANY
private val UHRZEIT = DateTimeFormatter.ofPattern("HH:mm", DE)

@Composable
fun TagebuchScreen(repo: KalorienRepository, onSuche: (LocalDate, Int) -> Unit, onEintrag: (String) -> Unit) {
    val heute = LocalDate.now()
    var tag by rememberSaveable { mutableStateOf(heute) }
    val daten by remember(tag) { repo.tag(tag) }.collectAsStateWithLifecycle(initialValue = null)
    val montag = tag.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val woche by remember(montag) { repo.kcalProTag(montag, montag.plusDays(6)) }
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val aktuellerSlot = Mahlzeiten.slotFuer(LocalTime.now()).id
    val aktivitaeten by remember(tag) { repo.aktivitaeten(tag) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var aktivitaetOffen by remember { mutableStateOf(false) }
    val wasser by remember(tag) { repo.wasser(tag) }.collectAsStateWithLifecycle(initialValue = 0)
    val gestern by remember(tag) { repo.tag(tag.minusDays(1)) }.collectAsStateWithLifecycle(initialValue = null)
    val textKopiert = stringResource(R.string.mahlzeit_kopiert)

    val textGeloescht = stringResource(R.string.tagebuch_geloescht)
    val textRueckgaengig = stringResource(R.string.rueckgaengig)

    Seite(
        titel = tagTitel(tag, heute),
        snackbar = snackbar,
        aktionen = {
            IconButton(onClick = { tag = tag.minusDays(1) }) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.tag_vorher))
            }
            IconButton(onClick = { tag = tag.plusDays(1) }) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.tag_nachher))
            }
        },
        fab = {
            FloatingActionButton(
                onClick = { onSuche(tag, 0) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.extraLarge,
            ) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.eintrag_hinzufuegen)) }
        },
    ) { pad ->
        if (aktivitaetOffen) {
            AktivitaetDialog(
                onAbbrechen = { aktivitaetOffen = false },
                onSpeichern = { name, kcal, min ->
                    aktivitaetOffen = false
                    scope.launch { repo.speichereAktivitaet(name, kcal, min, tag) }
                },
            )
        }
        LazyColumn(
            modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.s, bottom = 96.dp), // Platz für den Plus-Knopf
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item {
                Wochenleiste(
                    montag = montag, ausgewaehlt = tag, heute = heute,
                    kcal = woche, ziel = daten?.profil?.tageszielKcal ?: 0,
                    onWaehle = { tag = it },
                )
            }
            val d = daten
            if (d == null) {
                item { Box(Modifier.fillMaxWidth().height(120.dp).background(AppTheme.colors.surface2, MaterialTheme.shapes.large)) }
            } else {
                item { BilanzGleichung(d.bilanz) }
                item { Karte { MakroDreier(d.summe.fett, d.summe.kh, d.summe.eiweiss, d.makroZiel) } }
                items(Mahlzeiten.STANDARD, key = { it.id }) { slot ->
                    MahlzeitKarte(
                        slot = slot,
                        eintraege = d.imSlot(slot.id),
                        aktuell = tag == heute && slot.id == aktuellerSlot,
                        onPlus = { onSuche(tag, slot.id) },
                        onEintrag = onEintrag,
                        gesternAnzahl = gestern?.imSlot(slot.id)?.size ?: 0,
                        onVonGestern = {
                            val quelle = gestern?.imSlot(slot.id).orEmpty()
                            scope.launch {
                                val n = repo.kopiereMahlzeit(quelle, tag, slot.id)
                                snackbar.showSnackbar(textKopiert.format(n, slot.name))
                            }
                        },
                        onLoeschen = { e ->
                            scope.launch {
                                repo.loesche(e)
                                val r = snackbar.showSnackbar(
                                    textGeloescht.format(e.label), textRueckgaengig, duration = SnackbarDuration.Short,
                                )
                                if (r == SnackbarResult.ActionPerformed) repo.wiederherstellen(e)
                            }
                        },
                    )
                }
                item {
                    WasserKarte(
                        ml = wasser,
                        onPlus = { scope.launch { repo.trinke(250, tag) } },
                        onMinus = { scope.launch { repo.wasserZurueck(tag) } },
                    )
                }
                item {
                    AktivitaetenKarte(
                        aktivitaeten,
                        onPlus = { aktivitaetOffen = true },
                        onLoeschen = { a ->
                            scope.launch {
                                repo.loescheAktivitaet(a)
                                val r = snackbar.showSnackbar(textGeloescht.format(a.label), textRueckgaengig, duration = SnackbarDuration.Short)
                                if (r == SnackbarResult.ActionPerformed) repo.wiederherstellenAktivitaet(a)
                            }
                        },
                    )
                }
                item { Spacer(Modifier.height(88.dp)) } // Platz für den FAB
            }
        }
    }
}

@Composable
private fun tagTitel(tag: LocalDate, heute: LocalDate): String = when (tag) {
    heute -> stringResource(R.string.heute)
    heute.minusDays(1) -> stringResource(R.string.gestern)
    heute.plusDays(1) -> stringResource(R.string.morgen)
    else -> tag.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", DE))
}

@Composable
private fun Wochenleiste(
    montag: LocalDate,
    ausgewaehlt: LocalDate,
    heute: LocalDate,
    kcal: Map<LocalDate, Double>,
    ziel: Int,
    onWaehle: (LocalDate) -> Unit,
) {
    val kurz = stringArrayResource(R.array.wochentage_kurz)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0..6) {
            val t = montag.plusDays(i.toLong())
            val anteil = if (ziel > 0) ((kcal[t] ?: 0.0) / ziel).toFloat() else 0f
            val gewaehlt = t == ausgewaehlt
            val prozent = (anteil * 100).toInt()
            val ansage = stringResource(
                R.string.wochentag_ansage,
                t.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", DE)), prozent,
            )
            Column(
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Tab) { onWaehle(t) }
                    .clearAndSetSemantics { contentDescription = ansage; selected = gewaehlt }
                    .alpha(if (t.isAfter(heute)) 0.5f else 1f)
                    .padding(vertical = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(kurz[i], style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
                Spacer(Modifier.height(Spacing.xs))
                Box(
                    Modifier
                        .size(40.dp)
                        .background(if (gewaehlt) AppTheme.colors.accentSoft else MaterialTheme.colorScheme.background, MaterialTheme.shapes.extraLarge),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { anteil.coerceIn(0f, 1f) },
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = if (anteil > 1f) AppTheme.colors.warning else MaterialTheme.colorScheme.primary,
                        trackColor = AppTheme.colors.surface2,
                        gapSize = 0.dp,
                    )
                    Text(
                        t.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (t == heute) FontWeight.ExtraBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun MahlzeitKarte(
    slot: MahlzeitSlot,
    eintraege: List<DiaryEntryEntity>,
    aktuell: Boolean,
    onPlus: () -> Unit,
    onEintrag: (String) -> Unit,
    gesternAnzahl: Int,
    onVonGestern: () -> Unit,
    onLoeschen: (DiaryEntryEntity) -> Unit,
) {
    val kcal = eintraege.sumOf { it.kcal }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (aktuell) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(slot.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(Mahlzeiten.fensterText(slot), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
            }
            if (eintraege.isNotEmpty()) {
                Text(
                    stringResource(R.string.einheit_kcal, Format.kcal(kcal)),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            IconButton(onClick = onPlus) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.zu_mahlzeit_hinzufuegen, slot.name),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (eintraege.isEmpty() && gesternAnzahl > 0) {
            TextButton(onClick = onVonGestern, modifier = Modifier.padding(start = Spacing.s)) {
                Text(stringResource(R.string.von_gestern, gesternAnzahl))
            }
        }
        if (eintraege.isNotEmpty()) {
            HorizontalDivider(color = AppTheme.colors.border)
            eintraege.forEach { e -> EintragZeile(e, onClick = { if (e.foodRef != null) onEintrag(e.id) }, onLoeschen = { onLoeschen(e) }) }
            TextButton(onClick = onPlus, modifier = Modifier.padding(start = Spacing.s)) {
                Text(stringResource(R.string.lebensmittel_hinzufuegen))
            }
        }
    }
}

@Composable
private fun EintragZeile(e: DiaryEntryEntity, onClick: () -> Unit, onLoeschen: () -> Unit) {
    val zeit = Instant.ofEpochMilli(e.eatenAt).atZone(ZoneId.systemDefault()).toLocalTime().format(UHRZEIT)
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.eintrag_bearbeiten), onClick = onClick).padding(start = Spacing.l, top = Spacing.s, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Initiale(e.label)
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(e.label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                mengenText(e) + " · " + stringResource(
                    R.string.makro_kurz, Format.gramm(e.fett), Format.gramm(e.kh), Format.gramm(e.eiweiss),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textMuted,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.s))
        Column(horizontalAlignment = Alignment.End) {
            Text(Format.kcal(e.kcal), style = MaterialTheme.typography.titleSmall)
            Text(zeit, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
        }
        IconButton(onClick = onLoeschen) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.eintrag_loeschen, e.label),
                tint = AppTheme.colors.textMuted,
            )
        }
    }
}

/** Wasser: Summe, Fortschritt zu 2 Litern, +250 ml mit einem Tipp, letzten Eintrag zurücknehmen. */
@Composable
private fun WasserKarte(ml: Int, onPlus: () -> Unit, onMinus: () -> Unit) {
    val ziel = 2000
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.wasser), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.wasser_stand, Format.dezimal(ml / 1000.0, 2), Format.dezimal(ziel / 1000.0, 1)),
                    style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted,
                )
                Spacer(Modifier.height(Spacing.xs))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (ml.toFloat() / ziel).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = AppTheme.colors.water, trackColor = AppTheme.colors.surface2, drawStopIndicator = {},
                )
            }
            if (ml > 0) {
                IconButton(onClick = onMinus) {
                    Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.wasser_zurueck), tint = AppTheme.colors.textMuted)
                }
            }
            TextButton(onClick = onPlus) { Text(stringResource(R.string.wasser_plus)) }
        }
    }
}

@Composable
private fun AktivitaetenKarte(liste: List<ActivityEntryEntity>, onPlus: () -> Unit, onLoeschen: (ActivityEntryEntity) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.aktivitaeten), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (liste.isNotEmpty()) {
                Text(
                    "+" + stringResource(R.string.einheit_kcal, Format.kcal(liste.sumOf { it.kcal })),
                    style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.burned,
                )
            }
            IconButton(onClick = onPlus) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.aktivitaet_eintragen), tint = MaterialTheme.colorScheme.primary)
            }
        }
        liste.forEach { a ->
            HorizontalDivider(color = AppTheme.colors.border)
            Row(Modifier.fillMaxWidth().padding(start = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.label, style = MaterialTheme.typography.bodyLarge)
                    a.minutes?.let { Text(stringResource(R.string.minuten_wert, Format.dezimal(it, 0)), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted) }
                }
                Text(Format.kcal(a.kcal), style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.burned)
                IconButton(onClick = { onLoeschen(a) }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.eintrag_loeschen, a.label), tint = AppTheme.colors.textMuted)
                }
            }
        }
    }
}

/** "1 × Stück (mittel)" oder bei freier Menge "250 g". */
fun mengenText(e: DiaryEntryEntity): String {
    val label = e.servingLabel ?: return ""
    return if (label == "g" || label == "ml") "${Format.dezimal(e.quantity)} $label"
    else "${Format.dezimal(e.quantity)} × $label"
}
