package com.aurorashelf.app

import androidx.test.platform.app.InstrumentationRegistry
import com.aurorashelf.app.data.comic.ComicRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** Optional end-to-end comic check. Remote titles and image addresses are never logged. */
class LiveComicTest {
    @Test
    fun komiicBrowseDetailsAndChapterAreReadable() {
        if (InstrumentationRegistry.getArguments().getString("live") != "true") return
        runBlocking {
            val repository = ComicRepository()
            val comics = repository.browse(sourceId = "komiic", categoryId = "0", page = 1)
            assertTrue("Expected Komiic records", comics.isNotEmpty())
            assertTrue("Expected valid comic summaries", comics.all { it.id.isNotBlank() && it.title.isNotBlank() })

            val details = repository.details(comics.first())
            assertTrue("Expected comic chapters", details.chapters.isNotEmpty())

            val pages = repository.chapter(details.comic, details.chapters.first().id)
            assertTrue("Expected chapter images", pages.isNotEmpty())
            assertTrue("Expected secure image addresses and referers", pages.all {
                it.imageUrl.startsWith("https://") && it.referer.startsWith("https://")
            })
            android.util.Log.i(
                "LiveComicTest",
                "Verified comics=${comics.size}, chapters=${details.chapters.size}, pages=${pages.size}; content omitted",
            )
        }
    }
}
