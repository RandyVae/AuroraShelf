package com.aurorashelf.app.data.comic

import com.aurorashelf.app.model.ComicSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewComicSourcesTest {
    @Test
    fun eightMusesExposesCategoriesAndMetadata() {
        val source = EightMusesComicSource()
        assertEquals("8muses", source.info.id)
        assertEquals("8Muses", source.info.name)
        val categories = source.info.categories.map { it.id }
        assertEquals(listOf("popular", "new", "top", "browse"), categories)
    }

    @Test
    fun eightMusesGeneratesCorrectChapterPages() = runBlocking {
        val source = EightMusesComicSource()
        val comic = ComicSummary(
            sourceId = "8muses",
            id = "27375",
            title = "Superman's Fantasy",
        )
        val pages = source.chapter(comic, "5")
        assertEquals(5, pages.size)
        assertEquals("https://8muses.io/api/legacy-media/27375/1", pages[0].imageUrl)
        assertEquals("https://8muses.io/g/27375", pages[0].referer)
        assertEquals("https://8muses.io/api/legacy-media/27375/5", pages[4].imageUrl)
    }

    @Test
    fun gedeComixExposesCategoriesAndMetadata() {
        val source = GedeComixComicSource()
        assertEquals("gedecomix", source.info.id)
        assertEquals("GEDE Comix", source.info.name)
        val categories = source.info.categories.map { it.id }
        assertEquals(listOf("latest", "views", "trending", "rating"), categories)
    }

    @Test
    fun dongman91ExposesCategoriesAndMetadata() {
        val source = Dongman91ComicSource()
        assertEquals("91dongman", source.info.id)
        assertEquals("91动漫", source.info.name)
        val categories = source.info.categories.map { it.id }
        assertEquals(listOf("tongrenzhi", "books", "hanman", "ranking"), categories)
    }
}
