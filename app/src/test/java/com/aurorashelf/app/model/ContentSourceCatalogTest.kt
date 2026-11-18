package com.aurorashelf.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentSourceCatalogTest {
    @Test
    fun defaultsToStableDirectSource() {
        assertEquals(ContentSourceCatalog.T91DONGMAN_BASE_URL, ContentSourceCatalog.DEFAULT_BASE_URL)
        assertTrue(ContentSourceCatalog.find(ContentSourceCatalog.DEFAULT_BASE_URL)?.isDirectDomestic == true)
    }

    @Test
    fun exposesAllVendoredPipePipeServicesWithUsableKiosks() {
        val pipeSources = ContentSourceCatalog.sources.filter { it.extractorServiceName != null }
        val expectedServices = setOf(
            "MissAV", "KissJAV", "85po", "Pornhub", "TOKYO Motion",
            "SpankBang", "xHamster", "XVideos", "EPORNER", "MRDOUGA", "OHentai", "XNXX",
        )

        assertEquals(expectedServices, pipeSources.map(ContentSource::name).toSet())
        assertTrue(ContentSourceCatalog.sources.none { it.name == "JAV-NONI" || it.name == "JAVSB" })
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

    @Test
    fun sourceCategoriesExposeEveryOfficialPipePipeKiosk() {
        ContentSourceCatalog.sources.filter { it.extractorServiceName != null }.forEach { source ->
            val categories = ContentSourceCatalog.sourceCategories(source.baseUrl)
            assertEquals(source.kioskIds, categories.mapNotNull(SourceCategory::kioskId))
            assertEquals(source.kioskIds.distinct().size, categories.size)
        }
        assertTrue(ContentSourceCatalog.sourceCategories(ContentSourceCatalog.HUAVOD_BASE_URL).size >= 15)
        assertEquals(5, ContentSourceCatalog.sourceCategories(ContentSourceCatalog.HUANGGUO_BASE_URL).size)
        assertTrue(ContentSourceCatalog.sourceCategories(ContentSourceCatalog.HUANGGUO_BASE_URL).none { it.label.contains("换脸") })
        assertEquals(9, ContentSourceCatalog.sourceCategories(ContentSourceCatalog.T91DONGMAN_BASE_URL).size)
        assertEquals(12, ContentSourceCatalog.sourceCategories(ContentSourceCatalog.T91_BASE_URL).size)
        assertEquals(8, ContentSourceCatalog.sourceCategories(ContentSourceCatalog.BEEG_BASE_URL).size)
    }
}
