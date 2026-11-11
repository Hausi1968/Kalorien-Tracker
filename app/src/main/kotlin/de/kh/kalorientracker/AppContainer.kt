package de.kh.kalorientracker

import android.app.Application
import android.content.Context
import de.kh.kalorientracker.data.AppDatabase
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.data.StartdatenImport
import de.kh.kalorientracker.update.Aktualisierung

/** Einfache manuelle Abhängigkeiten statt Hilt; reicht für die erste Version. */
class AppContainer(context: Context) {
    private val db = AppDatabase.baue(context)
    val quellen = de.kh.kalorientracker.data.Quellen(context)
    val repo = KalorienRepository(db, quellen = quellen)
    val healthConnect = de.kh.kalorientracker.gesundheit.HealthConnect(context, repo)
    val startdaten = StartdatenImport(context, db)
    val katalog = de.kh.kalorientracker.data.KatalogImport(context, db)
    val aktualisierung = Aktualisierung(context)
}

class KalorienApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
