package de.kh.kalorientracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProfileEntity::class,
        FoodEntity::class,
        ServingEntity::class,
        DiaryEntryEntity::class,
        BodyEntryEntity::class,
        ActivityEntryEntity::class,
        FavoriteEntity::class,
        WaterEntryEntity::class,
        RezeptZutatEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profile(): ProfileDao
    abstract fun food(): FoodDao
    abstract fun diary(): DiaryDao
    abstract fun body(): BodyDao
    abstract fun activity(): ActivityDao
    abstract fun favorite(): FavoriteDao
    abstract fun water(): WaterDao
    abstract fun rezept(): RezeptDao
    abstract fun sicherung(): SicherungDao

    companion object {
        fun baue(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "kalorien.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .apply { testAnpassung?.invoke(this) }
                .build()

        /** Nur für automatische Tests: z. B. Datenbankarbeit auf dem Test-Thread statt im Hintergrund. */
        @Volatile var testAnpassung: ((RoomDatabase.Builder<AppDatabase>) -> Unit)? = null

        /** 0.12 → 0.13: Foto zur Mahlzeit. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `diary_entry` ADD COLUMN `foto` TEXT")
            }
        }

        /** 0.11 → 0.12: Vitamine, Mineralstoffe und Cholesterin an Lebensmitteln und Einträgen. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `food` ADD COLUMN `mikros` TEXT")
                db.execSQL("ALTER TABLE `diary_entry` ADD COLUMN `mikros` TEXT")
            }
        }

        /** 0.5 → 0.6: Rezept-Zutaten. SQL wie in schemas/…/4.json. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `recipe_ingredient` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `rezeptId` TEXT NOT NULL, `position` INTEGER NOT NULL, `foodId` TEXT NOT NULL, `label` TEXT NOT NULL, `servingLabel` TEXT, `quantity` REAL NOT NULL, `gramm` REAL NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_ingredient_rezeptId` ON `recipe_ingredient` (`rezeptId`)")
            }
        }

        /** 0.4 → 0.5: Tagesziel selbst festgelegt. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `zielManuell` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 0.3 → 0.4: Favoriten und Wasser. SQL wie in schemas/…/2.json, bestehende Daten bleiben. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `favorite` (`foodId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`foodId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `water_entry` (`id` TEXT NOT NULL, `day` TEXT NOT NULL, `ml` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_entry_day` ON `water_entry` (`day`)")
            }
        }
    }
}
