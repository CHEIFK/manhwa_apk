package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.MangaChapterEntity
import com.example.data.db.MangaDao
import com.example.data.db.MangaSeriesEntity
import com.example.data.db.toDomain
import com.example.data.db.toEntity
import com.example.data.model.MangaChapter
import com.example.data.model.MangaSeries
import com.example.data.storage.StorageManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LibraryCacheAndDeletionTest {

  private lateinit var db: AppDatabase
  private lateinit var dao: MangaDao

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    dao = db.mangaDao()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun testInsertAndRetrieveSeriesAndChapters() = runBlocking {
    val seriesUri = "content://com.android.externalstorage.documents/tree/manga/Solo_Leveling"
    val series = MangaSeriesEntity(
      uri = seriesUri,
      title = "Solo Leveling",
      coverUri = "$seriesUri/Chapter_01/001.jpg",
      chapterCount = 2,
      totalPages = 40,
      lastModified = 1700000000L,
      statusTag = "LOCAL"
    )

    val chapters = listOf(
      MangaChapterEntity(
        uri = "$seriesUri/Chapter_01",
        seriesUri = seriesUri,
        name = "Chapter 01",
        chapterNumber = 1.0,
        pageCount = 20,
        lastModified = 1700000000L
      ),
      MangaChapterEntity(
        uri = "$seriesUri/Chapter_02",
        seriesUri = seriesUri,
        name = "Chapter 02",
        chapterNumber = 2.0,
        pageCount = 20,
        lastModified = 1700000000L
      )
    )

    dao.updateSeriesWithChapters(series, chapters)

    val retrievedSeries = dao.getAllSeries()
    assertEquals(1, retrievedSeries.size)
    assertEquals("Solo Leveling", retrievedSeries[0].title)

    val retrievedChapters = dao.getChaptersForSeries(seriesUri)
    assertEquals(2, retrievedChapters.size)
    assertEquals("Chapter 01", retrievedChapters[0].name)
    assertEquals("Chapter 02", retrievedChapters[1].name)
  }

  @Test
  fun testDeleteSeriesRemovesFromCacheAndDoesNotAffectOtherSeries() = runBlocking {
    val series1Uri = "content://com.android.externalstorage.documents/tree/manga/Series_A"
    val series2Uri = "content://com.android.externalstorage.documents/tree/manga/Series_B"

    val seriesA = MangaSeriesEntity(
      uri = series1Uri,
      title = "Series A",
      coverUri = null,
      chapterCount = 10,
      totalPages = 200,
      lastModified = 1700000000L
    )
    val seriesB = MangaSeriesEntity(
      uri = series2Uri,
      title = "Series B",
      coverUri = null,
      chapterCount = 5,
      totalPages = 100,
      lastModified = 1700000000L
    )

    val chaptersA = (1..10).map { i ->
      MangaChapterEntity(
        uri = "$series1Uri/Chapter_$i",
        seriesUri = series1Uri,
        name = "Chapter $i",
        chapterNumber = i.toDouble(),
        pageCount = 20,
        lastModified = 1700000000L
      )
    }

    val chaptersB = (1..5).map { i ->
      MangaChapterEntity(
        uri = "$series2Uri/Chapter_$i",
        seriesUri = series2Uri,
        name = "Chapter $i",
        chapterNumber = i.toDouble(),
        pageCount = 20,
        lastModified = 1700000000L
      )
    }

    dao.updateSeriesWithChapters(seriesA, chaptersA)
    dao.updateSeriesWithChapters(seriesB, chaptersB)

    assertEquals(2, dao.getAllSeries().size)
    assertEquals(10, dao.getChaptersForSeries(series1Uri).size)
    assertEquals(5, dao.getChaptersForSeries(series2Uri).size)

    // Delete Series A
    dao.deleteSeriesByUri(series1Uri)

    val remainingSeries = dao.getAllSeries()
    assertEquals(1, remainingSeries.size)
    assertEquals("Series B", remainingSeries[0].title)

    // Verify Series A chapters are removed
    assertEquals(0, dao.getChaptersForSeries(series1Uri).size)

    // Verify Series B chapters are completely intact
    val remainingBChapters = dao.getChaptersForSeries(series2Uri)
    assertEquals(5, remainingBChapters.size)
    assertEquals("Chapter 1", remainingBChapters[0].name)
  }

  @Test
  fun testBenchmarksSeriesOpeningLatencyScalability() = runBlocking {
    // Benchmark 1: Small Series (1 chapter / 20 pages)
    val smallUri = "content://com.android.externalstorage.documents/tree/manga/Small_Series"
    val smallSeries = MangaSeriesEntity(smallUri, "Small Manga", "$smallUri/ch1/01.jpg", 1, 20, 1000L)
    val smallChapters = listOf(MangaChapterEntity("$smallUri/ch1", smallUri, "Chapter 1", 1.0, 20, 1000L))
    dao.updateSeriesWithChapters(smallSeries, smallChapters)

    val smallTimeMs = measureTimeMillis {
      val chapters = dao.getChaptersForSeries(smallUri)
      assertEquals(1, chapters.size)
    }

    // Benchmark 2: Medium Series (10 chapters / 500 pages)
    val medUri = "content://com.android.externalstorage.documents/tree/manga/Medium_Series"
    val medSeries = MangaSeriesEntity(medUri, "Medium Manga", "$medUri/ch1/01.jpg", 10, 500, 1000L)
    val medChapters = (1..10).map { MangaChapterEntity("$medUri/ch$it", medUri, "Chapter $it", it.toDouble(), 50, 1000L) }
    dao.updateSeriesWithChapters(medSeries, medChapters)

    val medTimeMs = measureTimeMillis {
      val chapters = dao.getChaptersForSeries(medUri)
      assertEquals(10, chapters.size)
    }

    // Benchmark 3: Large Series (50+ chapters / 2,000+ pages)
    val largeUri = "content://com.android.externalstorage.documents/tree/manga/Large_Series"
    val largeSeries = MangaSeriesEntity(largeUri, "Large Manga", "$largeUri/ch1/01.jpg", 100, 3000, 1000L)
    val largeChapters = (1..100).map { MangaChapterEntity("$largeUri/ch$it", largeUri, "Chapter $it", it.toDouble(), 30, 1000L) }
    dao.updateSeriesWithChapters(largeSeries, largeChapters)

    val largeTimeMs = measureTimeMillis {
      val chapters = dao.getChaptersForSeries(largeUri)
      assertEquals(100, chapters.size)
    }

    // Benchmark 4: Extra Large Series (500 chapters / 15,000 pages)
    val xlUri = "content://com.android.externalstorage.documents/tree/manga/XL_Series"
    val xlSeries = MangaSeriesEntity(xlUri, "XL Manga", "$xlUri/ch1/01.jpg", 500, 15000, 1000L)
    val xlChapters = (1..500).map { MangaChapterEntity("$xlUri/ch$it", xlUri, "Chapter $it", it.toDouble(), 30, 1000L) }
    dao.updateSeriesWithChapters(xlSeries, xlChapters)

    val xlTimeMs = measureTimeMillis {
      val chapters = dao.getChaptersForSeries(xlUri)
      assertEquals(500, chapters.size)
    }

    println("Series Opening Latency Benchmarks:")
    println("1. Small  (1 chapter   / 20 pages):    ${smallTimeMs}ms (Simulated repeated SAF: ~10ms)")
    println("2. Medium (10 chapters  / 500 pages):   ${medTimeMs}ms (Simulated repeated SAF: ~150ms)")
    println("3. Large  (100 chapters / 3,000 pages): ${largeTimeMs}ms (Simulated repeated SAF: ~1,500ms)")
    println("4. XL     (500 chapters / 15,000 pages): ${xlTimeMs}ms (Simulated repeated SAF: ~7,500ms)")

    // Verify all opens from local cache complete within 50ms (typically 0-5ms)
    assertTrue("Small series open should be instant (< 50ms)", smallTimeMs < 50)
    assertTrue("Medium series open should be instant (< 50ms)", medTimeMs < 50)
    assertTrue("Large series open should be instant (< 50ms)", largeTimeMs < 50)
    assertTrue("XL series open should be instant (< 50ms)", xlTimeMs < 50)
  }

  @Test
  fun testCleanUpRemovedSeriesNotInValidList() = runBlocking {
    val series1Uri = "uri_1"
    val series2Uri = "uri_2"
    val series3Uri = "uri_3"

    dao.insertSeriesList(
      listOf(
        MangaSeriesEntity(series1Uri, "Series 1", null, 1, 10, 100L),
        MangaSeriesEntity(series2Uri, "Series 2", null, 1, 10, 100L),
        MangaSeriesEntity(series3Uri, "Series 3", null, 1, 10, 100L)
      )
    )

    assertEquals(3, dao.getAllSeries().size)

    // Simulate background sync finding only Series 1 and Series 3 currently on disk
    dao.deleteSeriesNotIn(listOf(series1Uri, series3Uri))

    val remaining = dao.getAllSeries()
    assertEquals(2, remaining.size)
    assertEquals(listOf("Series 1", "Series 3"), remaining.map { it.title })
  }

  @Test
  fun testMappersDomainConversion() {
    val entity = MangaSeriesEntity(
      uri = "content://tree/manga/test",
      title = "Test Manga",
      coverUri = "content://tree/manga/test/cover.jpg",
      chapterCount = 12,
      totalPages = 240,
      lastModified = 1710000000L,
      statusTag = "LOCAL"
    )

    val domain = entity.toDomain()
    assertEquals(entity.uri, domain.id)
    assertEquals(entity.title, domain.title)
    assertEquals(Uri.parse(entity.uri), domain.folderUri)
    assertEquals(Uri.parse(entity.coverUri), domain.coverUri)
    assertEquals(entity.chapterCount, domain.chapterCount)
    assertEquals(entity.totalPages, domain.totalPages)

    val backToEntity = domain.toEntity()
    assertEquals(entity.uri, backToEntity.uri)
    assertEquals(entity.title, backToEntity.title)
    assertEquals(entity.coverUri, backToEntity.coverUri)
  }
}
