package com.aurorashelf.app.data.comic

import com.aurorashelf.app.model.ComicCategory
import com.aurorashelf.app.model.ComicChapter
import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSourceInfo
import com.aurorashelf.app.model.ComicSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ComicRepositoryTest {

    private class TestComicSource(override val info: ComicSourceInfo) : ComicSource {
        val chapterCallCount = AtomicInteger(0)

        override suspend fun browse(categoryId: String, page: Int): List<ComicSummary> = emptyList()
        override suspend fun search(query: String, page: Int): List<ComicSummary> = emptyList()
        override suspend fun details(comic: ComicSummary): ComicDetails = ComicDetails(comic, "", listOf(ComicChapter("ch1", "第 1 话")))
        override suspend fun chapter(comic: ComicSummary, chapterId: String): List<ComicPage> {
            chapterCallCount.incrementAndGet()
            return listOf(ComicPage("https://cdn.example.com/p1.webp", "https://example.com"))
        }
    }

    @Test
    fun prewarmChapterCachesResultAndSubsequentChapterCallHitsCache() = runBlocking {
        val sourceInfo = ComicSourceInfo("test", "Test Source", "", listOf(ComicCategory("all", "全部")))
        val testSource = TestComicSource(sourceInfo)
        val repository = ComicRepository(listOf(testSource))
        val comic = ComicSummary("test", "comic123", "测试漫画")

        assertNull(repository.getCachedChapter(comic, "ch1"))

        // Prewarm chapter
        repository.prewarmChapter(comic, "ch1")
        assertEquals(1, testSource.chapterCallCount.get())

        // Cached check
        val cached = repository.getCachedChapter(comic, "ch1")
        assertNotNull(cached)
        assertEquals(1, cached?.size)
        assertEquals("https://cdn.example.com/p1.webp", cached?.first()?.imageUrl)

        // Chapter call should hit cache without calling source again
        val pages = repository.chapter(comic, "ch1")
        assertEquals(1, testSource.chapterCallCount.get())
        assertEquals(1, pages.size)
    }

    @Test
    fun emptyChapterIsNotCachedSoSubsequentCallRetriesSource() = runBlocking {
        val calls = AtomicInteger(0)
        val source = object : ComicSource {
            override val info = ComicSourceInfo("flaky", "Flaky Source", "", listOf(ComicCategory("all", "全部")))
            override suspend fun browse(categoryId: String, page: Int) = emptyList<ComicSummary>()
            override suspend fun search(query: String, page: Int) = emptyList<ComicSummary>()
            override suspend fun details(comic: ComicSummary) = ComicDetails(comic, "", listOf(ComicChapter("c1", "第 1 话")))
            override suspend fun chapter(comic: ComicSummary, chapterId: String): List<ComicPage> {
                val n = calls.incrementAndGet()
                return if (n == 1) emptyList() else listOf(ComicPage("https://cdn.example.com/ok.jpg", "https://flaky.example.com/"))
            }
        }
        val repo = ComicRepository(listOf(source))
        val comic = ComicSummary("flaky", "c-100", "容错测试漫画")

        val first = repo.chapter(comic, "c1")
        assertEquals(0, first.size)
        assertNull(repo.getCachedChapter(comic, "c1"))

        val retry = repo.chapter(comic, "c1")
        assertEquals(1, retry.size)
        assertEquals(2, calls.get())
    }
}
