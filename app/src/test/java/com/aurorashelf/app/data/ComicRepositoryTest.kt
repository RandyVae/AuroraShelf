package com.aurorashelf.app.data

import com.aurorashelf.app.data.comic.ComicRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicRepositoryTest {
    private val sources = ComicRepository().availableSources

    @Test
    fun defaultCatalogContainsSevenDistinctNativeSources() {
        assertEquals(
            listOf("komiic", "baozi", "copy", "ikmmh", "picacg", "ehentai", "jm"),
            sources.map { it.id },
        )
        assertEquals(sources.size, sources.map { it.id }.distinct().size)
    }

    @Test
    fun everySourceExposesReadableCategories() {
        sources.forEach { source ->
            assertTrue("${source.name} must expose categories", source.categories.isNotEmpty())
            assertTrue(source.categories.all { it.label.isNotBlank() })
            assertTrue(source.categories.all { it.id.isNotBlank() || source.id == "copy" })
        }
    }

    @Test
    fun onlyPicacgRequiresAuthentication() {
        assertEquals(listOf("picacg"), sources.filter { it.requiresAuthentication }.map { it.id })
    }
}
