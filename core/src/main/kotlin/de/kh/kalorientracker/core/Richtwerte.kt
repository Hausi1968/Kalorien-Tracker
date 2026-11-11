package de.kh.kalorientracker.core

/**
 * Durchschnittsgewicht je Stück für Obst, Gemüse und Eier, die meist stückweise gegessen,
 * aber im Netz oder in der Packung verkauft werden (z. B. "Granny Smith Äpfel, 1 kg").
 * Dient als Richtwert für die Portion "Stück (Richtwert)".
 */
object Richtwerte {
    data class Stueck(val name: String, val gramm: Double, val woerter: Set<String>)

    val STUECK = listOf(
        Stueck("Apfel", 150.0, setOf("apfel", "aepfel")),
        Stueck("Banane", 120.0, setOf("banane", "bananen")),
        Stueck("Birne", 160.0, setOf("birne", "birnen")),
        Stueck("Orange", 150.0, setOf("orange", "orangen")),
        Stueck("Mandarine", 60.0, setOf("mandarine", "mandarinen", "clementine", "clementinen")),
        Stueck("Kiwi", 75.0, setOf("kiwi", "kiwis")),
        Stueck("Pfirsich", 130.0, setOf("pfirsich", "pfirsiche", "nektarine", "nektarinen")),
        Stueck("Zitrone", 100.0, setOf("zitrone", "zitronen", "limette", "limetten")),
        Stueck("Avocado", 150.0, setOf("avocado", "avocados")),
        Stueck("Tomate", 80.0, setOf("tomate", "tomaten")),
        Stueck("Gurke", 400.0, setOf("gurke", "salatgurke", "gurken")),
        Stueck("Paprika", 160.0, setOf("paprika", "spitzpaprika")),
        Stueck("Karotte", 70.0, setOf("karotte", "karotten", "moehre", "moehren")),
        Stueck("Zwiebel", 80.0, setOf("zwiebel", "zwiebeln")),
        Stueck("Kartoffel", 100.0, setOf("kartoffel", "kartoffeln")),
        Stueck("Ei", 60.0, setOf("ei", "eier", "freilandeier", "bodenhaltungseier")),
    )

    /** Verarbeitetes ist kein Stück Obst: "Apfelmus", "Tomaten passiert", "Paprikachips" … */
    private val VERARBEITET = setOf(
        "saft", "mus", "mark", "passiert", "passierte", "stueckig", "gehackt", "chips", "sauce", "sosse", "ketchup",
        "getrocknet", "getrocknete", "konserve", "dose", "pueree", "nudeln", "salat", "likoer", "eis", "joghurt",
        "kuchen", "riegel", "pulver", "flocken", "gewuerz", "pesto", "aufstrich", "mayonnaise", "nektar", "schorle",
    )

    /** Passendes Stück-Gewicht zu einem (normalisierten) Produktnamen, sonst null. */
    fun stueckFuer(nameNorm: String): Stueck? {
        val woerter = nameNorm.split(' ').filter { it.isNotBlank() }
        if (woerter.any { it in VERARBEITET }) return null
        return STUECK.firstOrNull { s -> woerter.any { it in s.woerter } }
    }
}
