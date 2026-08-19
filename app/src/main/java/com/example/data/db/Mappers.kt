package com.example.data.db

import android.net.Uri
import com.example.data.model.MangaChapter
import com.example.data.model.MangaSeries

fun MangaSeriesEntity.toDomain(): MangaSeries {
  return MangaSeries(
    id = uri,
    title = title,
    folderUri = Uri.parse(uri),
    coverUri = coverUri?.let { Uri.parse(it) },
    chapterCount = chapterCount,
    totalPages = totalPages,
    lastModified = lastModified,
    statusTag = statusTag
  )
}

fun MangaSeries.toEntity(): MangaSeriesEntity {
  return MangaSeriesEntity(
    uri = folderUri.toString(),
    title = title,
    coverUri = coverUri?.toString(),
    chapterCount = chapterCount,
    totalPages = totalPages,
    lastModified = lastModified,
    statusTag = statusTag
  )
}

fun MangaChapterEntity.toDomain(): MangaChapter {
  return MangaChapter(
    id = uri,
    name = name,
    chapterNumber = chapterNumber,
    folderUri = Uri.parse(uri),
    pageCount = pageCount,
    pages = emptyList()
  )
}

fun MangaChapter.toEntity(seriesUri: String, lastModified: Long = 0L): MangaChapterEntity {
  return MangaChapterEntity(
    uri = folderUri.toString(),
    seriesUri = seriesUri,
    name = name,
    chapterNumber = chapterNumber,
    pageCount = pageCount,
    lastModified = lastModified
  )
}
