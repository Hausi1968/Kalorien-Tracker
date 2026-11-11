@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.kh.kalorientracker.ui.detail

import de.kh.kalorientracker.ui.aufHauptThread
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import de.kh.kalorientracker.ui.Meldungen
import de.kh.kalorientracker.ui.Meldung
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kh.kalorientracker.R
import de.kh.kalorientracker.core.Format
import de.kh.kalorientracker.core.Mahlzeiten
import de.kh.kalorientracker.core.Naehrwerte
import de.kh.kalorientracker.data.DiaryEntryEntity
import de.kh.kalorientracker.data.FoodEntity
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.data.naehrwerte100
import de.kh.kalorientracker.ui.AbschnittTitel
import de.kh.kalorientracker.ui.HinweisBox
import de.kh.kalorientracker.ui.Karte
import de.kh.kalorientracker.ui.MakroDreier
import de.kh.kalorientracker.ui.PrimaerButton
import de.kh.kalorientracker.ui.Seite
import de.kh.kalorientracker.ui.suche.herkunft
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Eine wählbare Portion; "g"/"ml" mit 1 Gramm je Einheit steht für freie Mengeneingabe. */
private data class Option(val label: String, val gramm: Double) {
    val frei get() = gramm == 1.0 && (label == "g" || label == "ml")
    fun anzeige(einheit: String) = if (frei) label else "$label (${Format.dezimal(gramm, 0)} $einheit)"
}

/**
 * Lebensmittel ansehen und eintragen (S09) oder einen bestehenden Eintrag bearbeiten (S10).
 * Mit [eintragId] wird der Eintrag geladen, der Button heißt "Speichern" und oben gibt es "Löschen".
 */
@Composable
fun DetailScreen(
    repo: KalorienRepository,
    foodId: String?,
    tag: LocalDate,
    slotVorgabe: Int,
    eintragId: String? = null,
    onZurueck: () -> Unit,
    onEingetragen: () -> Unit,
    onRezeptBearbeiten: ((String) -> Unit)? = null,
) {
    var food by remember { mutableStateOf<FoodEntity?>(null) }
    var eintrag by remember { mutableStateOf<DiaryEntryEntity?>(null) }
    var fehlt by remember { mutableStateOf(false) }
    var optionen by remember { mutableStateOf<List<Option>>(emptyList()) }
    var gewaehlt by remember { mutableIntStateOf(0) }
    var mengeText by remember { mutableStateOf("1") }
    var datum by remember { mutableStateOf(tag) }
    var zeit by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var slotId by remember { mutableIntStateOf(if (slotVorgabe > 0) slotVorgabe else Mahlzeiten.slotFuer(LocalTime.now()).id) }
    var neuePortionOffen by remember { mutableStateOf(false) }
    var umbenennenOffen by remember { mutableStateOf(false) }
    var neuLaden by remember { mutableIntStateOf(0) }
    // Sperre gegen Doppeltipp: solange gespeichert wird, nimmt kein Knopf einen zweiten Tipp an
    var speichert by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val textEingetragen = stringResource(R.string.eingetragen)
    val textGeloescht = stringResource(R.string.tagebuch_geloescht)
    val textGeaendert = stringResource(R.string.eintrag_geaendert)

    /** Speichert einmal (Doppeltipp-sicher), meldet mit Rückgängig und geht zurück. */
    fun einmal(aktion: suspend () -> Meldung) {
        if (speichert) return
        speichert = true
        scope.launch {
            Meldungen.zeige(aktion())
            aufHauptThread(onEingetragen)
        }
    }
    fun neuerEintrag(f: FoodEntity, label: String, gramm: Double, menge: Double, zp: LocalDateTime, slot: Int) = einmal {
        val e = repo.eintragen(f, label, gramm, menge, zp, slot)
        Meldung(textEingetragen.format(e.label, Format.kcal(e.kcal))) { repo.loesche(e) }
    }

    LaunchedEffect(foodId, eintragId, neuLaden) {
        val e = eintragId?.let { repo.eintrag(it) }
        val id = e?.foodRef ?: foodId
        val f = id?.let { repo.lebensmittel(it) }
        if (f == null) { fehlt = true; return@LaunchedEffect }
        val portionen = repo.portionen(f.id)
        val liste = portionen.map { Option(it.label, it.gramm) } + Option(f.unitBase, 1.0)
        if (neuLaden == 0) {
            val wunsch = e?.servingLabel ?: repo.letztePortion(f.id)
            gewaehlt = liste.indexOfFirst { it.label == wunsch }.takeIf { it >= 0 }
                ?: portionen.indexOfFirst { it.isDefault }.takeIf { it >= 0 } ?: 0
            if (e != null) {
                // Menge so umrechnen, dass sie zur gefundenen Portion passt
                val o = liste[gewaehlt]
                val menge = if (o.label == e.servingLabel) e.quantity else (e.gramm ?: 100.0) / o.gramm
                mengeText = Format.dezimal(menge, 2)
                val t = Instant.ofEpochMilli(e.eatenAt).atZone(ZoneId.systemDefault())
                datum = LocalDate.parse(e.day)
                zeit = t.toLocalTime().withSecond(0).withNano(0)
                slotId = e.slotId
            } else {
                mengeText = if (liste[gewaehlt].frei) "100" else "1"
            }
        }
        optionen = liste
        eintrag = e
        food = f
        // Portionsnamen des Herstellers nachladen (z. B. "Becher" statt nur "Portion"), danach neu anzeigen
        if (neuLaden == 0 && repo.portionenNachladen(f)) neuLaden++
    }

    val f = food
    val fokus = LocalFocusManager.current
    val bearbeiten = eintragId != null
    val favoriten by repo.favoritIds.collectAsStateWithLifecycle(initialValue = emptySet())
    Seite(
        titel = f?.name ?: "",
        onZurueck = onZurueck,
        aktionen = {
            if (f != null && f.source == "rezept" && onRezeptBearbeiten != null && !bearbeiten) {
                IconButton(onClick = { onRezeptBearbeiten(f.id) }) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.rezept_bearbeiten))
                }
            }
            if (f != null && !bearbeiten && (f.source == "off" || f.source == "user")) {
                IconButton(onClick = { umbenennenOffen = true }) {
                    Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = stringResource(R.string.umbenennen))
                }
            }
            if (f != null) {
                val istFavorit = f.id in favoriten
                IconButton(onClick = { scope.launch { repo.setzeFavorit(f.id, !istFavorit) } }) {
                    Icon(
                        if (istFavorit) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = stringResource(if (istFavorit) R.string.favorit_entfernen else R.string.favorit_hinzufuegen),
                        tint = if (istFavorit) AppTheme.colors.highlight else LocalContentColor.current,
                    )
                }
            }
            val e = eintrag
            if (bearbeiten && e != null) {
                IconButton(onClick = { einmal { repo.loesche(e); Meldung(textGeloescht.format(e.label)) { repo.wiederherstellen(e) } } }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.eintrag_loeschen, e.label))
                }
            }
        },
    ) { pad ->
        if (fehlt) {
            Box(Modifier.padding(pad).padding(Spacing.l)) { HinweisBox(stringResource(R.string.lebensmittel_fehlt), warnung = true) }
            return@Seite
        }
        if (f == null || optionen.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad))
            return@Seite
        }
        val option = optionen[gewaehlt.coerceIn(optionen.indices)]
        val menge = Format.parseDezimal(mengeText)?.takeIf { it > 0 && it < 100_000 }
        val gramm = menge?.let { it * option.gramm }
        val werte = gramm?.let { f.naehrwerte100.fuerGramm(it) }

        Column(
            Modifier
                .padding(pad)
                .consumeWindowInsets(pad)
                .imePadding() // Tastatur schiebt den Inhalt hoch, "Eintragen" bleibt erreichbar
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(herkunft(f), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted)
            when (f.source) {
                "start" -> HinweisBox(stringResource(R.string.startdaten_hinweis))
                "off" -> HinweisBox(stringResource(R.string.off_hinweis))
            }
            if (!f.naehrwerte100.plausibel()) HinweisBox(stringResource(R.string.unplausibel_hinweis), warnung = true)

            // Portion + Menge
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = mengeText,
                    onValueChange = { mengeText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) },
                    label = { Text(stringResource(R.string.menge)) },
                    singleLine = true,
                    isError = menge == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { fokus.clearFocus() }),
                    modifier = Modifier.widthIn(min = 96.dp, max = 140.dp),
                )
                PortionsAuswahl(optionen.map { it.anzeige(f.unitBase) }, gewaehlt, Modifier.weight(1f)) { i ->
                    val vorherFrei = optionen[gewaehlt].frei
                    gewaehlt = i
                    if (optionen[i].frei != vorherFrei) mengeText = if (optionen[i].frei) "100" else "1"
                }
            }
            if (menge == null) {
                Text(stringResource(R.string.menge_fehlt), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // Ergebnis
            Karte {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        werte?.let { Format.kcal(it.kcal) } ?: "–",
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(Spacing.s))
                    Text(stringResource(R.string.kcal), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    if (werte != null) {
                        Text(
                            stringResource(R.string.einheit_kj, Format.kcal(Naehrwerte.kj(werte.kcal))),
                            style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.m))
                MakroDreier(werte?.fett, werte?.kh, werte?.eiweiss, null)
            }

            // Zeitpunkt: Datum, Mahlzeit, Uhrzeit
            Zeitpunkt(
                datum = datum, zeit = zeit, slotId = slotId,
                onDatum = { datum = it },
                onZeit = { zeit = it; slotId = Mahlzeiten.slotFuer(it).id },
                onSlot = { slotId = it },
            )

            PrimaerButton(
                text = stringResource(if (bearbeiten) R.string.speichern else R.string.eintragen),
                aktiv = menge != null && !speichert,
                onClick = {
                    val m = menge ?: return@PrimaerButton
                    val zp = LocalDateTime.of(datum, zeit)
                    val alt = eintrag
                    if (alt != null) {
                        einmal {
                            repo.aktualisiere(alt, f, option.label, option.gramm, m, zp, slotId)
                            Meldung(textGeaendert.format(alt.label)) { repo.wiederherstellen(alt) }
                        }
                    } else {
                        neuerEintrag(f, option.label, option.gramm, m, zp, slotId)
                    }
                },
            )

            if (!bearbeiten) {
                // Schnell-Eintrag je Portion
                AbschnittTitel(stringResource(R.string.schnell_eintrag))
                optionen.filterNot { it.frei }.forEach { o ->
                    val n = f.naehrwerte100.fuerGramm(o.gramm)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.anzeige(f.unitBase), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.einheit_kcal, Format.kcal(n.kcal)) + " · " +
                                    stringResource(R.string.makro_kurz, Format.gramm(n.fett), Format.gramm(n.kh), Format.gramm(n.eiweiss)),
                                style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted,
                            )
                        }
                        IconButton(enabled = !speichert, onClick = {
                            neuerEintrag(f, o.label, o.gramm, 1.0, LocalDateTime.of(datum, zeit), slotId)
                        }) {
                            Icon(
                                Icons.Rounded.AddCircle,
                                contentDescription = stringResource(R.string.portion_eintragen, o.anzeige(f.unitBase)),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                TextButton(onClick = { neuePortionOffen = true }) { Text(stringResource(R.string.portion_hinzufuegen)) }
            }

            // Nährwerte
            AbschnittTitel(stringResource(R.string.naehrwerte_titel, gramm?.let { Format.dezimal(it, 0) } ?: "–", f.unitBase))
            NaehrwertTabelle(werte, f.naehrwerte100, f.unitBase)
            Spacer(Modifier.height(Spacing.xl))
        }

        if (umbenennenOffen) {
            UmbenennenDialog(
                alt = f.name,
                onAbbrechen = { umbenennenOffen = false },
                onSpeichern = { name ->
                    umbenennenOffen = false
                    scope.launch {
                        repo.benenneUm(f.id, name)
                        neuLaden++
                    }
                },
            )
        }
        if (neuePortionOffen) {
            NeuePortionDialog(
                einheit = f.unitBase,
                onAbbrechen = { neuePortionOffen = false },
                onSpeichern = { label, g ->
                    neuePortionOffen = false
                    scope.launch {
                        repo.neuePortion(f.id, label, g)
                        neuLaden++
                    }
                },
            )
        }
    }
}

@Composable
private fun PortionsAuswahl(labels: List<String>, gewaehlt: Int, modifier: Modifier, onWahl: (Int) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { offen = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(labels[gewaehlt.coerceIn(labels.indices)], modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = stringResource(R.string.portion_waehlen))
        }
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            labels.forEachIndexed { i, l ->
                DropdownMenuItem(text = { Text(l) }, onClick = { onWahl(i); offen = false })
            }
        }
    }
}

@Composable
private fun Zeitpunkt(
    datum: LocalDate,
    zeit: LocalTime,
    slotId: Int,
    onDatum: (LocalDate) -> Unit,
    onZeit: (LocalTime) -> Unit,
    onSlot: (Int) -> Unit,
) {
    var slotOffen by remember { mutableStateOf(false) }
    var datumOffen by remember { mutableStateOf(false) }
    var zeitOffen by remember { mutableStateOf(false) }
    val heute = LocalDate.now()
    val datumText = when (datum) {
        heute -> stringResource(R.string.heute)
        heute.minusDays(1) -> stringResource(R.string.gestern)
        else -> datum.format(DateTimeFormatter.ofPattern("EE, d. MMM", Locale.GERMANY))
    }
    val slot = Mahlzeiten.STANDARD.firstOrNull { it.id == slotId } ?: Mahlzeiten.STANDARD.first()

    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        AssistChip(
            onClick = { datumOffen = true },
            label = { Text(datumText) },
            leadingIcon = { Icon(Icons.Rounded.CalendarToday, contentDescription = null) },
        )
        Box {
            AssistChip(
                onClick = { slotOffen = true },
                label = { Text(slot.name) },
                leadingIcon = { Icon(Icons.Rounded.Restaurant, contentDescription = null) },
                trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = stringResource(R.string.mahlzeit_waehlen)) },
            )
            DropdownMenu(expanded = slotOffen, onDismissRequest = { slotOffen = false }) {
                Mahlzeiten.STANDARD.forEach { s ->
                    DropdownMenuItem(
                        text = { Text("${s.name}  ·  ${Mahlzeiten.fensterText(s)}") },
                        onClick = { onSlot(s.id); slotOffen = false },
                    )
                }
            }
        }
        AssistChip(
            onClick = { zeitOffen = true },
            label = { Text(zeit.format(DateTimeFormatter.ofPattern("HH:mm"))) },
            leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = stringResource(R.string.uhrzeit_waehlen)) },
        )
    }

    if (datumOffen) {
        // Der DatePicker rechnet in UTC-Mitternacht
        val state = rememberDatePickerState(initialSelectedDateMillis = datum.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { datumOffen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDatum(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    datumOffen = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { datumOffen = false }) { Text(stringResource(R.string.abbrechen)) } },
        ) { DatePicker(state = state) }
    }
    if (zeitOffen) {
        val state = rememberTimePickerState(initialHour = zeit.hour, initialMinute = zeit.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { zeitOffen = false },
            confirmButton = {
                TextButton(onClick = { onZeit(LocalTime.of(state.hour, state.minute)); zeitOffen = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = { TextButton(onClick = { zeitOffen = false }) { Text(stringResource(R.string.abbrechen)) } },
            text = { TimePicker(state = state) },
        )
    }
}

/** Name ändern, z. B. wenn Open Food Facts einen unpassenden Namen liefert. Der alte bleibt suchbar. */
@Composable
private fun UmbenennenDialog(alt: String, onAbbrechen: () -> Unit, onSpeichern: (String) -> Unit) {
    var name by remember { mutableStateOf(alt) }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(stringResource(R.string.umbenennen)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(80) }, singleLine = true,
                    label = { Text(stringResource(R.string.produkt_name)) },
                )
                Text(stringResource(R.string.umbenennen_hinweis), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSpeichern(name.trim()) }, enabled = name.isNotBlank() && name.trim() != alt) {
                Text(stringResource(R.string.speichern))
            }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.abbrechen)) } },
    )
}

@Composable
private fun NeuePortionDialog(einheit: String, onAbbrechen: () -> Unit, onSpeichern: (String, Double) -> Unit) {
    var label by remember { mutableStateOf("") }
    var menge by remember { mutableStateOf("") }
    val g = Format.parseDezimal(menge)?.takeIf { it > 0 && it < 100_000 }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(stringResource(R.string.portion_hinzufuegen)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it.take(40) }, singleLine = true,
                    label = { Text(stringResource(R.string.portion_name)) },
                    placeholder = { Text(stringResource(R.string.portion_name_beispiel)) },
                )
                OutlinedTextField(
                    value = menge, onValueChange = { menge = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) },
                    singleLine = true, label = { Text(stringResource(R.string.portion_menge, einheit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { g?.let { onSpeichern(label.trim(), it) } }, enabled = label.isNotBlank() && g != null) {
                Text(stringResource(R.string.speichern))
            }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.abbrechen)) } },
    )
}

@Composable
private fun NaehrwertTabelle(portion: Naehrwerte?, pro100: Naehrwerte, einheit: String) {
    val zeilen = listOf(
        Triple(stringResource(R.string.nw_brennwert), portion?.kcal, pro100.kcal) to "kcal",
        Triple(stringResource(R.string.nw_fett), portion?.fett, pro100.fett) to "g",
        Triple(stringResource(R.string.nw_ges_fett), portion?.gesFett, pro100.gesFett) to "g",
        Triple(stringResource(R.string.nw_kh), portion?.kh, pro100.kh) to "g",
        Triple(stringResource(R.string.nw_zucker), portion?.zucker, pro100.zucker) to "g",
        Triple(stringResource(R.string.nw_ballaststoffe), portion?.ballaststoffe, pro100.ballaststoffe) to "g",
        Triple(stringResource(R.string.nw_eiweiss), portion?.eiweiss, pro100.eiweiss) to "g",
        Triple(stringResource(R.string.nw_salz), portion?.salz, pro100.salz) to "g",
    )
    Karte {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.nw_portion), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted, modifier = Modifier.width(90.dp))
            Text(stringResource(R.string.nw_pro_100, einheit), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted, modifier = Modifier.width(90.dp))
        }
        zeilen.forEachIndexed { i, (werte, e) ->
            val (name, p, h) = werte
            if (i > 0) HorizontalDivider(color = AppTheme.colors.border)
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(wert(p, e), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(90.dp))
                Text(wert(h, e), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted, modifier = Modifier.width(90.dp))
            }
        }
    }
}

private fun wert(x: Double?, einheit: String): String = when {
    x == null -> "k. A."
    einheit == "kcal" -> "${Format.kcal(x)} kcal"
    else -> "${Format.gramm(x)} g"
}
