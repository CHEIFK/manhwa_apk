package com.example.data.storage

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.data.db.AppDatabase
import com.example.data.db.MangaChapterEntity
import com.example.data.db.MangaSeriesEntity
import com.example.data.db.toDomain
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
   * Load series metadata from local Room cache immediately (< 1ms).
   */
  suspend fun getCachedSeriesList(context: Context): List<MangaSeries> = withContext(Dispatchers.IO) {
    try {
      val dao = AppDatabase.getInstance(context).mangaDao()
      dao.getAllSeries().map { it.toDomain() }
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager.getCachedSeriesList failed", e)
      emptyList()
    }
  }

  /**
   * Fast retrieval of chapter metadata for a series.
   * Reads from Room cache instantly without scanning image files over SAF.
   */
  suspend fun getChaptersForSeries(context: Context, seriesFolderUri: Uri): List<MangaChapter> = withContext(Dispatchers.IO) {
    val uriStr = seriesFolderUri.toString()
    val db = AppDatabase.getInstance(context)
    val dao = db.mangaDao()

    val cachedChapters = try {
      dao.getChaptersForSeries(uriStr)
    } catch (e: Exception) {
      emptyList()
    }

    if (cachedChapters.isNotEmpty()) {
      return@withContext cachedChapters.map { it.toDomain() }
    }

    // Fast fallback scan: discover chapter folders in O(1) SAF calls without scanning image files
    getChaptersForSeriesFast(context, seriesFolderUri)
  }

  /**
   * Fast chapter discovery that avoids scanning every chapter folder for images.
   * Total SAF calls: 1 (series dir) + 1 (cover image from first chapter).
   */
  suspend fun getChaptersForSeriesFast(
    context: Context,
    seriesFolderUri: Uri,
    seriesTitle: String? = null
  ): List<MangaChapter> = withContext(Dispatchers.IO) {
    val startMs = System.currentTimeMillis()
    val uriStr = seriesFolderUri.toString()
    val db = AppDatabase.getInstance(context)
    val dao = db.mangaDao()

    val seriesDoc = try {
      DocumentFile.fromTreeUri(context, seriesFolderUri)
        ?: DocumentFile.fromSingleUri(context, seriesFolderUri)
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager.getChaptersForSeriesFast: Failed to resolve series URI", e)
      null
    } ?: return@withContext emptyList()

    val title = seriesTitle ?: seriesDoc.name ?: "Unknown Series"

    val children = try {
      seriesDoc.listFiles()
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager.getChaptersForSeriesFast: Failed to list series children", e)
      return@withContext emptyList()
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

    var coverUri: String? = directImages.firstOrNull()?.uri?.toString()
    var samplePageCount = directImages.size
    val chapterEntities = mutableListOf<MangaChapterEntity>()

    if (chapterDirs.isNotEmpty()) {
      // Find cover image from ONLY the first chapter folder (1 SAF call only)
      val firstCh = chapterDirs.first()
      val firstChImages = try {
        firstCh.listFiles().filter { isImageFile(it) }
      } catch (e: Exception) {
        emptyList()
      }
      if (firstChImages.isNotEmpty()) {
        samplePageCount = firstChImages.size
        if (coverUri == null) {
          coverUri = firstChImages.sortedWith { f1, f2 -> NATURAL_ORDER_COMPARATOR.compare(f1.name ?: "", f2.name ?: "") }.firstOrNull()?.uri?.toString()
        }
      }

      for ((idx, chDir) in chapterDirs.withIndex()) {
        val chName = chDir.name ?: "Chapter ${idx + 1}"
        val pageCount = if (idx == 0 && samplePageCount > 0) samplePageCount else samplePageCount.coerceAtLeast(1)
        chapterEntities.add(
          MangaChapterEntity(
            uri = chDir.uri.toString(),
            seriesUri = uriStr,
            name = chName,
            chapterNumber = extractChapterNumber(chName),
            pageCount = pageCount,
            lastModified = chDir.lastModified()
          )
        )
      }
    } else if (directImages.isNotEmpty()) {
      chapterEntities.add(
        MangaChapterEntity(
          uri = uriStr,
          seriesUri = uriStr,
          name = title,
          chapterNumber = 1.0,
          pageCount = directImages.size,
          lastModified = seriesDoc.lastModified()
        )
      )
    }

    val totalPagesEst = chapterEntities.sumOf { it.pageCount }
    val seriesEntity = MangaSeriesEntity(
      uri = uriStr,
      title = title,
      coverUri = coverUri,
      chapterCount = chapterEntities.size,
      totalPages = totalPagesEst,
      lastModified = seriesDoc.lastModified(),
      statusTag = if (chapterEntities.isNotEmpty()) "LOCAL" else "EMPTY"
    )

    try {
      dao.updateSeriesWithChapters(seriesEntity, chapterEntities)
      seriesCache[uriStr] = seriesEntity.toDomain()
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager.getChaptersForSeriesFast: Failed to cache series in Room", e)
    }

    Log.d(TAG, "StorageManager.getChaptersForSeriesFast finished in ${System.currentTimeMillis() - startMs}ms for ${chapterEntities.size} chapters")
    chapterEntities.map { it.toDomain() }
  }

  /**
   * Lightweight background sync for a single series. Updates exact page counts in background without blocking UI.
   */
  suspend fun syncSingleSeriesInBackground(
    context: Context,
    seriesFolderUri: Uri,
    seriesTitle: String? = null,
    onUpdate: ((List<MangaChapter>, MangaSeries?) -> Unit)? = null
  ) = withContext(Dispatchers.IO) {
    try {
      val seriesDoc = DocumentFile.fromTreeUri(context, seriesFolderUri)
        ?: DocumentFile.fromSingleUri(context, seriesFolderUri)
      if (seriesDoc != null && seriesDoc.exists()) {
        val updatedSeries = scanSingleSeries(context, seriesDoc)
        if (updatedSeries != null) {
          val db = AppDatabase.getInstance(context)
          val chapters = db.mangaDao().getChaptersForSeries(seriesFolderUri.toString()).map { it.toDomain() }
          onUpdate?.invoke(chapters, updatedSeries)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager.syncSingleSeriesInBackground failed", e)
    }
  }

  /**
   * Reads image pages for a SINGLE chapter only when opening the reader.
   * Eliminates Binder starvation by touching only the relevant chapter's directory.
   */
  suspend fun getPagesForChapter(context: Context, chapterFolderUri: Uri): List<MangaPage> = withContext(Dispatchers.IO) {
    val chapterDoc = try {
      DocumentFile.fromTreeUri(context, chapterFolderUri)
        ?: DocumentFile.fromSingleUri(context, chapterFolderUri)
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager.getPagesForChapter: Failed to resolve chapter URI", e)
      null
    } ?: return@withContext emptyList()

    val imageFiles = try {
      chapterDoc.listFiles().filter { isImageFile(it) }
        .sortedWith { f1, f2 -> NATURAL_ORDER_COMPARATOR.compare(f1.name ?: "", f2.name ?: "") }
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager.getPagesForChapter: Failed to list files in ${chapterDoc.name}", e)
      emptyList()
    }

    imageFiles.mapIndexed { index, file ->
      MangaPage(
        index = index + 1,
        name = file.name ?: "page_${index + 1}",
        uri = file.uri,
        sizeBytes = 0L
      )
    }
  }

  /**
   * Lightweight background synchronization with SAF.
   * Compares directory modification timestamps to avoid unnecessary N² SAF scans.
   */
  suspend fun syncLibrary(context: Context, rootTreeUri: Uri): List<MangaSeries> = withContext(Dispatchers.IO) {
    val startMs = System.currentTimeMillis()
    Log.d(TAG, "StorageManager.syncLibrary started for root: $rootTreeUri")

    val db = AppDatabase.getInstance(context)
    val dao = db.mangaDao()

    val rootDoc = try {
      DocumentFile.fromTreeUri(context, rootTreeUri)
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager: Failed to resolve rootTreeUri $rootTreeUri", e)
      return@withContext dao.getAllSeries().map { it.toDomain() }
    }

    if (rootDoc == null || !rootDoc.exists() || !rootDoc.isDirectory) {
      Log.w(TAG, "StorageManager: Root document does not exist or is not a directory: $rootTreeUri")
      return@withContext emptyList()
    }

    val rootChildren = try {
      rootDoc.listFiles()
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager: Failed to list root children", e)
      return@withContext dao.getAllSeries().map { it.toDomain() }
    }

    val seriesDirs = rootChildren.filter { it.isDirectory }
    val currentUris = seriesDirs.map { it.uri.toString() }.toSet()

    // Clean up removed series from database
    if (currentUris.isEmpty()) {
      dao.clearAll()
      clearCache()
      return@withContext emptyList()
    } else {
      dao.deleteSeriesNotIn(currentUris.toList())
    }

    val cachedSeriesMap = try {
      dao.getAllSeries().associateBy { it.uri }
    } catch (e: Exception) {
      emptyMap()
    }

    val resultSeriesList = mutableListOf<MangaSeries>()

    for (seriesDir in seriesDirs) {
      val uriKey = seriesDir.uri.toString()
      val cachedSeries = cachedSeriesMap[uriKey]
      val dirLastModified = seriesDir.lastModified()

      // Skip scanning if directory timestamp has not changed
      if (cachedSeries != null && cachedSeries.lastModified == dirLastModified && cachedSeries.chapterCount > 0) {
        val domain = cachedSeries.toDomain()
        seriesCache[uriKey] = domain
        resultSeriesList.add(domain)
        continue
      }

      // New or modified series: scan incrementally
      try {
        val existingChapters = dao.getChaptersForSeries(uriKey).associateBy { it.uri }
        val scanned = scanSeriesIncremental(seriesDir, cachedSeries, existingChapters)
        if (scanned != null) {
          dao.updateSeriesWithChapters(scanned.first, scanned.second)
          val domain = scanned.first.toDomain()
          seriesCache[uriKey] = domain
          resultSeriesList.add(domain)
        } else if (cachedSeries != null) {
          resultSeriesList.add(cachedSeries.toDomain())
        }
      } catch (e: Exception) {
        Log.e(TAG, "StorageManager: Error scanning series directory ${seriesDir.name}", e)
        if (cachedSeries != null) {
          resultSeriesList.add(cachedSeries.toDomain())
        }
      }
    }

    resultSeriesList.sortBy { it.title.lowercase() }
    Log.d(TAG, "StorageManager.syncLibrary finished in ${System.currentTimeMillis() - startMs}ms, total ${resultSeriesList.size} series")
    resultSeriesList
  }

  /**
   * Fast incremental scan of a single series. Reuses cached chapter page counts if unchanged.
   */
  private fun scanSeriesIncremental(
    seriesDir: DocumentFile,
    cachedSeries: MangaSeriesEntity?,
    existingChapters: Map<String, MangaChapterEntity>
  ): Pair<MangaSeriesEntity, List<MangaChapterEntity>>? {
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

    var coverUri: String? = directImages.firstOrNull()?.uri?.toString() ?: cachedSeries?.coverUri
    var totalPagesCount = 0
    val chapterEntities = mutableListOf<MangaChapterEntity>()

    if (chapterDirs.isNotEmpty()) {
      for ((idx, chDir) in chapterDirs.withIndex()) {
        val chUri = chDir.uri.toString()
        val cachedCh = existingChapters[chUri]
        val chLastModified = chDir.lastModified()

        val pageCount: Int
        if (cachedCh != null && cachedCh.lastModified == chLastModified && cachedCh.pageCount > 0) {
          pageCount = cachedCh.pageCount
        } else if (idx == 0 || coverUri == null) {
          // Only scan files for first chapter or to find cover
          val chImages = try {
            chDir.listFiles().filter { isImageFile(it) }
          } catch (e: Exception) {
            emptyList()
          }
          pageCount = chImages.size
          if (coverUri == null && chImages.isNotEmpty()) {
            val firstImg = chImages.sortedWith { f1, f2 -> NATURAL_ORDER_COMPARATOR.compare(f1.name ?: "", f2.name ?: "") }.firstOrNull()
            coverUri = firstImg?.uri?.toString()
          }
        } else {
          // Fast estimation if not yet scanned
          pageCount = cachedCh?.pageCount ?: 1
        }

        val chName = chDir.name ?: "Chapter"
        chapterEntities.add(
          MangaChapterEntity(
            uri = chUri,
            seriesUri = seriesDir.uri.toString(),
            name = chName,
            chapterNumber = extractChapterNumber(chName),
            pageCount = pageCount,
            lastModified = chLastModified
          )
        )
        totalPagesCount += pageCount
      }
    } else if (directImages.isNotEmpty()) {
      totalPagesCount = directImages.size
      chapterEntities.add(
        MangaChapterEntity(
          uri = seriesDir.uri.toString(),
          seriesUri = seriesDir.uri.toString(),
          name = seriesName,
          chapterNumber = 1.0,
          pageCount = directImages.size,
          lastModified = seriesDir.lastModified()
        )
      )
    }

    val chapterCount = chapterEntities.size
    val seriesEntity = MangaSeriesEntity(
      uri = seriesDir.uri.toString(),
      title = seriesName,
      coverUri = coverUri,
      chapterCount = chapterCount,
      totalPages = totalPagesCount,
      lastModified = seriesDir.lastModified(),
      statusTag = if (chapterCount > 0) "LOCAL" else "EMPTY"
    )

    return Pair(seriesEntity, chapterEntities)
  }

  /**
   * Scans a single series directory incrementally and updates local cache.
   */
  suspend fun scanSingleSeries(context: Context, seriesDir: DocumentFile): MangaSeries? = withContext(Dispatchers.IO) {
    if (!seriesDir.exists() || !seriesDir.isDirectory) return@withContext null
    try {
      val db = AppDatabase.getInstance(context)
      val dao = db.mangaDao()
      val uriKey = seriesDir.uri.toString()
      val cachedSeries = dao.getSeriesByUri(uriKey)
      val existingChapters = dao.getChaptersForSeries(uriKey).associateBy { it.uri }

      val scanned = scanSeriesIncremental(seriesDir, cachedSeries, existingChapters)
      if (scanned != null) {
        dao.updateSeriesWithChapters(scanned.first, scanned.second)
        val domain = scanned.first.toDomain()
        seriesCache[uriKey] = domain
        domain
      } else {
        cachedSeries?.toDomain()
      }
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager.scanSingleSeries failed for ${seriesDir.name}", e)
      null
    }
  }

  /**
   * Scans the root library directory (synchronous fallback / initial scan).
   */
  suspend fun scanLibrary(context: Context, rootTreeUri: Uri): List<MangaSeries> = withContext(Dispatchers.IO) {
    syncLibrary(context, rootTreeUri)
  }

  /**
   * Deletes a series folder and all its contents from SAF storage and cleans up cache.
   */
  suspend fun deleteSeriesFolder(context: Context, seriesFolderUri: Uri): Boolean = withContext(Dispatchers.IO) {
    val uriStr = seriesFolderUri.toString()
    invalidateSeriesCache(uriStr)

    try {
      val db = AppDatabase.getInstance(context)
      db.mangaDao().deleteSeriesByUri(uriStr)
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager.deleteSeriesFolder: Error deleting from Room cache", e)
    }

    try {
      val seriesDoc = DocumentFile.fromTreeUri(context, seriesFolderUri)
        ?: DocumentFile.fromSingleUri(context, seriesFolderUri)
      if (seriesDoc != null && seriesDoc.exists()) {
        val deleted = seriesDoc.delete()
        Log.d(TAG, "StorageManager.deleteSeriesFolder: SAF delete returned $deleted for $seriesFolderUri")
        return@withContext deleted
      }
      true
    } catch (e: Exception) {
      Log.e(TAG, "StorageManager.deleteSeriesFolder: Failed to delete SAF folder for $seriesFolderUri", e)
      false
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
