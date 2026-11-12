@file:OptIn(ExperimentalMaterial3Api::class)

package de.kh.kalorientracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.kh.kalorientracker.core.Tagesbilanz
import de.kh.kalorientracker.update.Aktualisierung
import de.kh.kalorientracker.update.NeueVersion
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kh.kalorientracker.R
import de.kh.kalorientracker.core.Bedarfsrechner
import de.kh.kalorientracker.core.Format
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Übersicht (S04): Kalorien-Ring, Makros und die Woche als Balken. */
@Composable
fun UebersichtScreen(repo: KalorienRepository, onZumTagebuch: () -> Unit) {
    val heute = remember { LocalDate.now() }
    val montag = remember(heute) { heute.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val d by remember(heute) { repo.tag(heute) }.collectAsStateWithLifecycle(initialValue = null)
    val woche by remember(montag) { repo.kcalProTag(montag, montag.plusDays(6)) }.collectAsStateWithLifecycle(initialValue = emptyMap())
    Seite(titel = stringResource(R.string.tab_uebersicht)) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            val daten = d ?: return@Column
            Karte {
                KalorienRing(daten.bilanz, Modifier.fillMaxWidth())
            }
            Karte(titel = stringResource(R.string.makros_heute)) {
                MakroDreier(daten.summe.fett, daten.summe.kh, daten.summe.eiweiss, daten.makroZiel)
            }
            Karte(titel = stringResource(R.string.diese_woche)) {
                WochenBalken(montag, heute, woche, daten.profil?.tageszielKcal ?: 0)
            }
            PrimaerButton(stringResource(R.string.zum_tagebuch), onZumTagebuch)
        }
    }
}

/** Großer Ring: Mitte "übrig", links gegessen, rechts verbrannt, unten Ziel. Über Ziel: Rest in warning. */
@Composable
fun KalorienRing(b: Tagesbilanz, modifier: Modifier = Modifier) {
    val spur = AppTheme.colors.surface2
    val fuellung = if (b.zuViel) AppTheme.colors.warning else MaterialTheme.colorScheme.primary
    val ziel = animateFloatAsState(b.anteil.coerceIn(0f, 1f), animationSpec = tween(600), label = "ring")
    val ansage = stringResource(
        R.string.bilanz_ansage, Format.kcal(abs(b.uebrig)),
        stringResource(if (b.zuViel) R.string.bilanz_zu_viel else R.string.bilanz_uebrig),
        Format.kcal(b.ziel), Format.kcal(b.gegessen), Format.kcal(b.verbrannt),
    )
    Row(modifier.clearAndSetSemantics { contentDescription = ansage }, verticalAlignment = Alignment.CenterVertically) {
        SeitenZahl(Format.kcal(b.gegessen), stringResource(R.string.bilanz_gegessen), Modifier.weight(1f))
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(200.dp)) {
                val dicke = 22.dp.toPx()
                val inset = dicke / 2
                val bogen = androidx.compose.ui.geometry.Size(size.width - dicke, size.height - dicke)
                val start = Offset(inset, inset)
                drawArc(spur, 135f, 270f, false, start, bogen, style = Stroke(dicke, cap = StrokeCap.Round))
                drawArc(fuellung, 135f, 270f * ziel.value, false, start, bogen, style = Stroke(dicke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Format.kcal(abs(b.uebrig)), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold,
                    color = if (b.zuViel) AppTheme.colors.warning else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(if (b.zuViel) R.string.bilanz_zu_viel else R.string.bilanz_uebrig),
                    style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.textMuted,
                )
                Text(
                    stringResource(R.string.ziel_wert, Format.kcal(b.ziel)),
                    style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted,
                )
            }
        }
        SeitenZahl(Format.kcal(b.verbrannt), stringResource(R.string.bilanz_verbrannt), Modifier.weight(1f))
    }
}

@Composable
private fun SeitenZahl(zahl: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(zahl, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
    }
}

/** Sieben Balken Mo–So, Ziellinie gestrichelt; zu viel in warning. */
@Composable
private fun WochenBalken(montag: LocalDate, heute: LocalDate, kcal: Map<LocalDate, Double>, ziel: Int) {
    val kurz = stringArrayResource(R.array.wochentage_kurz)
    val normal = MaterialTheme.colorScheme.primary
    val warn = AppTheme.colors.warning
    val spur = AppTheme.colors.surface2
    val linie = AppTheme.colors.highlight
    val werte = (0..6).map { kcal[montag.plusDays(it.toLong())] ?: 0.0 }
    val maxWert = maxOf(werte.maxOrNull() ?: 0.0, ziel * 1.2, 1.0)
    val summe = werte.sum()
    val tage = (0..6).count { !montag.plusDays(it.toLong()).isAfter(heute) }
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val breite = size.width / 7f
        werte.forEachIndexed { i, w ->
            val x = i * breite + breite * 0.2f
            val bw = breite * 0.6f
            drawRoundRect(spur, Offset(x, 0f), androidx.compose.ui.geometry.Size(bw, size.height), CornerRadius(12f))
            val h = (w / maxWert * size.height).toFloat()
            if (h > 0) drawRoundRect(if (ziel > 0 && w > ziel) warn else normal, Offset(x, size.height - h), androidx.compose.ui.geometry.Size(bw, h), CornerRadius(12f))
        }
        if (ziel > 0) {
            val y = (size.height * (1 - ziel / maxWert)).toFloat()
            drawLine(linie, Offset(0f, y), Offset(size.width, y), strokeWidth = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        kurz.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted, textAlign = TextAlign.Center) }
    }
    Spacer(Modifier.height(Spacing.s))
    Text(
        stringResource(R.string.woche_schnitt, Format.kcal(if (tage > 0) summe / tage else 0.0), Format.kcal(ziel)),
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Profil (S03) im Durchstich: eigene Daten und Ziel ansehen, Ziel neu berechnen. */
@Composable
fun ProfilScreen(
    repo: KalorienRepository,
    aktualisierung: Aktualisierung,
    onZielAendern: () -> Unit,
    onNeueVersion: (NeueVersion) -> Unit,
    healthConnect: de.kh.kalorientracker.gesundheit.HealthConnect? = null,
    sicherung: de.kh.kalorientracker.data.Sicherung? = null,
    kiSchluessel: de.kh.kalorientracker.data.KiSchluessel? = null,
    erinnerungen: de.kh.kalorientracker.erinnerung.Erinnerungen? = null,
) {
    val p by repo.profil.collectAsStateWithLifecycle(initialValue = null)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val textAktuell = stringResource(R.string.update_keins)
    val textFehler = stringResource(R.string.update_pruefen_fehler)
    val gewicht by repo.letztesGewicht.collectAsStateWithLifecycle(initialValue = null)
    var zielDialog by remember { mutableStateOf(false) }
    val textGespeichert = stringResource(R.string.ziel_gespeichert)
    p?.let { profil ->
        if (zielDialog) {
            ZielAnpassenDialog(
                kcalBisher = profil.tageszielKcal, manuellBisher = profil.zielManuell,
                makroBisher = Triple(profil.fettProzent, profil.khProzent, profil.eiweissProzent),
                onAbbrechen = { zielDialog = false },
                onSpeichern = { kcal, f, c, e ->
                    zielDialog = false
                    scope.launch { repo.setzeZielUndMakros(kcal, f, c, e); snackbar.showSnackbar(textGespeichert) }
                },
            )
        }
    }
    Seite(titel = stringResource(R.string.tab_profil), snackbar = snackbar) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            val profil = p ?: return@Column
            val datum = DateTimeFormatter.ofPattern("dd.MM.uuuu")
            Karte(titel = stringResource(R.string.dein_ziel)) {
                Text(
                    stringResource(R.string.einheit_kcal, Format.kcal(profil.tageszielKcal)),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.tagesziel_label) + if (profil.zielManuell) " · " + stringResource(R.string.ziel_manuell_hinweis) else "",
                    style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.textMuted,
                )
                ProfilZeile(stringResource(R.string.ziel), stringResource(
                    when (profil.ziel) {
                        "ABNEHMEN" -> R.string.ziel_abnehmen
                        "ZUNEHMEN" -> R.string.ziel_zunehmen
                        else -> R.string.ziel_halten
                    },
                ))
                profil.zielgewichtKg?.let { ProfilZeile(stringResource(R.string.zielgewicht_kg), Format.dezimal(it)) }
                ProfilZeile(
                    stringResource(R.string.makro_verteilung),
                    stringResource(
                        R.string.makro_prozent, Format.dezimal(profil.fettProzent),
                        Format.dezimal(profil.khProzent), Format.dezimal(profil.eiweissProzent),
                    ),
                )
            }
            Karte(titel = stringResource(R.string.rechner_ueber_dich)) {
                val geburt = LocalDate.parse(profil.geburtsdatum)
                ProfilZeile(stringResource(R.string.geschlecht), stringResource(if (profil.geschlecht == "MANN") R.string.mann else R.string.frau))
                ProfilZeile(
                    stringResource(R.string.alter),
                    stringResource(R.string.alter_wert, Bedarfsrechner.alter(geburt, LocalDate.now()), geburt.format(datum)),
                )
                ProfilZeile(stringResource(R.string.groesse_cm), Format.dezimal(profil.groesseCm))
                ProfilZeile(stringResource(R.string.gewicht_kg), Format.dezimal(gewicht?.value ?: profil.startGewichtKg))
                ProfilZeile(stringResource(R.string.startgewicht), stringResource(
                    R.string.startgewicht_wert, Format.dezimal(profil.startGewichtKg), LocalDate.parse(profil.startDatum).format(datum),
                ))
                ProfilZeile(stringResource(R.string.aktivitaet), Format.dezimal(profil.pal))
            }
            PrimaerButton(stringResource(R.string.ziel_neu_berechnen), onZielAendern)
            OutlinedButton(onClick = { zielDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ziel_anpassen))
            }
            HinweisBox(stringResource(R.string.rechner_datenschutz))
            healthConnect?.let { hc -> de.kh.kalorientracker.gesundheit.HealthConnectKarte(hc) { text -> scope.launch { snackbar.showSnackbar(text) } } }
            sicherung?.let { SicherungKarte(it) { text -> scope.launch { snackbar.showSnackbar(text) } } }
            erinnerungen?.let { de.kh.kalorientracker.erinnerung.ErinnerungKarte(it) { text -> scope.launch { snackbar.showSnackbar(text) } } }
            kiSchluessel?.let { de.kh.kalorientracker.ui.foto.SchluesselKarte(it) { text -> scope.launch { snackbar.showSnackbar(text) } } }
            Karte(titel = stringResource(R.string.app_version, aktualisierung.aktuelleVersion)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        runCatching { aktualisierung.pruefe(immer = true) }
                            .onSuccess { v -> if (v != null) onNeueVersion(v) else snackbar.showSnackbar(textAktuell) }
                            .onFailure { snackbar.showSnackbar(textFehler) }
                    }
                }) { Text(stringResource(R.string.update_suchen)) }
            }
            Karte(titel = stringResource(R.string.datenquellen)) {
                Text(stringResource(R.string.datenquellen_text), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
            }
        }
    }
}

@Composable
private fun ProfilZeile(name: String, wert: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = AppTheme.colors.textMuted)
        Text(wert, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

/** Für Tabs, die nach dem Durchstich kommen. */
@Composable
fun PlatzhalterScreen(titel: Int, text: Int) {
    Seite(titel = stringResource(titel)) { pad ->
        Column(Modifier.padding(pad).padding(Spacing.l)) {
            HinweisBox(stringResource(text))
        }
    }
}
