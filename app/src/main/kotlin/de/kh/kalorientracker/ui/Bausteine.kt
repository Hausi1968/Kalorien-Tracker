@file:OptIn(ExperimentalMaterial3Api::class)

package de.kh.kalorientracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.kh.kalorientracker.R
import de.kh.kalorientracker.core.Format
import de.kh.kalorientracker.core.Tagesbilanz
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing
import kotlin.math.abs

/** Karte mit Titel: surface, große Rundung, 16 dp Innenabstand. */
@Composable
fun Karte(modifier: Modifier = Modifier, titel: String? = null, inhalt: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Spacing.l)) {
            if (titel != null) {
                Text(titel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Spacing.m))
            }
            inhalt()
        }
    }
}

/** "1.500 Ziel − 196 gegessen + 46 verbrannt = 1.350 übrig" */
@Composable
fun BilanzGleichung(b: Tagesbilanz, modifier: Modifier = Modifier) {
    val ansage = stringResource(
        R.string.bilanz_ansage, Format.kcal(abs(b.uebrig)),
        stringResource(if (b.zuViel) R.string.bilanz_zu_viel else R.string.bilanz_uebrig),
        Format.kcal(b.ziel), Format.kcal(b.gegessen), Format.kcal(b.verbrannt),
    )
    val farbe = if (b.zuViel) AppTheme.colors.warning else MaterialTheme.colorScheme.primary
    Karte(modifier) {
        Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = ansage }) {
            // Die eine Zahl, die zählt: was heute noch geht
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Format.kcal(abs(b.uebrig)),
                    style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = farbe,
                )
                Spacer(Modifier.width(Spacing.s))
                Text(
                    stringResource(R.string.kcal) + " " +
                        stringResource(if (b.zuViel) R.string.bilanz_zu_viel else R.string.bilanz_uebrig),
                    style = MaterialTheme.typography.titleMedium, color = farbe,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
            }
            Spacer(Modifier.height(Spacing.s))
            LinearProgressIndicator(
                progress = { b.anteil.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = farbe,
                trackColor = AppTheme.colors.surface2,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(Spacing.s))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BilanzSpalte(Format.kcal(b.ziel), stringResource(R.string.bilanz_ziel))
                Zeichen("−")
                BilanzSpalte(Format.kcal(b.gegessen), stringResource(R.string.bilanz_gegessen))
                Zeichen("+")
                BilanzSpalte(Format.kcal(b.verbrannt), stringResource(R.string.bilanz_verbrannt), AppTheme.colors.burned)
            }
        }
    }
}

@Composable
private fun BilanzSpalte(zahl: String, label: String, farbe: Color = MaterialTheme.colorScheme.onSurface, fett: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            zahl,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (fett) FontWeight.ExtraBold else FontWeight.Bold,
            color = farbe,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
    }
}

@Composable
private fun Zeichen(z: String) {
    Text(z, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textMuted)
}

/** Ein Makro als Balken: "Fett  16 / 50 g". Ohne Ziel nur der Wert. */
@Composable
fun MakroBalken(name: String, gramm: Double?, zielGramm: Int?, farbe: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        // Name und Wert untereinander, damit auch „Kohlenhydrate“ auf schmalen Handys nicht umbricht
        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(
            if (zielGramm != null) stringResource(R.string.makro_von_ziel, Format.gramm(gramm), zielGramm)
            else stringResource(R.string.einheit_gramm, Format.gramm(gramm)),
            style = MaterialTheme.typography.labelMedium,
            color = AppTheme.colors.textMuted,
            maxLines = 1,
        )
        // Ohne Tagesziel (z. B. in der Detailansicht) kein leerer Balken
        if (zielGramm == null || zielGramm <= 0) return@Column
        Spacer(Modifier.height(Spacing.xs))
        val anteil = ((gramm ?: 0.0) / zielGramm).toFloat()
        LinearProgressIndicator(
            progress = { anteil.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = farbe,
            trackColor = AppTheme.colors.surface2,
            drawStopIndicator = {},
        )
    }
}

@Composable
fun MakroDreier(fett: Double?, kh: Double?, eiweiss: Double?, ziel: de.kh.kalorientracker.core.MakroGramm?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        MakroBalken(stringResource(R.string.makro_fett), fett, ziel?.fett, AppTheme.colors.macroFat, Modifier.weight(1f))
        MakroBalken(stringResource(R.string.makro_kh), kh, ziel?.kh, AppTheme.colors.macroCarbs, Modifier.weight(1f))
        MakroBalken(stringResource(R.string.makro_eiweiss), eiweiss, ziel?.eiweiss, AppTheme.colors.macroProtein, Modifier.weight(1f))
    }
}

/** Info (accent-soft) oder Warnung (warning-Rand). */
@Composable
fun HinweisBox(text: String, warnung: Boolean = false, modifier: Modifier = Modifier) {
    val form = MaterialTheme.shapes.medium
    Row(
        modifier
            .fillMaxWidth()
            .background(if (warnung) MaterialTheme.colorScheme.surface else AppTheme.colors.accentSoft, form)
            .then(if (warnung) Modifier.border(1.5.dp, AppTheme.colors.warning, form) else Modifier)
            .padding(Spacing.m),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (warnung) Icons.Rounded.Warning else Icons.Rounded.Info,
            contentDescription = null,
            tint = if (warnung) AppTheme.colors.warning else AppTheme.colors.onAccentSoft,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.s))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (warnung) MaterialTheme.colorScheme.onSurface else AppTheme.colors.onAccentSoft,
        )
    }
}

/** Primärer Button: 56 dp hoch, volle Breite, Pill-Form. */
@Composable
fun PrimaerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, aktiv: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = aktiv,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Runder Platzhalter statt Produktbild: Anfangsbuchstabe auf accent-soft. */
@Composable
fun Initiale(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier.size(40.dp).background(AppTheme.colors.accentSoft, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = AppTheme.colors.onAccentSoft,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun AbschnittTitel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = AppTheme.colors.textMuted,
        modifier = modifier.padding(vertical = Spacing.s).semantics { heading() },
    )
}

/** Navigation nach Datenbankarbeit sicher auf dem Haupt-Thread ausführen. */
suspend fun aufHauptThread(aktion: () -> Unit) =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { aktion() }
