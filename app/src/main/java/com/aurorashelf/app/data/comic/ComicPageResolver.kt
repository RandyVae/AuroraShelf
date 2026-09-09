package com.aurorashelf.app.data.comic

import com.aurorashelf.app.model.ComicPage
import java.util.Collections
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

internal object ComicPageResolver {
    private val resolutionCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, ComicPage>(CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ComicPage>?): Boolean =
                size > CACHE_SIZE
        },
    )

    suspend fun resolve(page: ComicPage): ComicPage {
        val resolutionUrl = page.resolutionUrl ?: return page
        resolutionCache[resolutionUrl]?.let { return it }
        return withContext(Dispatchers.IO) {
            val headers = EhentaiComicSource.HEADERS + ("Referer" to page.referer)
            val (body, finalUrl) = EhentaiHttp.get(resolutionUrl, headers)
            val document = Jsoup.parse(body, finalUrl)
            val imageUrl = document.selectFirst("#img[src]")?.absUrl("src").orEmpty()
            check(imageUrl.startsWith("https://")) { "E-Hentai 原图地址解析失败" }
            page.copy(imageUrl = imageUrl).also { resolutionCache[resolutionUrl] = it }
        }
    }

    private const val CACHE_SIZE = 256
}
