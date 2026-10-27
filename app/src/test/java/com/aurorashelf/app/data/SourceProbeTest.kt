package com.aurorashelf.app.data

import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.FeedCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

class SourceProbeTest {
    @Test
    fun probeXVideosDirectly() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE_SOURCE_TESTS") == "1")
        val repo = VideoRepository()
        val resolver = PlaybackResolver()
        println("===== PROBING XVIDEOS DIRECTLY =====")
        val baseUrl = "https://www.xvideos.com"
        val items = repo.loadFeed(baseUrl, FeedCategory.RECENT, 1)
        println("XVideos Feed items count = ${items.size}")
        items.take(3).forEach { println("  - [${it.title}] url=${it.pageUrl} thumb=${it.thumbnailUrl} duration=${it.duration}") }
        if (items.isNotEmpty()) {
            val stream = resolver.resolve(items.first().pageUrl)
            println("XVideos playback resolved: ${stream.url}")
        }
        val searchResults = repo.search(baseUrl, "asian", 1)
        println("XVideos Search results count = ${searchResults.size}")
        searchResults.take(3).forEach { println("  - [${it.title}] url=${it.pageUrl}") }
    }

    @Test
    fun probeAllSourcesAndCategories() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE_SOURCE_TESTS") == "1")
        val repo = VideoRepository()
        val resolver = PlaybackResolver()
        println("===== PROBING ALL ${ContentSourceCatalog.sources.size} SOURCES (ALL CATEGORIES) =====")
        for (source in ContentSourceCatalog.sources) {
            println("--- [${source.name}] (${source.baseUrl}) kiosks=${source.kioskIds} ---")
            val categories = FeedCategory.availableFor(source.baseUrl)
            for (cat in categories) {
                try {
                    val items = repo.loadFeed(source.baseUrl, cat, 1)
                    print("  Cat [${cat.name}]: ${items.size} items")
                    val firstItem = items.firstOrNull()
                    if (firstItem != null) {
                        try {
                            val playback = resolver.resolve(firstItem.pageUrl)
                            println(" -> PLAYBACK OK")
                        } catch (e: Exception) {
                            println(" -> PLAYBACK FAILED: ${e::class.java.simpleName}: ${e.message}")
                        }
                    } else {
                        println(" -> EMPTY")
                    }
                } catch (e: Exception) {
                    println("  Cat [${cat.name}]: FAILED: ${e::class.java.simpleName}: ${e.message}")
                }
            }
        }
        println("===== PROBE FINISHED =====")
    }
}
