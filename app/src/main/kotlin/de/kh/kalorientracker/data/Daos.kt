package de.kh.kalorientracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun beobachte(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun lade(): ProfileEntity?

    @Upsert
    suspend fun speichere(p: ProfileEntity)
}

@Dao
interface FoodDao {
    @Query("SELECT COUNT(*) FROM food")
    suspend fun anzahl(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun fuegeEin(foods: List<FoodEntity>)

    @Insert
    suspend fun fuegeServingsEin(s: List<ServingEntity>)

    @Query("DELETE FROM food_serving WHERE foodId IN (:ids)")
    suspend fun loescheServings(ids: List<String>)

    /** Einfache Suche über den normalisierten Namen; reicht für die Startdaten. Für den großen Katalog folgt FTS. */
    @Query(
        """
        SELECT * FROM food
        WHERE nameNorm LIKE :muster1 OR nameNorm LIKE :muster2
        ORDER BY beliebtheit DESC, LENGTH(name)
        LIMIT 60
        """,
    )
    suspend fun suche(muster1: String, muster2: String): List<FoodEntity>

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun lade(id: String): FoodEntity?

    @Query("SELECT * FROM food_serving WHERE foodId = :foodId ORDER BY isDefault DESC, gramm")
    suspend fun portionen(foodId: String): List<ServingEntity>

    @Query("SELECT * FROM food WHERE barcode = :ean LIMIT 1")
    suspend fun ladeBarcode(ean: String): FoodEntity?

    @Query("SELECT * FROM food WHERE id IN (:ids)")
    suspend fun ladeAlle(ids: List<String>): List<FoodEntity>
}

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entry WHERE day = :day ORDER BY slotId, eatenAt")
    fun eintraegeAm(day: String): Flow<List<DiaryEntryEntity>>

    @Query("SELECT day, SUM(kcal) AS kcal FROM diary_entry WHERE day BETWEEN :von AND :bis GROUP BY day")
    fun kcalProTag(von: String, bis: String): Flow<List<TagesSumme>>

    /** Zuletzt gegessene Lebensmittel (je Lebensmittel der jüngste Eintrag). */
    @Query(
        """
        SELECT foodRef, MAX(eatenAt) AS zuletzt FROM diary_entry
        WHERE foodRef IS NOT NULL GROUP BY foodRef ORDER BY zuletzt DESC LIMIT :limit
        """,
    )
    suspend fun zuletztGegessen(limit: Int): List<ZuletztGegessen>

    @Query("SELECT servingLabel FROM diary_entry WHERE foodRef = :foodRef ORDER BY eatenAt DESC LIMIT 1")
    suspend fun letztePortion(foodRef: String): String?

    @Query("SELECT * FROM diary_entry WHERE id = :id")
    suspend fun lade(id: String): DiaryEntryEntity?

    @Upsert
    suspend fun speichere(e: DiaryEntryEntity)

    @Query("DELETE FROM diary_entry WHERE id = :id")
    suspend fun loesche(id: String)
}

data class TagesSumme(val day: String, val kcal: Double)
data class ZuletztGegessen(val foodRef: String, val zuletzt: Long)

@Dao
interface BodyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun speichere(e: BodyEntryEntity)

    @Query("SELECT * FROM body_entry WHERE metric = 'gewicht_kg' ORDER BY measuredAt DESC LIMIT 1")
    fun letztesGewicht(): Flow<BodyEntryEntity?>

    @Query("SELECT * FROM body_entry WHERE metric = 'gewicht_kg' ORDER BY measuredAt DESC LIMIT 1")
    suspend fun ladeLetztesGewicht(): BodyEntryEntity?

    @Query("SELECT * FROM body_entry WHERE metric = 'gewicht_kg' ORDER BY day")
    fun gewichtVerlauf(): Flow<List<BodyEntryEntity>>

    @Query("DELETE FROM body_entry WHERE id = :id")
    suspend fun loesche(id: String)
}

@Dao
interface ActivityDao {
    @Query("SELECT COALESCE(SUM(kcal), 0) FROM activity_entry WHERE day = :day")
    fun verbranntAm(day: String): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun speichere(e: ActivityEntryEntity)

    @Query("SELECT * FROM activity_entry WHERE day = :day ORDER BY startedAt")
    fun am(day: String): Flow<List<ActivityEntryEntity>>

    @Query("DELETE FROM activity_entry WHERE id = :id")
    suspend fun loesche(id: String)
}
