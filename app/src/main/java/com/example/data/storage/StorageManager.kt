package com.example.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.example.data.model.MangaChapter
import com.example.data.model.MangaPage
import com.example.data.model.MangaSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.regex.Pattern

object StorageManager {

  private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "avif", "gif")

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
    split1.size.compareTo(split2.size)
  }

  private fun splitStringAndNumbers(str: String): List<String> {
    val result = mutableListOf<String>()
    val matcher = Pattern.compile("\\d+|\\D+").matcher(str)
    while (matcher.find()) {
      result.add(matcher.group())
    }
    return result
  }

  fun extractChapterNumber(name: String): Double {
    val matcher = Pattern.compile("(?i)(?:chapter|ch|ep|episode)?[.\\s_-]*(\\d+(?:\\.\\d+)?)").matcher(name)
    if (matcher.find()) {
      return matcher.group(1)?.toDoubleOrNull() ?: 0.0
    }
    val fallbackMatcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(name)
    if (fallbackMatcher.find()) {
      return fallbackMatcher.group(1)?.toDoubleOrNull() ?: 0.0
    }
    return 0.0
  }

  /**
   * Scans a given root directory Uri and retrieves all manga series.
   */
  suspend fun scanLibrary(context: Context, rootTreeUri: Uri): List<MangaSeries> = withContext(Dispatchers.IO) {
    val seriesList = mutableListOf<MangaSeries>()
    val rootDoc = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return@withContext emptyList()

    if (!rootDoc.exists() || !rootDoc.isDirectory) {
      return@withContext emptyList()
    }

    val seriesDirs = rootDoc.listFiles().filter { it.isDirectory }

    for (seriesDir in seriesDirs) {
      val seriesName = seriesDir.name ?: "Unknown Series"
      val chapterDirs = seriesDir.listFiles().filter { it.isDirectory }
        .sortedWith { d1, d2 -> NATURAL_ORDER_COMPARATOR.compare(d1.name ?: "", d2.name ?: "") }

      var totalPages = 0
      var firstPageUri: Uri? = null

      for (chapterDir in chapterDirs) {
        val imageFiles = chapterDir.listFiles().filter { isImageFile(it) }
          .sortedWith { f1, f2 -> NATURAL_ORDER_COMPARATOR.compare(f1.name ?: "", f2.name ?: "") }

        totalPages += imageFiles.size
        if (firstPageUri == null && imageFiles.isNotEmpty()) {
          firstPageUri = imageFiles.first().uri
        }
      }

      // Check if series has images directly inside it (single-chapter format)
      if (chapterDirs.isEmpty()) {
        val rootImages = seriesDir.listFiles().filter { isImageFile(it) }
        if (rootImages.isNotEmpty()) {
          totalPages = rootImages.size
          firstPageUri = rootImages.first().uri
        }
      }

      val series = MangaSeries(
        id = seriesDir.uri.toString(),
        title = seriesName,
        folderUri = seriesDir.uri,
        coverUri = firstPageUri,
        chapterCount = if (chapterDirs.isNotEmpty()) chapterDirs.size else if (totalPages > 0) 1 else 0,
        totalPages = totalPages,
        lastModified = seriesDir.lastModified(),
        statusTag = if (totalPages > 0) "LOCAL" else "EMPTY"
      )
      seriesList.add(series)
    }

    seriesList.sortedBy { it.title.lowercase() }
  }

  /**
   * Scans chapters for a given series
   */
  suspend fun getChaptersForSeries(context: Context, seriesFolderUri: Uri): List<MangaChapter> = withContext(Dispatchers.IO) {
    val seriesDoc = DocumentFile.fromTreeUri(context, seriesFolderUri)
      ?: DocumentFile.fromSingleUri(context, seriesFolderUri)
      ?: return@withContext emptyList()

    val subDirs = seriesDoc.listFiles().filter { it.isDirectory }
      .sortedWith { d1, d2 -> NATURAL_ORDER_COMPARATOR.compare(d1.name ?: "", d2.name ?: "") }

    val chapters = mutableListOf<MangaChapter>()

    if (subDirs.isNotEmpty()) {
      for (chDir in subDirs) {
        val name = chDir.name ?: "Chapter"
        val imageFiles = chDir.listFiles().filter { isImageFile(it) }
          .sortedWith { f1, f2 -> NATURAL_ORDER_COMPARATOR.compare(f1.name ?: "", f2.name ?: "") }

        val pages = imageFiles.mapIndexed { index, file ->
          MangaPage(
            index = index + 1,
            name = file.name ?: "page_${index + 1}",
            uri = file.uri,
            sizeBytes = file.length()
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
    } else {
      // Single chapter fallback
      val imageFiles = seriesDoc.listFiles().filter { isImageFile(it) }
        .sortedWith { f1, f2 -> NATURAL_ORDER_COMPARATOR.compare(f1.name ?: "", f2.name ?: "") }

      if (imageFiles.isNotEmpty()) {
        val pages = imageFiles.mapIndexed { index, file ->
          MangaPage(
            index = index + 1,
            name = file.name ?: "page_${index + 1}",
            uri = file.uri,
            sizeBytes = file.length()
          )
        }
        chapters.add(
          MangaChapter(
            id = seriesDoc.uri.toString(),
            name = seriesDoc.name ?: "Chapter 1",
            chapterNumber = 1.0,
            folderUri = seriesDoc.uri,
            pageCount = pages.size,
            pages = pages
          )
        )
      }
    }

    chapters
  }

  fun isImageFile(file: DocumentFile): Boolean {
    if (file.isDirectory) return false
    val name = file.name ?: return false
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in IMAGE_EXTENSIONS
  }

  /**
   * Helper to find or create a subdirectory safely
   */
  fun getOrCreateSubdirectory(parent: DocumentFile, dirName: String): DocumentFile? {
    val cleanName = sanitizeFileName(dirName)
    val existing = parent.findFile(cleanName)
    if (existing != null && existing.isDirectory) {
      return existing
    }
    return parent.createDirectory(cleanName)
  }

  /**
   * Creates or gets a file in a folder
   */
  fun getOrCreateFile(parent: DocumentFile, mimeType: String, fileName: String): DocumentFile? {
    val cleanName = sanitizeFileName(fileName)
    val existing = parent.findFile(cleanName)
    if (existing != null && existing.isFile) {
      return existing
    }
    return parent.createFile(mimeType, cleanName)
  }

  fun sanitizeFileName(name: String): String {
    return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
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
