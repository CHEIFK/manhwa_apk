package com.example.ui.screens.reader

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MangaChapter
import com.example.data.model.MangaPage
import com.example.data.preferences.UserPreferences
import com.example.data.storage.StorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

  private val context: Context get() = getApplication()
  private val prefs = UserPreferences.getInstance(context)

  private val _seriesTitle = MutableStateFlow("")
  val seriesTitle: StateFlow<String> = _seriesTitle.asStateFlow()

  private val _currentChapter = MutableStateFlow<MangaChapter?>(null)
  val currentChapter: StateFlow<MangaChapter?> = _currentChapter.asStateFlow()

  private val _allChapters = MutableStateFlow<List<MangaChapter>>(emptyList())
  val allChapters: StateFlow<List<MangaChapter>> = _allChapters.asStateFlow()

  private val _pages = MutableStateFlow<List<MangaPage>>(emptyList())
  val pages: StateFlow<List<MangaPage>> = _pages.asStateFlow()

  private val _isLoading = MutableStateFlow(true)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _zoomScale = MutableStateFlow(prefs.readerZoom.value)
  val zoomScale: StateFlow<Float> = _zoomScale.asStateFlow()

  private val _isControlsVisible = MutableStateFlow(true)
  val isControlsVisible: StateFlow<Boolean> = _isControlsVisible.asStateFlow()

  private var seriesUriStr: String? = null

  fun loadChapter(seriesUri: String, chapterUri: String, title: String, chapterName: String) {
    seriesUriStr = seriesUri
    _seriesTitle.value = title
    _isLoading.value = true

    viewModelScope.launch {
      try {
        val sUri = Uri.parse(seriesUri)
        val chapters = StorageManager.getChaptersForSeries(context, sUri)
        _allChapters.value = chapters

        val targetChapter = chapters.find { it.id == chapterUri || it.name == chapterName }
          ?: chapters.firstOrNull()

        _currentChapter.value = targetChapter
        _pages.value = targetChapter?.pages ?: emptyList()

        if (targetChapter != null) {
          prefs.setLastReadChapter(seriesUri, targetChapter.id)
        }
      } catch (e: Exception) {
        e.printStackTrace()
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun toggleControls() {
    _isControlsVisible.value = !_isControlsVisible.value
  }

  fun setZoom(scale: Float) {
    val clamped = scale.coerceIn(0.5f, 3.0f)
    _zoomScale.value = clamped
    prefs.setReaderZoom(clamped)
  }

  fun goToPreviousChapter() {
    val current = _currentChapter.value ?: return
    val currentIndex = _allChapters.value.indexOfFirst { it.id == current.id }
    if (currentIndex > 0) {
      val prev = _allChapters.value[currentIndex - 1]
      switchChapter(prev)
    }
  }

  fun goToNextChapter() {
    val current = _currentChapter.value ?: return
    val currentIndex = _allChapters.value.indexOfFirst { it.id == current.id }
    if (currentIndex >= 0 && currentIndex < _allChapters.value.size - 1) {
      val next = _allChapters.value[currentIndex + 1]
      switchChapter(next)
    }
  }

  fun switchChapter(chapter: MangaChapter) {
    _currentChapter.value = chapter
    _pages.value = chapter.pages
    seriesUriStr?.let { prefs.setLastReadChapter(it, chapter.id) }
  }
}
