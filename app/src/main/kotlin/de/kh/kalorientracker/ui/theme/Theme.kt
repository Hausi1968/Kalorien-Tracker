package de.kh.kalorientracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Kein Dynamic Color (Material You): die Farben bestimmt die App.
 * Hell/dunkel und Akzentfarbe wählt der Nutzer im Profil (Standard: wie Handy, Türkis).
 */
@Composable
fun KalorienTheme(dark: Boolean? = null, content: @Composable () -> Unit) {
    val modus by Design.modus.collectAsState()
    val akzent by Design.akzent.collectAsState()
    val system = isSystemInDarkTheme()
    val dunkel = dark ?: when (modus) {
        Modus.HANDY -> system
        Modus.HELL -> false
        Modus.DUNKEL -> true
    }
    val werte = if (dunkel) akzent.dunkel else akzent.hell
    val view = LocalView.current
    if (!view.isInEditMode) {
        // Uhrzeit/Akku oben passend zum gewählten Modus einfärben, nicht zum Handy
        SideEffect {
            (view.context as? Activity)?.window?.let { w ->
                WindowCompat.getInsetsController(w, view).apply {
                    isAppearanceLightStatusBars = !dunkel
                    isAppearanceLightNavigationBars = !dunkel
                }
            }
        }
    }
    CompositionLocalProvider(LocalAppColors provides (if (dunkel) DarkAppColors else LightAppColors).mitAkzent(werte)) {
        MaterialTheme(
            colorScheme = (if (dunkel) DarkScheme else LightScheme).mitAkzent(werte),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object AppTheme {
    val colors: AppColors @Composable @ReadOnlyComposable get() = LocalAppColors.current
}
