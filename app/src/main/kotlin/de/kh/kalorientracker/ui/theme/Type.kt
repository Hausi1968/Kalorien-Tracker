// Schrift: vorgesehen ist Nunito (Google Fonts, OFL). Bis die TTF-Dateien in res/font liegen,
// wird die Systemschrift verwendet. Zum Umstellen: nunito_*.ttf nach app/src/main/res/font kopieren
// und AppFont unten auf FontFamily(Font(R.font.nunito_regular), ...) setzen.
package de.kh.kalorientracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppFont: FontFamily = FontFamily.Default

private fun style(size: Int, line: Int, weight: FontWeight, tabular: Boolean = false) = TextStyle(
    fontFamily = AppFont, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
    fontFeatureSettings = if (tabular) "tnum" else null,
)

val AppTypography = Typography(
    displayLarge = style(44, 48, FontWeight.ExtraBold, tabular = true),
    headlineMedium = style(32, 36, FontWeight.ExtraBold, tabular = true),
    headlineSmall = style(22, 28, FontWeight.ExtraBold),
    titleLarge = style(22, 28, FontWeight.ExtraBold),
    titleMedium = style(18, 24, FontWeight.Bold),
    titleSmall = style(16, 22, FontWeight.Bold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    labelLarge = style(16, 20, FontWeight.Bold),
    labelMedium = style(14, 18, FontWeight.SemiBold, tabular = true),
    labelSmall = style(12, 16, FontWeight.SemiBold),
)
