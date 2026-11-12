package de.kh.kalorientracker.data

/**
 * Grobe Art eines Lebensmittels, nur für das kleine Symbol in den Listen.
 * Sie wird aus dem Namen geraten; passt nichts, zeigt die Liste den Anfangsbuchstaben.
 */
enum class Lebensmittelart {
    HEISSGETRAENK, GETRAENK, ALKOHOL, PIZZA, FASTFOOD, KUCHEN, SUESSES, NUDELN, REIS, SUPPE, BROT, MUESLI,
    EI, NUESSE, FISCH, FLEISCH, EIS, MILCH, OEL, OBST, GEMUESE, REZEPT,
}

private class Regel(val art: Lebensmittelart, val woerter: List<String>, val genau: List<String> = emptyList())

// Reihenfolge ist wichtig: "Apfelsaft" ist ein Getränk, "Käsekuchen" ein Kuchen, "Erdnussbutter" eine Nuss,
// "Milchreis" Reis und kein Eis, "Weintrauben" Obst und kein Wein.
private val REGELN = listOf(
    Regel(Lebensmittelart.OBST, listOf("wassermelone", "honigmelone", "preiselbeer")),
    Regel(Lebensmittelart.HEISSGETRAENK, listOf("kaffee", "espresso", "cappuccino", "latte macchiato", "kakao", "tee", "heiße schokolade")),
    Regel(Lebensmittelart.GETRAENK, listOf("saft", "schorle", "limonade", "wasser", "smoothie", "nektar", "sprudel", "drink", "shake", "eistee", "fanta", "sprite"), genau = listOf("cola", "limo")),
    Regel(Lebensmittelart.PIZZA, listOf("pizza", "flammkuchen")),
    Regel(Lebensmittelart.FASTFOOD, listOf("burger", "pommes", "döner", "hotdog", "hot dog", "nuggets", "kebab", "currywurst", "fritten")),
    Regel(Lebensmittelart.KUCHEN, listOf("kuchen", "torte", "muffin", "strudel", "gebäck", "donut", "berliner", "plätzchen", "brownie")),
    Regel(Lebensmittelart.SUESSES, listOf("schoko", "keks", "cookie", "praline", "gummi", "bonbon", "riegel", "chips", "nougat", "nutella", "waffel", "lakritz", "zucker", "marmelade", "konfitüre", "honig", "pudding", "süßigkeit", "flips", "popcorn")),
    Regel(Lebensmittelart.NUDELN, listOf("nudel", "pasta", "spaghetti", "penne", "lasagne", "makkaroni", "tortellini", "ravioli", "spätzle", "fusilli", "tagliatelle", "gnocchi")),
    Regel(Lebensmittelart.REIS, listOf("reis", "risotto", "sushi", "couscous", "bulgur", "quinoa")),
    Regel(Lebensmittelart.SUPPE, listOf("suppe", "brühe", "eintopf")),
    Regel(Lebensmittelart.BROT, listOf("brot", "brötchen", "semmel", "toast", "baguette", "croissant", "laugen", "brezel", "knäcke", "wrap", "tortilla", "bagel", "zwieback")),
    Regel(Lebensmittelart.MUESLI, listOf("müsli", "haferflocken", "flocken", "cornflakes", "granola", "porridge", "getreide", "mehl", "grieß")),
    Regel(Lebensmittelart.EI, listOf("spiegelei", "rührei", "omelett", "eier"), genau = listOf("ei")),
    Regel(Lebensmittelart.NUESSE, listOf("nuss", "nüsse", "mandel", "cashew", "pistazie", "kerne", "samen", "erdnuss")),
    Regel(Lebensmittelart.FISCH, listOf("fisch", "lachs", "hering", "forelle", "garnele", "shrimp", "kabeljau", "matjes", "sardine", "makrele", "scampi", "thunfisch", "krabbe", "dorsch", "tilapia")),
    Regel(Lebensmittelart.FLEISCH, listOf("fleisch", "hähnchen", "huhn", "hühnchen", "pute", "rind", "schwein", "hack", "schnitzel", "steak", "wurst", "würstchen", "schinken", "salami", "speck", "frikadelle", "bulette", "gulasch", "kassler", "leberkäse", "eisbein", "geflügel", "lamm", "chicken", "bacon", "mett"), genau = listOf("ente")),
    Regel(Lebensmittelart.MILCH, listOf("milch", "joghurt", "jogurt", "quark", "käse", "sahne", "butter", "skyr", "kefir", "mozzarella", "gouda", "feta", "camembert", "parmesan", "frischkäse", "schmand")),
    Regel(Lebensmittelart.OEL, listOf("olivenöl", "rapsöl", "sonnenblumenöl", "margarine", "schmalz"), genau = listOf("öl")),
    Regel(Lebensmittelart.OBST, listOf("apfel", "äpfel", "birne", "banane", "orange", "mandarine", "kiwi", "traube", "erdbeer", "himbeer", "heidelbeer", "blaubeer", "beere", "kirsche", "pfirsich", "pflaume", "mango", "ananas", "melone", "zitrone", "obst", "nektarine", "aprikose", "feige", "dattel", "rosine", "grapefruit", "clementine", "papaya", "avocado")),
    Regel(Lebensmittelart.GEMUESE, listOf("gemüse", "salat", "tomate", "gurke", "paprika", "karotte", "möhre", "brokkoli", "kohl", "spinat", "zucchini", "aubergine", "zwiebel", "kartoffel", "pilz", "champignon", "bohne", "erbse", "mais", "linse", "kichererbse", "lauch", "porree", "sellerie", "spargel", "radieschen", "kürbis", "rote bete", "knoblauch", "rucola")),
    Regel(Lebensmittelart.ALKOHOL, listOf("bier", "wein", "sekt", "radler", "schnaps", "whisky", "wodka", "likör", "prosecco", "cocktail"), genau = listOf("rum", "gin")),
)

/** Rät die Art aus dem Namen. Rezepte bekommen immer das Rezept-Symbol. */
fun lebensmittelart(name: String, quelle: String? = null): Lebensmittelart? {
    if (quelle == "rezept") return Lebensmittelart.REZEPT
    val text = name.lowercase()
    val woerter = text.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
    // Eis: "Vanilleeis", "Eis am Stiel", aber nicht "Reis", "Mais" oder "Eisbein"
    fun istEis() = woerter.any { w -> w == "eis" || (w.endsWith("eis") && !w.endsWith("reis") && !w.endsWith("weis") && !w.endsWith("preis") && w.length > 4) }
    for (r in REGELN) {
        if (r.art == Lebensmittelart.PIZZA && istEis()) return Lebensmittelart.EIS
        if (r.woerter.any { text.contains(it) } || r.genau.any { it in woerter }) return r.art
    }
    return null
}
