package de.kh.kalorientracker.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Modus { HANDY, HELL, DUNKEL }

/** Akzentfarbe: Knöpfe, Balken, Auswahl. Türkis ist die Markenfarbe und der Standard. */
enum class Akzent(
    val hell: Akzentwerte,
    val dunkel: Akzentwerte,
) {
    TUERKIS(
        Akzentwerte(0xFF0F766E, 0xFFFFFFFF, 0xFFCFF5EA, 0xFF064E47, 0xFF6EE7B7),
        Akzentwerte(0xFF34D399, 0xFF04261F, 0xFF123D34, 0xFFA7F3D0, 0xFF0F766E),
    ),
    BLAU(
        Akzentwerte(0xFF1D4ED8, 0xFFFFFFFF, 0xFFDBEAFE, 0xFF1E3A8A, 0xFF93C5FD),
        Akzentwerte(0xFF60A5FA, 0xFF0B1B3A, 0xFF1E3A5F, 0xFFBFDBFE, 0xFF1D4ED8),
    ),
    LILA(
        Akzentwerte(0xFF6D28D9, 0xFFFFFFFF, 0xFFEDE9FE, 0xFF4C1D95, 0xFFC4B5FD),
        Akzentwerte(0xFFA78BFA, 0xFF1E1035, 0xFF3B2A63, 0xFFDDD6FE, 0xFF6D28D9),
    ),
    ORANGE(
        Akzentwerte(0xFFC2410C, 0xFFFFFFFF, 0xFFFFEDD5, 0xFF7C2D12, 0xFFFDBA74),
        Akzentwerte(0xFFFB923C, 0xFF2A1205, 0xFF4A2410, 0xFFFED7AA, 0xFFC2410C),
    ),
    ROSA(
        Akzentwerte(0xFFBE185D, 0xFFFFFFFF, 0xFFFCE7F3, 0xFF831843, 0xFFF9A8D4),
        Akzentwerte(0xFFF472B6, 0xFF2A0A1A, 0xFF4A1830, 0xFFFBCFE8, 0xFFBE185D),
    ),
}

class Akzentwerte(primary: Long, onPrimary: Long, container: Long, onContainer: Long, inverse: Long) {
    val primary = Color(primary)
    val onPrimary = Color(onPrimary)
    val container = Color(container)
    val onContainer = Color(onContainer)
    val inverse = Color(inverse)
}

fun ColorScheme.mitAkzent(a: Akzentwerte) = copy(
    primary = a.primary, onPrimary = a.onPrimary, inversePrimary = a.inverse,
    primaryContainer = a.container, onPrimaryContainer = a.onContainer,
    secondaryContainer = a.container, onSecondaryContainer = a.onContainer,
)

fun AppColors.mitAkzent(a: Akzentwerte) = copy(accentSoft = a.container, onAccentSoft = a.onContainer)

/** Merkt sich hell/dunkel und Akzentfarbe (auf dem Handy, in den App-Einstellungen). */
object Design {
    private var prefs: android.content.SharedPreferences? = null
    private val _modus = MutableStateFlow(Modus.HANDY)
    private val _akzent = MutableStateFlow(Akzent.TUERKIS)
    val modus: StateFlow<Modus> = _modus
    val akzent: StateFlow<Akzent> = _akzent

    fun lade(context: Context) {
        prefs = context.getSharedPreferences("anzeige", Context.MODE_PRIVATE)
        _modus.value = prefs?.getString("modus", null)?.let { runCatching { Modus.valueOf(it) }.getOrNull() } ?: Modus.HANDY
        _akzent.value = prefs?.getString("akzent", null)?.let { runCatching { Akzent.valueOf(it) }.getOrNull() } ?: Akzent.TUERKIS
    }

    fun setze(modus: Modus = _modus.value, akzent: Akzent = _akzent.value) {
        prefs?.edit()?.putString("modus", modus.name)?.putString("akzent", akzent.name)?.apply()
        _modus.value = modus
        _akzent.value = akzent
    }
}
