@file:OptIn(ExperimentalMaterial3Api::class)

package de.kh.kalorientracker.ui

import de.kh.kalorientracker.ui.rezepte.RezeptEditorScreen
import de.kh.kalorientracker.ui.rezepte.RezepteScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.kh.kalorientracker.AppContainer
import de.kh.kalorientracker.R
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.ui.detail.DetailScreen
import de.kh.kalorientracker.ui.onboarding.RechnerScreen
import de.kh.kalorientracker.ui.produkt.ProduktScreen
import de.kh.kalorientracker.ui.suche.SucheScreen
import de.kh.kalorientracker.ui.gewicht.GewichtScreen
import de.kh.kalorientracker.ui.tagebuch.TagebuchScreen
import de.kh.kalorientracker.update.Aktualisierung
import de.kh.kalorientracker.update.NeueVersion
import de.kh.kalorientracker.update.UpdateDialog
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Einstieg: ohne Profil zuerst der Pflicht-Rechner (jeder gibt eigene Daten ein),
 * danach die App mit Navigation unten.
 */
@Composable
fun KalorienAppUi(container: AppContainer) {
    LaunchedEffect(Unit) {
        container.startdaten.fallsLeer()
        container.katalog.fallsNoetig()
        runCatching { container.healthConnect.abgleichen() }
    }
    val profilDa by remember { container.repo.profil.map { it != null } }
        .collectAsStateWithLifecycle(initialValue = null)

    when (profilDa) {
        null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        false -> RechnerScreen(container.repo, erstesMal = true, onFertig = {}, onZurueck = null)
        true -> HauptNavigation(container.repo, container.aktualisierung, container.healthConnect)
    }

    // Automatische Update-Prüfung schon ab dem ersten Bildschirm (höchstens etwa einmal am Tag)
    var neueVersion by remember { mutableStateOf<NeueVersion?>(null) }
    LaunchedEffect(Unit) { neueVersion = runCatching { container.aktualisierung.pruefe() }.getOrNull() }
    neueVersion?.let { UpdateDialog(container.aktualisierung, it, onSchliessen = { neueVersion = null }) }
}

private enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    UEBERSICHT("uebersicht", R.string.tab_uebersicht, Icons.Rounded.Home),
    TAGEBUCH("tagebuch", R.string.tab_tagebuch, Icons.Rounded.Today),
    REZEPTE("rezepte", R.string.tab_rezepte, Icons.Rounded.RestaurantMenu),
    VERLAUF("verlauf", R.string.tab_verlauf, Icons.Rounded.Insights),
    PROFIL("profil", R.string.tab_profil, Icons.Rounded.Person),
}

/** Routen außerhalb der Tabs. slot = 0 bedeutet: Mahlzeit nach Uhrzeit wählen. */
object Routen {
    const val SUCHE = "suche?tag={tag}&slot={slot}"
    const val DETAIL = "lebensmittel/{id}?tag={tag}&slot={slot}"
    const val RECHNER = "rechner"
    const val PRODUKT = "produkt-neu?barcode={barcode}&name={name}"
    const val EINTRAG = "eintrag/{eintragId}"
    const val REZEPT = "rezept?id={id}"
    fun rezept(id: String?) = "rezept?id=${android.net.Uri.encode(id.orEmpty())}"
    fun produkt(barcode: String?, name: String?) =
        "produkt-neu?barcode=${barcode.orEmpty()}&name=${android.net.Uri.encode(name.orEmpty())}"
    fun eintrag(id: String) = "eintrag/$id"
    fun suche(tag: LocalDate, slot: Int) = "suche?tag=$tag&slot=$slot"
    fun detail(id: String, tag: LocalDate, slot: Int) = "lebensmittel/${android.net.Uri.encode(id)}?tag=$tag&slot=$slot"
}

@Composable
private fun HauptNavigation(repo: KalorienRepository, aktualisierung: Aktualisierung, healthConnect: de.kh.kalorientracker.gesundheit.HealthConnect) {
    val nav = rememberNavController()
    var neueVersion by remember { mutableStateOf<NeueVersion?>(null) }
    neueVersion?.let { UpdateDialog(aktualisierung, it, onSchliessen = { neueVersion = null }) }
    val eintrag by nav.currentBackStackEntryAsState()
    val aktuelleRoute = eintrag?.destination?.route
    val zeigeLeiste = Tab.entries.any { it.route == aktuelleRoute }

    Scaffold(
        bottomBar = {
            if (zeigeLeiste) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = aktuelleRoute == tab.route,
                            onClick = { nav.zuTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { innen ->
        val tagArgs = listOf(
            navArgument("tag") { type = NavType.StringType; defaultValue = "" },
            navArgument("slot") { type = NavType.IntType; defaultValue = 0 },
        )
        NavHost(nav, startDestination = Tab.TAGEBUCH.route, modifier = Modifier.padding(bottom = innen.calculateBottomPadding())) {
            composable(Tab.UEBERSICHT.route) {
                UebersichtScreen(repo, onZumTagebuch = { nav.zuTab(Tab.TAGEBUCH.route) })
            }
            composable(Tab.TAGEBUCH.route) {
                TagebuchScreen(
                    repo,
                    onSuche = { tag, slot -> nav.navigate(Routen.suche(tag, slot)) },
                    onEintrag = { id -> nav.navigate(Routen.eintrag(id)) },
                )
            }
            composable(Tab.REZEPTE.route) {
                RezepteScreen(
                    repo,
                    onOeffnen = { id -> nav.navigate(Routen.detail(id, java.time.LocalDate.now(), 0)) },
                    onNeu = { nav.navigate(Routen.rezept(null)) },
                )
            }
            composable(Routen.REZEPT, arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" })) { e ->
                RezeptEditorScreen(
                    repo,
                    rezeptId = e.arguments?.getString("id")?.takeIf { it.isNotEmpty() },
                    onZurueck = { nav.popBackStack() },
                    onGespeichert = { id ->
                        // zurück zur Rezeptliste (auch wenn der Editor aus der Detailseite kam, deren Werte sonst veraltet wären)
                        if (!nav.popBackStack(Tab.REZEPTE.route, inclusive = false)) nav.popBackStack()
                        // Neues Rezept: gleich öffnen, damit man eine Portion eintragen kann
                        if (e.arguments?.getString("id").isNullOrEmpty()) nav.navigate(Routen.detail(id, java.time.LocalDate.now(), 0))
                    },
                )
            }
            composable(Tab.VERLAUF.route) { GewichtScreen(repo) }
            composable(Tab.PROFIL.route) {
                ProfilScreen(
                    repo, aktualisierung,
                    onZielAendern = { nav.navigate(Routen.RECHNER) },
                    onNeueVersion = { neueVersion = it },
                    healthConnect = healthConnect,
                )
            }
            composable(Routen.RECHNER) {
                RechnerScreen(repo, erstesMal = false, onFertig = { nav.popBackStack() }, onZurueck = { nav.popBackStack() })
            }
            composable(Routen.SUCHE, arguments = tagArgs) { e ->
                val tag = e.arguments?.getString("tag").zuDatum()
                val slot = e.arguments?.getInt("slot") ?: 0
                SucheScreen(
                    repo, tag, slot,
                    onZurueck = { nav.popBackStack() },
                    onLebensmittel = { id -> nav.navigate(Routen.detail(id, tag, slot)) },
                    onProduktAnlegen = { ean, name -> nav.navigate(Routen.produkt(ean, name)) },
                )
            }
            composable(
                Routen.PRODUKT,
                arguments = listOf(
                    navArgument("barcode") { type = NavType.StringType; defaultValue = "" },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                // Tag und Mahlzeit kommen vom Suche-Eintrag darunter im Verlauf
                val suche = nav.previousBackStackEntry
                val tag = suche?.arguments?.getString("tag").zuDatum()
                val slot = suche?.arguments?.getInt("slot") ?: 0
                ProduktScreen(
                    repo,
                    barcode = e.arguments?.getString("barcode")?.ifEmpty { null },
                    nameVorschlag = e.arguments?.getString("name")?.ifEmpty { null },
                    onZurueck = { nav.popBackStack() },
                    onGespeichert = { id ->
                        // zurück zur Rezeptliste (auch wenn der Editor aus der Detailseite kam, deren Werte sonst veraltet wären)
                        if (!nav.popBackStack(Tab.REZEPTE.route, inclusive = false)) nav.popBackStack()
                        nav.navigate(Routen.detail(id, tag, slot))
                    },
                )
            }
            composable(Routen.EINTRAG, arguments = listOf(navArgument("eintragId") { type = NavType.StringType })) { e ->
                DetailScreen(
                    repo,
                    foodId = null,
                    tag = LocalDate.now(),
                    slotVorgabe = 0,
                    eintragId = e.arguments?.getString("eintragId"),
                    onZurueck = { nav.popBackStack() },
                    onEingetragen = { nav.popBackStack() },
                )
            }
            composable(
                Routen.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }) + tagArgs,
            ) { e ->
                DetailScreen(
                    repo,
                    foodId = e.arguments?.getString("id").orEmpty(),
                    tag = e.arguments?.getString("tag").zuDatum(),
                    slotVorgabe = e.arguments?.getInt("slot") ?: 0,
                    onZurueck = { nav.popBackStack() },
                    onEingetragen = { if (!nav.popBackStack(Tab.TAGEBUCH.route, inclusive = false)) nav.zuTab(Tab.TAGEBUCH.route) },
                    onRezeptBearbeiten = { id -> nav.navigate(Routen.rezept(id)) },
                )
            }
        }
    }
}

private fun String?.zuDatum(): LocalDate =
    this?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()

private fun NavHostController.zuTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
