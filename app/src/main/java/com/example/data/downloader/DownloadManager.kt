package com.example.data.downloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import com.example.data.db.AppDatabase
import com.example.data.db.MangaChapterEntity
import com.example.data.db.MangaSeriesEntity
import com.example.data.model.ChapterDownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.data.preferences.UserPreferences
import com.example.data.storage.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

class DownloadManager private constructor(private val context: Context) {

  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private var activeJob: Job? = null

  private val _activeTask = MutableStateFlow<DownloadTask?>(null)
  val activeTask: StateFlow<DownloadTask?> = _activeTask.asStateFlow()

  private val _downloadHistory = MutableStateFlow<List<DownloadTask>>(emptyList())
  val downloadHistory: StateFlow<List<DownloadTask>> = _downloadHistory.asStateFlow()

  private val _downloadCompletedEvents = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 64)
  val downloadCompletedEvents: SharedFlow<String> = _downloadCompletedEvents.asSharedFlow()

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .build()

  fun startDownload(
    seriesTitle: String,
    seriesUrl: String,
    selectedChapters: List<ChapterDownloadItem>
  ) {
    if (selectedChapters.isEmpty()) return

    val taskId = UUID.randomUUID().toString()
    val task = DownloadTask(
      id = taskId,
      seriesTitle = seriesTitle,
      seriesUrl = seriesUrl,
      chapters = selectedChapters.map { it.copy(status = DownloadStatus.QUEUED) },
      status = DownloadStatus.DOWNLOADING
    )

    _activeTask.value = task

    // Start Foreground Service for background persistence
    val serviceIntent = Intent(context, DownloadService::class.java).apply {
      action = DownloadService.ACTION_START
      putExtra(DownloadService.EXTRA_SERIES_TITLE, seriesTitle)
    }
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
      } else {
        context.startService(serviceIntent)
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }

    activeJob?.cancel()
    activeJob = scope.launch {
      runDownloadTask(task)
    }
  }

  fun cancelCurrentDownload() {
    activeJob?.cancel()
    _activeTask.value?.let { current ->
      current.status = DownloadStatus.CANCELLED
      _downloadHistory.value = listOf(current) + _downloadHistory.value
      _activeTask.value = null
    }
    stopService()
  }

  private fun stopService() {
    try {
      val serviceIntent = Intent(context, DownloadService::class.java).apply {
        action = DownloadService.ACTION_STOP
      }
      context.startService(serviceIntent)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private suspend fun runDownloadTask(task: DownloadTask) = withContext(Dispatchers.IO) {
    val prefs = UserPreferences.getInstance(context)
    val rootTreeUriString = prefs.libraryUri.value
    if (rootTreeUriString == null) {
      task.status = DownloadStatus.FAILED
      task.errorMessage = "No library storage folder selected. Please set library folder in Settings."
      updateTask(task)
      stopService()
      return@withContext
    }

    val rootTreeUri = Uri.parse(rootTreeUriString)
    val rootDoc = DocumentFile.fromTreeUri(context, rootTreeUri)
    if (rootDoc == null || !rootDoc.exists()) {
      task.status = DownloadStatus.FAILED
      task.errorMessage = "Cannot access selected library folder. Permission may have been revoked."
      updateTask(task)
      stopService()
      return@withContext
    }

    val workers = prefs.downloadWorkers.value.coerceIn(2, 32)
    val semaphore = Semaphore(workers)
    val validateImages = prefs.validateImages.value

    // 1. Get or create Series Directory in user's library
    val seriesDoc = StorageManager.getOrCreateSubdirectory(rootDoc, task.seriesTitle)
    if (seriesDoc == null) {
      task.status = DownloadStatus.FAILED
      task.errorMessage = "Failed to create series directory: ${task.seriesTitle}"
      updateTask(task)
      stopService()
      return@withContext
    }

    var totalChaptersCompleted = 0

    // Cache chapter directories in series once to avoid repeated IPC queries
    val existingChapters = seriesDoc.listFiles().filter { it.isDirectory }
    val chapterMap = existingChapters.associateBy { it.name?.lowercase() ?: "" }.toMutableMap()

    for ((index, chapter) in task.chapters.withIndex()) {
      task.currentChapterIndex = index
      chapter.status = DownloadStatus.DOWNLOADING
      updateTask(task)

      try {
        // 2. Get or create Chapter Directory fast from local cache
        val cleanChapterName = StorageManager.sanitizeFileName(chapter.name)
        var chapterDoc = chapterMap[cleanChapterName.lowercase()]
        if (chapterDoc == null || !chapterDoc.exists()) {
          chapterDoc = seriesDoc.createDirectory(cleanChapterName)
          if (chapterDoc != null) {
            chapterMap[cleanChapterName.lowercase()] = chapterDoc
          }
        }

        if (chapterDoc == null) {
          chapter.status = DownloadStatus.FAILED
          chapter.errorMessage = "Could not create folder for chapter"
          continue
        }

        // 3. Extract Images from Chapter URL
        val imageUrls = MangaScraper.fetchChapterImageUrls(chapter.url)
        if (imageUrls.isEmpty()) {
          chapter.status = DownloadStatus.FAILED
          chapter.errorMessage = "No reader images found at URL"
          continue
        }

        chapter.totalPages = imageUrls.size
        chapter.downloadedPages = 0
        task.totalImagesEstimated += imageUrls.size
        updateTask(task)

        // Cache existing files in chapter folder once
        val existingFiles = chapterDoc.listFiles().filter { StorageManager.isImageFile(it) }
        val existingFilesMap = existingFiles.associateBy { it.name?.substringBeforeLast('.') ?: "" }.toMutableMap()

        // 4. Download images concurrently with worker concurrency limit & fast resume check
        val downloadJobs = imageUrls.mapIndexed { pageIndex, imgUrl ->
          async(Dispatchers.IO) {
            semaphore.withPermit {
              val pageNumberStr = String.format("%03d", pageIndex + 1)
              val existingFile = existingFilesMap[pageNumberStr]

              // Fast in-memory resume check: if file exists and valid image, skip
              if (existingFile != null && existingFile.length() > 512) {
                val isValid = if (validateImages) {
                  StorageManager.isValidImage(context, existingFile.uri)
                } else true

                if (isValid) {
                  synchronized(task) {
                    chapter.downloadedPages++
                    task.totalImagesDownloaded++
                  }
                  updateTask(task)
                  return@withPermit true
                } else {
                  // Corrupt file, delete and re-download
                  existingFile.delete()
                  synchronized(existingFilesMap) {
                    existingFilesMap.remove(pageNumberStr)
                  }
                }
              }

              // Download image directly without O(N) findFile traversals
              val success = downloadSingleImage(
                imgUrl = imgUrl,
                refererUrl = chapter.url,
                targetFolder = chapterDoc,
                pageNumberPrefix = pageNumberStr,
                existingTargetFile = existingFilesMap[pageNumberStr],
                validate = validateImages
              )

              if (success) {
                synchronized(task) {
                  chapter.downloadedPages++
                  task.totalImagesDownloaded++
                }
                updateTask(task)
              }
              success
            }
          }
        }

        val results = downloadJobs.awaitAll()
        val allSucceeded = results.all { it }

        if (allSucceeded) {
          chapter.status = DownloadStatus.COMPLETED
          totalChaptersCompleted++
          task.completedChapters = totalChaptersCompleted

          // Update chapter and series metadata directly in local Room cache immediately
          try {
            val db = AppDatabase.getInstance(context)
            val dao = db.mangaDao()
            val chLastModified = chapterDoc.lastModified()
            val chEntity = MangaChapterEntity(
              uri = chapterDoc.uri.toString(),
              seriesUri = seriesDoc.uri.toString(),
              name = chapter.name,
              chapterNumber = StorageManager.extractChapterNumber(chapter.name),
              pageCount = chapter.downloadedPages,
              lastModified = chLastModified
            )
            dao.insertChapter(chEntity)

            val currentChapters = dao.getChaptersForSeries(seriesDoc.uri.toString())
            val totalPagesSum = currentChapters.sumOf { it.pageCount }
            val existingSeries = dao.getSeriesByUri(seriesDoc.uri.toString())
            val firstCover = existingSeries?.coverUri ?: run {
              val firstChDoc = DocumentFile.fromTreeUri(context, chapterDoc.uri) ?: chapterDoc
              firstChDoc.listFiles().firstOrNull { StorageManager.isImageFile(it) }?.uri?.toString()
            }

            val seriesEntity = MangaSeriesEntity(
              uri = seriesDoc.uri.toString(),
              title = task.seriesTitle,
              coverUri = firstCover,
              chapterCount = currentChapters.size,
              totalPages = totalPagesSum,
              lastModified = seriesDoc.lastModified(),
              statusTag = "LOCAL"
            )
            dao.insertSeries(seriesEntity)
          } catch (e: Exception) {
            android.util.Log.e("ManwaManager", "DownloadManager: Failed to update Room cache on chapter completion", e)
          }

          // Invalidate series cache and emit completion event per chapter
          StorageManager.invalidateSeriesCache(seriesDoc.uri.toString())
          android.util.Log.d("ManwaManager", "DownloadManager: Chapter '${chapter.name}' completed for '${task.seriesTitle}'. Emitting event.")
          _downloadCompletedEvents.tryEmit(task.seriesTitle)
        } else {
          chapter.status = DownloadStatus.FAILED
          chapter.errorMessage = "Some pages failed to download"
          android.util.Log.w("ManwaManager", "DownloadManager: Chapter '${chapter.name}' failed to download completely")
        }

      } catch (e: Exception) {
        chapter.status = DownloadStatus.FAILED
        chapter.errorMessage = e.message ?: "Unknown error"
        android.util.Log.e("ManwaManager", "DownloadManager: Exception during chapter download: ${e.message}", e)
      }

      updateTask(task)
    }

    task.status = if (totalChaptersCompleted == task.chapters.size) {
      DownloadStatus.COMPLETED
    } else if (totalChaptersCompleted > 0) {
      DownloadStatus.COMPLETED
    } else {
      DownloadStatus.FAILED
    }

    // Invalidate series cache and emit final completion event for the series
    StorageManager.invalidateSeriesCache(seriesDoc.uri.toString())
    android.util.Log.d("ManwaManager", "DownloadManager: Series '${task.seriesTitle}' finished with status ${task.status}. Emitting final event.")
    _downloadCompletedEvents.tryEmit(task.seriesTitle)

    _downloadHistory.value = listOf(task) + _downloadHistory.value
    _activeTask.value = null
    stopService()
  }

  private suspend fun downloadSingleImage(
    imgUrl: String,
    refererUrl: String,
    targetFolder: DocumentFile,
    pageNumberPrefix: String,
    existingTargetFile: DocumentFile?,
    validate: Boolean
  ): Boolean = withContext(Dispatchers.IO) {
    var attempts = 0
    val maxAttempts = 3

    while (attempts < maxAttempts) {
      attempts++
      try {
        val request = Request.Builder()
          .url(imgUrl)
          .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0")
          .header("Referer", refererUrl)
          .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
          .build()

        val response = httpClient.newCall(request).execute()
        response.use { res ->
          if (!res.isSuccessful) {
            return@use
          }

          val mimeType = res.header("Content-Type") ?: "image/jpeg"
          val ext = when {
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            mimeType.contains("avif") -> "avif"
            else -> "jpg"
          }
          val actualFileName = "$pageNumberPrefix.$ext"

          val targetFile = existingTargetFile ?: targetFolder.createFile(mimeType, actualFileName)
            ?: return@withContext false

          res.body?.byteStream()?.use { input ->
            context.contentResolver.openOutputStream(targetFile.uri, "w")?.use { output ->
              input.copyTo(output)
              output.flush()
            }
          }

          if (validate) {
            val valid = StorageManager.isValidImage(context, targetFile.uri)
            if (!valid) {
              targetFile.delete()
              return@use
            }
          }

          return@withContext true
        }
      } catch (e: Exception) {
        if (attempts >= maxAttempts) {
          return@withContext false
        }
      }
    }
    false
  }

  private fun updateTask(task: DownloadTask) {
    synchronized(task) {
      _activeTask.value = task.copy(
        chapters = task.chapters.map { it.copy() }
      )
    }
  }

  companion object {
    @Volatile
    private var INSTANCE: DownloadManager? = null

    fun getInstance(context: Context): DownloadManager {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: DownloadManager(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}
