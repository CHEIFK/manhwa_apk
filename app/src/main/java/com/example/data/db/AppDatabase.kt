package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    MangaSeriesEntity::class,
    MangaChapterEntity::class
  ],
  version = 1,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

  abstract fun mangaDao(): MangaDao

  companion object {
    private const val DATABASE_NAME = "manwa_manager_cache.db"

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          DATABASE_NAME
        )
        .fallbackToDestructiveMigration()
        .build()
        .also { INSTANCE = it }
      }
    }
  }
}
