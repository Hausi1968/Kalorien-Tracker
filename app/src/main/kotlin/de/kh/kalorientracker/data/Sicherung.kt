package de.kh.kalorientracker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Alles, was nur der Nutzer selbst eingegeben hat. Der mitgelieferte Katalog gehört nicht dazu. */
@Serializable
data class SicherungsDaten(
    val format: Int = 1,
    val erstellt: Long,
    val profil: ProfileEntity? = null,
    val eintraege: List<DiaryEntryEntity> = emptyList(),
    val koerper: List<BodyEntryEntity> = emptyList(),
    val aktivitaeten: List<ActivityEntryEntity> = emptyList(),
    val favoriten: List<FavoriteEntity> = emptyList(),
    val wasser: List<WaterEntryEntity> = emptyList(),
    val zutaten: List<RezeptZutatEntity> = emptyList(),
    val lebensmittel: List<FoodEntity> = emptyList(),
    val portionen: List<ServingEntity> = emptyList(),
)

@Dao
interface SicherungDao {
    @Query("SELECT * FROM profile") suspend fun profile(): List<ProfileEntity>
    @Query("SELECT * FROM diary_entry") suspend fun eintraege(): List<DiaryEntryEntity>
    @Query("SELECT * FROM body_entry WHERE source != 'health_connect'") suspend fun koerper(): List<BodyEntryEntity>
    @Query("SELECT * FROM activity_entry WHERE source != 'health_connect'") suspend fun aktivitaeten(): List<ActivityEntryEntity>
    @Query("SELECT * FROM favorite") suspend fun favoriten(): List<FavoriteEntity>
    @Query("SELECT * FROM water_entry") suspend fun wasser(): List<WaterEntryEntity>
    @Query("SELECT * FROM recipe_ingredient") suspend fun zutaten(): List<RezeptZutatEntity>

    /** Eigene Produkte, Rezepte, gescannte oder online geladene Produkte und alles, worauf Tagebuch, Favoriten oder Rezepte zeigen (außer Grundnahrungsmitteln). */
    @Query(
        """
        SELECT * FROM food WHERE source IN ('user', 'rezept') OR geladenAm IS NOT NULL
           OR (source != 'start' AND (id IN (SELECT foodRef FROM diary_entry WHERE foodRef IS NOT NULL)
               OR id IN (SELECT foodId FROM favorite) OR id IN (SELECT foodId FROM recipe_ingredient)))
        """,
    )
    suspend fun lebensmittel(): List<FoodEntity>

    @Query("SELECT * FROM food_serving WHERE foodId IN (:ids)") suspend fun portionen(ids: List<String>): List<ServingEntity>

    @Upsert suspend fun profil(p: ProfileEntity)
    @Upsert suspend fun eintraege(e: List<DiaryEntryEntity>)
    @Upsert suspend fun koerper(e: List<BodyEntryEntity>)
    @Upsert suspend fun aktivitaeten(e: List<ActivityEntryEntity>)
    @Upsert suspend fun favoriten(e: List<FavoriteEntity>)
    @Upsert suspend fun wasser(e: List<WaterEntryEntity>)
    @Upsert suspend fun lebensmittel(e: List<FoodEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun lebensmittelWennNeu(e: List<FoodEntity>): List<Long>
    @Insert suspend fun portionenEin(e: List<ServingEntity>)
    @Insert suspend fun zutatenEin(e: List<RezeptZutatEntity>)
    @Query("DELETE FROM food_serving WHERE foodId IN (:ids)") suspend fun loeschePortionen(ids: List<String>)
    @Query("DELETE FROM recipe_ingredient WHERE rezeptId IN (:ids)") suspend fun loescheZutaten(ids: List<String>)
}

/**
 * Sicherung der eigenen Daten als kleine JSON-Datei.
 *
 * Die Datenbank selbst ist mit dem Katalog zu groß für Androids automatische Sicherung (Grenze 25 MB),
 * deshalb wird sie davon ausgenommen (res/xml/sicherung_regeln.xml). Stattdessen liegt diese Datei in
 * files/sicherung/ und wird mitgesichert. Nach einem Handywechsel liest die App sie beim ersten Start ein.
 * Dieselbe Datei kann der Nutzer auch selbst exportieren und wieder einlesen.
 */
class Sicherung(private val context: Context, private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val datei get() = File(File(context.filesDir, "sicherung").apply { mkdirs() }, "sicherung.json")

    suspend fun erstelle(): SicherungsDaten = withContext(Dispatchers.IO) {
        val s = db.sicherung()
        val lebensmittel = s.lebensmittel()
        SicherungsDaten(
            erstellt = System.currentTimeMillis(),
            profil = s.profile().firstOrNull(),
            eintraege = s.eintraege(),
            koerper = s.koerper(),
            aktivitaeten = s.aktivitaeten(),
            favoriten = s.favoriten(),
            wasser = s.wasser(),
            zutaten = s.zutaten(),
            lebensmittel = lebensmittel,
            portionen = lebensmittel.map { it.id }.chunked(500).flatMap { s.portionen(it) },
        )
    }

    /** Schreibt die Sicherungsdatei für Androids automatische Sicherung (nur wenn es ein Profil gibt). */
    suspend fun schreibe() = withContext(Dispatchers.IO) {
        val d = erstelle()
        if (d.profil == null) return@withContext
        val tmp = File(datei.parentFile, "sicherung.tmp")
        tmp.writeText(json.encodeToString(SicherungsDaten.serializer(), d))
        tmp.renameTo(datei)
    }

    suspend fun exportiere(ziel: OutputStream) = withContext(Dispatchers.IO) {
        ziel.bufferedWriter().use { it.write(json.encodeToString(SicherungsDaten.serializer(), erstelle())) }
    }

    /** Liest eine exportierte Datei ein; vorhandene Einträge mit gleicher Kennung werden überschrieben. */
    suspend fun importiere(quelle: InputStream): SicherungsDaten = withContext(Dispatchers.IO) {
        val d = json.decodeFromString(SicherungsDaten.serializer(), quelle.bufferedReader().use { it.readText() })
        spieleEin(d)
        d
    }

    /** Nach Neuinstallation: gibt es noch kein Profil, aber eine gesicherte Datei, wird sie eingelesen. */
    suspend fun wiederherstellenFallsNoetig(): Boolean = withContext(Dispatchers.IO) {
        if (db.profile().lade() != null || !datei.exists()) return@withContext false
        val d = runCatching { json.decodeFromString(SicherungsDaten.serializer(), datei.readText()) }.getOrNull()
            ?: return@withContext false
        if (d.profil == null) return@withContext false
        spieleEin(d)
        true
    }

    private suspend fun spieleEin(d: SicherungsDaten) {
        val s = db.sicherung()
        db.withTransaction {
            // Eigene Produkte und Rezepte vollständig ersetzen, alles andere nur ergänzen
            val eigene = d.lebensmittel.filter { it.source == "user" || it.source == "rezept" }
            val andere = d.lebensmittel - eigene.toSet()
            s.lebensmittel(eigene)
            s.loeschePortionen(eigene.map { it.id })
            val neu = s.lebensmittelWennNeu(andere).withIndex().filter { it.value != -1L }.map { andere[it.index].id }.toSet()
            val mitPortionen = eigene.map { it.id }.toSet() + neu
            s.portionenEin(d.portionen.filter { it.foodId in mitPortionen }.map { it.copy(id = 0) })
            val rezepte = d.zutaten.map { it.rezeptId }.distinct()
            s.loescheZutaten(rezepte)
            s.zutatenEin(d.zutaten.map { it.copy(id = 0) })
            d.profil?.let { s.profil(it) }
            s.eintraege(d.eintraege)
            s.koerper(d.koerper)
            s.aktivitaeten(d.aktivitaeten)
            s.favoriten(d.favoriten)
            s.wasser(d.wasser)
        }
    }
}
