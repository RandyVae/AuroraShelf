package com.aurorashelf.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentSourceCatalogTest {
    @Test
    fun exposesAllVendoredPipePipeServicesWithUsableKiosks() {
        val pipeSources = ContentSourceCatalog.sources.filter { it.extractorServiceName != null }
        val expectedServices = setOf(
            "MissAV", "KissJAV", "85po", "Pornhub", "JAV-NONI", "JAVSB", "TOKYO Motion",
            "SpankBang", "xHamster", "XVideos", "EPORNER", "MRDOUGA", "OHentai", "XNXX",
        )

        assertEquals(expectedServices, pipeSources.map(ContentSource::name).toSet())
        assertEquals(pipeSources.size, pipeSources.map(ContentSource::id).distinct().size)
        assertTrue(pipeSources.all { it.baseUrl.startsWith("https://") && it.kioskIds.isNotEmpty() })
    }

    @Test
    fun eachPipePipeCategoryMapsToItsOwnKiosk() {
        ContentSourceCatalog.sources.filter { it.extractorServiceName != null }.forEach { source ->
            val categories = FeedCategory.availableFor(source.baseUrl)

            if (ContentSourceCatalog.isXVideos(source.baseUrl)) {
                // XVideos exposes the full official category list (41 entries)
                assertEquals(FeedCategory.XVIDEOS_CATEGORIES.size, categories.size)
                assertEquals(41, categories.size)
                assertEquals(41, categories.mapNotNull { it.xvideosKioskId }.distinct().size)
                FeedCategory.XVIDEOS_CATEGORIES.forEach { category ->
                    assertEquals(category.xvideosKioskId, ContentSourceCatalog.kioskId(source.baseUrl, category))
                }
            } else {
                assertEquals(source.kioskIds.size.coerceAtMost(FeedCategory.DEFAULT_5.size), categories.size)
                categories.forEachIndexed { index, category ->
                    assertEquals(source.kioskIds[index], ContentSourceCatalog.kioskId(source.baseUrl, category))
                }
            }
        }
    }
}
