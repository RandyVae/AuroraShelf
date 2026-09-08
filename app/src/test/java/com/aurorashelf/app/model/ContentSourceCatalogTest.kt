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

            assertEquals(source.kioskIds.size.coerceAtMost(FeedCategory.entries.size), categories.size)
            categories.forEachIndexed { index, category ->
                assertEquals(source.kioskIds[index], ContentSourceCatalog.kioskId(source.baseUrl, category))
            }
        }
    }
}
