package com.example

import com.example.data.storage.StorageManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testExtractChapterNumber() {
    assertEquals(15.0, StorageManager.extractChapterNumber("Chapter 15"), 0.001)
    assertEquals(15.5, StorageManager.extractChapterNumber("Ch. 15.5"), 0.001)
    assertEquals(15.0, StorageManager.extractChapterNumber("Solo Leveling Season 2 Chapter 15"), 0.001)
    assertEquals(45.0, StorageManager.extractChapterNumber("Manga - 45"), 0.001)
    assertEquals(100.0, StorageManager.extractChapterNumber("Ch_100_final"), 0.001)
    assertEquals(3.0, StorageManager.extractChapterNumber("Episode 3"), 0.001)
    assertEquals(0.0, StorageManager.extractChapterNumber("Prologue"), 0.001)
  }

  @Test
  fun testNaturalOrderComparator() {
    val items = listOf("Chapter 10", "Chapter 1", "Chapter 2", "Chapter 20", "Chapter 3")
    val sorted = items.sortedWith(StorageManager.NATURAL_ORDER_COMPARATOR)
    assertEquals(listOf("Chapter 1", "Chapter 2", "Chapter 3", "Chapter 10", "Chapter 20"), sorted)

    val files = listOf("010.jpg", "001.jpg", "002.jpg")
    val sortedFiles = files.sortedWith(StorageManager.NATURAL_ORDER_COMPARATOR)
    assertEquals(listOf("001.jpg", "002.jpg", "010.jpg"), sortedFiles)
  }

  @Test
  fun testSanitizeFileName() {
    assertEquals("Omniscient Reader_ Side Story_", StorageManager.sanitizeFileName("Omniscient Reader: Side Story?"))
    assertEquals("Untitled", StorageManager.sanitizeFileName(""))
    assertEquals("Solo Leveling", StorageManager.sanitizeFileName("Solo Leveling"))
  }

  @Test
  fun testCacheOperations() {
    StorageManager.clearCache()
    StorageManager.invalidateSeriesCache("test_uri")
    // Verification that clear and invalidate execute cleanly without exceptions
    assertEquals(true, true)
  }
}
