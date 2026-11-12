package de.kh.kalorientracker.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.KebabDining
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.RiceBowl
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WineBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import de.kh.kalorientracker.R
import de.kh.kalorientracker.data.Lebensmittelart

/** Das Symbol zu einer Lebensmittel-Art. */
@Composable
fun symbol(art: Lebensmittelart): ImageVector = when (art) {
    Lebensmittelart.HEISSGETRAENK -> Icons.Rounded.LocalCafe
    Lebensmittelart.GETRAENK -> Icons.Rounded.LocalDrink
    Lebensmittelart.ALKOHOL -> Icons.Rounded.WineBar
    Lebensmittelart.PIZZA -> Icons.Rounded.LocalPizza
    Lebensmittelart.FASTFOOD -> Icons.Rounded.Fastfood
    Lebensmittelart.KUCHEN -> Icons.Rounded.Cake
    Lebensmittelart.SUESSES -> Icons.Rounded.Cookie
    Lebensmittelart.NUDELN -> Icons.Rounded.RamenDining
    Lebensmittelart.REIS -> Icons.Rounded.RiceBowl
    Lebensmittelart.SUPPE -> Icons.Rounded.SoupKitchen
    Lebensmittelart.BROT -> Icons.Rounded.BakeryDining
    Lebensmittelart.MUESLI -> Icons.Rounded.Grain
    Lebensmittelart.EI -> Icons.Rounded.Egg
    Lebensmittelart.NUESSE -> Icons.Rounded.Spa
    Lebensmittelart.FISCH -> Icons.Rounded.SetMeal
    Lebensmittelart.FLEISCH -> Icons.Rounded.KebabDining
    Lebensmittelart.EIS -> Icons.Rounded.Icecream
    Lebensmittelart.MILCH -> ImageVector.vectorResource(R.drawable.ic_art_milch)
    Lebensmittelart.OEL -> Icons.Rounded.WaterDrop
    Lebensmittelart.OBST -> ImageVector.vectorResource(R.drawable.ic_art_obst)
    Lebensmittelart.GEMUESE -> Icons.Rounded.Eco
    Lebensmittelart.REZEPT -> Icons.Rounded.RestaurantMenu
}
