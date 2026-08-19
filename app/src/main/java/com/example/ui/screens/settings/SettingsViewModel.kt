package com.example.ui.screens.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.example.data.preferences.UserPreferences
import com.example.data.storage.StorageManager
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

  private val context: Context get() = getApplication()
  private val prefs = UserPreferences.getInstance(context)

  val libraryUri: StateFlow<String?> = prefs.libraryUri
  val downloadWorkers: StateFlow<Int> = prefs.downloadWorkers
  val readerZoom: StateFlow<Float> = prefs.readerZoom
  val validateImages: StateFlow<Boolean> = prefs.validateImages

  fun setLibraryFolder(uri: Uri) {
    StorageManager.clearCache()
    prefs.setLibraryUri(uri.toString())
  }

  fun setDownloadWorkers(workers: Int) {
    prefs.setDownloadWorkers(workers)
  }

  fun setReaderZoom(zoom: Float) {
    prefs.setReaderZoom(zoom)
  }

  fun setValidateImages(validate: Boolean) {
    prefs.setValidateImages(validate)
  }

  fun getDisplayPath(): String {
    val uriStr = prefs.libraryUri.value ?: return "No folder selected"
    return try {
      StorageManager.getDisplayPath(context, Uri.parse(uriStr))
    } catch (e: Exception) {
      uriStr
    }
  }
}
