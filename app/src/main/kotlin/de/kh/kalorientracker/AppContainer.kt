package de.kh.kalorientracker

import android.app.Application
import android.content.Context
import de.kh.kalorientracker.data.AppDatabase
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.data.StartdatenImport
import de.kh.kalorientracker.data.Sicherung
import de.kh.kalorientracker.update.Aktualisierung
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Einfache manuelle Abhängigkeiten statt Hilt; reicht für die erste Version. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.baue(context)
    val quellen = de.kh.kalorientracker.data.Quellen(context)
    val repo = KalorienRepository(db, quellen = quellen)
    val healthConnect = de.kh.kalorientracker.gesundheit.HealthConnect(context, repo)
    val startdaten = StartdatenImport(context, db)
    val katalog = de.kh.kalorientracker.data.KatalogImport(context, db)
    val aktualisierung = Aktualisierung(context)
    val sicherung = Sicherung(context, db)
    val fotos = de.kh.kalorientracker.data.Fotos(context)
    val erinnerungen = de.kh.kalorientracker.erinnerung.Erinnerungen(context)
    val kiSchluessel = de.kh.kalorientracker.data.KiSchluessel(context)
    val erkenner: de.kh.kalorientracker.data.FotoErkenner = de.kh.kalorientracker.data.ClaudeErkenner(kiSchluessel)

    /** Lebt so lange wie die App, nicht nur wie ein Bildschirm: Drehen o. Ä. bricht nichts ab. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _katalogBereit = MutableStateFlow(false)
    /** false, solange der Katalog beim ersten Start noch eingelesen wird (Suche dann unvollständig). */
    val katalogBereit: StateFlow<Boolean> = _katalogBereit

    private var startJob: Job? = null

    /**
     * Arbeiten beim Start, jede einzeln abgesichert: Ein Fehler (z. B. Speicher voll) darf die App
     * nicht bei jedem Start abstürzen lassen. Mehrfacher Aufruf startet nichts doppelt.
     */
    fun starte() {
        if (startJob != null) return
        startJob = scope.launch {
            runCatching { erinnerungen.kanalAnlegen(); erinnerungen.planeAlle() }
            // Widget folgt jeder Änderung von heute, solange die App läuft
            launch {
                runCatching {
                    while (true) {
                        // Um Mitternacht auf den neuen Tag umschalten
                        val heute = java.time.LocalDate.now()
                        val bisMitternacht = java.time.Duration.between(
                            java.time.LocalDateTime.now(), heute.plusDays(1).atStartOfDay(),
                        ).toMillis() + 1000
                        kotlinx.coroutines.withTimeoutOrNull(bisMitternacht) {
                            repo.tag(heute).collect { d ->
                                de.kh.kalorientracker.widget.RestWidget.zeichne(appContext, d.takeIf { it.profil != null }?.bilanz)
                            }
                        }
                    }
                }
            }
            runCatching { sicherung.wiederherstellenFallsNoetig() }
            runCatching { startdaten.fallsLeer() }
            launch { gesundheitAbgleichenWennFaellig() }
            runCatching { katalog.fallsNoetig() }
            _katalogBereit.value = true
            runCatching { sicherung.schreibe() }
            runCatching { fotos.aufraeumen(repo.benutzteFotos()) }
        }
    }

    private var letzterAbgleich = 0L

    /** Health Connect abgleichen, höchstens alle 2 Minuten (z. B. bei schnellem Hin- und Herwechseln). */
    suspend fun gesundheitAbgleichenWennFaellig() {
        val jetzt = System.currentTimeMillis()
        if (jetzt - letzterAbgleich < 2 * 60 * 1000L) return
        letzterAbgleich = jetzt
        runCatching { healthConnect.abgleichen() }
    }

    /** Sicherungsdatei auffrischen, z. B. wenn die App in den Hintergrund geht. */
    fun sichere() {
        scope.launch { runCatching { sicherung.schreibe() } }
    }
}

class KalorienApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
