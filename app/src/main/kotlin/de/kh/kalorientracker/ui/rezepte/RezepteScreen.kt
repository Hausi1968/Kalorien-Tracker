package de.kh.kalorientracker.ui.rezepte

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kh.kalorientracker.R
import de.kh.kalorientracker.core.Format
import de.kh.kalorientracker.core.RezeptRechner
import de.kh.kalorientracker.core.RezeptZutat
import de.kh.kalorientracker.data.FoodEntity
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.data.RezeptZutatEntity
import de.kh.kalorientracker.data.ServingEntity
import de.kh.kalorientracker.data.naehrwerte100
import de.kh.kalorientracker.ui.HinweisBox
import de.kh.kalorientracker.ui.Initiale
import de.kh.kalorientracker.ui.Karte
import de.kh.kalorientracker.ui.MakroDreier
import de.kh.kalorientracker.ui.PrimaerButton
import de.kh.kalorientracker.ui.Seite
import de.kh.kalorientracker.ui.aufHauptThread
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Liste der eigenen Rezepte. Antippen öffnet das Rezept zum Eintragen, „Neues Rezept“ den Editor. */
@Composable
fun RezepteScreen(repo: KalorienRepository, onOeffnen: (String) -> Unit, onNeu: () -> Unit) {
    val rezepte by repo.rezepte.collectAsStateWithLifecycle(initialValue = null)
    Seite(
        titel = stringResource(R.string.tab_rezepte),
        fab = {
            ExtendedFloatingActionButton(
                onClick = onNeu,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.rezept_neu)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { pad ->
        val liste = rezepte ?: return@Seite
        if (liste.isEmpty()) {
            Column(Modifier.padding(pad).padding(Spacing.l)) { HinweisBox(stringResource(R.string.rezepte_leer)) }
            return@Seite
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(liste, key = { it.id }) { r ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOeffnen(r.id) }
                        .padding(horizontal = Spacing.l, vertical = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Initiale(r.name)
                    Spacer(Modifier.width(Spacing.m))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.rezept_unterzeile, Format.kcal(r.kcal)),
                            style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(start = 72.dp), color = AppTheme.colors.surface2)
            }
        }
    }
}

/** Zutat im Editor: Datenbank-Zeile plus das Lebensmittel für die Nährwerte. */
private data class EditorZutat(val zutat: RezeptZutatEntity, val food: FoodEntity)

/**
 * Rezept anlegen oder bearbeiten: Name, Zutaten mit Menge, Anzahl Portionen und optional das Gewicht
 * nach dem Kochen. Werte pro Portion werden live berechnet.
 */
@Composable
fun RezeptEditorScreen(repo: KalorienRepository, rezeptId: String?, onZurueck: () -> Unit, onGespeichert: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var portionenText by remember { mutableStateOf("2") }
    var gewichtText by remember { mutableStateOf("") }
    val zutaten = remember { mutableStateListOf<EditorZutat>() }
    var suchen by remember { mutableStateOf(false) }
    var loeschenFragen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Stand beim Öffnen; weicht der aktuelle davon ab, fragt Zurück erst nach
    var anfang by remember { mutableStateOf<String?>(null) }
    fun stand() = name + "|" + portionenText + "|" + gewichtText + "|" + zutaten.joinToString(";") { "${it.food.id}:${it.zutat.gramm}" }
    LaunchedEffect(rezeptId) {
        val id = rezeptId ?: run { anfang = stand(); return@LaunchedEffect }
        val f = repo.lebensmittel(id) ?: return@LaunchedEffect
        val (portionen, gesamt) = repo.rezeptKopf(id)
        val z = repo.rezeptZutaten(id)
        name = f.name
        portionenText = portionen.toString()
        val summe = z.sumOf { it.first.gramm }
        if (gesamt != null && kotlin.math.abs(gesamt - summe) > 0.5) gewichtText = Format.dezimal(gesamt, 0)
        zutaten.clear(); zutaten.addAll(z.map { EditorZutat(it.first, it.second) })
        anfang = stand()
    }

    val portionen = portionenText.toIntOrNull()?.takeIf { it in 1..50 }
    val gewicht = Format.parseDezimal(gewichtText)?.takeIf { it > 0 }
    val werte = portionen?.let { RezeptRechner.berechne(zutaten.map { RezeptZutat(it.food.naehrwerte100, it.zutat.gramm) }, it, gewicht) }
    val kannSpeichern = name.isNotBlank() && werte != null

    if (suchen) {
        ZutatAuswahlSeite(repo, onZurueck = { suchen = false }) { f, label, menge, gramm ->
            zutaten.add(EditorZutat(RezeptZutatEntity(rezeptId = "", position = 0, foodId = f.id, label = f.name, servingLabel = label, quantity = menge, gramm = gramm), f))
            suchen = false
        }
        return
    }
    if (loeschenFragen && rezeptId != null) {
        AlertDialog(
            onDismissRequest = { loeschenFragen = false },
            title = { Text(stringResource(R.string.rezept_loeschen_frage)) },
            text = { Text(stringResource(R.string.rezept_loeschen_text)) },
            confirmButton = {
                TextButton(onClick = { loeschenFragen = false; scope.launch { repo.loescheRezept(rezeptId); aufHauptThread(onZurueck) } }) {
                    Text(stringResource(R.string.loeschen))
                }
            },
            dismissButton = { TextButton(onClick = { loeschenFragen = false }) { Text(stringResource(R.string.abbrechen)) } },
        )
    }

    var fertig by remember { mutableStateOf(false) }
    val zurueck = de.kh.kalorientracker.ui.verwerfenSchutz(anfang != null && stand() != anfang && !fertig, onZurueck)
    Seite(
        titel = stringResource(if (rezeptId == null) R.string.rezept_neu else R.string.rezept_bearbeiten),
        onZurueck = zurueck,
        snackbar = snackbar,
        aktionen = {
            if (rezeptId != null) {
                IconButton(onClick = { loeschenFragen = true }) { Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.rezept_loeschen_frage)) }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).consumeWindowInsets(pad).imePadding().fillMaxSize(),
            contentPadding = PaddingValues(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(80) }, singleLine = true,
                    label = { Text(stringResource(R.string.rezept_name)) }, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    OutlinedTextField(
                        value = portionenText, onValueChange = { portionenText = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.rezept_portionen)) }, singleLine = true,
                        isError = portionen == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = gewichtText, onValueChange = { gewichtText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                        label = { Text(stringResource(R.string.rezept_gewicht_gekocht)) }, singleLine = true,
                        placeholder = { werte?.let { Text(Format.dezimal(zutaten.sumOf { z -> z.zutat.gramm }, 0)) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1.4f),
                    )
                }
            }
            item {
                Text(stringResource(R.string.rezept_gewicht_hinweis), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
            }
            item {
                Text(stringResource(R.string.rezept_zutaten), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (zutaten.isEmpty()) item { HinweisBox(stringResource(R.string.rezept_zutaten_leer)) }
            items(zutaten.size) { i ->
                val z = zutaten[i]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(z.food.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(
                                R.string.rezept_zutat_menge, mengenText(z.zutat),
                                Format.kcal(z.food.naehrwerte100.fuerGramm(z.zutat.gramm).kcal),
                            ),
                            style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted,
                        )
                    }
                    IconButton(onClick = { zutaten.removeAt(i) }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.rezept_zutat_entfernen, z.food.name), tint = AppTheme.colors.textMuted)
                    }
                }
            }
            item {
                OutlinedButton(onClick = { suchen = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(Spacing.s))
                    Text(stringResource(R.string.rezept_zutat_hinzufuegen))
                }
            }
            werte?.let { w ->
                item {
                    Karte(titel = stringResource(R.string.rezept_pro_portion, Format.dezimal(w.portionGramm, 0))) {
                        Text(
                            stringResource(R.string.einheit_kcal, Format.kcal(w.proPortion.kcal)),
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.rezept_gesamt, Format.kcal(w.gesamt.kcal), Format.kcal(w.pro100.kcal)),
                            style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted,
                        )
                        Spacer(Modifier.padding(top = Spacing.s))
                        MakroDreier(w.proPortion.fett, w.proPortion.kh, w.proPortion.eiweiss, null)
                    }
                }
            }
            item {
                PrimaerButton(stringResource(R.string.speichern), aktiv = kannSpeichern, onClick = {
                    scope.launch {
                        val gespeichert = repo.speichereRezept(
                            rezeptId, name, portionen ?: 1, gewicht, zutaten.map { it.zutat to it.food },
                        )
                        if (gespeichert != null) { fertig = true; aufHauptThread { onGespeichert(gespeichert.id) } }
                    }
                })
            }
        }
    }
}

private fun mengenText(z: RezeptZutatEntity): String =
    if (z.servingLabel == null || z.servingLabel.startsWith("100 ")) "${Format.dezimal(z.gramm, 0)} g"
    else "${Format.dezimal(z.quantity)} × ${z.servingLabel} (${Format.dezimal(z.gramm, 0)} g)"

/**
 * Zutat auswählen als eigene Seite (Listen sind in kleinen Fenstern zu eng): oben suchen, Lebensmittel antippen,
 * dann Menge in Gramm oder als Portion angeben.
 */
@Composable
fun ZutatAuswahlSeite(
    repo: KalorienRepository,
    titel: String? = null,
    startText: String = "",
    onZurueck: () -> Unit,
    onFertig: (food: FoodEntity, label: String?, menge: Double, gramm: Double) -> Unit,
) {
    var eingabe by remember { mutableStateOf(startText) }
    var treffer by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }
    var online by remember { mutableStateOf<List<FoodEntity>?>(null) }
    var laedt by remember { mutableStateOf(false) }
    var zuletzt by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }
    var gewaehltesFood by remember { mutableStateOf<FoodEntity?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { zuletzt = repo.zuletztGegessen(10).filter { it.source != "rezept" } }
    LaunchedEffect(eingabe) {
        online = null
        if (eingabe.trim().length < 2) { treffer = emptyList(); return@LaunchedEffect }
        delay(150)
        treffer = repo.suche(eingabe).filter { it.source != "rezept" }
    }
    androidx.activity.compose.BackHandler { if (gewaehltesFood != null) gewaehltesFood = null else onZurueck() }

    Seite(
        titel = titel ?: stringResource(R.string.rezept_zutat_hinzufuegen),
        onZurueck = { if (gewaehltesFood != null) gewaehltesFood = null else onZurueck() },
    ) { pad ->
        val f = gewaehltesFood
        if (f != null) {
            ZutatMenge(repo, f, Modifier.padding(pad)) { label, menge, gramm -> onFertig(f, label, menge, gramm) }
            return@Seite
        }
        val liste = (online ?: emptyList()) + treffer.filter { t -> online?.none { it.id == t.id } ?: true }
        val zeige = if (eingabe.trim().length < 2) zuletzt else liste
        LazyColumn(Modifier.padding(pad).consumeWindowInsets(pad).imePadding().fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                OutlinedTextField(
                    value = eingabe, onValueChange = { eingabe = it.take(60) }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.suche_platzhalter)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
                )
            }
            items(zeige, key = { it.id }) { food ->
                Row(
                    Modifier.fillMaxWidth().clickable { gewaehltesFood = food }.padding(horizontal = Spacing.l, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Initiale(food.name)
                    Spacer(Modifier.width(Spacing.m))
                    Column(Modifier.weight(1f)) {
                        Text(food.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(food.brand, "${Format.kcal(food.kcal)} kcal / 100 ${food.unitBase}").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted,
                        )
                    }
                }
            }
            if (eingabe.trim().length >= 2 && online == null) {
                item {
                    TextButton(enabled = !laedt, modifier = Modifier.padding(horizontal = Spacing.s), onClick = {
                        laedt = true
                        scope.launch { online = repo.onlineSuche(eingabe) ?: emptyList(); laedt = false }
                    }) { Text(stringResource(if (laedt) R.string.suche_laedt else R.string.online_suchen)) }
                }
            }
        }
    }
}

/** Menge einer Zutat: in Gramm oder als Vielfaches einer Portion des Lebensmittels. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ZutatMenge(
    repo: KalorienRepository, f: FoodEntity, modifier: Modifier,
    onFertig: (label: String?, menge: Double, gramm: Double) -> Unit,
) {
    var portionen by remember { mutableStateOf<List<ServingEntity>>(emptyList()) }
    var gewaehlt by remember { mutableStateOf<ServingEntity?>(null) }
    var mengeText by remember { mutableStateOf("100") }
    LaunchedEffect(f.id) { portionen = repo.portionen(f.id).filter { !it.label.startsWith("100 ") } }
    val menge = Format.parseDezimal(mengeText)?.takeIf { it > 0 && it < 100_000 }
    val gramm = menge?.let { m -> gewaehlt?.let { it.gramm * m } ?: m }
    Column(modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(f.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            AssistChip(onClick = { gewaehlt = null; mengeText = "100" }, label = { Text(f.unitBase) })
            portionen.forEach { p ->
                AssistChip(onClick = { gewaehlt = p; mengeText = "1" }, label = { Text(p.label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
        }
        OutlinedTextField(
            value = mengeText, onValueChange = { mengeText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) },
            label = { Text(gewaehlt?.let { stringResource(R.string.rezept_anzahl, it.label) } ?: stringResource(R.string.rezept_menge_in, f.unitBase)) },
            singleLine = true, isError = menge == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        gramm?.let {
            Text(
                stringResource(R.string.rezept_menge_ergebnis, Format.dezimal(it, 0), f.unitBase, Format.kcal(f.naehrwerte100.fuerGramm(it).kcal)),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        PrimaerButton(stringResource(R.string.hinzufuegen), aktiv = gramm != null, onClick = { onFertig(gewaehlt?.label, menge!!, gramm!!) })
    }
}
