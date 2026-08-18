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

  init {
    viewModelScope.launch {
      prefs.libraryUri.collect { uriStr ->
        if (uriStr != null) {
          refreshLibrary()
        } else {
          _allSeries.value = emptyList()
        }
      }
    }
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun setLibraryFolderUri(uri: Uri) {
    prefs.setLibraryUri(uri.toString())
    refreshLibrary()
  }

  fun refreshLibrary() {
    val uriStr = prefs.libraryUri.value ?: return
    viewModelScope.launch {
      _isScanning.value = true
      try {
        val uri = Uri.parse(uriStr)
        val list = StorageManager.scanLibrary(context, uri)
        _allSeries.value = list
      } catch (e: Exception) {
        e.printStackTrace()
      } finally {
        _isScanning.value = false
      }
    }
  }

  fun selectSeries(series: MangaSeries?) {
    _selectedSeries.value = series
    if (series != null) {
      viewModelScope.launch {
        _isLoadingChapters.value = true
        try {
          val chapters = StorageManager.getChaptersForSeries(context, series.folderUri)
          _seriesChapters.value = chapters
        } catch (e: Exception) {
          e.printStackTrace()
          _seriesChapters.value = emptyList()
        } finally {
          _isLoadingChapters.value = false
        }
      }
    } else {
      _seriesChapters.value = emptyList()
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

        withContext(Dispatchers.Main) {
          refreshLibrary()
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
