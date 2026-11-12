@file:OptIn(ExperimentalLayoutApi::class)

package de.kh.kalorientracker.ui.foto

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kh.kalorientracker.R
import de.kh.kalorientracker.core.Format
import de.kh.kalorientracker.core.Mahlzeiten
import de.kh.kalorientracker.data.ErkennungFehlgeschlagen
import de.kh.kalorientracker.data.ErkennungsFehler
import de.kh.kalorientracker.data.FoodEntity
import de.kh.kalorientracker.data.FotoErkenner
import de.kh.kalorientracker.data.Fotos
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.data.KiSchluessel
import de.kh.kalorientracker.data.naehrwerte100
import de.kh.kalorientracker.ui.HinweisBox
import de.kh.kalorientracker.ui.Karte
import de.kh.kalorientracker.ui.Meldung
import de.kh.kalorientracker.ui.Meldungen
import de.kh.kalorientracker.ui.PrimaerButton
import de.kh.kalorientracker.ui.Seite
import de.kh.kalorientracker.ui.aufHauptThread
import de.kh.kalorientracker.ui.rezepte.ZutatAuswahlSeite
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Alles, was die Foto-Mahlzeit braucht, gebündelt. */
class FotoWerkzeug(val fotos: Fotos, val schluessel: KiSchluessel, val erkenner: FotoErkenner)

/** Eine Zeile der Prüf-Liste: was erkannt wurde, welches Lebensmittel dazu passt und wie viel. */
private class Posten(val nr: Int, food: FoodEntity?, gramm: Double, val erkanntAls: String?) {
    var food by mutableStateOf(food)
    var grammText by mutableStateOf(Format.dezimal(gramm, 0))
    val gramm: Double? get() = Format.parseDezimal(grammText)?.takeIf { it > 0 && it < 10_000 }
    val kcal: Double get() = food?.let { f -> gramm?.let { f.naehrwerte100.fuerGramm(it).kcal } } ?: 0.0
}

private enum class Zustand { OHNE_FOTO, ERKENNT, BEREIT, FEHLER }

/**
 * Mahlzeit per Foto: Foto aufnehmen oder auswählen, die KI schlägt Lebensmittel mit Gramm vor,
 * der Nutzer prüft, ändert, löscht oder ergänzt und trägt alles auf einmal ein.
 */
@Composable
fun FotoScreen(
    repo: KalorienRepository,
    werkzeug: FotoWerkzeug,
    tag: LocalDate,
    slot: Int,
    onZurueck: () -> Unit,
    onFertig: () -> Unit,
    onSchluessel: () -> Unit,
    /** Nur für Tests: mit diesem schon gespeicherten Foto beginnen. */
    startFoto: String? = null,
) {
    val scope = rememberCoroutineScope()
    var foto by rememberSaveable { mutableStateOf(startFoto) }
    var aufnahmeUri by rememberSaveable { mutableStateOf<String?>(null) }
    var bild by remember { mutableStateOf<ImageBitmap?>(null) }
    var zustand by remember { mutableStateOf(Zustand.OHNE_FOTO) }
    var fehler by remember { mutableStateOf<ErkennungsFehler?>(null) }
    val posten = remember { mutableStateListOf<Posten>() }
    var naechsteNr by remember { mutableIntStateOf(0) }
    var auswahlFuer by remember { mutableStateOf<Posten?>(null) }
    var auswahlNeu by remember { mutableStateOf(false) }
    var slotId by remember { mutableIntStateOf(if (slot > 0) slot else Mahlzeiten.slotFuer(LocalTime.now()).id) }
    var speichert by remember { mutableStateOf(false) }
    val schluessel by werkzeug.schluessel.wert.collectAsStateWithLifecycle()

    val textEingetragen = stringResource(R.string.foto_eingetragen)
    val textBildFehler = stringResource(R.string.foto_nicht_lesbar)
    var bildFehler by remember { mutableStateOf(false) }

    fun erkenne() {
        val name = foto ?: return
        if (!werkzeug.erkenner.bereit) { zustand = Zustand.BEREIT; return }
        zustand = Zustand.ERKENNT
        fehler = null
        scope.launch {
            val bytes = werkzeug.fotos.bytes(name)
            val r = if (bytes == null) Result.failure(ErkennungFehlgeschlagen(ErkennungsFehler.UNBEKANNT))
            else werkzeug.erkenner.erkenne(bytes)
            r.onSuccess { liste ->
                posten.clear()
                liste.forEach { e -> posten.add(Posten(naechsteNr++, repo.zuordnen(e.name), e.gramm, e.name)) }
                zustand = Zustand.BEREIT
            }.onFailure { t ->
                fehler = (t as? ErkennungFehlgeschlagen)?.grund ?: ErkennungsFehler.UNBEKANNT
                zustand = Zustand.FEHLER
            }
        }
    }

    fun uebernimm(uri: android.net.Uri?) {
        if (uri == null) return
        scope.launch {
            val name = werkzeug.fotos.uebernimm(uri)
            if (name == null) { bildFehler = true; return@launch }
            bildFehler = false
            foto = name
            posten.clear()
            erkenne()
        }
    }

    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) uebernimm(aufnahmeUri?.let(android.net.Uri::parse))
    }
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uebernimm(uri) }

    fun fotografieren() {
        val uri = werkzeug.fotos.neueAufnahme()
        aufnahmeUri = uri.toString()
        runCatching { kamera.launch(uri) }.onFailure { bildFehler = true }
    }
    fun ausGalerie() = runCatching {
        galerie.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }.onFailure { bildFehler = true }

    LaunchedEffect(Unit) { if (startFoto != null && posten.isEmpty()) erkenne() }
    LaunchedEffect(foto) {
        val name = foto ?: run { bild = null; return@LaunchedEffect }
        if (zustand == Zustand.OHNE_FOTO) zustand = Zustand.BEREIT
        bild = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(werkzeug.fotos.datei(name).path)?.asImageBitmap() }.getOrNull() }
    }

    // Lebensmittel wählen (neu oder Austausch) als eigene Seite
    if (auswahlNeu || auswahlFuer != null) {
        val ziel = auswahlFuer
        ZutatAuswahlSeite(
            repo,
            titel = stringResource(if (ziel != null) R.string.foto_austauschen else R.string.foto_hinzufuegen),
            startText = ziel?.erkanntAls ?: ziel?.food?.name ?: "",
            onZurueck = { auswahlNeu = false; auswahlFuer = null },
        ) { f, _, _, gramm ->
            if (ziel != null) {
                ziel.food = f
                if (ziel.gramm == null) ziel.grammText = Format.dezimal(gramm, 0)
            } else {
                posten.add(Posten(naechsteNr++, f, gramm, null))
            }
            auswahlNeu = false; auswahlFuer = null
        }
        return
    }

    val gueltig = posten.filter { it.food != null && it.gramm != null }
    val summe = gueltig.sumOf { it.kcal }

    Seite(titel = stringResource(R.string.foto_titel), onZurueck = onZurueck) { pad ->
        LazyColumn(
            Modifier.padding(pad).consumeWindowInsets(pad).imePadding().fillMaxSize(),
            contentPadding = PaddingValues(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (foto == null) {
                item { Text(stringResource(R.string.foto_einleitung), style = MaterialTheme.typography.bodyLarge) }
                item { PrimaerButton(stringResource(R.string.foto_aufnehmen), onClick = { fotografieren() }) }
                item {
                    OutlinedButton(onClick = { ausGalerie() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(Spacing.s))
                        Text(stringResource(R.string.foto_galerie))
                    }
                }
                if (schluessel.isBlank()) item { SchluesselHinweis(onSchluessel) }
            } else {
                item {
                    bild?.let {
                        Image(
                            it, contentDescription = stringResource(R.string.foto_beschreibung),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        TextButton(onClick = { fotografieren() }) {
                            Icon(Icons.Rounded.PhotoCamera, contentDescription = null); Spacer(Modifier.width(Spacing.xs))
                            Text(stringResource(R.string.foto_neu))
                        }
                        TextButton(onClick = { ausGalerie() }) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null); Spacer(Modifier.width(Spacing.xs))
                            Text(stringResource(R.string.foto_galerie_kurz))
                        }
                    }
                }
                when (zustand) {
                    Zustand.ERKENNT -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Text(stringResource(R.string.foto_erkennt), style = MaterialTheme.typography.bodyLarge)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                    Zustand.FEHLER -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            HinweisBox(stringResource(fehlerText(fehler)), warnung = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                if (fehler == ErkennungsFehler.SCHLUESSEL_FALSCH || fehler == ErkennungsFehler.KEIN_SCHLUESSEL) {
                                    OutlinedButton(onClick = onSchluessel) { Text(stringResource(R.string.foto_schluessel_eintragen)) }
                                }
                                OutlinedButton(onClick = { erkenne() }) { Text(stringResource(R.string.foto_nochmal)) }
                            }
                        }
                    }
                    else -> {
                        if (schluessel.isBlank()) item { SchluesselHinweis(onSchluessel) }
                        else if (posten.isEmpty()) item { HinweisBox(stringResource(R.string.foto_nichts_erkannt)) }
                    }
                }
            }
            if (bildFehler) item { HinweisBox(textBildFehler, warnung = true) }

            if (foto != null && zustand != Zustand.ERKENNT) {
                if (posten.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.foto_pruefen), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                items(posten.toList(), key = { it.nr }) { p ->
                    PostenZeile(
                        p,
                        onAustauschen = { auswahlFuer = p },
                        onEntfernen = { posten.remove(p) },
                    )
                }
                item {
                    OutlinedButton(onClick = { auswahlNeu = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(Spacing.s))
                        Text(stringResource(R.string.foto_hinzufuegen))
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Mahlzeiten.STANDARD.forEach { s ->
                            FilterChip(selected = s.id == slotId, onClick = { slotId = s.id }, label = { Text(s.name) })
                        }
                    }
                }
                item {
                    PrimaerButton(
                        stringResource(R.string.foto_eintragen, gueltig.size, Format.kcal(summe)),
                        aktiv = gueltig.isNotEmpty() && !speichert,
                        onClick = {
                            if (speichert) return@PrimaerButton
                            speichert = true
                            scope.launch {
                                val zeit = LocalDateTime.of(tag, LocalTime.now().withSecond(0).withNano(0))
                                val neu = repo.eintragenMehrere(gueltig.map { it.food!! to it.gramm!! }, zeit, slotId, foto)
                                Meldungen.zeige(Meldung(textEingetragen.format(neu.size, Format.kcal(summe))) { neu.forEach { repo.loesche(it) } })
                                aufHauptThread(onFertig)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SchluesselHinweis(onSchluessel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        HinweisBox(stringResource(R.string.foto_ohne_schluessel))
        OutlinedButton(onClick = onSchluessel) { Text(stringResource(R.string.foto_schluessel_eintragen)) }
    }
}

@Composable
private fun PostenZeile(p: Posten, onAustauschen: () -> Unit, onEntfernen: () -> Unit) {
    val f = p.food
    Karte {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    f?.name ?: stringResource(R.string.foto_nicht_gefunden, p.erkanntAls.orEmpty()),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (f == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                val unter = when {
                    f == null -> stringResource(R.string.foto_nicht_gefunden_hinweis)
                    p.erkanntAls != null && !f.name.equals(p.erkanntAls, ignoreCase = true) ->
                        stringResource(R.string.foto_erkannt_als, p.erkanntAls)
                    else -> f.brand
                }
                unter?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted) }
            }
            IconButton(onClick = onAustauschen) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = stringResource(R.string.foto_austauschen))
            }
            IconButton(onClick = onEntfernen) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.foto_entfernen, f?.name ?: p.erkanntAls.orEmpty()), tint = AppTheme.colors.textMuted)
            }
        }
        if (f != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = p.grammText,
                    onValueChange = { p.grammText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                    label = { Text(stringResource(R.string.foto_menge, f.unitBase)) },
                    singleLine = true, isError = p.gramm == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(140.dp),
                )
                Spacer(Modifier.width(Spacing.m))
                Text(
                    stringResource(R.string.einheit_kcal, Format.kcal(p.kcal)),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            TextButton(onClick = onAustauschen) { Text(stringResource(R.string.foto_waehlen)) }
        }
    }
}

private fun fehlerText(f: ErkennungsFehler?): Int = when (f) {
    ErkennungsFehler.KEIN_SCHLUESSEL -> R.string.foto_ohne_schluessel
    ErkennungsFehler.SCHLUESSEL_FALSCH -> R.string.foto_fehler_schluessel
    ErkennungsFehler.KEINE_VERBINDUNG -> R.string.foto_fehler_netz
    ErkennungsFehler.ABGELEHNT -> R.string.foto_fehler_abgelehnt
    else -> R.string.foto_fehler_unbekannt
}
