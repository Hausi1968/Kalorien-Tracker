package de.kh.kalorientracker.data

import de.kh.kalorientracker.core.Bedarfsrechner
import de.kh.kalorientracker.core.Geschlecht
import de.kh.kalorientracker.core.Koerperdaten
import de.kh.kalorientracker.core.MakroAnteile
import de.kh.kalorientracker.core.MakroGramm
import de.kh.kalorientracker.core.Naehrwerte
import de.kh.kalorientracker.core.Ranking
import de.kh.kalorientracker.core.SuchText
import de.kh.kalorientracker.core.Tagesbilanz
import de.kh.kalorientracker.core.Ziel
import de.kh.kalorientracker.core.Zielvorgabe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import androidx.room.withTransaction
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

class KalorienRepository(private val db: AppDatabase, private val off: OpenFoodFacts = OpenFoodFacts()) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    // ---------- Profil ----------
    val profil: Flow<ProfileEntity?> = db.profile().beobachte()
    val letztesGewicht: Flow<BodyEntryEntity?> = db.body().letztesGewicht()

    suspend fun ladeProfil(): ProfileEntity? = db.profile().lade()
    suspend fun ladeLetztesGewicht(): Double? = db.body().ladeLetztesGewicht()?.value

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
        db.activity().verbranntAm(t.toString()),
    ) { p, e, v -> TagesDaten(t, p, e, v) }

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
    ): DiaryEntryEntity {
        val jetzt = System.currentTimeMillis()
        val e = baueEintrag(UUID.randomUUID().toString(), food, portionLabel, grammJePortion, menge, zeitpunkt, slotId, jetzt)
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
        val neu = baueEintrag(alt.id, food, portionLabel, grammJePortion, menge, zeitpunkt, slotId, alt.createdAt)
        db.diary().speichere(neu)
    }

    suspend fun eintrag(id: String): DiaryEntryEntity? = db.diary().lade(id)

    private fun baueEintrag(
        id: String, food: FoodEntity, portionLabel: String, grammJePortion: Double, menge: Double,
        zeitpunkt: LocalDateTime, slotId: Int, erstellt: Long,
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
        val roh = db.food().suche("$erstes%", "% $erstes%")
            .filter { f -> woerter.all { w -> f.nameNorm.contains(w) } }
        val zuletzt = db.diary().zuletztGegessen(200).associate { it.foodRef to it.zuletzt }
        val jetzt = Instant.now()
        val kandidaten = roh.map { f ->
            Ranking.Kandidat(
                id = f.id,
                name = f.name,
                istGrundnahrungsmittel = f.brand == null,
                beliebtheit = f.beliebtheit,
                zuletztGegessenVorTagen = zuletzt[f.id]?.let { Duration.between(Instant.ofEpochMilli(it), jetzt).toDays().toInt() },
                istFavorit = false,
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

    /** Speichert OFF-Treffer; Produkte, deren Barcode schon als eigenes Produkt existiert, bleiben unangetastet. */
    private suspend fun speichere(treffer: List<OffTreffer>): List<FoodEntity> {
        val neu = treffer.filter { t ->
            val vorhanden = t.food.barcode?.let { db.food().ladeBarcode(it) }
            vorhanden == null || vorhanden.id == t.food.id
        }
        db.withTransaction {
            db.food().loescheServings(neu.map { it.food.id })
            db.food().fuegeEin(neu.map { it.food })
            db.food().fuegeServingsEin(neu.flatMap { it.portionen })
        }
        val nachId = treffer.associate { it.food.id to it.food }
        return treffer.mapNotNull { t -> nachId[t.food.id] }
            .map { f -> f.barcode?.let { db.food().ladeBarcode(it) } ?: f }
            .distinctBy { it.id }
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

    // ---------- Gewicht ----------
    val gewichtVerlauf: Flow<List<BodyEntryEntity>> = db.body().gewichtVerlauf()

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
        val p = db.profile().lade() ?: return
        val neu = runCatching {
            Bedarfsrechner.berechne(
                Koerperdaten(Geschlecht.valueOf(p.geschlecht), LocalDate.parse(p.geburtsdatum), p.groesseCm, kg, p.pal),
                Zielvorgabe(Ziel.valueOf(p.ziel), p.zielgewichtKg, p.tempoKgProWoche),
                LocalDate.now(zone),
            ).tagesziel
        }.getOrNull() ?: return
        if (neu != p.tageszielKcal) db.profile().speichere(p.copy(tageszielKcal = neu, updatedAt = System.currentTimeMillis()))
    }

    suspend fun loescheGewicht(e: BodyEntryEntity) = db.body().loesche(e.id)

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

    fun aktivitaeten(tag: LocalDate): Flow<List<ActivityEntryEntity>> = db.activity().am(tag.toString())

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
}
