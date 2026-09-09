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

    @Test
    fun ehentaiBrowseDetailsAndPageResolutionAreReadable() {
        if (InstrumentationRegistry.getArguments().getString("live") != "true") return
        runBlocking {
            val repository = ComicRepository()
            val comics = repository.browse(sourceId = "ehentai", categoryId = "0", page = 1)
            assertTrue("Expected E-Hentai records", comics.isNotEmpty())
            assertTrue("Expected E-Hentai thumbnail addresses", comics.take(10).all {
                it.coverUrl?.startsWith("https://ehgt.org/") == true && it.coverReferer == "https://e-hentai.org"
            })

            val details = repository.details(comics.first())
            val pages = repository.chapter(details.comic, details.chapters.first().id)
            assertTrue("Expected E-Hentai page records", pages.isNotEmpty())
            val resolved = com.aurorashelf.app.data.comic.ComicPageResolver.resolve(pages.first())
            assertTrue("Expected secure E-Hentai image address", resolved.imageUrl.startsWith("https://"))
        }
    }

    @Test
    fun jmBrowseDetailsAndChapterAreReadable() {
        if (InstrumentationRegistry.getArguments().getString("live") != "true") return
        runBlocking {
            val repository = ComicRepository()
            val comics = repository.browse(sourceId = "jm", categoryId = "0", page = 1)
            assertTrue("Expected JM records", comics.isNotEmpty())

            val details = repository.details(comics.first())
            assertTrue("Expected JM chapters", details.chapters.isNotEmpty())
            val pages = repository.chapter(details.comic, details.chapters.first().id)
            assertTrue("Expected JM image records", pages.isNotEmpty())
            assertTrue("Expected secure JM image addresses", pages.all { it.imageUrl.startsWith("https://") })
        }
    }
}
