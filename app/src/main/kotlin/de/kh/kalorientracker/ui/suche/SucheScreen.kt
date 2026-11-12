@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package de.kh.kalorientracker.ui.suche

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.kh.kalorientracker.R
import de.kh.kalorientracker.core.Format
import de.kh.kalorientracker.core.Mahlzeiten
import de.kh.kalorientracker.data.BarcodeErgebnis
import de.kh.kalorientracker.data.FoodEntity
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.ui.AbschnittTitel
import de.kh.kalorientracker.ui.Initiale
import de.kh.kalorientracker.ui.KalorienDirektDialog
import de.kh.kalorientracker.ui.Seite
import de.kh.kalorientracker.ui.aufHauptThread
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun SucheScreen(
    repo: KalorienRepository,
    tag: LocalDate,
    slot: Int,
    onZurueck: () -> Unit,
    onLebensmittel: (String) -> Unit,
    onProduktAnlegen: (barcode: String?, name: String?) -> Unit,
    onFoto: (() -> Unit)? = null,
) {
    var eingabe by rememberSaveable { mutableStateOf("") }
    var treffer by remember { mutableStateOf<List<FoodEntity>?>(null) }
    var zuletzt by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }
    var favoriten by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val fokus = remember { FocusRequester() }
    val context = LocalContext.current
    var online by remember { mutableStateOf<List<FoodEntity>?>(null) }
    var laedt by remember { mutableStateOf(false) }
    var direktOffen by remember { mutableStateOf(false) }
    var inArbeit by remember { mutableStateOf(emptySet<String>()) }
    val tastatur = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        if (eingabe.isEmpty()) fokus.requestFocus()
        zuletzt = repo.zuletztGegessen()
        favoriten = repo.favoriten()
    }
    LaunchedEffect(eingabe) {
        online = null
        if (eingabe.trim().length < 2) { treffer = null; return@LaunchedEffect }
        delay(150)
        treffer = repo.suche(eingabe)
    }

    val textEingetragen = stringResource(R.string.eingetragen)
    val textRueckgaengig = stringResource(R.string.rueckgaengig)
    val textOffline = stringResource(R.string.keine_verbindung)
    val textOfflineBarcode = stringResource(R.string.keine_verbindung_barcode)
    val textAnlegen = stringResource(R.string.produkt_anlegen)
    val textScanFehler = stringResource(R.string.scan_fehler)

    fun sucheOnline() = scope.launch {
        laedt = true
        val ergebnis = repo.onlineSuche(eingabe)
        laedt = false
        if (ergebnis == null) snackbar.showSnackbar(textOffline) else online = ergebnis
    }

    fun scanne() = starteBarcodeScan(
        context,
        onCode = { ean ->
            scope.launch {
                laedt = true
                val r = repo.barcode(ean)
                laedt = false
                when (r) {
                    is BarcodeErgebnis.Gefunden -> {
                        // Lerneffekt: Suchbegriff, unter dem das Produkt nicht gefunden wurde, am Produkt merken
                        val begriff = eingabe.trim()
                        if (begriff.length >= 2 && treffer.orEmpty().none { it.id == r.food.id }) {
                            repo.lerneSuchbegriff(r.food.id, begriff)
                        }
                        aufHauptThread { onLebensmittel(r.food.id) }
                    }
                    BarcodeErgebnis.Unbekannt -> onProduktAnlegen(ean, null)
                    BarcodeErgebnis.KeineVerbindung -> {
                        val a = snackbar.showSnackbar(textOfflineBarcode, textAnlegen, duration = SnackbarDuration.Long)
                        if (a == SnackbarResult.ActionPerformed) onProduktAnlegen(ean, null)
                    }
                }
            }
        },
        onAbbruch = {},
        onFehler = { scope.launch { snackbar.showSnackbar(textScanFehler) } },
    )

    /** Schnelleintrag mit Standardportion (bzw. zuletzt genutzter Portion), mit Rückgängig. */
    fun schnell(f: FoodEntity) {
        if (f.id in inArbeit) return // Doppeltipp auf dasselbe ⊕ ignorieren
        inArbeit = inArbeit + f.id
        scope.launch {
            val e = try {
                val portionen = repo.portionen(f.id)
                val letzte = repo.letztePortion(f.id)
                val p = portionen.firstOrNull { it.label == letzte } ?: portionen.firstOrNull { it.isDefault } ?: portionen.firstOrNull()
                val zeit = LocalDateTime.of(tag, LocalTime.now().withSecond(0).withNano(0))
                val slotId = if (slot > 0) slot else Mahlzeiten.slotFuer(zeit.toLocalTime()).id
                repo.eintragen(f, p?.label ?: "100 ${f.unitBase}", p?.gramm ?: 100.0, 1.0, zeit, slotId)
            } finally {
                inArbeit = inArbeit - f.id
            }
            val r = snackbar.showSnackbar(
                textEingetragen.format(e.label, Format.kcal(e.kcal)), textRueckgaengig, duration = SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) repo.loesche(e)
        }
    }

    Seite(
        titel = stringResource(R.string.suche_titel),
        onZurueck = onZurueck,
        snackbar = snackbar,
        aktionen = {
            if (onFoto != null) {
                IconButton(onClick = onFoto) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = stringResource(R.string.foto_titel))
                }
            }
            IconButton(onClick = { direktOffen = true }) {
                Icon(Icons.Rounded.Bolt, contentDescription = stringResource(R.string.kalorien_direkt))
            }
            IconButton(onClick = { scanne() }) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = stringResource(R.string.barcode_scannen))
            }
        },
    ) { pad ->
        if (direktOffen) {
            KalorienDirektDialog(
                onAbbrechen = { direktOffen = false },
                onSpeichern = { name, kcal, f, k, e ->
                    direktOffen = false
                    scope.launch {
                        val zeit = LocalDateTime.of(tag, LocalTime.now().withSecond(0).withNano(0))
                        val slotId = if (slot > 0) slot else Mahlzeiten.slotFuer(zeit.toLocalTime()).id
                        repo.kalorienDirekt(name, kcal, f, k, e, zeit, slotId)
                        snackbar.showSnackbar(textEingetragen.format(name, Format.kcal(kcal)))
                    }
                },
            )
        }
        Column(Modifier.padding(pad).consumeWindowInsets(pad).imePadding()) {
            OutlinedTextField(
                value = eingabe,
                onValueChange = { eingabe = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.l)
                    .focusRequester(fokus),
                placeholder = { Text(stringResource(R.string.suche_platzhalter)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (eingabe.isNotEmpty()) {
                        IconButton(onClick = { eingabe = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.suche_leeren))
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // Enter: Tastatur zu; ohne Treffer gleich online weitersuchen
                keyboardActions = KeyboardActions(onSearch = {
                    tastatur?.hide()
                    if (eingabe.trim().length >= 2 && treffer?.isEmpty() == true && online == null) sucheOnline()
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = AppTheme.colors.borderInput,
                ),
            )
            Spacer(Modifier.height(Spacing.s))
            if (laedt) LinearProgressIndicator(Modifier.fillMaxWidth())
            val liste = treffer
            val onl = online
            val anlegen = { onProduktAnlegen(null, eingabe.trim().ifEmpty { null }) }
            when {
                onl != null && onl.isEmpty() -> NichtsGefunden(eingabe, online = true, onOnline = null, onAnlegen = anlegen)
                // Lerneffekt: Was lokal nicht gefunden wurde und online gewählt wird, merkt sich den Suchbegriff
                onl != null -> TrefferListe(
                    stringResource(R.string.online_treffer), onl,
                    { id ->
                        val begriff = eingabe.trim()
                        scope.launch {
                            if (begriff.length >= 2) repo.lerneSuchbegriff(id, begriff)
                            aufHauptThread { onLebensmittel(id) }
                        }
                    },
                    { f ->
                        val begriff = eingabe.trim()
                        if (begriff.length >= 2) scope.launch { repo.lerneSuchbegriff(f.id, begriff) }
                        schnell(f)
                    },
                ) {
                    WeiterZeile(stringResource(R.string.nicht_dabei_anlegen), anlegen)
                }
                liste == null && zuletzt.isEmpty() && favoriten.isEmpty() -> LeerHinweis(stringResource(R.string.suche_hinweis_start))
                liste == null -> StartListen(favoriten, zuletzt, onLebensmittel) { schnell(it) }
                liste.isEmpty() -> NichtsGefunden(eingabe, online = false, onOnline = { sucheOnline() }, onAnlegen = anlegen)
                else -> TrefferListe(null, liste, onLebensmittel, { schnell(it) }) {
                    WeiterZeile(stringResource(R.string.nicht_dabei_online), { sucheOnline() })
                }
            }
        }
    }
}

@Composable
private fun TrefferListe(
    titel: String?,
    liste: List<FoodEntity>,
    onLebensmittel: (String) -> Unit,
    onSchnell: (FoodEntity) -> Unit,
    fuss: @Composable () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl)) {
        if (titel != null) item { AbschnittTitel(titel, Modifier.padding(horizontal = Spacing.l)) }
        items(liste, key = { it.id }) { f ->
            LebensmittelZeile(f, onClick = { onLebensmittel(f.id) }, onSchnell = { onSchnell(f) })
            HorizontalDivider(color = AppTheme.colors.border, modifier = Modifier.padding(start = 72.dp))
        }
        item { fuss() }
    }
}

/** Ohne Suchtext: erst Favoriten, dann zuletzt gegessen (ohne Doppelte). */
@Composable
private fun StartListen(
    favoriten: List<FoodEntity>,
    zuletzt: List<FoodEntity>,
    onLebensmittel: (String) -> Unit,
    onSchnell: (FoodEntity) -> Unit,
) {
    val favIds = favoriten.map { it.id }.toSet()
    val rest = zuletzt.filterNot { it.id in favIds }
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl)) {
        if (favoriten.isNotEmpty()) {
            item { AbschnittTitel(stringResource(R.string.favoriten), Modifier.padding(horizontal = Spacing.l)) }
            items(favoriten, key = { "f" + it.id }) { f ->
                LebensmittelZeile(f, onClick = { onLebensmittel(f.id) }, onSchnell = { onSchnell(f) })
                HorizontalDivider(color = AppTheme.colors.border, modifier = Modifier.padding(start = 72.dp))
            }
        }
        if (rest.isNotEmpty()) {
            item { AbschnittTitel(stringResource(R.string.zuletzt_gegessen), Modifier.padding(horizontal = Spacing.l)) }
            items(rest, key = { "z" + it.id }) { f ->
                LebensmittelZeile(f, onClick = { onLebensmittel(f.id) }, onSchnell = { onSchnell(f) })
                HorizontalDivider(color = AppTheme.colors.border, modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

@Composable
private fun WeiterZeile(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(Spacing.s)) { Text(text) }
}

/** "Marke", "Eigenes Produkt" oder "Grundnahrungsmittel". */
@Composable
fun herkunft(f: FoodEntity): String = f.brand ?: when (f.source) {
    "user" -> stringResource(R.string.eigenes_produkt)
    "rezept" -> stringResource(R.string.eigenes_rezept)
    "off" -> stringResource(R.string.open_food_facts)
    else -> stringResource(R.string.grundnahrungsmittel)
}


@Composable
private fun LebensmittelZeile(f: FoodEntity, onClick: () -> Unit, onSchnell: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = Spacing.l, top = Spacing.s, bottom = Spacing.s, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Initiale(f.name)
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(
                f.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    R.string.lebensmittel_unterzeile,
                    herkunft(f),
                    Format.kcal(f.kcal), f.unitBase,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textMuted,
            )
            Text(
                stringResource(R.string.makro_kurz, Format.gramm(f.fett), Format.gramm(f.kh), Format.gramm(f.eiweiss)),
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textMuted,
            )
        }
        IconButton(onClick = onSchnell) {
            Icon(
                Icons.Rounded.AddCircle,
                contentDescription = stringResource(R.string.schnell_eintragen, f.name),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LeerHinweis(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textMuted,
        modifier = Modifier.padding(Spacing.xl),
    )
}

/** Keine Treffer: hier hört es nicht auf, es geht weiter zu Online-Suche oder Produkt anlegen. */
@Composable
private fun NichtsGefunden(eingabe: String, online: Boolean, onOnline: (() -> Unit)?, onAnlegen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            stringResource(if (online) R.string.nichts_gefunden_online else R.string.nichts_gefunden, eingabe.trim()),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(if (online) R.string.nichts_gefunden_online_text else R.string.nichts_gefunden_text),
            style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted,
        )
        if (onOnline != null) FilledTonalButton(onClick = onOnline, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Search, contentDescription = null)
            Spacer(Modifier.width(Spacing.s))
            Text(stringResource(R.string.online_suchen))
        }
        OutlinedButton(onClick = onAnlegen, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.produkt_anlegen))
        }
    }
}
