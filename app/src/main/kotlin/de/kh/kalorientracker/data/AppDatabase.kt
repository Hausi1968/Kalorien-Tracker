package de.kh.kalorientracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        FoodEntity::class,
        ServingEntity::class,
        DiaryEntryEntity::class,
        BodyEntryEntity::class,
        ActivityEntryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profile(): ProfileDao
    abstract fun food(): FoodDao
    abstract fun diary(): DiaryDao
    abstract fun body(): BodyDao
    abstract fun activity(): ActivityDao

    companion object {
        fun baue(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "kalorien.db").build()
    }
}
