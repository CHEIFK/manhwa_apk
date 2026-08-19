package com.example.ui.screens.library

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.toDomain
import com.example.data.downloader.DownloadManager
import com.example.data.model.MangaChapter
import com.example.data.model.MangaSeries
import com.example.data.preferences.UserPreferences
import com.example.data.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

  private val context: Context get() = getApplication()
  private val prefs = UserPreferences.getInstance(context)
  private val downloadManager = DownloadManager.getInstance(context)

  val libraryUri: StateFlow<String?> = prefs.libraryUri

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _allSeries = MutableStateFlow<List<MangaSeries>>(emptyList())

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  val filteredSeries: StateFlow<List<MangaSeries>> =
    combine(_allSeries, _searchQuery) { seriesList, query ->
      if (query.isBlank()) {
        seriesList
      } else {
        seriesList.filter { it.title.contains(query, ignoreCase = true) }
      }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _selectedSeries = MutableStateFlow<MangaSeries?>(null)
  val selectedSeries: StateFlow<MangaSeries?> = _selectedSeries.asStateFlow()

  private val _seriesChapters = MutableStateFlow<List<MangaChapter>>(emptyList())
  val seriesChapters: StateFlow<List<MangaChapter>> = _seriesChapters.asStateFlow()

  private val _isLoadingChapters = MutableStateFlow(false)
  val isLoadingChapters: StateFlow<Boolean> = _isLoadingChapters.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private var refreshJob: kotlinx.coroutines.Job? = null

  init {
    viewModelScope.launch {
      prefs.libraryUri.collect { uriStr ->
        if (uriStr != null) {
          android.util.Log.d("ManwaManager", "Library URI updated from prefs: $uriStr")
          // 1. Immediately load cached data from Room for instant UI display
          loadCachedLibrary()
          // 2. Perform lightweight background synchronization with SAF
          refreshLibrary()
        } else {
          _allSeries.value = emptyList()
        }
      }
    }

    // Automatically synchronize library immediately when downloads complete
    viewModelScope.launch {
      downloadManager.downloadCompletedEvents.collect { seriesTitle ->
        android.util.Log.d("ManwaManager", "Received downloadCompletedEvent for: $seriesTitle")
        refreshLibraryIncremental(seriesTitle)
      }
    }
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun setLibraryFolderUri(uri: Uri) {
    android.util.Log.d("ManwaManager", "User selected library folder: $uri")
    prefs.setLibraryUri(uri.toString())
  }

  fun clearError() {
    _errorMessage.value = null
  }

  /**
   * Loads series instantly from the local Room database cache without blocking or calling SAF.
   */
  private fun loadCachedLibrary() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val cached = StorageManager.getCachedSeriesList(context)
        if (cached.isNotEmpty()) {
          _allSeries.value = cached
          android.util.Log.d("ManwaManager", "Loaded ${cached.size} series immediately from local cache")
        }
      } catch (e: Exception) {
        android.util.Log.e("ManwaManager", "Failed to load cached library", e)
      }
    }
  }

  /**
   * Performs lightweight background sync: compares timestamps and updates only changed/new/deleted entries.
   */
  fun refreshLibrary() {
    val uriStr = prefs.libraryUri.value
    if (uriStr == null) {
      android.util.Log.w("ManwaManager", "refreshLibrary called with null libraryUri")
      _isScanning.value = false
      return
    }

    refreshJob?.cancel()
    refreshJob = viewModelScope.launch(Dispatchers.IO) {
      _isScanning.value = true
      val startMs = System.currentTimeMillis()
      android.util.Log.d("ManwaManager", "Starting lightweight background library sync for URI: $uriStr")
      try {
        val uri = Uri.parse(uriStr)
        val list = StorageManager.syncLibrary(context, uri)
        _allSeries.value = list
        android.util.Log.d("ManwaManager", "Library sync successful in ${System.currentTimeMillis() - startMs}ms, loaded ${list.size} series")

        val activeSelected = _selectedSeries.value
        if (activeSelected != null) {
          val matching = list.find { it.id == activeSelected.id || it.title.equals(activeSelected.title, ignoreCase = true) }
          if (matching != null) {
            _selectedSeries.value = matching
            val chapters = StorageManager.getChaptersForSeries(context, matching.folderUri)
            _seriesChapters.value = chapters
          }
        }
      } catch (t: Throwable) {
        if (t !is kotlinx.coroutines.CancellationException) {
          android.util.Log.e("ManwaManager", "Library sync failed with exception: ${t.message}", t)
        }
      } finally {
        _isScanning.value = false
      }
    }
  }

  /**
   * Fast incremental update for a single downloaded series without full disk rescan.
   */
  fun refreshLibraryIncremental(seriesTitle: String) {
    val uriStr = prefs.libraryUri.value ?: return

    viewModelScope.launch(Dispatchers.IO) {
      val startMs = System.currentTimeMillis()
      android.util.Log.d("ManwaManager", "Starting incremental refresh for '$seriesTitle'")
      try {
        val db = AppDatabase.getInstance(context)
        val dao = db.mangaDao()

        // 1. Check if series entity is already in Room DB
        val allSeriesFromDb = dao.getAllSeries().map { it.toDomain() }
        var updatedSeries = allSeriesFromDb.find { it.title.equals(seriesTitle, ignoreCase = true) }

        if (updatedSeries == null) {
          // If not in DB yet, locate and scan this single series folder
          val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
          if (rootDoc != null) {
            val seriesDoc = StorageManager.findSeriesDirectory(rootDoc, seriesTitle)
            if (seriesDoc != null) {
              updatedSeries = StorageManager.scanSingleSeries(context, seriesDoc)
            }
          }
        }

        if (updatedSeries != null) {
          val currentList = _allSeries.value.toMutableList()
          val index = currentList.indexOfFirst {
            it.id == updatedSeries.id ||
              it.title.equals(updatedSeries.title, ignoreCase = true) ||
              it.title.equals(seriesTitle, ignoreCase = true)
          }
          if (index >= 0) {
            currentList[index] = updatedSeries
          } else {
            currentList.add(updatedSeries)
          }
          currentList.sortBy { it.title.lowercase() }
          _allSeries.value = currentList
          android.util.Log.d("ManwaManager", "Incremental refresh for '$seriesTitle' completed in ${System.currentTimeMillis() - startMs}ms (total series: ${currentList.size})")

          // Synchronize opened series & chapter list immediately if user has it open
          val activeSelected = _selectedSeries.value
          if (activeSelected != null && (
              activeSelected.id == updatedSeries.id ||
              activeSelected.title.equals(updatedSeries.title, ignoreCase = true) ||
              activeSelected.title.equals(seriesTitle, ignoreCase = true)
            )
          ) {
            _selectedSeries.value = updatedSeries
            val updatedChapters = StorageManager.getChaptersForSeries(context, updatedSeries.folderUri)
            _seriesChapters.value = updatedChapters
            android.util.Log.d("ManwaManager", "Synchronized active selected series chapters: ${updatedChapters.size} chapters")
          }
        }
      } catch (t: Throwable) {
        if (t !is kotlinx.coroutines.CancellationException) {
          android.util.Log.e("ManwaManager", "Incremental refresh failed for '$seriesTitle'", t)
        }
      }
    }
  }

  /**
   * Instant opening of series & chapter metadata from Room cache.
   * Eliminates UI freeze by loading indexed chapters instantly (< 1ms) and delegating sync to background.
   */
  fun selectSeries(series: MangaSeries?) {
    _selectedSeries.value = series
    if (series == null) {
      _seriesChapters.value = emptyList()
      _isLoadingChapters.value = false
      return
    }

    val seriesUriStr = series.folderUri.toString()
    viewModelScope.launch(Dispatchers.IO) {
      val db = AppDatabase.getInstance(context)
      val dao = db.mangaDao()

      // 1. Fast cache query (0 SAF calls, instant UI display)
      val cachedChapters = try {
        dao.getChaptersForSeries(seriesUriStr)
      } catch (e: Exception) {
        emptyList()
      }

      if (cachedChapters.isNotEmpty()) {
        val domainChapters = cachedChapters.map { it.toDomain() }
        withContext(Dispatchers.Main) {
          _seriesChapters.value = domainChapters
          _isLoadingChapters.value = false
        }
      } else {
        withContext(Dispatchers.Main) {
          _isLoadingChapters.value = true
        }
        val chapters = StorageManager.getChaptersForSeriesFast(context, series.folderUri, series.title)
        withContext(Dispatchers.Main) {
          _seriesChapters.value = chapters
          _isLoadingChapters.value = false
        }
      }

      // 2. Non-blocking background sync for updated page counts
      StorageManager.syncSingleSeriesInBackground(context, series.folderUri, series.title) { updatedChapters, updatedSeries ->
        if (_selectedSeries.value?.id == series.id) {
          viewModelScope.launch(Dispatchers.Main) {
            if (updatedChapters.isNotEmpty()) {
              _seriesChapters.value = updatedChapters
            }
            if (updatedSeries != null) {
              _selectedSeries.value = updatedSeries
            }
          }
        }
      }
    }
  }

  /**
   * Deletes the selected series and all its chapters from SAF storage and local cache immediately.
   */
  fun deleteSeries(series: MangaSeries, onComplete: ((Boolean) -> Unit)? = null) {
    viewModelScope.launch(Dispatchers.IO) {
      android.util.Log.d("ManwaManager", "Deleting series: ${series.title} (${series.folderUri})")
      try {
        // 1. Immediately update UI state
        withContext(Dispatchers.Main) {
          if (_selectedSeries.value?.id == series.id) {
            _selectedSeries.value = null
            _seriesChapters.value = emptyList()
          }
          _allSeries.value = _allSeries.value.filter { it.id != series.id }
        }

        // 2. Delete from SAF storage & Room database
        val deleted = StorageManager.deleteSeriesFolder(context, series.folderUri)
        android.util.Log.d("ManwaManager", "deleteSeries result for ${series.title}: $deleted")

        withContext(Dispatchers.Main) {
          if (!deleted) {
            _errorMessage.value = "Failed to completely delete folder for ${series.title}. Please check storage permissions."
          }
          onComplete?.invoke(deleted)
        }
      } catch (e: Exception) {
        android.util.Log.e("ManwaManager", "Error deleting series ${series.title}", e)
        withContext(Dispatchers.Main) {
          _errorMessage.value = "Error deleting ${series.title}: ${e.message}"
          onComplete?.invoke(false)
        }
      }
    }
  }

  /**
   * Generates a starter demo series with rendered artwork pages
   * directly inside the user's selected SAF library folder for instant offline reading verification.
   */
  fun createSampleDemoSeries() {
    val uriStr = prefs.libraryUri.value ?: return
    viewModelScope.launch(Dispatchers.IO) {
      _isScanning.value = true
      try {
        val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(uriStr)) ?: return@launch
        val demoSeriesDoc = StorageManager.getOrCreateSubdirectory(rootDoc, "Omniscient Reader (Demo)")
          ?: return@launch

        // Create Chapter 01
        val ch1Doc = StorageManager.getOrCreateSubdirectory(demoSeriesDoc, "Chapter 01")
        if (ch1Doc != null && ch1Doc.listFiles().isEmpty()) {
          createDemoPages(ch1Doc, "Omniscient Reader", 1, 5)
        }

        // Create Chapter 02
        val ch2Doc = StorageManager.getOrCreateSubdirectory(demoSeriesDoc, "Chapter 02")
        if (ch2Doc != null && ch2Doc.listFiles().isEmpty()) {
          createDemoPages(ch2Doc, "Omniscient Reader", 2, 5)
        }

        // Create second demo series "Solo Leveling (Demo)"
        val demoSeries2Doc = StorageManager.getOrCreateSubdirectory(rootDoc, "Solo Leveling (Demo)")
        if (demoSeries2Doc != null) {
          val chA = StorageManager.getOrCreateSubdirectory(demoSeries2Doc, "Chapter 01")
          if (chA != null && chA.listFiles().isEmpty()) {
            createDemoPages(chA, "Solo Leveling", 1, 4)
          }
        }

        // Rescan and update database
        StorageManager.syncLibrary(context, Uri.parse(uriStr))
        val updatedList = StorageManager.getCachedSeriesList(context)

        withContext(Dispatchers.Main) {
          _allSeries.value = updatedList
        }
      } catch (e: Exception) {
        e.printStackTrace()
      } finally {
        _isScanning.value = false
      }
    }
  }

  private fun createDemoPages(chapterDoc: DocumentFile, title: String, chNum: Int, pageCount: Int) {
    val width = 800
    val height = 1200

    for (p in 1..pageCount) {
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)

      // Background gradient color
      val bgPaint = Paint().apply {
        color = if (p == 1) Color.rgb(20, 24, 38) else Color.rgb(15, 18, 26)
      }
      canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

      // Artwork box
      val boxPaint = Paint().apply {
        color = Color.rgb(35, 42, 60)
        style = Paint.Style.FILL
      }
      canvas.drawRoundRect(60f, 100f, 740f, 900f, 24f, 24f, boxPaint)

      // Border
      val borderPaint = Paint().apply {
        color = Color.rgb(80, 110, 160)
        style = Paint.Style.STROKE
        strokeWidth = 4f
      }
      canvas.drawRoundRect(60f, 100f, 740f, 900f, 24f, 24f, borderPaint)

      // Text Title
      val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 46f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
      }
      canvas.drawText(title, width / 2f, 380f, textPaint)

      // Chapter & Page text
      val subTextPaint = Paint().apply {
        color = Color.rgb(180, 205, 240)
        textSize = 36f
        textAlign = Paint.Align.CENTER
      }
      canvas.drawText("Chapter $chNum • Page $p of $pageCount", width / 2f, 460f, subTextPaint)

      val tipPaint = Paint().apply {
        color = Color.rgb(130, 140, 155)
        textSize = 28f
        textAlign = Paint.Align.CENTER
      }
      canvas.drawText("Manwa Manager Offline Reader", width / 2f, 780f, tipPaint)
      canvas.drawText("Scroll vertically to read", width / 2f, 830f, tipPaint)

      // Save file as 00X.jpg
      val fileName = String.format("%03d.jpg", p)
      val fileDoc = StorageManager.getOrCreateFile(chapterDoc, "image/jpeg", fileName)
      if (fileDoc != null) {
        context.contentResolver.openOutputStream(fileDoc.uri, "w")?.use { out ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
          out.flush()
        }
      }
      bitmap.recycle()
    }
  }
}
