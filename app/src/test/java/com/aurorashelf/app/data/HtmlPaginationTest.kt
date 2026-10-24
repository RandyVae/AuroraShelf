package com.aurorashelf.app.data

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.utils.HtmlPagination
import org.schabi.newpipe.extractor.services.eightyfivepo.EightyFivePoKioskExtractor

class HtmlPaginationTest {
    @Test fun readsAsyncPagerParametersWithoutGuessingPageNumber() {
        val document = Jsoup.parse("<div class='pagination'><a class='next' data-block-id='list_videos' data-parameters='sort_by:post_date;from:2'></a></div>", "https://example.com/latest/")
        assertEquals("https://example.com/latest/?mode=async&function=get_block&block_id=list_videos&sort_by=post_date&from=2", EightyFivePoKioskExtractor.nextPage(document)?.url)
        document.selectFirst("a")!!.attr("data-block-id", "invalid&injected=true")
        assertNull(EightyFivePoKioskExtractor.nextPage(document))
    }

    @Test fun followsExplicitRelativeNextLink() {
        val document = Jsoup.parse("<a rel='next' href='?page=2'>Next</a>", "https://example.com/list")
        assertEquals("https://example.com/list?page=2", HtmlPagination.nextPage(document)?.url)
    }

    @Test fun followsPaginationContainer() {
        val document = Jsoup.parse("<div class='pagination'><span class='next'><a href='/list/2'>2</a></span></div>", "https://example.com/list")
        assertEquals("https://example.com/list/2", HtmlPagination.nextPage(document)?.url)
    }

    @Test fun doesNotGuessNextPageOrFollowAds() {
        val document = Jsoup.parse("<a rel='next' href='https://ads.example/2'>Next</a><a href='/list/2'>2</a>", "https://example.com/list")
        assertNull(HtmlPagination.nextPage(document))
    }

    @Test fun rejectsUnsafeAndSelfLinks() {
        for (target in listOf("http://example.com/list/2", "javascript:alert(1)",
            "https://example.com/list", "https://example.com/list#next", "https://u@example.com/list/2")) {
            assertFalse(target, HtmlPagination.isSafeNext("https://example.com/list", target))
        }
    }

    @Test fun directionalPagerDoesNotFollowPreviousPage() {
        val document = Jsoup.parse("<a class='prevnext' href='/page=1'>«</a><a class='prevnext' href='/page=3'>»</a>", "https://example.com/page=2")
        assertEquals("https://example.com/page=3", HtmlPagination.nextPage(document)?.url)
    }

    @Test fun decodesEntireAbsolutePagerUrlButStillRejectsOtherHosts() {
        val document = Jsoup.parse("<a rel='next' href='https%3A%2F%2Fexample.com%2Fvideos%3Fpage%3D2'>Next</a>", "https://example.com/videos")
        assertEquals("https://example.com/videos?page=2", HtmlPagination.nextPage(document)?.url)
        document.selectFirst("a")!!.attr("href", "https%3A%2F%2Fother.example%2Fvideos%3Fpage%3D2")
        assertNull(HtmlPagination.nextPage(document))
    }
}
