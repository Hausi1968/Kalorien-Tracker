package de.kh.kalorientracker.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Format von assets/startdaten.json (Grundnahrungsmittel mit gerundeten Näherungswerten). */
@Serializable
data class StartdatenDatei(val version: Int, val hinweis: String = "", val lebensmittel: List<StartLebensmittel>)

@Serializable
data class StartLebensmittel(
    val id: String,
    val name: String,
    val nameNorm: String,
    val einheit: String,
    val kcal: Double,
    val fett: Double? = null,
    val gesFett: Double? = null,
    val kh: Double? = null,
    val zucker: Double? = null,
    val ballaststoffe: Double? = null,
    val eiweiss: Double? = null,
    val salz: Double? = null,
    val beliebtheit: Int = 0,
    val portionen: List<StartPortion> = emptyList(),
)

@Serializable
data class StartPortion(val label: String, val gramm: Double, val standard: Boolean = false)

/** Lädt die Startdaten beim ersten Start in die Datenbank. Später ersetzt der Katalog (USDA + Open Food Facts) sie. */
class StartdatenImport(private val context: Context, private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fallsLeer() = withContext(Dispatchers.IO) {
        if (db.food().anzahl() > 0) return@withContext
        val text = context.assets.open("startdaten.json").bufferedReader().use { it.readText() }
        val datei = json.decodeFromString(StartdatenDatei.serializer(), text)
        val foods = datei.lebensmittel.map { l ->
            FoodEntity(
                id = l.id, source = "start", barcode = null, name = l.name, nameNorm = l.nameNorm, brand = null,
                unitBase = l.einheit, kcal = l.kcal, fett = l.fett, gesFett = l.gesFett, kh = l.kh, zucker = l.zucker,
                ballaststoffe = l.ballaststoffe, eiweiss = l.eiweiss, salz = l.salz, beliebtheit = l.beliebtheit,
            )
        }
        val servings = datei.lebensmittel.flatMap { l ->
            l.portionen.map { p -> ServingEntity(foodId = l.id, label = p.label, gramm = p.gramm, isDefault = p.standard) }
        }
        db.withTransaction {
            db.food().fuegeEin(foods)
            db.food().fuegeServingsEin(servings)
        }
    }
}
