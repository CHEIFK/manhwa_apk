package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {

  @Query("SELECT * FROM series_cache ORDER BY title COLLATE NOCASE ASC")
  fun getAllSeriesFlow(): Flow<List<MangaSeriesEntity>>

  @Query("SELECT * FROM series_cache ORDER BY title COLLATE NOCASE ASC")
  suspend fun getAllSeries(): List<MangaSeriesEntity>

  @Query("SELECT * FROM series_cache WHERE uri = :uri LIMIT 1")
  suspend fun getSeriesByUri(uri: String): MangaSeriesEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSeries(series: MangaSeriesEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSeriesList(seriesList: List<MangaSeriesEntity>)

  @Update
  suspend fun updateSeries(series: MangaSeriesEntity)

  @Query("DELETE FROM series_cache WHERE uri = :uri")
  suspend fun deleteSeriesByUri(uri: String)

  @Query("DELETE FROM series_cache WHERE uri NOT IN (:validUris)")
  suspend fun deleteSeriesNotIn(validUris: List<String>)

  @Query("DELETE FROM series_cache")
  suspend fun deleteAllSeries()

  @Query("SELECT * FROM chapter_cache WHERE seriesUri = :seriesUri ORDER BY chapterNumber ASC, name ASC")
  suspend fun getChaptersForSeries(seriesUri: String): List<MangaChapterEntity>

  @Query("SELECT * FROM chapter_cache WHERE seriesUri = :seriesUri ORDER BY chapterNumber ASC, name ASC")
  fun getChaptersForSeriesFlow(seriesUri: String): Flow<List<MangaChapterEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertChapters(chapters: List<MangaChapterEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertChapter(chapter: MangaChapterEntity)

  @Query("DELETE FROM chapter_cache WHERE seriesUri = :seriesUri")
  suspend fun deleteChaptersForSeries(seriesUri: String)

  @Query("DELETE FROM chapter_cache WHERE seriesUri = :seriesUri AND uri NOT IN (:validChapterUris)")
  suspend fun deleteChaptersNotIn(seriesUri: String, validChapterUris: List<String>)

  @Query("DELETE FROM chapter_cache WHERE uri = :uri")
  suspend fun deleteChapterByUri(uri: String)

  @Transaction
  suspend fun updateSeriesWithChapters(series: MangaSeriesEntity, chapters: List<MangaChapterEntity>) {
    insertSeries(series)
    if (chapters.isNotEmpty()) {
      deleteChaptersNotIn(series.uri, chapters.map { it.uri })
      insertChapters(chapters)
    } else {
      deleteChaptersForSeries(series.uri)
    }
  }

  @Transaction
  suspend fun clearAll() {
    deleteAllSeries()
  }
}
