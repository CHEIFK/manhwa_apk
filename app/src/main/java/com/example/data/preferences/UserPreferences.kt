package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferences(context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences("manwa_manager_prefs", Context.MODE_PRIVATE)

  private val _libraryUri = MutableStateFlow<String?>(prefs.getString(KEY_LIBRARY_URI, null))
  val libraryUri: StateFlow<String?> = _libraryUri.asStateFlow()

  private val _downloadWorkers = MutableStateFlow(prefs.getInt(KEY_WORKERS, 8))
  val downloadWorkers: StateFlow<Int> = _downloadWorkers.asStateFlow()

  private val _readerZoom = MutableStateFlow(prefs.getFloat(KEY_READER_ZOOM, 1.0f))
  val readerZoom: StateFlow<Float> = _readerZoom.asStateFlow()

  private val _validateImages = MutableStateFlow(prefs.getBoolean(KEY_VALIDATE_IMAGES, true))
  val validateImages: StateFlow<Boolean> = _validateImages.asStateFlow()

  fun setLibraryUri(uri: String?) {
    prefs.edit().putString(KEY_LIBRARY_URI, uri).apply()
    _libraryUri.value = uri
  }

  fun setDownloadWorkers(workers: Int) {
    val clamped = workers.coerceIn(2, 32)
    prefs.edit().putInt(KEY_WORKERS, clamped).apply()
    _downloadWorkers.value = clamped
  }

  fun setReaderZoom(zoom: Float) {
    val clamped = zoom.coerceIn(0.5f, 3.0f)
    prefs.edit().putFloat(KEY_READER_ZOOM, clamped).apply()
    _readerZoom.value = clamped
  }

  fun setValidateImages(validate: Boolean) {
    prefs.edit().putBoolean(KEY_VALIDATE_IMAGES, validate).apply()
    _validateImages.value = validate
  }

  fun getLastReadChapter(seriesId: String): String? {
    return prefs.getString("last_ch_$seriesId", null)
  }

  fun setLastReadChapter(seriesId: String, chapterId: String) {
    prefs.edit().putString("last_ch_$seriesId", chapterId).apply()
  }

  companion object {
    private const val KEY_LIBRARY_URI = "key_library_uri"
    private const val KEY_WORKERS = "key_download_workers"
    private const val KEY_READER_ZOOM = "key_reader_zoom"
    private const val KEY_VALIDATE_IMAGES = "key_validate_images"

    @Volatile
    private var INSTANCE: UserPreferences? = null

    fun getInstance(context: Context): UserPreferences {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: UserPreferences(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}
