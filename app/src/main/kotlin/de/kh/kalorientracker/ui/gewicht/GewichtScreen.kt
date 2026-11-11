package de.kh.kalorientracker.ui.gewicht

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kh.kalorientracker.R
import de.kh.kalorientracker.core.Format
import de.kh.kalorientracker.data.BodyEntryEntity
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.ui.HinweisBox
import de.kh.kalorientracker.ui.Karte
import de.kh.kalorientracker.ui.PrimaerButton
import de.kh.kalorientracker.ui.Seite
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val DATUM = DateTimeFormatter.ofPattern("EE, dd.MM.uuuu", java.util.Locale.GERMANY)

/** Verlauf (S15/S16): Gewicht schnell eintragen, Kurve, Fortschritt zum Zielgewicht, BMI. */
@Composable
fun GewichtScreen(repo: KalorienRepository) {
    val profil by repo.profil.collectAsStateWithLifecycle(initialValue = null)
    val verlauf by repo.gewichtVerlauf.collectAsStateWithLifecycle(initialValue = emptyList())
    var wert by remember { mutableStateOf<Double?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val textGespeichert = stringResource(R.string.gewicht_gespeichert)

    LaunchedEffect(verlauf.isNotEmpty()) {
        if (wert == null) wert = verlauf.lastOrNull()?.value ?: profil?.startGewichtKg
    }

    Seite(titel = stringResource(R.string.tab_verlauf), snackbar = snackbar) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            val p = profil ?: return@Column
            val aktuell = verlauf.lastOrNull()?.value ?: p.startGewichtKg

            // Eintragen
            Karte(titel = stringResource(R.string.gewicht_heute)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    FilledTonalIconButton(onClick = { wert = ((wert ?: aktuell) - 0.1).coerceAtLeast(30.0) }) {
                        Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.gewicht_weniger))
                    }
                    Spacer(Modifier.width(Spacing.l))
                    Text(
                        stringResource(R.string.kg_wert, Format.dezimal(wert ?: aktuell)),
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.width(Spacing.l))
                    FilledTonalIconButton(onClick = { wert = ((wert ?: aktuell) + 0.1).coerceAtMost(350.0) }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.gewicht_mehr))
                    }
                }
                Spacer(Modifier.height(Spacing.m))
                PrimaerButton(stringResource(R.string.gewicht_speichern), onClick = {
                    val kg = ((wert ?: aktuell) * 10).roundToInt() / 10.0
                    scope.launch {
                        repo.speichereGewicht(kg)
                        snackbar.showSnackbar(textGespeichert)
                    }
                })
                Spacer(Modifier.height(Spacing.s))
                Text(stringResource(R.string.gewicht_ziel_anpassung), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
            }

            // Fortschritt
            Karte(titel = stringResource(R.string.fortschritt)) {
                val ziel = p.zielgewichtKg
                val start = p.startGewichtKg
                val veraendert = aktuell - start
                Text(
                    stringResource(
                        if (veraendert <= 0) R.string.seit_start_weniger else R.string.seit_start_mehr,
                        Format.dezimal(abs(veraendert)),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (ziel != null && ziel != start) {
                    val anteil = ((start - aktuell) / (start - ziel)).toFloat().coerceIn(0f, 1f)
                    Spacer(Modifier.height(Spacing.s))
                    LinearProgressIndicator(
                        progress = { anteil }, modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = MaterialTheme.colorScheme.primary, trackColor = AppTheme.colors.surface2,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.kg_wert, Format.dezimal(start)), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
                        Text(stringResource(R.string.noch_kg, Format.dezimal(abs(aktuell - ziel))), style = MaterialTheme.typography.labelMedium)
                        Text(stringResource(R.string.kg_wert, Format.dezimal(ziel)), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
                    }
                }
                val bmi = aktuell / ((p.groesseCm / 100) * (p.groesseCm / 100))
                Spacer(Modifier.height(Spacing.s))
                Text(
                    stringResource(R.string.bmi_wert, Format.dezimal(bmi), stringResource(bmiKlasse(bmi))),
                    style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted,
                )
            }

            // Kurve
            Karte(titel = stringResource(R.string.gewichtsverlauf)) {
                if (verlauf.size < 2) {
                    Text(stringResource(R.string.zu_wenig_daten), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted)
                } else {
                    GewichtKurve(verlauf.takeLast(60), p.zielgewichtKg)
                }
            }

            // Liste
            if (verlauf.isNotEmpty()) {
                Karte(titel = stringResource(R.string.eintraege)) {
                    verlauf.takeLast(30).asReversed().forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(color = AppTheme.colors.border)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(LocalDate.parse(e.day).format(DATUM), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.kg_wert, Format.dezimal(e.value)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { scope.launch { repo.loescheGewicht(e) } }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.gewicht_loeschen), tint = AppTheme.colors.textMuted)
                            }
                        }
                    }
                }
            }
            HinweisBox(stringResource(R.string.health_connect_folgt))
        }
    }
}

private fun bmiKlasse(bmi: Double): Int = when {
    bmi < 18.5 -> R.string.bmi_unter
    bmi < 25 -> R.string.bmi_normal
    bmi < 30 -> R.string.bmi_ueber
    else -> R.string.bmi_adipositas
}

/** Einfache Linie mit gestrichelter Ziellinie; Achsenwerte als Text darunter. */
@Composable
private fun GewichtKurve(werte: List<BodyEntryEntity>, ziel: Double?) {
    val linie = MaterialTheme.colorScheme.primary
    val zielFarbe = AppTheme.colors.highlight
    val gitter = AppTheme.colors.border
    val alle = werte.map { it.value } + listOfNotNull(ziel)
    val min = alle.min() - 0.5
    val max = alle.max() + 0.5
    val beschreibung = stringResource(
        R.string.kurve_ansage, werte.size, Format.dezimal(werte.first().value), Format.dezimal(werte.last().value),
    )
    Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = beschreibung }) {
        fun y(v: Double) = (size.height * (1 - (v - min) / (max - min))).toFloat()
        val schritt = if (werte.size > 1) size.width / (werte.size - 1) else 0f
        for (i in 0..3) {
            val yy = size.height * i / 3f
            drawLine(gitter, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1f)
        }
        if (ziel != null) {
            drawLine(
                zielFarbe, Offset(0f, y(ziel)), Offset(size.width, y(ziel)), strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)),
            )
        }
        val pfad = Path()
        werte.forEachIndexed { i, e ->
            val p = Offset(i * schritt, y(e.value))
            if (i == 0) pfad.moveTo(p.x, p.y) else pfad.lineTo(p.x, p.y)
        }
        drawPath(pfad, linie, style = Stroke(width = 6f, cap = StrokeCap.Round))
        werte.forEachIndexed { i, e -> drawCircle(linie, radius = 7f, center = Offset(i * schritt, y(e.value))) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(LocalDate.parse(werte.first().day).format(DateTimeFormatter.ofPattern("dd.MM.")), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
        Text(LocalDate.parse(werte.last().day).format(DateTimeFormatter.ofPattern("dd.MM.")), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
    }
}
