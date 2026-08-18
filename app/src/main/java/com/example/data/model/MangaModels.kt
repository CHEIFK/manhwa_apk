package com.example.data.model

import android.net.Uri

data class MangaSeries(
  val id: String,
  val title: String,
  val folderUri: Uri,
  val coverUri: Uri? = null,
  val chapterCount: Int = 0,
  val totalPages: Int = 0,
  val lastModified: Long = 0L,
  val statusTag: String = "LOCAL"
)

data class MangaChapter(
  val id: String,
  val name: String,
  val chapterNumber: Double,
  val folderUri: Uri,
  val pageCount: Int,
  val pages: List<MangaPage> = emptyList()
)

data class MangaPage(
  val index: Int,
  val name: String,
  val uri: Uri,
  val sizeBytes: Long = 0L
)

enum class DownloadStatus {
  IDLE,
  QUEUED,
  DOWNLOADING,
  COMPLETED,
  FAILED,
  CANCELLED
}

data class ChapterDownloadItem(
  val name: String,
  val url: String,
  val chapterNumber: Double = 0.0,
  var status: DownloadStatus = DownloadStatus.QUEUED,
  var downloadedPages: Int = 0,
  var totalPages: Int = 0,
  var errorMessage: String? = null
)

data class DownloadTask(
  val id: String,
  val seriesTitle: String,
  val seriesUrl: String,
  val chapters: List<ChapterDownloadItem>,
  var status: DownloadStatus = DownloadStatus.QUEUED,
  var currentChapterIndex: Int = 0,
  var totalChapters: Int = chapters.size,
  var completedChapters: Int = 0,
  var totalImagesDownloaded: Int = 0,
  var totalImagesEstimated: Int = 0,
  var errorMessage: String? = null
)
