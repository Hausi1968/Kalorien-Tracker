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
    val repo = KalorienRepository(db)
    val startdaten = StartdatenImport(context, db)
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
