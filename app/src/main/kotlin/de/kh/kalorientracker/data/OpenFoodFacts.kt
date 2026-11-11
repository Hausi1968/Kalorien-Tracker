package de.kh.kalorientracker.data

import de.kh.kalorientracker.core.Mikros
import de.kh.kalorientracker.core.Richtwerte
import de.kh.kalorientracker.core.SuchText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Ein Produkt aus Open Food Facts, schon in unser Format übersetzt. */
data class OffTreffer(val food: FoodEntity, val portionen: List<ServingEntity>)

/**
 * Kleiner Client für die Open-Food-Facts-API (Daten unter ODbL).
 * Barcode-Abfrage ist großzügig erlaubt, die Suche nur etwa 10-mal pro Minute.
 * Alles, was hier ankommt, speichert das Repository lokal, damit es nicht noch einmal abgefragt wird.
 */
open class OpenFoodFacts {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** null = Produkt unbekannt. Wirft IOException ohne Verbindung. */
    open suspend fun produkt(ean: String): OffTreffer? {
        val text = hole("$BASIS/api/v2/product/$ean.json?fields=$FELDER")
        val wurzel = json.parseToJsonElement(text).jsonObject
        if ((wurzel["status"] as? JsonPrimitive)?.intOrNull != 1) return null
        val p = wurzel["product"] as? JsonObject ?: return null
        return uebersetze(p, ean)
    }

    /** Volltextsuche über search.openfoodfacts.org; fällt auf die alte Suche zurück, wenn die nicht antwortet. */
    open suspend fun suche(eingabe: String): List<OffTreffer> {
        val q = URLEncoder.encode(eingabe.trim(), "UTF-8")
        val (text, schluessel) = try {
            hole("$SUCHE/search?q=$q&page_size=24&langs=de&fields=$FELDER") to "hits"
        } catch (e: IOException) {
            hole(
                "$BASIS/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1" +
                    "&page_size=24&fields=$FELDER&cc=de&lc=de",
            ) to "products"
        }
        val produkte = json.parseToJsonElement(text).jsonObject[schluessel] as? JsonArray ?: return emptyList()
        return produkte.mapNotNull { el ->
            val p = el as? JsonObject ?: return@mapNotNull null
            val code = p.text("code") ?: return@mapNotNull null
            uebersetze(p, code)
        }
    }

    private fun uebersetze(p: JsonObject, ean: String): OffTreffer? {
        val name = (p.text("product_name_de") ?: p.text("product_name"))?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val n = p["nutriments"] as? JsonObject ?: return null
        val kcal = n.zahl("energy-kcal_100g") ?: n.zahl("energy_100g")?.let { it / 4.184 } ?: return null
        val menge = p.text("quantity")?.lowercase().orEmpty()
        val einheit = if (Regex("""\d\s*(ml|cl|l)\b""").containsMatchIn(menge)) "ml" else "g"
        val id = "off:$ean"
        val food = FoodEntity(
            id = id, source = "off", barcode = ean, name = name, nameNorm = SuchText.normalisiere(name),
            brand = marke(p["brands"]),
            unitBase = einheit, kcal = kcal,
            fett = n.zahl("fat_100g"), gesFett = n.zahl("saturated-fat_100g"),
            kh = n.zahl("carbohydrates_100g"), zucker = n.zahl("sugars_100g"),
            ballaststoffe = n.zahl("fiber_100g"), eiweiss = n.zahl("proteins_100g"), salz = n.zahl("salt_100g"),
            mikros = Mikros.kodiere(
                Mikros.ALLE.mapNotNull { st -> n.zahl("${st.schluessel}_100g")?.let { st.schluessel to it } }.toMap(),
            ),
        )
        val portionen = buildList {
            add(ServingEntity(foodId = id, label = "100 $einheit", gramm = 100.0))
            // Obst, Gemüse, Eier im Netz/Karton: durchschnittliches Stück als Richtwert
            if (einheit == "g") Richtwerte.stueckFuer(SuchText.normalisiere(name))?.let {
                add(ServingEntity(foodId = id, label = RICHTWERT_STUECK, gramm = it.gramm, isDefault = true))
            }
            // Portion des Herstellers, z. B. "1 Becher (150 g)" -> "Becher" mit 150 g
            val portionText = p.text("serving_size")
            val portionGramm = p.zahl("serving_quantity")?.takeIf { it > 0 } ?: portionText?.let(::grammAus)
            portionGramm?.let {
                add(ServingEntity(foodId = id, label = portionsName(portionText), gramm = it, isDefault = none { s -> s.isDefault }))
            }
            p.zahl("product_quantity")?.takeIf { it > 0 }?.let {
                add(ServingEntity(foodId = id, label = "Packung", gramm = it, isDefault = none { s -> s.isDefault }))
            }
        }
        return OffTreffer(food, portionen)
    }

    private suspend fun hole(adresse: String): String = withContext(Dispatchers.IO) {
        val c = URL(adresse).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 12000
            // OFF bittet um eine erkennbare App-Kennung, ohne persönliche Daten.
            c.setRequestProperty("User-Agent", "KalorienTracker-Android/0.2")
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** "brands" ist mal ein Text mit Kommas, mal eine Liste. */
    private fun marke(e: JsonElement?): String? {
        val erste = when (e) {
            is JsonArray -> e.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }
            is JsonPrimitive -> e.contentOrNull?.split(',')?.firstOrNull()
            else -> null
        }
        return erste?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.zahl(k: String): Double? =
        (this[k] as? JsonPrimitive)?.contentOrNull?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it >= 0 }

    companion object {
        const val RICHTWERT_STUECK = "Stück (Richtwert)"
        private val EINHEITEN = setOf("g", "gr", "gramm", "ml", "l", "cl", "kg", "portion", "portionen", "serving", "x", "à", "a", "je", "pro")

        /** "1 Becher (150 g)" -> "Becher", "2 Scheiben = 50 g" -> "2 Scheiben", "30 g" -> "Portion". */
        fun portionsName(text: String?): String {
            if (text.isNullOrBlank()) return "Portion"
            val vorne = text.substringBefore('(').substringBefore('=').substringBefore(',').trim()
            val woerter = vorne.split(Regex("""\s+""")).filter { it.isNotBlank() }
            val anzahl = woerter.firstOrNull()?.toDoubleOrNull()?.takeIf { it != 1.0 }
            val rest = woerter.dropWhile { it.replace(',', '.').toDoubleOrNull() != null }
                .filter { w -> w.lowercase().trimEnd('.') !in EINHEITEN && !w.any(Char::isDigit) }
            if (rest.isEmpty() || rest.joinToString(" ").length > 24) return "Portion"
            val name = rest.joinToString(" ").replaceFirstChar { it.uppercase() }
            return if (anzahl != null) "${anzahl.toInt()} $name" else name
        }

        /** Grammzahl aus einem Text wie "1 Riegel (25 g)" oder "250ml". */
        fun grammAus(text: String): Double? =
            Regex("""(\d+(?:[.,]\d+)?)\s*(g|ml)\b""", RegexOption.IGNORE_CASE).find(text)
                ?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 }

        private const val BASIS = "https://world.openfoodfacts.org"
        private const val SUCHE = "https://search.openfoodfacts.org"
        private const val FELDER = "code,product_name,product_name_de,brands,quantity,product_quantity,serving_quantity,serving_size,nutriments"
    }
}
