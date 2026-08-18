package com.example.ui.screens.downloader

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.downloader.DownloadManager
import com.example.data.downloader.MangaScraper
import com.example.data.downloader.ScrapedSeriesInfo
import com.example.data.model.ChapterDownloadItem
import com.example.data.model.DownloadTask
import com.example.data.preferences.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {

  private val context: Context get() = getApplication()
  private val downloadManager = DownloadManager.getInstance(context)
  private val prefs = UserPreferences.getInstance(context)

  val activeTask: StateFlow<DownloadTask?> = downloadManager.activeTask
  val downloadHistory: StateFlow<List<DownloadTask>> = downloadManager.downloadHistory
  val downloadWorkers: StateFlow<Int> = prefs.downloadWorkers

  private val _urlInput = MutableStateFlow("")
  val urlInput: StateFlow<String> = _urlInput.asStateFlow()

  private val _isFetching = MutableStateFlow(false)
  val isFetching: StateFlow<Boolean> = _isFetching.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _scrapedSeries = MutableStateFlow<ScrapedSeriesInfo?>(null)
  val scrapedSeries: StateFlow<ScrapedSeriesInfo?> = _scrapedSeries.asStateFlow()

  private val _selectedChapterUrls = MutableStateFlow<Set<String>>(emptySet())
  val selectedChapterUrls: StateFlow<Set<String>> = _selectedChapterUrls.asStateFlow()

  fun setUrlInput(url: String) {
    _urlInput.value = url
    _errorMessage.value = null
  }

  fun fetchChapters() {
    val url = _urlInput.value.trim()
    if (url.isBlank()) {
      _errorMessage.value = "Please enter a valid Manga/Manhwa series URL"
      return
    }

    _isFetching.value = true
    _errorMessage.value = null
    _scrapedSeries.value = null

    viewModelScope.launch {
      try {
        val result = MangaScraper.fetchSeries(url)
        if (result.chapters.isEmpty()) {
          _errorMessage.value = "No chapters found at the provided URL. Check that the URL is a series overview page."
        } else {
          _scrapedSeries.value = result
          // Select all by default
          _selectedChapterUrls.value = result.chapters.map { it.url }.toSet()
        }
      } catch (e: Exception) {
        _errorMessage.value = e.message ?: "Failed to connect to website. Check URL and internet."
      } finally {
        _isFetching.value = false
      }
    }
  }

  fun toggleChapterSelection(chapterUrl: String) {
    val current = _selectedChapterUrls.value.toMutableSet()
    if (current.contains(chapterUrl)) {
      current.remove(chapterUrl)
    } else {
      current.add(chapterUrl)
    }
    _selectedChapterUrls.value = current
  }

  fun selectAllChapters() {
    val all = _scrapedSeries.value?.chapters?.map { it.url }?.toSet() ?: emptySet()
    _selectedChapterUrls.value = all
  }

  fun deselectAllChapters() {
    _selectedChapterUrls.value = emptySet()
  }

  fun startDownloading() {
    val series = _scrapedSeries.value ?: return
    val selectedUrls = _selectedChapterUrls.value
    val chaptersToDownload = series.chapters.filter { selectedUrls.contains(it.url) }

    if (chaptersToDownload.isEmpty()) {
      _errorMessage.value = "Please select at least one chapter to download"
      return
    }

    if (prefs.libraryUri.value == null) {
      _errorMessage.value = "Please select a Library Storage folder in Settings first!"
      return
    }

    downloadManager.startDownload(
      seriesTitle = series.title,
      seriesUrl = _urlInput.value.trim(),
      selectedChapters = chaptersToDownload
    )
  }

  fun cancelDownload() {
    downloadManager.cancelCurrentDownload()
  }

  fun clearError() {
    _errorMessage.value = null
  }
}
