package com.aurorashelf.app

import androidx.test.platform.app.InstrumentationRegistry
import com.aurorashelf.app.data.AppPreferences
import com.aurorashelf.app.data.SourceAddress
import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.model.FeedCategory
import com.aurorashelf.app.model.ContentSourceCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** Optional network smoke check. Never prints or captures remote titles or images. */
class LiveFeedTest {
    @Test fun configuredSourceReturnsUsableRecords() {
        if (InstrumentationRegistry.getArguments().getString("live") != "true") return
        runBlocking {
            val repository = VideoRepository()
            val source = InstrumentationRegistry.getArguments().getString("source") ?: AppPreferences.DEFAULT_SOURCE
            val records = repository.loadFeed(source, FeedCategory.MONTHLY_HOT, page = 1)
            val nextPage = repository.loadFeed(source, FeedCategory.MONTHLY_HOT, page = 2)
            assertTrue("Expected parsed remote records", records.isNotEmpty())
            if (source.contains("huangguoai.com", ignoreCase = true) || ContentSourceCatalog.isMissAv(source)) {
                // Fixed server-curated kiosks legitimately end after their first page.
                assertTrue("Expected the fixed live kiosk to stop at page 1", nextPage.isEmpty())
            } else if (nextPage.isNotEmpty()) {
                // A source may expose a valid one-page kiosk. When it advertises another
                // page, that page must contain at least one new record.
                assertTrue("Expected page 2 to add records", nextPage.any { candidate -> records.none { it.id == candidate.id } })
            }
            assertTrue("Every record needs a title and valid page address", records.all {
                it.title.isNotBlank() && SourceAddress.resolve(it.pageUrl, it.pageUrl) != null
            })
            assertTrue("Expected at least one thumbnail address", records.any { it.thumbnailUrl != null })
            android.util.Log.i(
                "LiveFeedTest",
                "Verified page1=${records.size}, page2=${nextPage.size}; content omitted",
            )
        }
    }
}
