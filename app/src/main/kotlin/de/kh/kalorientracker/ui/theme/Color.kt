// Erzeugt aus replica/design/tokens-*.json. Werte nicht hier ändern, sondern in den Tokens.
package de.kh.kalorientracker.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LightScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFF5EA),
    onPrimaryContainer = Color(0xFF064E47),
    secondary = Color(0xFF0EA5A4),
    onSecondary = Color(0xFF04261F),
    // Ohne diese Werte nimmt Material das Standard-Lila (z. B. für ausgewählte Knöpfe und Navigation)
    secondaryContainer = Color(0xFFCFF5EA),
    onSecondaryContainer = Color(0xFF064E47),
    tertiary = Color(0xFFFDE047),
    onTertiary = Color(0xFF2B2400),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF2B2400),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F8F7),
    surfaceContainerHighest = Color(0xFFDCE8E4),
    background = Color(0xFFF4F8F7),
    onBackground = Color(0xFF0F1F1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1F1B),
    surfaceVariant = Color(0xFFE9F2EF),
    onSurfaceVariant = Color(0xFF4F625C),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE9F2EF),
    outline = Color(0xFF6F827C),
    outlineVariant = Color(0xFFD5E2DE),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
)

val DarkScheme = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF04261F),
    primaryContainer = Color(0xFF123D34),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF0EA5A4),
    onSecondary = Color(0xFF04261F),
    secondaryContainer = Color(0xFF123D34),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = Color(0xFFFDE047),
    onTertiary = Color(0xFF2B2400),
    tertiaryContainer = Color(0xFF3D3510),
    onTertiaryContainer = Color(0xFFFEF3C7),
    surfaceContainerLowest = Color(0xFF0D1513),
    surfaceContainerLow = Color(0xFF121C19),
    surfaceContainerHighest = Color(0xFF253430),
    background = Color(0xFF0D1513),
    onBackground = Color(0xFFE9F2EF),
    surface = Color(0xFF16211E),
    onSurface = Color(0xFFE9F2EF),
    surfaceVariant = Color(0xFF1E2B27),
    onSurfaceVariant = Color(0xFFA5B7B1),
    surfaceContainer = Color(0xFF16211E),
    surfaceContainerHigh = Color(0xFF1E2B27),
    outline = Color(0xFF7E918B),
    outlineVariant = Color(0xFF2A3934),
    error = Color(0xFFF97066),
    onError = Color(0xFF0D1513),
)

/** Farben, die Material 3 nicht kennt: Makros, Wasser, Verbrauch, Markenfarben. */
@Immutable
data class AppColors(
    val surface2: Color,
    val textMuted: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val brandMint: Color,
    val brandTeal: Color,
    val highlight: Color,
    val onHighlight: Color,
    val success: Color,
    val warning: Color,
    val macroFat: Color,
    val macroCarbs: Color,
    val macroProtein: Color,
    val water: Color,
    val burned: Color,
    val border: Color,
    val borderInput: Color,
)

val LightAppColors = AppColors(
    surface2 = Color(0xFFE9F2EF),
    textMuted = Color(0xFF4F625C),
    accentSoft = Color(0xFFCFF5EA),
    onAccentSoft = Color(0xFF064E47),
    brandMint = Color(0xFF34D399),
    brandTeal = Color(0xFF0EA5A4),
    highlight = Color(0xFFFDE047),
    onHighlight = Color(0xFF2B2400),
    success = Color(0xFF15803D),
    warning = Color(0xFFA15C07),
    macroFat = Color(0xFFD9480F),
    macroCarbs = Color(0xFFB7791F),
    macroProtein = Color(0xFF0E7490),
    water = Color(0xFF2563EB),
    burned = Color(0xFFC2410C),
    border = Color(0xFFD5E2DE),
    borderInput = Color(0xFF6F827C),
)

val DarkAppColors = AppColors(
    surface2 = Color(0xFF1E2B27),
    textMuted = Color(0xFFA5B7B1),
    accentSoft = Color(0xFF123D34),
    onAccentSoft = Color(0xFFA7F3D0),
    brandMint = Color(0xFF34D399),
    brandTeal = Color(0xFF0EA5A4),
    highlight = Color(0xFFFDE047),
    onHighlight = Color(0xFF2B2400),
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFACC15),
    macroFat = Color(0xFFFB8A5B),
    macroCarbs = Color(0xFFFBBF24),
    macroProtein = Color(0xFF38BDF8),
    water = Color(0xFF60A5FA),
    burned = Color(0xFFFB923C),
    border = Color(0xFF2A3934),
    borderInput = Color(0xFF7E918B),
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
