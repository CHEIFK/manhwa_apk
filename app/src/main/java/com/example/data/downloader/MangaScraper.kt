package com.example.data.downloader

import com.example.data.model.ChapterDownloadItem
import com.example.data.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ScrapedSeriesInfo(
  val title: String,
  val coverUrl: String?,
  val chapters: List<ChapterDownloadItem>
)

object MangaScraper {

  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0"

  suspend fun fetchSeries(url: String): ScrapedSeriesInfo = withContext(Dispatchers.IO) {
    val cleanUrl = url.trim()
    val request = Request.Builder()
      .url(cleanUrl)
      .header("User-Agent", USER_AGENT)
      .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
      .header("Accept-Language", "en-US,en;q=0.5")
      .build()

    val response = httpClient.newCall(request).execute()
    if (!response.isSuccessful) {
      throw IllegalStateException("Server returned HTTP ${response.code}: ${response.message}")
    }

    val html = response.body?.string() ?: throw IllegalStateException("Empty response from server")
    val doc = Jsoup.parse(html, cleanUrl)

    // Extract Title
    val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
      ?: doc.selectFirst("h1")?.text()
      ?: doc.selectFirst(".post-title h1, .story-info-right h1, .entry-title")?.text()
      ?: doc.title()
      ?: "Manwa Series"

    val cleanTitle = title.replace(Regex("(?i)read|manga|manhwa|online|free|chapter.*|all chapters.*"), "")
      .trim().ifEmpty { "Manwa Series" }

    // Extract Cover
    val coverUrl = doc.selectFirst("meta[property=og:image]")?.attr("content")
      ?: doc.selectFirst(".summary_image img, .story-info-left img, .thumb img")?.let {
        it.attr("src").ifEmpty { it.attr("data-src") }
      }

    // Extract Chapters
    val chapterLinks = mutableListOf<Pair<String, String>>()
    val seenUrls = mutableSetOf<String>()

    // Heuristics for various manga theme structures
    val selectors = listOf(
      "li.wp-manga-chapter a",
      ".listing-chapters_list a",
      ".chapter-list a",
      ".row-content-chapter a",
      "ul.sub-chap li a",
      ".chapters-list a",
      "a[href*='/chapter']",
      "a[href*='-chapter-']",
      "a[href*='/ch-']",
      "a[href*='episode']"
    )

    for (selector in selectors) {
      val elements = doc.select(selector)
      if (elements.isNotEmpty()) {
        for (el in elements) {
          val href = el.absUrl("href").ifEmpty { el.attr("href") }
          val name = el.text().trim()
          if (href.isNotBlank() && name.isNotBlank() && !seenUrls.contains(href)) {
            // Ensure it actually looks like a chapter
            if (isLikelyChapter(href, name)) {
              seenUrls.add(href)
              chapterLinks.add(Pair(name, href))
            }
          }
        }
        if (chapterLinks.size >= 1) break
      }
    }

    // If still empty, scan all anchor tags
    if (chapterLinks.isEmpty()) {
      for (el in doc.select("a[href]")) {
        val href = el.absUrl("href")
        val name = el.text().trim()
        if (isLikelyChapter(href, name) && !seenUrls.contains(href)) {
          seenUrls.add(href)
          chapterLinks.add(Pair(name, href))
        }
      }
    }

    val chapters = chapterLinks.map { (name, chUrl) ->
      val chNum = StorageManager.extractChapterNumber(name)
      ChapterDownloadItem(
        name = name.ifEmpty { "Chapter ${chNum.toInt()}" },
        url = chUrl,
        chapterNumber = chNum
      )
    }.sortedWith { c1, c2 ->
      val numCmp = c1.chapterNumber.compareTo(c2.chapterNumber)
      if (numCmp != 0) numCmp else StorageManager.NATURAL_ORDER_COMPARATOR.compare(c1.name, c2.name)
    }

    ScrapedSeriesInfo(
      title = StorageManager.sanitizeFileName(cleanTitle),
      coverUrl = coverUrl,
      chapters = chapters
    )
  }

  private fun isLikelyChapter(url: String, text: String): Boolean {
    val lowerUrl = url.lowercase()
    val lowerText = text.lowercase()
    if (lowerUrl.contains("facebook") || lowerUrl.contains("twitter") || lowerUrl.contains("discord")) return false
    if (lowerText.contains("login") || lowerText.contains("home") || lowerText.contains("bookmark")) return false
    return lowerUrl.contains("chapter") || lowerUrl.contains("ch-") || lowerUrl.contains("/ch/") ||
      lowerText.contains("chapter") || lowerText.contains("ch.") || lowerText.matches(Regex(".*\\b\\d+(\\.\\d+)?\\b.*"))
  }

  suspend fun fetchChapterImageUrls(chapterUrl: String): List<String> = withContext(Dispatchers.IO) {
    val cleanUrl = chapterUrl.trim()
    val request = Request.Builder()
      .url(cleanUrl)
      .header("User-Agent", USER_AGENT)
      .header("Referer", cleanUrl)
      .build()

    val response = httpClient.newCall(request).execute()
    if (!response.isSuccessful) {
      throw IllegalStateException("Failed to load chapter page: HTTP ${response.code}")
    }

    val html = response.body?.string() ?: return@withContext emptyList()
    val doc = Jsoup.parse(html, cleanUrl)

    val imageUrls = mutableListOf<String>()
    val seenImages = mutableSetOf<String>()

    // Heuristics for reader image containers
    val imgSelectors = listOf(
      ".reading-content img",
      ".reader-area img",
      "#chapter-images img",
      ".entry-content img",
      ".wp-manga-chapter-img",
      ".page-break img",
      "img.chapter-img",
      "div[id*=reader] img",
      ".container-chapter-reader img"
    )

    for (selector in imgSelectors) {
      val imgs = doc.select(selector)
      if (imgs.isNotEmpty()) {
        for (img in imgs) {
          val src = extractImageSrc(img)
          if (src != null && !seenImages.contains(src) && isLikelyMangaPage(src)) {
            seenImages.add(src)
            imageUrls.add(src)
          }
        }
        if (imageUrls.isNotEmpty()) break
      }
    }

    // Fallback: search all images in page
    if (imageUrls.isEmpty()) {
      for (img in doc.select("img")) {
        val src = extractImageSrc(img)
        if (src != null && !seenImages.contains(src) && isLikelyMangaPage(src)) {
          seenImages.add(src)
          imageUrls.add(src)
        }
      }
    }

    imageUrls
  }

  private fun extractImageSrc(img: org.jsoup.nodes.Element): String? {
    val attrs = listOf("data-src", "data-lazy-src", "data-original", "data-url", "src")
    for (attr in attrs) {
      val raw = img.attr(attr).trim()
      if (raw.isNotEmpty() && !raw.startsWith("data:image")) {
        return if (raw.startsWith("http")) raw else img.absUrl(attr)
      }
    }
    return null
  }

  private fun isLikelyMangaPage(url: String): Boolean {
    val lower = url.lowercase()
    if (lower.contains("logo") || lower.contains("avatar") || lower.contains("banner") ||
      lower.contains("icon") || lower.contains("discord") || lower.contains("patreon")) {
      return false
    }
    return lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") ||
      lower.contains(".webp") || lower.contains(".avif") || lower.contains("cdn") || lower.contains("chapter")
  }
}
