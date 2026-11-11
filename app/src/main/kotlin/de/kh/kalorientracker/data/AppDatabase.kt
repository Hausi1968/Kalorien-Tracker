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
    ],
    version = 2,
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

    companion object {
        fun baue(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "kalorien.db")
                .addMigrations(MIGRATION_1_2)
                .build()

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
