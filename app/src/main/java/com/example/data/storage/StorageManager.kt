package com.example.data.storage

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.data.model.MangaChapter
import com.example.data.model.MangaPage
import com.example.data.model.MangaSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object StorageManager {

  private const val TAG = "ManwaManager"
  private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "avif", "gif")

  // Fast in-memory cache: seriesUriString -> MangaSeries
  private val seriesCache = ConcurrentHashMap<String, MangaSeries>()

  /**
   * Natural order comparator (e.g., "Ch. 1", "Ch. 2", "Ch. 10")
   */
  val NATURAL_ORDER_COMPARATOR = Comparator<String> { s1, s2 ->
    val split1 = splitStringAndNumbers(s1.lowercase())
    val split2 = splitStringAndNumbers(s2.lowercase())
    val minLen = minOf(split1.size, split2.size)

    for (i in 0 until minLen) {
      val p1 = split1[i]
      val p2 = split2[i]

      val isNum1 = p1.all { it.isDigit() }
      val isNum2 = p2.all { it.isDigit() }

      if (isNum1 && isNum2) {
        val num1 = p1.toLongOrNull() ?: 0L
        val num2 = p2.toLongOrNull() ?: 0L
        val cmp = num1.compareTo(num2)
        if (cmp != 0) return@Comparator cmp
      } else {
        val cmp = p1.compareTo(p2)
        if (cmp != 0) return@Comparator cmp
      }
    }
    val lenCmp = split1.size.compareTo(split2.size)
    if (lenCmp != 0) lenCmp else s1.compareTo(s2)
  }

  private fun splitStringAndNumbers(str: String): List<String> {
    val result = mutableListOf<String>()
    val matcher = Pattern.compile("\\d+|\\D+").matcher(str)
    while (matcher.find()) {
      result.add(matcher.group())
    }
    return result
  }

  /**
   * Robust chapter number extraction: prioritizes explicit chapter prefixes
   * before falling back to standalone boundary numbers.
   */
  fun extractChapterNumber(name: String): Double {
    val keywordMatcher = Pattern.compile("(?i)\\b(?:chapter|ch|ep|episode)[.\\s_-]*(\\d+(?:\\.\\d+)?)").matcher(name)
    if (keywordMatcher.find()) {
      return keywordMatcher.group(1)?.toDoubleOrNull() ?: 0.0
    }
    val fallbackMatcher = Pattern.compile("(?i)\\b(\\d+(?:\\.\\d+)?)\\b").matcher(name)
    var lastFound: Double? = null
    while (fallbackMatcher.find()) {
      lastFound = fallbackMatcher.group(1)?.toDoubleOrNull()
    }
    return lastFound ?: 0.0
  }

  /**
   * Fast, reliable scan of the root library directory.
   * Performs sequential, non-blocking SAF traversal to prevent ContentProvider Binder starvation.
   */
  suspend fun scanLibrary(context: Context, rootTreeUri: Uri): List<MangaSeries> = withContext(Dispatchers.IO) {
    val startMs = System.currentTimeMillis()
    Log.d(TAG, "StorageManager.scanLibrary started for root: $rootTreeUri")

    val rootDoc = try {
      DocumentFile.fromTreeUri(context, rootTreeUri)
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager: Failed to resolve rootTreeUri $rootTreeUri", e)
      return@withContext emptyList()
    }

    if (rootDoc == null || !rootDoc.exists() || !rootDoc.isDirectory) {
      Log.w(TAG, "StorageManager: Root document does not exist or is not a directory: $rootTreeUri")
      return@withContext emptyList()
    }

    val rootChildren = try {
      rootDoc.listFiles()
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager: Failed to list root children", e)
      return@withContext emptyList()
    }

    val seriesDirs = rootChildren.filter { it.isDirectory }
    Log.d(TAG, "StorageManager: Found ${seriesDirs.size} directories in root storage")

    if (seriesDirs.isEmpty()) {
      seriesCache.clear()
      return@withContext emptyList()
    }

    val currentUris = seriesDirs.map { it.uri.toString() }.toSet()
    seriesCache.keys.retainAll(currentUris)

    val seriesList = mutableListOf<MangaSeries>()

    for (seriesDir in seriesDirs) {
      val uriKey = seriesDir.uri.toString()
      try {
        val scanned = scanSingleSeriesInternal(seriesDir)
        if (scanned != null) {
          seriesCache[uriKey] = scanned
          seriesList.add(scanned)
        }
      } catch (e: Exception) {
        Log.e(TAG, "StorageManager: Error scanning series directory ${seriesDir.name}", e)
        seriesCache[uriKey]?.let { seriesList.add(it) }
      }
    }

    seriesList.sortBy { it.title.lowercase() }
    Log.d(TAG, "StorageManager.scanLibrary finished in ${System.currentTimeMillis() - startMs}ms, returning ${seriesList.size} series")
    seriesList
  }

  /**
   * Scans a single series directory incrementally.
   */
  suspend fun scanSingleSeries(context: Context, seriesDir: DocumentFile): MangaSeries? = withContext(Dispatchers.IO) {
    if (!seriesDir.exists() || !seriesDir.isDirectory) return@withContext null
    try {
      val scanned = scanSingleSeriesInternal(seriesDir)
      if (scanned != null) {
        seriesCache[seriesDir.uri.toString()] = scanned
      }
      scanned
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager.scanSingleSeries failed for ${seriesDir.name}", e)
      null
    }
  }

  fun findSeriesDirectory(rootDoc: DocumentFile, seriesTitle: String): DocumentFile? {
    val cleanTitle = sanitizeFileName(seriesTitle)
    val children = try {
      rootDoc.listFiles()
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager.findSeriesDirectory failed to list files", e)
      return null
    }

    return children.find { doc ->
      if (!doc.isDirectory) return@find false
      val name = doc.name ?: return@find false
      name.equals(cleanTitle, ignoreCase = true) ||
        name.equals(seriesTitle, ignoreCase = true) ||
        name.startsWith(cleanTitle, ignoreCase = true) ||
        cleanTitle.startsWith(name, ignoreCase = true)
    }
  }

  fun invalidateSeriesCache(seriesUriString: String) {
    seriesCache.remove(seriesUriString)
  }

  fun clearCache() {
    seriesCache.clear()
  }

  private fun scanSingleSeriesInternal(seriesDir: DocumentFile): MangaSeries? {
    val seriesName = seriesDir.name ?: return null
    val children = try {
      seriesDir.listFiles()
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager: Failed to list files in $seriesName", e)
      return null
    }

    val chapterDirs = children.filter { it.isDirectory }
      .sortedWith { d1, d2 ->
        val c1 = extractChapterNumber(d1.name ?: "")
        val c2 = extractChapterNumber(d2.name ?: "")
        val numCmp = c1.compareTo(c2)
        if (numCmp != 0) numCmp else NATURAL_ORDER_COMPARATOR.compare(d1.name ?: "", d2.name ?: "")
      }

    val directImages = children.filter { isImageFile(it) }
      .sortedWith { f1, f2 -> NATURAL_ORDER_COMPARATOR.compare(f1.name ?: "", f2.name ?: "") }

    var coverUri: Uri? = directImages.firstOrNull()?.uri
    var pageCountEstimate = directImages.size

    if (coverUri == null && chapterDirs.isNotEmpty()) {
      for (chDir in chapterDirs) {
        val chapterImages = try {
          chDir.listFiles().filter { isImageFile(it) }
            .sortedWith { f1, f2 -> NATURAL_ORDER_COMPARATOR.compare(f1.name ?: "", f2.name ?: "") }
        } catch (e: Exception) {
          emptyList()
        }
        if (chapterImages.isNotEmpty()) {
          coverUri = chapterImages.first().uri
          pageCountEstimate = chapterDirs.size * chapterImages.size
          break
        }
      }
    }

    val chapterCount = if (chapterDirs.isNotEmpty()) chapterDirs.size else if (directImages.isNotEmpty()) 1 else 0

    return MangaSeries(
      id = seriesDir.uri.toString(),
      title = seriesName,
      folderUri = seriesDir.uri,
      coverUri = coverUri,
      chapterCount = chapterCount,
      totalPages = pageCountEstimate,
      lastModified = seriesDir.lastModified(),
      statusTag = if (chapterCount > 0) "LOCAL" else "EMPTY"
    )
  }

  /**
   * Scans chapters and pages for a given series folder.
   * Avoids calling length() on every image file over SAF IPC, drastically speeding up loading.
   */
  suspend fun getChaptersForSeries(context: Context, seriesFolderUri: Uri): List<MangaChapter> = withContext(Dispatchers.IO) {
    val startMs = System.currentTimeMillis()
    Log.d(TAG, "StorageManager.getChaptersForSeries started for $seriesFolderUri")

    val seriesDoc = try {
      DocumentFile.fromTreeUri(context, seriesFolderUri)
        ?: DocumentFile.fromSingleUri(context, seriesFolderUri)
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager.getChaptersForSeries: Failed to resolve series URI", e)
      return@withContext emptyList()
    } ?: return@withContext emptyList()

    val children = try {
      seriesDoc.listFiles()
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager.getChaptersForSeries: Failed to list seriesDoc children", e)
      return@withContext emptyList()
    }

    val subDirs = children.filter { it.isDirectory }
      .sortedWith { d1, d2 ->
        val c1 = extractChapterNumber(d1.name ?: "")
        val c2 = extractChapterNumber(d2.name ?: "")
        val numCmp = c1.compareTo(c2)
        if (numCmp != 0) numCmp else NATURAL_ORDER_COMPARATOR.compare(d1.name ?: "", d2.name ?: "")
      }

    if (subDirs.isNotEmpty()) {
      val chapters = mutableListOf<MangaChapter>()
      for (chDir in subDirs) {
        val name = chDir.name ?: "Chapter"
        val imageFiles = try {
          chDir.listFiles().filter { isImageFile(it) }
            .sortedWith { f1, f2 -> NATURAL_ORDER_COMPARATOR.compare(f1.name ?: "", f2.name ?: "") }
        } catch (e: Exception) {
          emptyList()
        }

        val pages = imageFiles.mapIndexed { index, file ->
          MangaPage(
            index = index + 1,
            name = file.name ?: "page_${index + 1}",
            uri = file.uri,
            sizeBytes = 0L
          )
        }

        chapters.add(
          MangaChapter(
            id = chDir.uri.toString(),
            name = name,
            chapterNumber = extractChapterNumber(name),
            folderUri = chDir.uri,
            pageCount = pages.size,
            pages = pages
          )
        )
      }
      Log.d(TAG, "StorageManager.getChaptersForSeries finished in ${System.currentTimeMillis() - startMs}ms, returning ${chapters.size} chapters")
      chapters
    } else {
      // Single chapter fallback
      val imageFiles = children.filter { isImageFile(it) }
        .sortedWith { f1, f2 -> NATURAL_ORDER_COMPARATOR.compare(f1.name ?: "", f2.name ?: "") }

      if (imageFiles.isNotEmpty()) {
        val pages = imageFiles.mapIndexed { index, file ->
          MangaPage(
            index = index + 1,
            name = file.name ?: "page_${index + 1}",
            uri = file.uri,
            sizeBytes = 0L
          )
        }
        listOf(
          MangaChapter(
            id = seriesDoc.uri.toString(),
            name = seriesDoc.name ?: "Chapter 1",
            chapterNumber = 1.0,
            folderUri = seriesDoc.uri,
            pageCount = pages.size,
            pages = pages
          )
        )
      } else {
        emptyList()
      }
    }
  }

  fun isImageFile(file: DocumentFile): Boolean {
    if (file.isDirectory) return false
    val name = file.name ?: return false
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in IMAGE_EXTENSIONS
  }

  /**
   * Helper to find or create a subdirectory safely without full repeat traversals
   */
  fun getOrCreateSubdirectory(parent: DocumentFile, dirName: String): DocumentFile? {
    val cleanName = sanitizeFileName(dirName)
    val existing = findChildDirectory(parent, cleanName)
    if (existing != null) {
      return existing
    }
    return parent.createDirectory(cleanName)
  }

  fun findChildDirectory(parent: DocumentFile, dirName: String): DocumentFile? {
    val cleanName = sanitizeFileName(dirName)
    val children = try {
      parent.listFiles()
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager.findChildDirectory failed to list files", e)
      return null
    }
    return children.find { it.isDirectory && it.name.equals(cleanName, ignoreCase = true) }
  }

  /**
   * Creates or gets a file in a folder
   */
  fun getOrCreateFile(parent: DocumentFile, mimeType: String, fileName: String): DocumentFile? {
    val cleanName = sanitizeFileName(fileName)
    val children = try {
      parent.listFiles()
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager.getOrCreateFile failed to list files", e)
      return null
    }
    val existing = children.find { it.isFile && it.name.equals(cleanName, ignoreCase = true) }
    if (existing != null) {
      return existing
    }
    return parent.createFile(mimeType, cleanName)
  }

  fun sanitizeFileName(name: String): String {
    return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Untitled" }
  }

  /**
   * Checks if an image file is valid (non-zero size and valid header)
   */
  suspend fun isValidImage(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
      context.contentResolver.openInputStream(uri)?.use { stream ->
        val options = BitmapFactory.Options().apply {
          inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(stream, null, options)
        return@withContext options.outWidth > 0 && options.outHeight > 0
      } ?: false
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Format human-readable path for user from Uri
   */
  fun getDisplayPath(context: Context, uri: Uri): String {
    return try {
      val docFile = DocumentFile.fromTreeUri(context, uri)
      docFile?.name ?: uri.path ?: uri.toString()
    } catch (e: Exception) {
      uri.lastPathSegment ?: uri.toString()
    }
  }
}
