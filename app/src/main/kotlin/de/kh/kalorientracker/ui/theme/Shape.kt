package de.kh.kalorientracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),     // Chips, Eingabefelder
    medium = RoundedCornerShape(12.dp),   // Listenkarten
    large = RoundedCornerShape(20.dp),    // große Karten (Tagesbilanz)
    extraLarge = RoundedCornerShape(28.dp), // Bottom Sheets, FAB
)

object Spacing { val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 24.dp; val xxl = 32.dp; val minTouch = 48.dp }
