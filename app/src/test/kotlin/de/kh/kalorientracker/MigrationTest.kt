package de.kh.kalorientracker

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.kh.kalorientracker.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Prüft, dass ein Update die Nutzerdaten übernimmt: Datenbank im Stand von Version 1 mit Einträgen,
 * dann alle Migrationen bis heute, Room vergleicht das Ergebnis mit dem exportierten Schema.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun von1BisHeute_behaeltEintraege() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL(
                "INSERT INTO diary_entry (id, day, eatenAt, slotId, kind, foodRef, label, brand, servingLabel, quantity, gramm, " +
                    "kcal, fett, gesFett, kh, zucker, ballaststoffe, eiweiss, salz, createdAt, updatedAt) " +
                    "VALUES ('e1', '2026-11-10', 0, 1, 'food', NULL, 'Apfel', NULL, 'Stück', 1, 150, 78, 0.3, 0, 17, 15, 3, 0.4, 0, 0, 0)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(
            "migration-test", 6, true,
            AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6,
        )
        db.query("SELECT label, kcal, mikros, foto FROM diary_entry WHERE id = 'e1'").use { c ->
            c.moveToFirst()
            assertEquals("Apfel", c.getString(0))
            assertEquals(78.0, c.getDouble(1), 0.01)
            assertEquals(true, c.isNull(2))
            assertEquals(true, c.isNull(3))
        }
    }
}
