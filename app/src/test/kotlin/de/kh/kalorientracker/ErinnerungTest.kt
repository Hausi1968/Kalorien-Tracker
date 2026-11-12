package de.kh.kalorientracker

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.kh.kalorientracker.erinnerung.Art
import de.kh.kalorientracker.erinnerung.ErinnerungEmpfaenger
import de.kh.kalorientracker.erinnerung.Erinnerungen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ErinnerungTest {
    private val app = ApplicationProvider.getApplicationContext<KalorienApp>()

    @Test fun naechsterZeitpunkt_heuteOderMorgen() {
        val jetzt = LocalDateTime.of(2026, 11, 12, 10, 0)
        val um = { h: Int -> Erinnerungen.naechsterZeitpunkt(LocalTime.of(h, 0), jetzt, ZoneOffset.UTC) }
        assertEquals(LocalDateTime.of(2026, 11, 12, 13, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), um(13))
        assertEquals(LocalDateTime.of(2026, 11, 13, 7, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), um(7))
    }

    @Test fun einschaltenPlantWecker_undErinnerungErscheint() {
        val e = app.container.erinnerungen
        e.kanalAnlegen()
        val am = shadowOf(app.getSystemService(AlarmManager::class.java))
        assertNull(am.nextScheduledAlarm)
        e.setze(e.erinnerung(Art.FRUEHSTUECK).copy(an = true))
        assertNotNull(am.nextScheduledAlarm)

        // Ohne Eintrag im Frühstück: Erinnerung wird angezeigt
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        ErinnerungEmpfaenger().onReceive(
            app, Intent(Erinnerungen.AKTION).putExtra(Erinnerungen.EXTRA_ART, Art.FRUEHSTUECK.name),
        )
        val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
        val bis = System.currentTimeMillis() + 5000
        while (nm.allNotifications.isEmpty() && System.currentTimeMillis() < bis) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(); Thread.sleep(50)
        }
        assertEquals(1, nm.allNotifications.size)
        e.setze(e.erinnerung(Art.FRUEHSTUECK).copy(an = false))
    }
}
