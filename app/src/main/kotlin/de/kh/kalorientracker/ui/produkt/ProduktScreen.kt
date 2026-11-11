@file:OptIn(ExperimentalMaterial3Api::class)

package de.kh.kalorientracker.ui.produkt

import de.kh.kalorientracker.ui.aufHauptThread
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import de.kh.kalorientracker.R
import de.kh.kalorientracker.core.Format
import de.kh.kalorientracker.core.Naehrwerte
import de.kh.kalorientracker.core.SuchText
import de.kh.kalorientracker.data.FoodEntity
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.ui.HinweisBox
import de.kh.kalorientracker.ui.Karte
import de.kh.kalorientracker.ui.PrimaerButton
import de.kh.kalorientracker.ui.Seite
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Eigenes Produkt anlegen (S11). Bei unbekanntem Barcode ist er schon eingetragen.
 * Das Produkt bleibt privat auf dem Handy.
 */
@Composable
fun ProduktScreen(
    repo: KalorienRepository,
    barcode: String?,
    nameVorschlag: String?,
    onZurueck: () -> Unit,
    onGespeichert: (foodId: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(nameVorschlag.orEmpty()) }
    var marke by rememberSaveable { mutableStateOf("") }
    var ean by rememberSaveable { mutableStateOf(barcode.orEmpty()) }
    var ml by rememberSaveable { mutableStateOf(false) }
    var inKj by rememberSaveable { mutableStateOf(false) }
    var energie by rememberSaveable { mutableStateOf("") }
    var fett by rememberSaveable { mutableStateOf("") }
    var gesFett by rememberSaveable { mutableStateOf("") }
    var kh by rememberSaveable { mutableStateOf("") }
    var zucker by rememberSaveable { mutableStateOf("") }
    var ballast by rememberSaveable { mutableStateOf("") }
    var eiweiss by rememberSaveable { mutableStateOf("") }
    var salz by rememberSaveable { mutableStateOf("") }
    var portionName by rememberSaveable { mutableStateOf("") }
    var portionMenge by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val einheit = if (ml) "ml" else "g"

    fun zahl(s: String) = Format.parseDezimal(s)?.takeIf { it >= 0 && it < 10_000 }
    fun optional(s: String) = if (s.isBlank()) null else zahl(s)
    fun ungueltig(s: String) = s.isNotBlank() && zahl(s) == null

    val kcal = zahl(energie)?.let { if (inKj) it / 4.184 else it }
    val werte = kcal?.let {
        Naehrwerte(it, optional(fett), optional(gesFett), optional(kh), optional(zucker), optional(ballast), optional(eiweiss), optional(salz))
    }
    val eanOk = ean.isEmpty() || ean.length in 8..14
    val portionG = zahl(portionMenge)?.takeIf { it > 0 }
    val portionOk = (portionName.isBlank() && portionMenge.isBlank()) || (portionName.isNotBlank() && portionG != null)
    val felderOk = listOf(fett, gesFett, kh, zucker, ballast, eiweiss, salz).none { ungueltig(it) }
    val speicherbar = name.isNotBlank() && werte != null && eanOk && portionOk && felderOk
    val textBarcodeVergeben = stringResource(R.string.barcode_vergeben)

    Seite(titel = stringResource(R.string.produkt_anlegen), onZurueck = onZurueck, snackbar = snackbar) { pad ->
        Column(
            Modifier
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (barcode != null) HinweisBox(stringResource(R.string.barcode_unbekannt_hinweis, barcode))

            Karte(titel = stringResource(R.string.produkt_angaben)) {
                Feld(name, { name = it.take(80) }, stringResource(R.string.produkt_name), text = true)
                Feld(marke, { marke = it.take(60) }, stringResource(R.string.produkt_marke), text = true)
                Feld(
                    ean, { ean = it.filter { c -> c.isDigit() }.take(14) }, stringResource(R.string.produkt_barcode),
                    fehler = !eanOk, tastatur = KeyboardType.Number, text = true,
                )
                Text(stringResource(R.string.produkt_einheit), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FilterChip(!ml, { ml = false }, { Text(stringResource(R.string.einheit_fest)) })
                    FilterChip(ml, { ml = true }, { Text(stringResource(R.string.einheit_fluessig)) })
                }
            }

            Karte(titel = stringResource(R.string.naehrwerte_pro_100, einheit)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FilterChip(!inKj, { inKj = false }, { Text(stringResource(R.string.kcal)) })
                    FilterChip(inKj, { inKj = true }, { Text(stringResource(R.string.kj)) })
                }
                Feld(
                    energie, { energie = it }, stringResource(if (inKj) R.string.brennwert_kj else R.string.brennwert_kcal),
                    fehler = ungueltig(energie),
                    hinweis = if (inKj && kcal != null) stringResource(R.string.einheit_kcal, Format.kcal(kcal)) else null,
                )
                Feld(fett, { fett = it }, stringResource(R.string.nw_fett_g), fehler = ungueltig(fett))
                Feld(gesFett, { gesFett = it }, stringResource(R.string.nw_ges_fett_g), fehler = ungueltig(gesFett))
                Feld(kh, { kh = it }, stringResource(R.string.nw_kh_g), fehler = ungueltig(kh))
                Feld(zucker, { zucker = it }, stringResource(R.string.nw_zucker_g), fehler = ungueltig(zucker))
                Feld(ballast, { ballast = it }, stringResource(R.string.nw_ballaststoffe_g), fehler = ungueltig(ballast))
                Feld(eiweiss, { eiweiss = it }, stringResource(R.string.nw_eiweiss_g), fehler = ungueltig(eiweiss))
                Feld(salz, { salz = it }, stringResource(R.string.nw_salz_g), fehler = ungueltig(salz))
            }
            if (werte != null && !werte.plausibel()) HinweisBox(stringResource(R.string.unplausibel_eingabe), warnung = true)

            Karte(titel = stringResource(R.string.portion_optional)) {
                Feld(portionName, { portionName = it.take(40) }, stringResource(R.string.portion_name), text = true,
                    hinweis = stringResource(R.string.portion_name_beispiel))
                Feld(portionMenge, { portionMenge = it }, stringResource(R.string.portion_menge, einheit),
                    fehler = !portionOk && portionMenge.isNotBlank())
            }

            PrimaerButton(
                text = stringResource(R.string.speichern),
                aktiv = speicherbar,
                onClick = {
                    val w = werte ?: return@PrimaerButton
                    val f = FoodEntity(
                        id = "user:${UUID.randomUUID()}",
                        source = "user",
                        barcode = ean.ifEmpty { null },
                        name = name.trim(),
                        nameNorm = SuchText.normalisiere(name),
                        brand = marke.trim().ifEmpty { null },
                        unitBase = einheit,
                        kcal = w.kcal, fett = w.fett, gesFett = w.gesFett, kh = w.kh, zucker = w.zucker,
                        ballaststoffe = w.ballaststoffe, eiweiss = w.eiweiss, salz = w.salz,
                    )
                    val portion = portionG?.let { portionName.trim() to it }
                    scope.launch {
                        val r = repo.legeProduktAn(f, portion)
                        val neu = r.getOrNull()
                        if (neu != null) aufHauptThread { onGespeichert(neu.id) }
                        else snackbar.showSnackbar(textBarcodeVergeben.format(r.exceptionOrNull()?.message ?: ""))
                    }
                },
            )
            Text(
                stringResource(R.string.produkt_privat),
                style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted,
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun Feld(
    wert: String,
    onWert: (String) -> Unit,
    label: String,
    fehler: Boolean = false,
    hinweis: String? = null,
    text: Boolean = false,
    tastatur: KeyboardType = if (text) KeyboardType.Text else KeyboardType.Decimal,
) {
    OutlinedTextField(
        value = wert,
        onValueChange = { neu -> onWert(if (text) neu else neu.filter { it.isDigit() || it == ',' || it == '.' }.take(8)) },
        label = { Text(label) },
        isError = fehler,
        supportingText = when {
            fehler -> { { Text(stringResource(R.string.eingabe_pruefen)) } }
            hinweis != null -> { { Text(hinweis) } }
            else -> null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = tastatur,
            capitalization = if (tastatur == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
    )
}
