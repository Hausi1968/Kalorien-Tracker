package de.kh.kalorientracker.data

import android.content.Context
import androidx.room.withTransaction
import de.kh.kalorientracker.core.SuchText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.GZIPInputStream

/**
 * Mitgelieferter Katalog: beliebte Produkte aus Deutschland, Österreich und der Schweiz von Open Food Facts
 * (ODbL). Datei assets/katalog.tsv.gzip (gzip), eine Zeile je Produkt, Spalten siehe [Spalte]. Wird einmal je
 * Katalog-Version eingelesen; was schon in der Datenbank steht (online geladen, eigene Produkte), bleibt.
 */
class KatalogImport(private val context: Context, private val db: AppDatabase) {
    private val prefs = context.getSharedPreferences("katalog", Context.MODE_PRIVATE)

    suspend fun fallsNoetig() = withContext(Dispatchers.IO) {
        if (prefs.getInt("version", 0) >= VERSION) return@withContext
        val datei = runCatching { context.assets.open(DATEI) }.getOrNull() ?: return@withContext
        GZIPInputStream(datei).bufferedReader().useLines { zeilen ->
            zeilen.drop(1).mapNotNull(::zeile).chunked(2000).forEach { stapel ->
                db.withTransaction {
                    val ids = db.food().fuegeEinWennNeu(stapel.map { it.first })
                    val neu = stapel.filterIndexed { i, _ -> ids[i] != -1L }
                    db.food().fuegeServingsEin(neu.flatMap { it.second })
                }
            }
        }
        prefs.edit().putInt("version", VERSION).apply()
    }

    private fun zeile(z: String): Pair<FoodEntity, List<ServingEntity>>? {
        val f = z.split('\t')
        if (f.size < 15) return null
        fun d(i: Int) = f[i].takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        val ean = f[0]
        val name = f[1].ifEmpty { return null }
        val einheit = if (f[3] == "ml") "ml" else "g"
        val id = "off:$ean"
        val food = FoodEntity(
            id = id, source = "off", barcode = ean, name = name, nameNorm = SuchText.normalisiere(name),
            brand = f[2].ifEmpty { null }, unitBase = einheit, kcal = d(4) ?: return null,
            fett = d(5), gesFett = d(6), kh = d(7), zucker = d(8), ballaststoffe = d(9), eiweiss = d(10), salz = d(11),
            beliebtheit = f[14].toIntOrNull() ?: 0,
        )
        val portion = d(12)
        val packung = d(13)
        val servings = listOfNotNull(
            ServingEntity(foodId = id, label = "100 $einheit", gramm = 100.0, isDefault = portion == null),
            portion?.let { ServingEntity(foodId = id, label = "Portion", gramm = it, isDefault = true) },
            packung?.takeIf { it != portion }?.let { ServingEntity(foodId = id, label = "Packung", gramm = it) },
        )
        return food to servings
    }

    companion object {
        /** Bei einer neuen Katalog-Datei hochzählen, dann liest die App sie beim nächsten Start ein. */
        const val VERSION = 1
        // Nicht ".gz": das Android-Build-Werkzeug würde die Datei sonst beim Bauen entpacken.
        const val DATEI = "katalog.tsv.gzip"
    }
}
