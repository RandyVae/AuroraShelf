package com.aurorashelf.app.data.comic

import com.aurorashelf.app.model.ComicPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

internal object ComicPageResolver {
    suspend fun resolve(page: ComicPage): ComicPage {
        val resolutionUrl = page.resolutionUrl ?: return page
        return withContext(Dispatchers.IO) {
            val headers = mapOf("Cookie" to "nw=1", "Referer" to page.referer)
            val document = Jsoup.parse(ComicHttp.get(resolutionUrl, headers), resolutionUrl)
            val imageUrl = document.selectFirst("#img[src]")?.absUrl("src").orEmpty()
            check(imageUrl.startsWith("https://")) { "E-Hentai 原图地址解析失败" }
            page.copy(imageUrl = imageUrl)
        }
    }
}
