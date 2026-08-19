package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "series_cache"
)
data class MangaSeriesEntity(
  @PrimaryKey
  val uri: String,
  val title: String,
  val coverUri: String?,
  val chapterCount: Int,
  val totalPages: Int,
  val lastModified: Long,
  val statusTag: String = "LOCAL"
)

@Entity(
  tableName = "chapter_cache",
  foreignKeys = [
    ForeignKey(
      entity = MangaSeriesEntity::class,
      parentColumns = ["uri"],
      childColumns = ["seriesUri"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [
    Index(value = ["seriesUri"]),
    Index(value = ["chapterNumber"])
  ]
)
data class MangaChapterEntity(
  @PrimaryKey
  val uri: String,
  val seriesUri: String,
  val name: String,
  val chapterNumber: Double,
  val pageCount: Int,
  val lastModified: Long
)
