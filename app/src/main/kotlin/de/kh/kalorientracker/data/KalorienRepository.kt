package de.kh.kalorientracker.data

import de.kh.kalorientracker.core.Bedarfsrechner
import de.kh.kalorientracker.core.Geschlecht
import de.kh.kalorientracker.core.Koerperdaten
import de.kh.kalorientracker.core.MakroAnteile
import de.kh.kalorientracker.core.MakroGramm
import de.kh.kalorientracker.core.Mikros
import de.kh.kalorientracker.core.Naehrwerte
import de.kh.kalorientracker.core.Richtwerte
import de.kh.kalorientracker.core.Ranking
import de.kh.kalorientracker.core.RezeptRechner
import de.kh.kalorientracker.core.RezeptZutat
import de.kh.kalorientracker.core.SuchText
import de.kh.kalorientracker.core.Tagesbilanz
import de.kh.kalorientracker.core.Ziel
import de.kh.kalorientracker.core.Zielvorgabe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlin.math.roundToInt

/** Alles, was ein Tag im Tagebuch braucht. */
data class TagesDaten(
    val tag: LocalDate,
    val profil: ProfileEntity?,
    val eintraege: List<DiaryEntryEntity>,
    val verbrannt: Double,
) {
    val summe: Naehrwerte = eintraege.fold(Naehrwerte.NULL) { acc, e -> acc + e.naehrwerte }
    val bilanz: Tagesbilanz = Tagesbilanz(profil?.tageszielKcal ?: 0, summe.kcal.roundToInt(), verbrannt.roundToInt())
    val makroZiel: MakroGramm? = profil?.let {
        MakroAnteile(it.fettProzent, it.khProzent, it.eiweissProzent).inGramm(it.tageszielKcal.toDouble())
    }
    fun imSlot(slotId: Int) = eintraege.filter { it.slotId == slotId }
}

/** Ergebnis einer Barcode-Abfrage. */
sealed interface BarcodeErgebnis {
    data class Gefunden(val food: FoodEntity) : BarcodeErgebnis
    data object Unbekannt : BarcodeErgebnis
    data object KeineVerbindung : BarcodeErgebnis
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KalorienRepository(
    private val db: AppDatabase,
    private val off: OpenFoodFacts = OpenFoodFacts(),
    val quellen: Quellen = Quellen(),
) {
    private val gewichtModus get() = quellen.wahl.value.gewicht.name
    private val zone: ZoneId get() = ZoneId.systemDefault()

    // ---------- Profil ----------
    val profil: Flow<ProfileEntity?> = db.profile().beobachte()
    val letztesGewicht: Flow<BodyEntryEntity?> = quellen.wahl.flatMapLatest { db.body().letztesGewicht(it.gewicht.name) }

    suspend fun ladeProfil(): ProfileEntity? = db.profile().lade()
    suspend fun ladeLetztesGewicht(): Double? = db.body().ladeLetztesGewicht(gewichtModus)?.value

    /** Speichert Profil und Ziel; das Gewicht wird zusätzlich als Körperwert für heute abgelegt. */
    suspend fun speichereProfil(p: ProfileEntity, gewichtKg: Double) {
        val bisher = db.profile().lade()
        val jetzt = System.currentTimeMillis()
        db.profile().speichere(
            if (bisher == null) p.copy(updatedAt = jetzt)
            else p.copy(startGewichtKg = bisher.startGewichtKg, startDatum = bisher.startDatum, updatedAt = jetzt),
        )
        val heute = LocalDate.now(zone)
        db.body().speichere(
            BodyEntryEntity(
                id = "gewicht:$heute", day = heute.toString(), measuredAt = jetzt,
                metric = "gewicht_kg", value = gewichtKg,
            ),
        )
    }

    // ---------- Tagebuch ----------
    fun tag(t: LocalDate): Flow<TagesDaten> = combine(
        db.profile().beobachte(),
        db.diary().eintraegeAm(t.toString()),
        quellen.wahl.flatMapLatest { db.activity().verbranntAm(t.toString(), it.verbrannt.name, it.hcAnteil / 100.0) },
    ) { p, e, v -> TagesDaten(t, p, e, v) }

    /** Die 7 Tage der Woche (Montag bis Sonntag), in der [t] liegt. */
    fun woche(t: LocalDate): Flow<List<TagesDaten>> {
        val montag = t.minusDays((t.dayOfWeek.value - 1).toLong())
        return combine((0L..6L).map { tag(montag.plusDays(it)) }) { it.toList() }
    }

    /** Gegessene kcal je Tag im Zeitraum (für die Wochenleiste). */
    fun kcalProTag(von: LocalDate, bis: LocalDate): Flow<Map<LocalDate, Double>> =
        db.diary().kcalProTag(von.toString(), bis.toString())
            .map { liste -> liste.associate { LocalDate.parse(it.day) to it.kcal } }

    suspend fun eintragen(
        food: FoodEntity,
        portionLabel: String,
        grammJePortion: Double,
        menge: Double,
        zeitpunkt: LocalDateTime,
        slotId: Int,
        foto: String? = null,
    ): DiaryEntryEntity {
        val jetzt = System.currentTimeMillis()
        val e = baueEintrag(UUID.randomUUID().toString(), food, portionLabel, grammJePortion, menge, zeitpunkt, slotId, jetzt, foto)
        db.diary().speichere(e)
        return e
    }

    /** Eintrag ändern: Nährwerte werden aus dem Lebensmittel neu berechnet. */
    suspend fun aktualisiere(
        alt: DiaryEntryEntity,
        food: FoodEntity,
        portionLabel: String,
        grammJePortion: Double,
        menge: Double,
        zeitpunkt: LocalDateTime,
        slotId: Int,
    ) {
        val neu = baueEintrag(alt.id, food, portionLabel, grammJePortion, menge, zeitpunkt, slotId, alt.createdAt, alt.foto)
        db.diary().speichere(neu)
    }

    suspend fun eintrag(id: String): DiaryEntryEntity? = db.diary().lade(id)

    private fun baueEintrag(
        id: String, food: FoodEntity, portionLabel: String, grammJePortion: Double, menge: Double,
        zeitpunkt: LocalDateTime, slotId: Int, erstellt: Long, foto: String? = null,
    ): DiaryEntryEntity {
        val gramm = grammJePortion * menge
        val n = food.naehrwerte100.fuerGramm(gramm)
        return DiaryEntryEntity(
            id = id,
            day = zeitpunkt.toLocalDate().toString(),
            eatenAt = zeitpunkt.atZone(zone).toInstant().toEpochMilli(),
            slotId = slotId,
            kind = "food",
            foodRef = food.id,
            label = food.name,
            brand = food.brand,
            servingLabel = portionLabel,
            quantity = menge,
            gramm = gramm,
            kcal = n.kcal, fett = n.fett, gesFett = n.gesFett, kh = n.kh, zucker = n.zucker,
            ballaststoffe = n.ballaststoffe, eiweiss = n.eiweiss, salz = n.salz,
            createdAt = erstellt, updatedAt = System.currentTimeMillis(),
            mikros = Mikros.kodiere(Mikros.mal(Mikros.lese(food.mikros), gramm / 100.0)),
            foto = foto,
        )
    }

    suspend fun loesche(e: DiaryEntryEntity) = db.diary().loesche(e.id)
    suspend fun wiederherstellen(e: DiaryEntryEntity) = db.diary().speichere(e)

    // ---------- Lebensmittel ----------
    suspend fun lebensmittel(id: String): FoodEntity? = db.food().lade(id)
    suspend fun portionen(foodId: String): List<ServingEntity> = db.food().portionen(foodId)
    suspend fun letztePortion(foodId: String): String? = db.diary().letztePortion(foodId)

    suspend fun zuletztGegessen(anzahl: Int = 15): List<FoodEntity> {
        val ids = db.diary().zuletztGegessen(anzahl).map { it.foodRef }
        if (ids.isEmpty()) return emptyList()
        val nachId = db.food().ladeAlle(ids).associateBy { it.id }
        return ids.mapNotNull { nachId[it] }
    }

    /** Lokale Suche: erstes Wort per LIKE, weitere Wörter als Filter, dann eigenes Ranking. */
    suspend fun suche(eingabe: String): List<FoodEntity> {
        val woerter = SuchText.normalisiere(eingabe).split(' ').filter { it.isNotBlank() }
        val erstes = woerter.firstOrNull()?.takeIf { it.length >= 2 } ?: return emptyList()
        // Erstes Wort am Wortanfang, alle weiteren irgendwo im Namen: direkt in der Datenbank filtern,
        // damit das Limit erst nach dem Filter greift ("milch hafer" findet Hafermilch).
        val weitere = woerter.drop(1).distinct()
        val sql = buildString {
            // Auch die Marke zählt: "exquisa" findet die Produkte dieser Marke
            append("SELECT * FROM food WHERE (nameNorm LIKE ? OR nameNorm LIKE ? OR LOWER(brand) LIKE ?)")
            weitere.forEach { _ -> append(" AND (nameNorm LIKE ? OR LOWER(brand) LIKE ?)") }
            append(" ORDER BY (source IN ('start', 'user', 'rezept')) DESC, beliebtheit DESC, LENGTH(name) LIMIT 120")
        }
        val argumente: Array<Any?> =
            (listOf("$erstes%", "% $erstes%", "$erstes%") + weitere.flatMap { listOf("%$it%", "%$it%") }).toTypedArray()
        val roh = db.food().sucheRoh(SimpleSQLiteQuery(sql, argumente))
        val zuletzt = db.diary().zuletztGegessen(200).associate { it.foodRef to it.zuletzt }
        val favoriten = db.favorite().ladeIds().toSet()
        val jetzt = Instant.now()
        val kandidaten = roh.map { f ->
            Ranking.Kandidat(
                id = f.id,
                name = f.name,
                istGrundnahrungsmittel = f.source == "start",
                beliebtheit = f.beliebtheit,
                zuletztGegessenVorTagen = zuletzt[f.id]?.let { Duration.between(Instant.ofEpochMilli(it), jetzt).toDays().toInt() },
                istFavorit = f.id in favoriten,
            )
        }
        val nachId = roh.associateBy { it.id }
        return Ranking.sortiere(eingabe, kandidaten).mapNotNull { nachId[it.id] }
    }

    // ---------- Open Food Facts + eigene Produkte ----------

    /** Erst lokal (inkl. früher geladener OFF-Produkte), dann online. Online-Treffer werden gespeichert. */
    suspend fun barcode(ean: String): BarcodeErgebnis {
        db.food().ladeBarcode(ean)?.let { return BarcodeErgebnis.Gefunden(it) }
        val treffer = try {
            off.produkt(ean)
        } catch (e: java.io.IOException) {
            return BarcodeErgebnis.KeineVerbindung
        } catch (e: kotlinx.serialization.SerializationException) {
            return BarcodeErgebnis.KeineVerbindung
        } ?: return BarcodeErgebnis.Unbekannt
        speichere(listOf(treffer))
        return BarcodeErgebnis.Gefunden(treffer.food)
    }

    /** Online-Suche; alle Treffer landen in der lokalen Datenbank. null = keine Verbindung. */
    suspend fun onlineSuche(eingabe: String): List<FoodEntity>? {
        val treffer = try {
            off.suche(eingabe)
        } catch (e: java.io.IOException) {
            return null
        } catch (e: kotlinx.serialization.SerializationException) {
            return null
        }
        return speichere(treffer)
    }

    /**
     * Speichert OFF-Treffer. Unangetastet bleiben: Produkte, deren Barcode schon als eigenes Produkt existiert,
     * und umbenannte Produkte (source "user"). Gelernte Suchbegriffe, Beliebtheit und alle Portionen bleiben.
     */
    private suspend fun speichere(treffer: List<OffTreffer>): List<FoodEntity> {
        val neu = treffer.filter { t ->
            val vorhanden = t.food.barcode?.let { db.food().ladeBarcode(it) }
            vorhanden == null || (vorhanden.id == t.food.id && vorhanden.source != "user")
        }
        db.withTransaction {
            val alt = db.food().ladeAlle(neu.map { it.food.id }).associateBy { it.id }
            db.food().fuegeEin(
                neu.map { t ->
                    val a = alt[t.food.id]
                    t.food.copy(
                        beliebtheit = maxOf(t.food.beliebtheit, a?.beliebtheit ?: 0),
                        // nameNorm kann gelernte Suchbegriffe enthalten: dann behalten
                        nameNorm = a?.nameNorm?.takeIf { it.contains(t.food.nameNorm) } ?: t.food.nameNorm,
                    )
                },
            )
            neu.forEach { t -> ergaenzePortionen(t.food.id, t.portionen, vorhandenesProdukt = t.food.id in alt) }
        }
        val nachId = treffer.associate { it.food.id to it.food }
        return treffer.mapNotNull { t -> nachId[t.food.id] }
            .map { f -> f.barcode?.let { db.food().ladeBarcode(it) } ?: f }
            .distinctBy { it.id }
    }

    /**
     * Neue Portionen hinzufügen, ohne Doppelte: Gibt es schon eine namenlose "Portion" mit gleichem Gewicht,
     * bekommt sie den besseren Namen des Herstellers (z. B. "Becher").
     */
    private suspend fun ergaenzePortionen(foodId: String, neue: List<ServingEntity>, vorhandenesProdukt: Boolean) {
        val vorhanden = if (vorhandenesProdukt) db.food().portionen(foodId) else emptyList()
        val dazu = neue.filter { n ->
            if (vorhanden.any { it.label == n.label }) return@filter false
            val namenlos = vorhanden.firstOrNull { it.label == "Portion" && kotlin.math.abs(it.gramm - n.gramm) < 0.5 }
            if (namenlos != null) {
                db.food().benennePortion(namenlos.id, n.label)
                false
            } else {
                true
            }
        }
        db.food().fuegeServingsEin(dazu.map { it.copy(id = 0, isDefault = it.isDefault && vorhanden.none { v -> v.isDefault }) })
    }

    private val ergaenzt = mutableSetOf<String>()

    /**
     * Ergänzt ein Produkt beim Öffnen:
     * - Obst, Gemüse, Eier bekommen ein Stück-Gewicht als Richtwert (auch ohne Internet).
     * - Katalogprodukten fehlen oft Portionsname und Vitamine/Mineralstoffe: einmal je App-Start
     *   still bei Open Food Facts nachfragen.
     * true = es kam etwas Neues dazu.
     */
    suspend fun portionenNachladen(food: FoodEntity): Boolean {
        if (!ergaenzt.add(food.id)) return false
        var neu = false
        val vorhanden = db.food().portionen(food.id)
        if (food.unitBase == "g" && food.source != "start" && vorhanden.none { it.label.startsWith("Stück") }) {
            Richtwerte.stueckFuer(food.nameNorm)?.let { r ->
                db.food().fuegeServingsEin(listOf(ServingEntity(foodId = food.id, label = OpenFoodFacts.RICHTWERT_STUECK, gramm = r.gramm)))
                neu = true
            }
        }
        val ean = food.barcode ?: return neu
        if (food.source != "off") return neu
        val vorher = db.food().portionen(food.id).map { it.label to it.gramm }
        val treffer = runCatching { off.produkt(ean) }.getOrNull() ?: return neu
        if (treffer.food.id != food.id) return neu
        db.withTransaction {
            ergaenzePortionen(food.id, treffer.portionen, vorhandenesProdukt = true)
            if (food.mikros == null && treffer.food.mikros != null) {
                db.food().fuegeEin(listOf(food.copy(mikros = treffer.food.mikros)))
            }
        }
        return neu || db.food().portionen(food.id).map { it.label to it.gramm } != vorher ||
            (food.mikros == null && treffer.food.mikros != null)
    }

    /**
     * Lerneffekt: Wurde [suchbegriff] in der Suche nicht gefunden, das Produkt dann aber per Scan,
     * merkt sich das Produkt den Begriff. Die nächste Suche danach findet es.
     */
    suspend fun lerneSuchbegriff(foodId: String, suchbegriff: String) {
        val f = db.food().lade(foodId) ?: return
        val neu = SuchText.normalisiere(suchbegriff).split(' ').filter { it.length >= 2 && !f.nameNorm.contains(it) }
        if (neu.isEmpty()) return
        db.food().fuegeEin(listOf(f.copy(nameNorm = (f.nameNorm + " " + neu.joinToString(" ")).take(400))))
    }

    /** Zusätzliche Suchbegriffe eines Produkts (alles, was nicht schon im Namen steht). */
    fun suchbegriffe(f: FoodEntity): List<String> {
        val imNamen = SuchText.normalisiere(f.name).split(' ').toSet()
        return f.nameNorm.split(' ').filter { it.isNotBlank() && it !in imNamen }.distinct()
    }

    suspend fun entferneSuchbegriff(foodId: String, wort: String) {
        val f = db.food().lade(foodId) ?: return
        val woerter = f.nameNorm.split(' ').filter { it.isNotBlank() }
        val imNamen = SuchText.normalisiere(f.name).split(' ').toSet()
        db.food().fuegeEin(listOf(f.copy(nameNorm = woerter.filter { it != wort || it in imNamen }.joinToString(" "))))
    }

    /** Produkt umbenennen. Es gilt dann als eigenes Produkt und wird von Open Food Facts nicht mehr überschrieben. */
    suspend fun benenneUm(foodId: String, name: String) {
        val f = db.food().lade(foodId) ?: return
        val alt = SuchText.normalisiere(f.name)
        // Gelernte Begriffe und der alte Name bleiben suchbar
        val norm = (SuchText.normalisiere(name) + " " + f.nameNorm.removePrefix(alt).trim() + " " + alt).trim().take(400)
        db.food().fuegeEin(listOf(f.copy(name = name.trim(), nameNorm = norm, source = if (f.source == "off") "user" else f.source)))
    }

    /** Eigenes Produkt anlegen. Fehlertext statt Exception, wenn der Barcode schon vergeben ist. */
    suspend fun legeProduktAn(food: FoodEntity, portion: Pair<String, Double>?): Result<FoodEntity> {
        food.barcode?.let { ean ->
            db.food().ladeBarcode(ean)?.let { return Result.failure(IllegalStateException(it.name)) }
        }
        db.withTransaction {
            db.food().fuegeEin(listOf(food))
            db.food().fuegeServingsEin(
                listOfNotNull(
                    ServingEntity(foodId = food.id, label = "100 ${food.unitBase}", gramm = 100.0, isDefault = portion == null),
                    portion?.let { (label, gramm) -> ServingEntity(foodId = food.id, label = label, gramm = gramm, isDefault = true) },
                ),
            )
        }
        return Result.success(food)
    }

    /** Eigene Portionsgröße zu einem Lebensmittel. */
    suspend fun neuePortion(foodId: String, label: String, gramm: Double) =
        db.food().fuegeServingsEin(listOf(ServingEntity(foodId = foodId, label = label, gramm = gramm)))

    // ---------- Rezepte ----------

    val rezepte: Flow<List<FoodEntity>> = db.rezept().alle()

    /** Zutaten eines Rezepts samt Lebensmittel (fehlende Lebensmittel werden übersprungen). */
    suspend fun rezeptZutaten(rezeptId: String): List<Pair<RezeptZutatEntity, FoodEntity>> {
        val z = db.rezept().zutaten(rezeptId)
        val foods = db.food().ladeAlle(z.map { it.foodId }.distinct()).associateBy { it.id }
        return z.mapNotNull { zutat -> foods[zutat.foodId]?.let { zutat to it } }
    }

    /** Anzahl Portionen und Gesamtgewicht eines gespeicherten Rezepts (für den Editor). */
    suspend fun rezeptKopf(rezeptId: String): Pair<Int, Double?> {
        val s = db.food().portionen(rezeptId)
        val portionen = s.firstOrNull { it.label.startsWith("Portion (1/") }
            ?.label?.substringAfter("1/")?.substringBefore(")")?.toIntOrNull() ?: 1
        return portionen to s.firstOrNull { it.label == "Ganzes Rezept" }?.gramm
    }

    /**
     * Speichert ein Rezept als Lebensmittel mit Werten pro 100 g und einer Portion (Gesamtgewicht / Portionen).
     * Neue Rezepte bekommen die id "rezept:<uuid>".
     */
    suspend fun speichereRezept(
        id: String?, name: String, portionen: Int, gesamtGramm: Double?,
        zutaten: List<Pair<RezeptZutatEntity, FoodEntity>>,
    ): FoodEntity? {
        val werte = RezeptRechner.berechne(
            zutaten.map { (z, f) -> RezeptZutat(f.naehrwerte100, z.gramm) }, portionen, gesamtGramm,
        ) ?: return null
        val rid = id ?: "rezept:${UUID.randomUUID()}"
        val p = werte.pro100
        val food = FoodEntity(
            id = rid, source = "rezept", barcode = null, name = name.trim(), nameNorm = SuchText.normalisiere(name),
            brand = null, unitBase = "g", kcal = p.kcal, fett = p.fett, gesFett = p.gesFett, kh = p.kh,
            zucker = p.zucker, ballaststoffe = p.ballaststoffe, eiweiss = p.eiweiss, salz = p.salz,
            beliebtheit = db.food().lade(rid)?.beliebtheit ?: 0,
        )
        db.withTransaction {
            // Nur die drei automatisch erzeugten Portionen ersetzen, eigene Portionen bleiben
            val automatisch = db.food().portionen(rid)
                .filter { it.label == "Ganzes Rezept" || it.label == "100 g" || it.label.startsWith("Portion (1/") }
            db.food().loescheServingIds(automatisch.map { it.id })
            db.food().fuegeEin(listOf(food))
            db.food().fuegeServingsEin(
                listOf(
                    ServingEntity(foodId = rid, label = "Portion (1/$portionen)", gramm = werte.portionGramm, isDefault = true),
                    ServingEntity(foodId = rid, label = "Ganzes Rezept", gramm = werte.gesamtGramm),
                    ServingEntity(foodId = rid, label = "100 g", gramm = 100.0),
                ),
            )
            db.rezept().loescheZutaten(rid)
            db.rezept().fuegeZutatenEin(zutaten.mapIndexed { i, (z, _) -> z.copy(id = 0, rezeptId = rid, position = i) })
        }
        return food
    }

    /** Löscht das Rezept; Tagebuch-Einträge bleiben erhalten, weil sie ihre Werte selbst speichern. */
    suspend fun loescheRezept(id: String) = db.withTransaction {
        db.rezept().loescheZutaten(id)
        db.rezept().loescheFavorit(id)
        db.food().loescheServings(listOf(id))
        db.rezept().loescheFood(id)
    }

    // ---------- Gewicht ----------
    val gewichtVerlauf: Flow<List<BodyEntryEntity>> = quellen.wahl.flatMapLatest { db.body().gewichtVerlauf(it.gewicht.name) }

    /**
     * Gewicht für einen Tag speichern (pro Tag ein Wert) und das Tagesziel
     * mit dem neuen Gewicht neu berechnen, damit es zum aktuellen Körper passt.
     */
    suspend fun speichereGewicht(kg: Double, tag: LocalDate = LocalDate.now(zone)) {
        db.body().speichere(
            BodyEntryEntity(
                id = "gewicht:$tag", day = tag.toString(), measuredAt = System.currentTimeMillis(),
                metric = "gewicht_kg", value = kg,
            ),
        )
        zielNachGewicht(kg)
    }

    /** Tagesziel an ein neues Gewicht anpassen (nicht, wenn es selbst festgelegt ist). */
    suspend fun zielNachGewicht(kg: Double? = null) {
        val gewicht = kg ?: ladeLetztesGewicht() ?: return
        val p = db.profile().lade() ?: return
        if (p.zielManuell) return
        val neu = automatischesZiel(p, gewicht) ?: return
        if (neu != p.tageszielKcal) db.profile().speichere(p.copy(tageszielKcal = neu, updatedAt = System.currentTimeMillis()))
    }

    private fun automatischesZiel(p: ProfileEntity, kg: Double): Int? = runCatching {
            Bedarfsrechner.berechne(
                Koerperdaten(Geschlecht.valueOf(p.geschlecht), LocalDate.parse(p.geburtsdatum), p.groesseCm, kg, p.pal),
                Zielvorgabe(Ziel.valueOf(p.ziel), p.zielgewichtKg, p.tempoKgProWoche, p.aerztlichBegleitet),
                LocalDate.now(zone),
            ).tagesziel
        }.getOrNull()

    /**
     * Tagesziel und Makro-Anteile selbst festlegen. kcal = null heißt: wieder automatisch aus Profil und
     * letztem Gewicht berechnen.
     */
    suspend fun setzeZielUndMakros(kcal: Int?, fett: Double, kh: Double, eiweiss: Double) {
        val p = db.profile().lade() ?: return
        val ziel = kcal ?: automatischesZiel(p, db.body().ladeLetztesGewicht(gewichtModus)?.value ?: p.startGewichtKg) ?: p.tageszielKcal
        db.profile().speichere(
            p.copy(
                tageszielKcal = ziel, zielManuell = kcal != null,
                fettProzent = fett, khProzent = kh, eiweissProzent = eiweiss,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun loescheGewicht(e: BodyEntryEntity) = db.body().loesche(e.id)
    suspend fun gewichtWiederherstellen(e: BodyEntryEntity) = db.body().speichere(e)

    // ---------- Kalorien direkt + Aktivitäten ----------

    /** Eintrag ohne Lebensmittel, nur mit kcal (und optional Makros). */
    suspend fun kalorienDirekt(
        bezeichnung: String, kcal: Double, fett: Double?, kh: Double?, eiweiss: Double?,
        zeitpunkt: LocalDateTime, slotId: Int,
    ) {
        val jetzt = System.currentTimeMillis()
        db.diary().speichere(
            DiaryEntryEntity(
                id = UUID.randomUUID().toString(), day = zeitpunkt.toLocalDate().toString(),
                eatenAt = zeitpunkt.atZone(zone).toInstant().toEpochMilli(), slotId = slotId, kind = "kcal",
                foodRef = null, label = bezeichnung, brand = null, servingLabel = null, quantity = 1.0, gramm = null,
                kcal = kcal, fett = fett, gesFett = null, kh = kh, zucker = null, ballaststoffe = null, eiweiss = eiweiss, salz = null,
                createdAt = jetzt, updatedAt = jetzt,
            ),
        )
    }

    fun aktivitaeten(tag: LocalDate): Flow<List<ActivityEntryEntity>> =
        quellen.wahl.flatMapLatest { db.activity().am(tag.toString(), it.verbrannt.name) }

    /**
     * Übernimmt Werte aus Health Connect ab [von]: alte übernommene Werte in dem Zeitraum werden ersetzt.
     * Gewicht: der letzte Wert je Tag. Verbrannt: aktive Kalorien je Tag.
     */
    suspend fun uebernehmeHealthConnect(von: LocalDate, gewichte: Map<LocalDate, Pair<Long, Double>>, aktivKcal: Map<LocalDate, Double>) =
        db.withTransaction {
            db.body().loescheHealthConnectAb(von.toString())
            db.activity().loescheHealthConnectAb(von.toString())
            gewichte.forEach { (tag, wert) ->
                db.body().speichere(
                    BodyEntryEntity(
                        id = "hc:gewicht:$tag", day = tag.toString(), measuredAt = wert.first,
                        metric = "gewicht_kg", value = wert.second, source = "health_connect",
                    ),
                )
            }
            aktivKcal.filterValues { it >= 1 }.forEach { (tag, kcal) ->
                db.activity().speichere(
                    ActivityEntryEntity(
                        id = "hc:aktiv:$tag", day = tag.toString(),
                        startedAt = tag.atStartOfDay(zone).toInstant().toEpochMilli(),
                        label = "Aktive Kalorien (Samsung Health)", minutes = null, kcal = kcal, source = "health_connect",
                    ),
                )
            }
        }

    suspend fun speichereAktivitaet(bezeichnung: String, kcal: Double, minuten: Double?, tag: LocalDate) {
        val zeit = if (tag == LocalDate.now(zone)) LocalDateTime.now(zone) else tag.atTime(12, 0)
        db.activity().speichere(
            ActivityEntryEntity(
                id = UUID.randomUUID().toString(), day = tag.toString(),
                startedAt = zeit.atZone(zone).toInstant().toEpochMilli(),
                label = bezeichnung, minutes = minuten, kcal = kcal,
            ),
        )
    }

    suspend fun loescheAktivitaet(e: ActivityEntryEntity) = db.activity().loesche(e.id)
    suspend fun wiederherstellenAktivitaet(e: ActivityEntryEntity) = db.activity().speichere(e)

    // ---------- Favoriten ----------
    val favoritIds: Flow<Set<String>> = db.favorite().ids().map { it.toSet() }
    suspend fun favoriten(): List<FoodEntity> = db.favorite().lebensmittel()

    suspend fun setzeFavorit(foodId: String, favorit: Boolean) {
        if (favorit) db.favorite().speichere(FavoriteEntity(foodId, System.currentTimeMillis()))
        else db.favorite().loesche(foodId)
    }

    // ---------- Wasser ----------
    fun wasser(tag: LocalDate): Flow<Int> = db.water().summeAm(tag.toString())

    suspend fun trinke(ml: Int, tag: LocalDate) = db.water().speichere(
        WaterEntryEntity(UUID.randomUUID().toString(), tag.toString(), ml, System.currentTimeMillis()),
    )

    /** Letzten Wasser-Eintrag des Tages zurücknehmen. */
    suspend fun wasserZurueck(tag: LocalDate) {
        db.water().letzterAm(tag.toString())?.let { db.water().loesche(it.id) }
    }

    // ---------- Mahlzeit kopieren ----------

    /** Kopiert alle Einträge einer Mahlzeit auf einen anderen Tag (gleiche Uhrzeit). Liefert die Kopien (für Rückgängig). */
    suspend fun kopiereMahlzeit(eintraege: List<DiaryEntryEntity>, nach: LocalDate, slotId: Int): List<DiaryEntryEntity> {
        val jetzt = System.currentTimeMillis()
        return eintraege.map { e ->
            val uhrzeit = Instant.ofEpochMilli(e.eatenAt).atZone(zone).toLocalTime()
            e.copy(
                id = UUID.randomUUID().toString(), day = nach.toString(),
                eatenAt = nach.atTime(uhrzeit).atZone(zone).toInstant().toEpochMilli(),
                slotId = if (slotId == 0) e.slotId else slotId, createdAt = jetzt, updatedAt = jetzt,
            ).also { db.diary().speichere(it) }
        }
    }

    /** Verschiebt Einträge auf einen anderen Tag und/oder in eine andere Mahlzeit (0 = Mahlzeit bleibt). Uhrzeit bleibt. */
    suspend fun verschiebe(eintraege: List<DiaryEntryEntity>, nach: LocalDate, slotId: Int) {
        val jetzt = System.currentTimeMillis()
        eintraege.forEach { e ->
            val uhrzeit = Instant.ofEpochMilli(e.eatenAt).atZone(zone).toLocalTime()
            db.diary().speichere(
                e.copy(
                    day = nach.toString(), eatenAt = nach.atTime(uhrzeit).atZone(zone).toInstant().toEpochMilli(),
                    slotId = if (slotId == 0) e.slotId else slotId, updatedAt = jetzt,
                ),
            )
        }
    }

    // ---------- Foto-Mahlzeit ----------

    /** Passendstes Lebensmittel für einen erkannten Namen (z. B. "Reis gekocht"), Rezepte ausgenommen. */
    suspend fun zuordnen(name: String): FoodEntity? {
        val n = name.trim()
        if (n.length < 2) return null
        suche(n).firstOrNull { it.source != "rezept" }?.let { return it }
        // Ohne Treffer: nur das erste Wort (z. B. "Spiegelei mit Speck" → "Spiegelei")
        val erstes = n.split(' ', ',').firstOrNull { it.length >= 3 } ?: return null
        return if (erstes != n) suche(erstes).firstOrNull { it.source != "rezept" } else null
    }

    /** Mehrere Lebensmittel einer Foto-Mahlzeit in Gramm eintragen, alle mit demselben Foto. */
    suspend fun eintragenMehrere(
        posten: List<Pair<FoodEntity, Double>>,
        zeitpunkt: LocalDateTime,
        slotId: Int,
        foto: String?,
    ): List<DiaryEntryEntity> = posten.map { (food, gramm) ->
        eintragen(food, food.unitBase, 1.0, gramm, zeitpunkt, slotId, foto)
    }

    /** Alle Fotos, die noch an einem Eintrag hängen (zum Aufräumen alter Dateien). */
    suspend fun benutzteFotos(): Set<String> = db.diary().fotos().toSet()

    // ---------- Erinnerungen ----------

    /** Gibt es an diesem Tag schon einen Eintrag in der Mahlzeit? */
    suspend fun hatEintrag(tag: LocalDate, slotId: Int): Boolean =
        db.diary().eintraegeAm(tag.toString()).first().any { it.slotId == slotId }

    /** Wurde heute schon ein Gewicht eingetragen (oder aus Health Connect übernommen)? */
    suspend fun heuteGewogen(): Boolean =
        db.body().ladeLetztesGewicht(gewichtModus)?.day == LocalDate.now(zone).toString()
}
