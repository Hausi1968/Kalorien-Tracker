package de.kh.kalorientracker.data

import android.content.Context
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.util.Base64

/** Ein Lebensmittel, das auf dem Foto gesehen wurde, mit geschätzter Menge. */
@Serializable
data class ErkanntesLebensmittel(val name: String, val gramm: Double)

/** Warum die Erkennung nichts liefern konnte; der Bildschirm zeigt dazu einen passenden Satz. */
enum class ErkennungsFehler { KEIN_SCHLUESSEL, SCHLUESSEL_FALSCH, KEINE_VERBINDUNG, ABGELEHNT, UNBEKANNT }

class ErkennungFehlgeschlagen(val grund: ErkennungsFehler) : Exception(grund.name)

/** Erkennt Lebensmittel auf einem Foto (JPEG). */
interface FotoErkenner {
    val bereit: Boolean
    suspend fun erkenne(jpeg: ByteArray): Result<List<ErkanntesLebensmittel>>
}

/**
 * Der Zugangsschlüssel für den KI-Dienst. Er liegt nur auf dem Handy (App-Einstellungen, nicht in der Sicherung)
 * und wird nie mitgeschickt außer an den Dienst selbst.
 */
class KiSchluessel(context: Context? = null) {
    private val prefs = context?.getSharedPreferences("ki", Context.MODE_PRIVATE)
    private val _wert = MutableStateFlow(prefs?.getString("schluessel", null).orEmpty())
    val wert: StateFlow<String> = _wert

    fun setze(neu: String) {
        val s = neu.trim()
        prefs?.edit()?.putString("schluessel", s)?.apply()
        _wert.value = s
    }
}

/** Erkennung über Claude (Anthropic). Pro Foto eine Anfrage; das Foto wird dafür verschickt. */
class ClaudeErkenner(private val schluessel: KiSchluessel) : FotoErkenner {
    override val bereit: Boolean get() = schluessel.wert.value.isNotBlank()

    @Serializable
    private data class Antwort(val lebensmittel: List<ErkanntesLebensmittel> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun erkenne(jpeg: ByteArray): Result<List<ErkanntesLebensmittel>> = withContext(Dispatchers.IO) {
        val key = schluessel.wert.value
        if (key.isBlank()) return@withContext Result.failure(ErkennungFehlgeschlagen(ErkennungsFehler.KEIN_SCHLUESSEL))
        val client = AnthropicOkHttpClient.builder().apiKey(key).timeout(Duration.ofSeconds(90)).maxRetries(1).build()
        try {
            val params = MessageCreateParams.builder()
                .model(MODELL)
                .maxTokens(4000L)
                .outputConfig(
                    OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW)
                        .format(JsonOutputFormat.builder().schema(schema()).build())
                        .build(),
                )
                // Falls eine Anfrage abgelehnt wird, beantwortet der Dienst sie mit einem anderen Modell
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .addUserMessageOfContentBlockParams(
                    listOf(
                        ContentBlockParam.ofImage(
                            ImageBlockParam.builder()
                                .source(
                                    Base64ImageSource.builder()
                                        .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                                        .data(Base64.getEncoder().encodeToString(jpeg))
                                        .build(),
                                )
                                .build(),
                        ),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(AUFTRAG).build()),
                    ),
                )
                .build()
            val antwort = client.messages().create(params)
            if (antwort.stopReason().map { it.toString() }.orElse("") == "refusal") {
                return@withContext Result.failure(ErkennungFehlgeschlagen(ErkennungsFehler.ABGELEHNT))
            }
            val text = antwort.content().mapNotNull { b -> b.text().map { it.text() }.orElse(null) }.joinToString("")
            val liste = json.decodeFromString(Antwort.serializer(), text).lebensmittel
                .filter { it.name.isNotBlank() && it.gramm > 0 && it.gramm < 5000 }
            Result.success(liste)
        } catch (e: UnauthorizedException) {
            Result.failure(ErkennungFehlgeschlagen(ErkennungsFehler.SCHLUESSEL_FALSCH))
        } catch (e: AnthropicServiceException) {
            Result.failure(ErkennungFehlgeschlagen(ErkennungsFehler.UNBEKANNT))
        } catch (e: java.io.IOException) {
            Result.failure(ErkennungFehlgeschlagen(ErkennungsFehler.KEINE_VERBINDUNG))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Auch unerwartete Fehler (z. B. fehlende Klassen) sollen nur die Erkennung scheitern lassen, nicht die App
            if (e.cause is java.io.IOException) Result.failure(ErkennungFehlgeschlagen(ErkennungsFehler.KEINE_VERBINDUNG))
            else Result.failure(ErkennungFehlgeschlagen(ErkennungsFehler.UNBEKANNT))
        } finally {
            client.close()
        }
    }

    private fun schema(): JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder()
        .putAdditionalProperty("type", JsonValue.from("object"))
        .putAdditionalProperty(
            "properties",
            JsonValue.from(
                mapOf(
                    "lebensmittel" to mapOf(
                        "type" to "array",
                        "items" to mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "name" to mapOf("type" to "string"),
                                "gramm" to mapOf("type" to "number"),
                            ),
                            "required" to listOf("name", "gramm"),
                            "additionalProperties" to false,
                        ),
                    ),
                ),
            ),
        )
        .putAdditionalProperty("required", JsonValue.from(listOf("lebensmittel")))
        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
        .build()

    companion object {
        const val MODELL = "claude-opus-5-5"

        private val AUFTRAG = """
            Auf dem Foto ist eine Mahlzeit, ein Getränk oder ein einzelnes Lebensmittel.
            Liste jedes erkennbare Lebensmittel einzeln auf, mit einem kurzen deutschen Namen, wie man ihn in einer
            Nährwert-Datenbank suchen würde (z. B. "Reis gekocht", "Hähnchenbrust gebraten", "Brokkoli", "Vollkornbrot",
            "Butter", "Apfel"), und schätze die sichtbare Menge in Gramm (bei Getränken in ml).
            Nutze Teller, Besteck, Hände oder Verpackungen als Größenvergleich. Ein Produkt mit lesbarer Marke nennst du
            mit Marke. Soßen, Öl oder Belag nur aufführen, wenn sie gut sichtbar sind.
            Ist kein Lebensmittel zu sehen, gib eine leere Liste zurück.
        """.trimIndent()
    }
}
