package de.kh.kalorientracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.kh.kalorientracker.core.Naehrwerte
import kotlinx.serialization.Serializable

/**
 * Durchstich: eine Datenbank für Katalog und Nutzerdaten.
 * Laut Architektur kommt später die Trennung in food.db (Katalog) und user.db.
 */

@Entity(tableName = "profile")
@Serializable
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val geschlecht: String,          // "MANN" | "FRAU"
    val geburtsdatum: String,        // YYYY-MM-DD
    val groesseCm: Double,
    val pal: Double,
    val ziel: String,                // "ABNEHMEN" | "HALTEN" | "ZUNEHMEN"
    val zielgewichtKg: Double?,
    val tempoKgProWoche: Double,
    val startGewichtKg: Double,
    val startDatum: String,
    val tageszielKcal: Int,
    val fettProzent: Double,
    val khProzent: Double,
    val eiweissProzent: Double,
    val updatedAt: Long,
    /** true = Tagesziel selbst festgelegt; wird dann bei neuem Gewicht nicht neu berechnet. */
    @ColumnInfo(defaultValue = "0") val zielManuell: Boolean = false,
    /** Abnehmen unter ärztlicher Begleitung: Ziel darf unter dem Grundumsatz liegen (seit DB-Version 7). */
    @ColumnInfo(defaultValue = "0") val aerztlichBegleitet: Boolean = false,
)

@Entity(
    tableName = "food",
    indices = [Index("nameNorm"), Index(value = ["barcode"], unique = true)],
)
@Serializable
data class FoodEntity(
    @PrimaryKey val id: String,      // "start:apfel", später "off:<ean>", "usda:<id>", "user:<uuid>"
    val source: String,              // "start" | "off" | "usda" | "user"
    val barcode: String?,
    val name: String,
    val nameNorm: String,
    val brand: String?,
    val unitBase: String,            // "g" | "ml"
    val kcal: Double,
    val fett: Double?,
    val gesFett: Double?,
    val kh: Double?,
    val zucker: Double?,
    val ballaststoffe: Double?,
    val eiweiss: Double?,
    val salz: Double?,
    val beliebtheit: Int = 0,
    /** Vitamine, Mineralstoffe, Cholesterin je 100 g/ml (seit DB-Version 5), siehe core.Mikros. */
    val mikros: String? = null,
)

/** Nährwerte pro 100 g/ml (Erweiterung statt Property, damit Room sie nicht als Spalte sieht). */
val FoodEntity.naehrwerte100: Naehrwerte
    get() = Naehrwerte(kcal, fett, gesFett, kh, zucker, ballaststoffe, eiweiss, salz)

@Entity(
    tableName = "food_serving",
    foreignKeys = [ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("foodId")],
)
@Serializable
data class ServingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: String,
    val label: String,               // "Stück", "Becher", "Scheibe"
    val gramm: Double,
    val isDefault: Boolean = false,
)

@Entity(
    tableName = "diary_entry",
    indices = [Index(value = ["day", "slotId", "eatenAt"]), Index(value = ["foodRef", "eatenAt"])],
)
@Serializable
data class DiaryEntryEntity(
    @PrimaryKey val id: String,
    val day: String,                 // lokales Datum YYYY-MM-DD
    val eatenAt: Long,               // UTC ms
    val slotId: Int,
    val kind: String,                // "food" | "kcal"
    val foodRef: String?,
    val label: String,
    val brand: String?,
    val servingLabel: String?,
    val quantity: Double,
    val gramm: Double?,
    val kcal: Double,
    val fett: Double?,
    val gesFett: Double?,
    val kh: Double?,
    val zucker: Double?,
    val ballaststoffe: Double?,
    val eiweiss: Double?,
    val salz: Double?,
    val createdAt: Long,
    val updatedAt: Long,
    /** Vitamine, Mineralstoffe, Cholesterin für diesen Eintrag (absolut, seit DB-Version 5). */
    val mikros: String? = null,
    /** Dateiname des Mahlzeit-Fotos in files/fotos (seit DB-Version 6); mehrere Einträge können dasselbe Foto haben. */
    val foto: String? = null,
)

val DiaryEntryEntity.naehrwerte: Naehrwerte
    get() = Naehrwerte(kcal, fett, gesFett, kh, zucker, ballaststoffe, eiweiss, salz)

@Entity(tableName = "body_entry", indices = [Index(value = ["metric", "day"])])
@Serializable
data class BodyEntryEntity(
    @PrimaryKey val id: String,
    val day: String,
    val measuredAt: Long,
    val metric: String,              // "gewicht_kg" | "koerperfett_pct" | ...
    val value: Double,
    val source: String = "manuell",
)

@Entity(tableName = "activity_entry", indices = [Index("day")])
@Serializable
data class ActivityEntryEntity(
    @PrimaryKey val id: String,
    val day: String,
    val startedAt: Long,
    val label: String,
    val minutes: Double?,
    val kcal: Double,
    val source: String = "manuell",
)

/** Favorit: verweist auf ein Lebensmittel (seit Datenbank-Version 2). */
@Entity(tableName = "favorite")
@Serializable
data class FavoriteEntity(
    @PrimaryKey val foodId: String,
    val createdAt: Long,
)

/** Getrunkene Menge Wasser in ml (seit Datenbank-Version 2). */
@Entity(tableName = "water_entry", indices = [Index("day")])
@Serializable
data class WaterEntryEntity(
    @PrimaryKey val id: String,
    val day: String,
    val ml: Int,
    val createdAt: Long,
)

/**
 * Zutat eines eigenen Rezepts. Das Rezept selbst liegt als Lebensmittel (source "rezept") in food,
 * damit es in Suche, Favoriten und Tagebuch ohne Sonderweg funktioniert.
 */
@Entity(tableName = "recipe_ingredient", indices = [Index("rezeptId")])
@Serializable
data class RezeptZutatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rezeptId: String,
    val position: Int,
    val foodId: String,
    val label: String,
    val servingLabel: String?,
    val quantity: Double,
    val gramm: Double,
)
