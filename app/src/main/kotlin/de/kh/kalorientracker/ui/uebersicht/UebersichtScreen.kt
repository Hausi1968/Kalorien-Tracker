@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package de.kh.kalorientracker.ui.uebersicht

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kh.kalorientracker.R
import de.kh.kalorientracker.core.Format
import de.kh.kalorientracker.core.Mahlzeiten
import de.kh.kalorientracker.core.Mikros
import de.kh.kalorientracker.data.DiaryEntryEntity
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.data.TagesDaten
import de.kh.kalorientracker.ui.Karte
import de.kh.kalorientracker.ui.Seite
import de.kh.kalorientracker.ui.theme.AppTheme
import de.kh.kalorientracker.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val DATUM = DateTimeFormatter.ofPattern("EEE, d. MMMM", Locale.GERMANY)

/** Kurze Namen für die Balken, damit sechs Mahlzeiten nebeneinander passen. */
private val KURZ = mapOf(1 to "Frühst.", 2 to "Snack 1", 3 to "Mittag", 4 to "Snack 2", 5 to "Abend", 6 to "Snack 3")

/** Wonach "Einträge mit viel …" sortiert. */
private enum class Sortierung(val text: Int, val wert: (DiaryEntryEntity) -> Double?) {
    KCAL(R.string.kcal, { it.kcal }),
    FETT(R.string.makro_fett, { it.fett }),
    KH(R.string.makro_kh, { it.kh }),
    EIWEISS(R.string.makro_eiweiss, { it.eiweiss }),
    ZUCKER(R.string.zucker, { it.zucker }),
}

/**
 * Tagesübersicht: wie verteilen sich Kalorien und Nährwerte über den Tag, und welche Einträge
 * tragen am meisten bei. Zwei Reiter: Kalorien und Nährwerte.
 */
@Composable
fun UebersichtScreen(repo: KalorienRepository, start: LocalDate, onZurueck: () -> Unit, onEintrag: (String) -> Unit) {
    var tag by rememberSaveable { mutableStateOf(start) }
    var reiter by rememberSaveable { mutableIntStateOf(0) }
    val daten by remember(tag) { repo.tag(tag) }.collectAsStateWithLifecycle(initialValue = null)

    Seite(titel = stringResource(R.string.uebersicht_titel), onZurueck = onZurueck) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { tag = tag.minusDays(1) }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.tag_vorher))
                }
                Text(tagText(tag), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { tag = tag.plusDays(1) }) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.tag_nachher))
                }
            }
            PrimaryScrollableTabRow(selectedTabIndex = reiter, containerColor = MaterialTheme.colorScheme.background, edgePadding = Spacing.s) {
                Tab(reiter == 0, onClick = { reiter = 0 }, text = { Text(stringResource(R.string.uebersicht_kalorien)) })
                Tab(reiter == 1, onClick = { reiter = 1 }, text = { Text(stringResource(R.string.uebersicht_naehrwerte)) })
                Tab(reiter == 2, onClick = { reiter = 2 }, text = { Text(stringResource(R.string.uebersicht_mikros)) })
                Tab(reiter == 3, onClick = { reiter = 3 }, text = { Text(stringResource(R.string.uebersicht_woche)) })
            }
            val d = daten ?: return@Column
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (reiter == 3) {
                    WochenReiter(repo, tag) { tag = it; reiter = 0 }
                    return@Column
                }
                if (d.eintraege.isEmpty()) {
                    Karte { Text(stringResource(R.string.uebersicht_leer), color = AppTheme.colors.textMuted) }
                    return@Column
                }
                when (reiter) {
                    0 -> KalorienReiter(d, onEintrag)
                    1 -> NaehrwertReiter(d)
                    else -> MikrosReiter(d)
                }
            }
        }
    }
}

@Composable
private fun tagText(t: LocalDate): String {
    val heute = LocalDate.now()
    return when (t) {
        heute -> stringResource(R.string.heute)
        heute.minusDays(1) -> stringResource(R.string.gestern)
        heute.plusDays(1) -> stringResource(R.string.morgen)
        else -> t.format(DATUM)
    }
}

// ---------------- Kalorien ----------------

@Composable
private fun KalorienReiter(d: TagesDaten, onEintrag: (String) -> Unit) {
    val b = d.bilanz
    Karte {
        Text(
            stringResource(R.string.einheit_kcal, Format.kcal(b.gegessen.toDouble())),
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(R.string.uebersicht_gegessen), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
        Spacer(Modifier.height(Spacing.l))
        SaeulenDiagramm(
            gruppen = Mahlzeiten.STANDARD.map { s -> (KURZ[s.id] ?: s.name) to listOf(d.imSlot(s.id).sumOf { it.kcal }) },
            farben = listOf(AppTheme.colors.burned),
        )
        HorizontalDivider(Modifier.padding(vertical = Spacing.m), color = AppTheme.colors.border)
        Row(Modifier.fillMaxWidth()) {
            Kennzahl(
                Format.kcal(abs(b.uebrig).toDouble()) + " kcal",
                stringResource(if (b.zuViel) R.string.bilanz_zu_viel else R.string.bilanz_uebrig),
                Modifier.weight(1f),
            )
            Kennzahl("${(b.anteil * 100).roundToInt()} %", stringResource(R.string.uebersicht_erreicht), Modifier.weight(1f))
            Kennzahl(d.eintraege.size.toString(), stringResource(R.string.uebersicht_eintraege), Modifier.weight(1f))
        }
    }
    GroessteEintraege(d.eintraege, onEintrag)
}

@Composable
private fun Kennzahl(wert: String, text: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(wert, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(text, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted, maxLines = 1)
    }
}

@Composable
private fun GroessteEintraege(eintraege: List<DiaryEntryEntity>, onEintrag: (String) -> Unit) {
    var sortierung by rememberSaveable { mutableStateOf(Sortierung.KCAL) }
    Karte(titel = stringResource(R.string.uebersicht_viel)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Sortierung.entries.forEach { s ->
                FilterChip(selected = s == sortierung, onClick = { sortierung = s }, label = { Text(stringResource(s.text)) })
            }
        }
        Spacer(Modifier.height(Spacing.s))
        val liste = eintraege.filter { (sortierung.wert(it) ?: 0.0) > 0 }.sortedByDescending { sortierung.wert(it) }.take(10)
        val gesamt = eintraege.sumOf { sortierung.wert(it) ?: 0.0 }
        if (liste.isEmpty()) {
            Text(stringResource(R.string.uebersicht_keine_werte), style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textMuted)
        }
        liste.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = AppTheme.colors.border)
            val wert = sortierung.wert(e) ?: 0.0
            Row(
                Modifier.fillMaxWidth().clickable(enabled = e.foodRef != null) { onEintrag(e.id) }.padding(vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            e.servingLabel?.let { "${Format.dezimal(e.quantity)} × $it" },
                            stringResource(R.string.makro_kurz, Format.gramm(e.fett), Format.gramm(e.kh), Format.gramm(e.eiweiss)),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (sortierung == Sortierung.KCAL) stringResource(R.string.einheit_kcal, Format.kcal(wert))
                        else stringResource(R.string.einheit_gramm, Format.gramm(wert)),
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                    )
                    if (gesamt > 0) {
                        Text(
                            stringResource(R.string.uebersicht_anteil, (wert / gesamt * 100).roundToInt()),
                            style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

// ---------------- Nährwerte ----------------

@Composable
private fun NaehrwertReiter(d: TagesDaten) {
    val c = AppTheme.colors
    val s = d.summe
    Karte(titel = stringResource(R.string.uebersicht_je_mahlzeit)) {
        SaeulenDiagramm(
            gruppen = Mahlzeiten.STANDARD.map { slot ->
                val e = d.imSlot(slot.id)
                (KURZ[slot.id] ?: slot.name) to listOf(e.sumOf { it.fett ?: 0.0 }, e.sumOf { it.kh ?: 0.0 }, e.sumOf { it.eiweiss ?: 0.0 })
            },
            farben = listOf(c.macroFat, c.macroCarbs, c.macroProtein),
            einheit = "g",
        )
        Spacer(Modifier.height(Spacing.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Legende(stringResource(R.string.makro_fett), c.macroFat)
            Spacer(Modifier.width(Spacing.m))
            Legende(stringResource(R.string.makro_kh), c.macroCarbs)
            Spacer(Modifier.width(Spacing.m))
            Legende(stringResource(R.string.makro_eiweiss), c.macroProtein)
        }
    }
    val ziel = d.makroZiel
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MakroRing(stringResource(R.string.makro_fett), s.fett ?: 0.0, ziel?.fett, c.macroFat, Modifier.weight(1f))
        MakroRing(stringResource(R.string.makro_kh_kurz), s.kh ?: 0.0, ziel?.kh, c.macroCarbs, Modifier.weight(1f))
        MakroRing(stringResource(R.string.makro_eiweiss), s.eiweiss ?: 0.0, ziel?.eiweiss, c.macroProtein, Modifier.weight(1f))
    }

    // Richtwerte für Erwachsene (DGE/WHO): höchstens 10 % der Energie aus Zucker und aus gesättigten Fetten,
    // höchstens 6 g Salz, mindestens 30 g Ballaststoffe.
    val kcalZiel = (d.profil?.tageszielKcal ?: 2000).toDouble()
    Karte(titel = stringResource(R.string.uebersicht_weitere)) {
        NaehrwertZeile(stringResource(R.string.ges_fett), s.gesFett, kcalZiel * 0.10 / 9, hoechstens = true)
        HorizontalDivider(color = AppTheme.colors.border)
        NaehrwertZeile(stringResource(R.string.zucker), s.zucker, kcalZiel * 0.10 / 4, hoechstens = true)
        HorizontalDivider(color = AppTheme.colors.border)
        NaehrwertZeile(stringResource(R.string.ballaststoffe), s.ballaststoffe, 30.0, hoechstens = false)
        HorizontalDivider(color = AppTheme.colors.border)
        NaehrwertZeile(stringResource(R.string.salz), s.salz, 6.0, hoechstens = true)
        Spacer(Modifier.height(Spacing.s))
        Text(stringResource(R.string.uebersicht_richtwerte), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
    }
}

// ---------------- Mikros ----------------

@Composable
private fun MikrosReiter(d: TagesDaten) {
    val mitAngaben = d.eintraege.filter { !it.mikros.isNullOrEmpty() }
    if (mitAngaben.isEmpty()) {
        Karte { Text(stringResource(R.string.uebersicht_mikros_leer), color = AppTheme.colors.textMuted) }
        return
    }
    val summe = Mikros.summe(mitAngaben.map { Mikros.lese(it.mikros) })
    Text(
        stringResource(R.string.uebersicht_mikros_hinweis, mitAngaben.size, d.eintraege.size),
        style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted,
    )
    Mikros.Gruppe.entries.forEach { gruppe ->
        val stoffe = Mikros.ALLE.filter { it.gruppe == gruppe && it.schluessel in summe }
        if (stoffe.isEmpty()) return@forEach
        Karte(
            titel = stringResource(
                when (gruppe) {
                    Mikros.Gruppe.VITAMINE -> R.string.mikros_vitamine
                    Mikros.Gruppe.MINERALSTOFFE -> R.string.mikros_mineralstoffe
                    Mikros.Gruppe.WEITERE -> R.string.mikros_weitere
                },
            ),
        ) {
            stoffe.forEachIndexed { i, st ->
                if (i > 0) HorizontalDivider(color = AppTheme.colors.border)
                val g = summe.getValue(st.schluessel)
                val prozent = Mikros.prozent(st, g)
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Text(st.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(Mikros.anzeige(g), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(Spacing.m))
                    Text(
                        prozent?.let { "$it %" } ?: "",
                        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End,
                        color = if ((prozent ?: 0) >= 100) AppTheme.colors.success else AppTheme.colors.textMuted,
                        modifier = Modifier.width(52.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Legende(text: String, farbe: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(farbe, MaterialTheme.shapes.extraSmall))
        Spacer(Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** Ring mit Anteil am Tagesziel, in der Mitte "x g übrig" bzw. "x g zu viel". */
@Composable
private fun MakroRing(name: String, gramm: Double, ziel: Int?, farbe: Color, modifier: Modifier) {
    val anteil = if (ziel == null || ziel <= 0) 0f else (gramm / ziel).toFloat()
    val rest = (ziel ?: 0) - gramm
    val spur = AppTheme.colors.surface2
    Karte(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Spacing.s))
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val strich = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(spur, 0f, 360f, false, style = strich)
                    drawArc(farbe, -90f, 360f * anteil.coerceIn(0f, 1f), false, style = strich)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (ziel != null) Text("${(anteil * 100).roundToInt()} %", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
                    Text(
                        stringResource(R.string.einheit_gramm, Format.gramm(if (ziel != null) abs(rest) else gramm)),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    )
                    if (ziel != null) {
                        Text(
                            stringResource(if (rest < 0) R.string.bilanz_zu_viel else R.string.bilanz_uebrig),
                            style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                if (ziel != null) stringResource(R.string.makro_von_ziel, Format.gramm(gramm), ziel)
                else stringResource(R.string.einheit_gramm, Format.gramm(gramm)),
                style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted, maxLines = 1,
            )
        }
    }
}

@Composable
private fun NaehrwertZeile(name: String, gramm: Double?, richtwert: Double, hoechstens: Boolean) {
    val anteil = if (gramm == null) null else (gramm / richtwert * 100).roundToInt()
    // Bei "höchstens" ist zu viel schlecht, bei "mindestens" (Ballaststoffe) zu wenig
    val gut = anteil == null || if (hoechstens) anteil <= 100 else anteil >= 100
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    if (hoechstens) R.string.uebersicht_hoechstens else R.string.uebersicht_mindestens,
                    Format.gramm(richtwert),
                ),
                style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted,
            )
        }
        Text(
            gramm?.let { stringResource(R.string.einheit_gramm, Format.gramm(it)) } ?: "–",
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(Spacing.m))
        Text(
            anteil?.let { "$it %" } ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = if (gut) AppTheme.colors.success else AppTheme.colors.warning,
            textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp),
        )
    }
}

/**
 * Säulen je Mahlzeit; mehrere Werte je Gruppe stehen nebeneinander. Höhe relativ zum größten Wert.
 */
@Composable
private fun SaeulenDiagramm(
    gruppen: List<Pair<String, List<Double>>>,
    farben: List<Color>,
    einheit: String? = null,
    hoehe: Dp = 120.dp,
) {
    val maximum = gruppen.flatMap { it.second }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        gruppen.forEach { (name, werte) ->
            val ansage = name + ": " + werte.joinToString(", ") { Format.kcal(it) + (einheit?.let { e -> " $e" } ?: " kcal") }
            Column(
                Modifier.weight(1f).semantics { contentDescription = ansage },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    werte.forEachIndexed { i, w ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                Format.kcal(w), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                color = AppTheme.colors.textMuted,
                            )
                            Box(
                                Modifier
                                    .width(if (werte.size == 1) 28.dp else 10.dp)
                                    .height((hoehe * (w / maximum).toFloat()).coerceAtLeast(2.dp))
                                    .background(farben[i % farben.size], MaterialTheme.shapes.extraSmall),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }
}

/** Woche: kcal je Tag als Säulen, Durchschnitt, an wie vielen Tagen das Ziel getroffen wurde. */
@Composable
private fun WochenReiter(repo: KalorienRepository, tag: LocalDate, onTag: (LocalDate) -> Unit) {
    val woche by remember(tag.minusDays((tag.dayOfWeek.value - 1).toLong())) { repo.woche(tag) }
        .collectAsStateWithLifecycle(initialValue = null)
    val tage = woche ?: return
    val kurz = java.time.format.DateTimeFormatter.ofPattern("EE", java.util.Locale.GERMANY)
    val heute = LocalDate.now()
    val mitEintraegen = tage.filter { it.eintraege.isNotEmpty() }
    val schnitt = if (mitEintraegen.isEmpty()) 0.0 else mitEintraegen.sumOf { it.summe.kcal } / mitEintraegen.size
    val getroffen = mitEintraegen.count { !it.bilanz.zuViel }
    val ziel = tage.firstNotNullOfOrNull { it.profil?.tageszielKcal } ?: 0
    val von = tage.first().tag; val bis = tage.last().tag
    val datum = java.time.format.DateTimeFormatter.ofPattern("d. MMM", java.util.Locale.GERMANY)

    Text(stringResource(R.string.woche_zeitraum, von.format(datum), bis.format(datum)), style = MaterialTheme.typography.titleMedium)
    Karte {
        Row(Modifier.fillMaxWidth()) {
            Kennzahl(Format.kcal(schnitt), stringResource(R.string.woche_schnitt_kurz), Modifier.weight(1f))
            Kennzahl("$getroffen / ${mitEintraegen.size}", stringResource(R.string.woche_getroffen), Modifier.weight(1f))
            Kennzahl(Format.kcal(ziel.toDouble()), stringResource(R.string.woche_ziel), Modifier.weight(1f))
        }
    }
    Karte(titel = stringResource(R.string.woche_je_tag)) {
        SaeulenDiagramm(
            tage.map { it.tag.format(kurz).removeSuffix(".") to listOf(it.summe.kcal) },
            listOf(MaterialTheme.colorScheme.primary),
        )
    }
    Karte {
        tage.forEachIndexed { i, d ->
            if (i > 0) androidx.compose.material3.HorizontalDivider(color = AppTheme.colors.border)
            val leer = d.eintraege.isEmpty()
            Row(
                Modifier.fillMaxWidth().clickable { onTag(d.tag) }.padding(vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    d.tag.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d.M.", java.util.Locale.GERMANY)),
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                    fontWeight = if (d.tag == heute) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    if (leer) "–" else stringResource(R.string.einheit_kcal, Format.kcal(d.summe.kcal)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(Spacing.m))
                val (zeichen, farbe, ansage) = when {
                    leer -> Triple("·", AppTheme.colors.textMuted, R.string.woche_ohne)
                    d.bilanz.zuViel -> Triple("✗", AppTheme.colors.warning, R.string.woche_drueber)
                    else -> Triple("✓", MaterialTheme.colorScheme.primary, R.string.woche_im_ziel)
                }
                val ansageText = stringResource(ansage)
                Text(
                    zeichen, color = farbe, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(20.dp).semantics { contentDescription = ansageText },
                )
            }
        }
    }
    Text(stringResource(R.string.woche_hinweis), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textMuted)
}
