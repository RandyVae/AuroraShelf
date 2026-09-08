package com.aurorashelf.app.data

import com.aurorashelf.app.data.comic.ComicRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicRepositoryTest {
    private val sources = ComicRepository().availableSources

    @Test
    fun defaultCatalogContainsFourDistinctNativeSources() {
        assertEquals(listOf("komiic", "baozi", "copy", "ikmmh"), sources.map { it.id })
        assertEquals(sources.size, sources.map { it.id }.distinct().size)
    }

    @Test
    fun everySourceExposesAnAllCategoryAndReadableLabels() {
        sources.forEach { source ->
            assertTrue("${source.name} must expose categories", source.categories.isNotEmpty())
            assertEquals("全部", source.categories.first().label)
            assertTrue(source.categories.all { it.id.isNotBlank() || source.id == "copy" })
        }
    }
}
