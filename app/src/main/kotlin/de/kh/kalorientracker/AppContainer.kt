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
    private val db = AppDatabase.baue(context)
    val quellen = de.kh.kalorientracker.data.Quellen(context)
    val repo = KalorienRepository(db, quellen = quellen)
    val healthConnect = de.kh.kalorientracker.gesundheit.HealthConnect(context, repo)
    val startdaten = StartdatenImport(context, db)
    val katalog = de.kh.kalorientracker.data.KatalogImport(context, db)
    val aktualisierung = Aktualisierung(context)
    val sicherung = Sicherung(context, db)
    val fotos = de.kh.kalorientracker.data.Fotos(context)
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
            runCatching { sicherung.wiederherstellenFallsNoetig() }
            runCatching { startdaten.fallsLeer() }
            launch { runCatching { healthConnect.abgleichen() } }
            runCatching { katalog.fallsNoetig() }
            _katalogBereit.value = true
            runCatching { sicherung.schreibe() }
            runCatching { fotos.aufraeumen(repo.benutzteFotos()) }
        }
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
