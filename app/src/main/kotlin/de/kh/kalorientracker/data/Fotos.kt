package de.kh.kalorientracker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Fotos von Mahlzeiten. Sie liegen nur auf dem Handy (files/fotos) und werden verkleinert gespeichert
 * (längste Seite 1280 px), damit sie wenig Platz brauchen und schnell verschickt werden können.
 */
class Fotos(private val context: Context) {
    private val ordner get() = File(context.filesDir, "fotos").apply { mkdirs() }
    private val aufnahmeOrdner get() = File(context.cacheDir, "aufnahme").apply { mkdirs() }

    /** Leere Datei samt Adresse, in die die Kamera-App das Foto schreibt. */
    fun neueAufnahme(): Uri {
        aufnahmeOrdner.listFiles()?.forEach { it.delete() }
        val datei = File(aufnahmeOrdner, "aufnahme-${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.dateien", datei)
    }

    /**
     * Übernimmt ein Foto (aus der Kamera oder der Galerie): richtig gedreht, verkleinert, als JPEG.
     * Liefert den Dateinamen oder null, wenn das Bild nicht lesbar war.
     */
    suspend fun uebernimm(quelle: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bild = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, quelle)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val seite = max(info.size.width, info.size.height)
                if (seite > MAX_SEITE) {
                    val f = MAX_SEITE.toDouble() / seite
                    decoder.setTargetSize((info.size.width * f).roundToInt(), (info.size.height * f).roundToInt())
                }
            }
            val name = "${UUID.randomUUID()}.jpg"
            File(ordner, name).outputStream().use { bild.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bild.recycle()
            if (quelle.authority == "${context.packageName}.dateien") aufnahmeOrdner.listFiles()?.forEach { it.delete() }
            name
        }.getOrNull()
    }

    fun datei(name: String): File = File(ordner, name)

    suspend fun bytes(name: String): ByteArray? = withContext(Dispatchers.IO) { runCatching { datei(name).readBytes() }.getOrNull() }

    /** Löscht Fotos, die an keinem Eintrag mehr hängen und älter als einen Tag sind. */
    suspend fun aufraeumen(benutzt: Set<String>) = withContext(Dispatchers.IO) {
        val grenze = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        ordner.listFiles()?.forEach { f -> if (f.name !in benutzt && f.lastModified() < grenze) f.delete() }
    }

    companion object {
        const val MAX_SEITE = 1280
    }
}
