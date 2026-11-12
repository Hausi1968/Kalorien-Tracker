@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.kh.kalorientracker.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import de.kh.kalorientracker.R
import de.kh.kalorientracker.core.Bedarfsergebnis
import de.kh.kalorientracker.core.Bedarfsrechner
import de.kh.kalorientracker.core.Format
import de.kh.kalorientracker.core.Geschlecht
import de.kh.kalorientracker.core.Koerperdaten
import de.kh.kalorientracker.core.MakroAnteile
import de.kh.kalorientracker.core.Warnung
import de.kh.kalorientracker.core.Ziel
import de.kh.kalorientracker.core.Zielvorgabe
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.data.ProfileEntity
import de.kh.kalorientracker.ui.HinweisBox
import de.kh.kalorientracker.ui.Karte
import de.kh.kalorientracker.ui.PrimaerButton
import de.kh.kalorientracker.ui.Seite
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

private val DATUM = DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT)
private val DATUM_AUSGABE = DateTimeFormatter.ofPattern("dd.MM.uuuu")
private val DATUM_OHNE_PUNKT = DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT)

/** "01.05.1981", "1.5.1981" oder ohne Punkte "01051981" (Samsung-Zifferntastaturen haben oft keinen Punkt). */
internal fun leseDatum(text: String): LocalDate? {
    val t = text.trim()
    return runCatching { LocalDate.parse(t, DATUM) }.getOrNull()
        ?: t.takeIf { it.length == 8 && it.all(Char::isDigit) }?.let { runCatching { LocalDate.parse(it, DATUM_OHNE_PUNKT) }.getOrNull() }
}

/**
 * Bedarfsrechner und Zielsetzung. Beim ersten Start Pflicht (eigene Daten, nichts vorbelegt),
 * später aus dem Profil erreichbar, dann mit den gespeicherten Werten vorbelegt.
 */
@Composable
fun RechnerScreen(
    repo: KalorienRepository,
    erstesMal: Boolean,
    onFertig: () -> Unit,
    onZurueck: (() -> Unit)?,
) {
    var geschlecht by rememberSaveable { mutableStateOf<Geschlecht?>(null) }
    var geburtsdatum by rememberSaveable { mutableStateOf("") }
    var groesse by rememberSaveable { mutableStateOf("") }
    var gewicht by rememberSaveable { mutableStateOf("") }
    var pal by rememberSaveable { mutableDoubleStateOf(0.0) }
    var ziel by rememberSaveable { mutableStateOf(Ziel.ABNEHMEN) }
    var zielgewicht by rememberSaveable { mutableStateOf("") }
    var tempo by rememberSaveable { mutableDoubleStateOf(0.5) }
    var begleitet by rememberSaveable { mutableStateOf(false) }
    var geladen by rememberSaveable { mutableStateOf(erstesMal) }
    val scope = rememberCoroutineScope()

    // Bearbeiten: gespeicherte Werte übernehmen
    LaunchedEffect(Unit) {
        if (geladen) return@LaunchedEffect
        repo.ladeProfil()?.let { p ->
            geschlecht = Geschlecht.valueOf(p.geschlecht)
            geburtsdatum = LocalDate.parse(p.geburtsdatum).format(DATUM_AUSGABE)
            groesse = Format.dezimal(p.groesseCm)
            gewicht = Format.dezimal(repo.ladeLetztesGewicht() ?: p.startGewichtKg)
            pal = p.pal
            ziel = Ziel.valueOf(p.ziel)
            zielgewicht = p.zielgewichtKg?.let { Format.dezimal(it) } ?: ""
            tempo = p.tempoKgProWoche
            begleitet = p.aerztlichBegleitet
        }
        geladen = true
    }

    val heute = LocalDate.now()
    val geburt = leseDatum(geburtsdatum)
    val alter = geburt?.let { Bedarfsrechner.alter(it, heute) }
    val datumOk = alter != null && alter in 14..110
    val cm = Format.parseDezimal(groesse)?.takeIf { it in 100.0..250.0 }
    val kg = Format.parseDezimal(gewicht)?.takeIf { it in 30.0..350.0 }
    val zielKg = if (ziel == Ziel.HALTEN) null else Format.parseDezimal(zielgewicht)?.takeIf { it in 30.0..350.0 }

    val koerper = if (geschlecht != null && datumOk && cm != null && kg != null && pal > 0) {
        Koerperdaten(geschlecht!!, geburt!!, cm, kg, pal)
    } else null
    val ergebnis: Bedarfsergebnis? = koerper?.let {
        runCatching { Bedarfsrechner.berechne(it, Zielvorgabe(ziel, zielKg, tempo, begleitet && ziel == Ziel.ABNEHMEN), heute) }.getOrNull()
    }

    Seite(
        titel = stringResource(if (erstesMal) R.string.rechner_titel_start else R.string.rechner_titel),
        onZurueck = onZurueck,
        insets = if (erstesMal) WindowInsets.safeDrawing else WindowInsets(0, 0, 0, 0),
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                // Was die Seite schon an Rand (inkl. Tastatur) abzieht, nicht noch einmal abziehen
                .consumeWindowInsets(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            if (erstesMal) {
                Text(stringResource(R.string.rechner_willkommen), style = MaterialTheme.typography.bodyLarge)
                HinweisBox(stringResource(R.string.rechner_datenschutz))
            }

            Karte(titel = stringResource(R.string.rechner_ueber_dich)) {
                Text(stringResource(R.string.geschlecht), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FilterChip(geschlecht == Geschlecht.FRAU, { geschlecht = Geschlecht.FRAU }, { Text(stringResource(R.string.frau)) })
                    FilterChip(geschlecht == Geschlecht.MANN, { geschlecht = Geschlecht.MANN }, { Text(stringResource(R.string.mann)) })
                }
                Spacer(Modifier.height(Spacing.s))
                var kalenderOffen by remember { mutableStateOf(false) }
                Zahlenfeld(
                    geburtsdatum, { geburtsdatum = it }, stringResource(R.string.geburtsdatum),
                    stringResource(R.string.geburtsdatum_hinweis), fehler = geburtsdatum.isNotBlank() && !datumOk,
                    fehlerText = stringResource(if (geburt == null) R.string.fehler_datum else R.string.fehler_alter),
                    tastatur = KeyboardType.Number, erlaubt = { c -> c.isDigit() || c == '.' },
                    rechts = {
                        IconButton(onClick = { kalenderOffen = true }) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = stringResource(R.string.geburtsdatum_kalender))
                        }
                    },
                )
                if (kalenderOffen) {
                    GeburtsdatumKalender(geburt, onAbbrechen = { kalenderOffen = false }) {
                        geburtsdatum = it.format(DATUM_AUSGABE); kalenderOffen = false
                    }
                }
                Zahlenfeld(
                    groesse, { groesse = it }, stringResource(R.string.groesse_cm), null, fehler = groesse.isNotBlank() && cm == null,
                    fehlerText = stringResource(R.string.fehler_groesse),
                )
                Zahlenfeld(
                    gewicht, { gewicht = it }, stringResource(R.string.gewicht_kg), null, fehler = gewicht.isNotBlank() && kg == null,
                    fehlerText = stringResource(R.string.fehler_gewicht),
                )
            }

            Karte(titel = stringResource(R.string.aktivitaet)) {
                val texte = listOf(
                    R.string.pal_12, R.string.pal_14, R.string.pal_16, R.string.pal_18, R.string.pal_20,
                )
                Column(Modifier.selectableGroup()) {
                    Bedarfsrechner.PAL_STUFEN.forEachIndexed { i, stufe ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = pal == stufe, role = Role.RadioButton) { pal = stufe }
                                .padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = pal == stufe, onClick = null)
                            Spacer(Modifier.padding(start = Spacing.s))
                            Text(stringResource(texte[i]), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Karte(titel = stringResource(R.string.dein_ziel)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FilterChip(ziel == Ziel.ABNEHMEN, { ziel = Ziel.ABNEHMEN }, { Text(stringResource(R.string.ziel_abnehmen)) })
                    FilterChip(ziel == Ziel.HALTEN, { ziel = Ziel.HALTEN }, { Text(stringResource(R.string.ziel_halten)) })
                    FilterChip(ziel == Ziel.ZUNEHMEN, { ziel = Ziel.ZUNEHMEN }, { Text(stringResource(R.string.ziel_zunehmen)) })
                }
                if (ziel != Ziel.HALTEN) {
                    Spacer(Modifier.height(Spacing.s))
                    Zahlenfeld(
                        zielgewicht, { zielgewicht = it }, stringResource(R.string.zielgewicht_kg),
                        stringResource(R.string.zielgewicht_hinweis), fehler = zielgewicht.isNotBlank() && zielKg == null,
                        fehlerText = stringResource(R.string.fehler_gewicht),
                    )
                    Text(stringResource(R.string.tempo), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Bedarfsrechner.TEMPO_STUFEN.forEach { t ->
                            FilterChip(tempo == t, { tempo = t }, { Text(stringResource(R.string.tempo_stufe, Format.dezimal(t, 2))) })
                        }
                    }
                    if (ziel == Ziel.ABNEHMEN) {
                        Row(
                            Modifier.fillMaxWidth().toggleable(value = begleitet, role = Role.Checkbox, onValueChange = { begleitet = it }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = begleitet, onCheckedChange = null)
                            Spacer(Modifier.width(Spacing.s))
                            Text(stringResource(R.string.aerztlich_begleitet), style = MaterialTheme.typography.bodyLarge)
                        }
                        Text(
                            stringResource(R.string.aerztlich_begleitet_hinweis),
                            style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted,
                        )
                    }
                }
            }

            if (ergebnis != null) {
                ErgebnisKarte(ergebnis, koerper?.geschlecht ?: Geschlecht.FRAU)
            } else {
                HinweisBox(stringResource(R.string.rechner_unvollstaendig))
            }

            PrimaerButton(
                text = stringResource(R.string.ziel_uebernehmen),
                aktiv = ergebnis != null,
                onClick = {
                    val k = koerper ?: return@PrimaerButton
                    val e = ergebnis ?: return@PrimaerButton
                    val m = MakroAnteile.STANDARD
                    scope.launch {
                        repo.speichereProfil(
                            ProfileEntity(
                                geschlecht = k.geschlecht.name,
                                geburtsdatum = k.geburtsdatum.toString(),
                                groesseCm = k.groesseCm,
                                pal = k.pal,
                                ziel = ziel.name,
                                zielgewichtKg = zielKg,
                                tempoKgProWoche = tempo,
                                aerztlichBegleitet = begleitet && ziel == Ziel.ABNEHMEN,
                                startGewichtKg = k.gewichtKg,
                                startDatum = heute.toString(),
                                tageszielKcal = e.tagesziel,
                                fettProzent = m.fettProzent,
                                khProzent = m.khProzent,
                                eiweissProzent = m.eiweissProzent,
                                updatedAt = System.currentTimeMillis(),
                            ),
                            gewichtKg = k.gewichtKg,
                        )
                        onFertig()
                    }
                },
            )
            Text(
                stringResource(R.string.rechner_formel_hinweis),
                style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted,
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun ErgebnisKarte(e: Bedarfsergebnis, geschlecht: Geschlecht) {
    Karte(titel = stringResource(R.string.ergebnis)) {
        Text(
            stringResource(R.string.einheit_kcal, Format.kcal(e.tagesziel)),
            style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(R.string.tagesziel_label), style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.textMuted)
        Spacer(Modifier.height(Spacing.m))
        ErgebnisZeile(stringResource(R.string.grundumsatz), stringResource(R.string.einheit_kcal, Format.kcal(e.grundumsatz)))
        ErgebnisZeile(stringResource(R.string.gesamtbedarf), stringResource(R.string.einheit_kcal, Format.kcal(e.gesamtbedarf)))
        ErgebnisZeile(
            stringResource(if (e.defizit >= 0) R.string.defizit else R.string.ueberschuss),
            stringResource(R.string.einheit_kcal, Format.kcal(kotlin.math.abs(e.defizit))),
        )
        val datum = e.zieldatum
        val wochen = e.wochenBisZiel
        if (datum != null && wochen != null) {
            ErgebnisZeile(
                stringResource(R.string.zieldatum),
                stringResource(R.string.zieldatum_wert, datum.format(DATUM_AUSGABE), wochen),
            )
        }
        if (e.warnungen.isNotEmpty()) Spacer(Modifier.height(Spacing.s))
        if (Warnung.UNTER_GRUNDUMSATZ in e.warnungen) HinweisBox(stringResource(R.string.warnung_grundumsatz), warnung = true)
        if (Warnung.BEGLEITET_UNTER_GRUNDUMSATZ in e.warnungen) HinweisBox(stringResource(R.string.warnung_begleitet), warnung = true)
        if (Warnung.UNTER_MINIMUM in e.warnungen) {
            val min = if (geschlecht == Geschlecht.MANN) Bedarfsrechner.MINIMUM_MANN else Bedarfsrechner.MINIMUM_FRAU
            HinweisBox(stringResource(R.string.warnung_minimum, Format.kcal(min)), warnung = true)
        }
        if (Warnung.ZIELGEWICHT_UNPASSEND in e.warnungen) HinweisBox(stringResource(R.string.warnung_zielgewicht), warnung = true)
    }
}

@Composable
private fun ErgebnisZeile(name: String, wert: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(wert, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Zahlenfeld(
    wert: String,
    onWert: (String) -> Unit,
    label: String,
    hinweis: String?,
    fehler: Boolean,
    fehlerText: String? = null,
    tastatur: KeyboardType = KeyboardType.Decimal,
    erlaubt: (Char) -> Boolean = { it.isDigit() || it == ',' || it == '.' },
    rechts: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = wert,
        onValueChange = { neu -> onWert(neu.filter(erlaubt).take(10)) },
        label = { Text(label) },
        supportingText = if (fehler) {
            { Text(fehlerText ?: stringResource(R.string.eingabe_pruefen)) }
        } else if (hinweis != null) {
            { Text(hinweis) }
        } else null,
        isError = fehler,
        trailingIcon = rechts,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = tastatur, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Geburtsdatum aus dem Kalender: startet bei der Eingabe oder beim Jahr 1980, Jahr oben antippen zum schnellen Wechsel. */
@Composable
private fun GeburtsdatumKalender(vorher: LocalDate?, onAbbrechen: () -> Unit, onWahl: (LocalDate) -> Unit) {
    val start = (vorher ?: LocalDate.of(1980, 1, 1)).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    val heute = LocalDate.now()
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = start,
        yearRange = (heute.year - 110)..(heute.year - 14),
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onAbbrechen,
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = state.selectedDateMillis != null, onClick = {
                state.selectedDateMillis?.let { onWahl(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()) }
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.abbrechen)) } },
    ) { androidx.compose.material3.DatePicker(state = state) }
}
