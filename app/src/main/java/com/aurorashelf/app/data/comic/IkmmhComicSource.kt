package com.aurorashelf.app.data.comic

import com.aurorashelf.app.model.ComicChapter
import com.aurorashelf.app.model.ComicCategory
import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSourceInfo
import com.aurorashelf.app.model.ComicSummary
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal class IkmmhComicSource : ComicSource {
    override val info = ComicSourceInfo(
        "ikmmh",
        "爱看漫",
        "中文漫画 · 多类型筛选",
        listOf(
            ComicCategory("全部", "全部"), ComicCategory("长条", "长条"),
            ComicCategory("大女主", "大女主"), ComicCategory("百合", "百合"),
            ComicCategory("耽美", "耽美"), ComicCategory("奇幻", "奇幻"),
            ComicCategory("都市", "都市"), ComicCategory("热血", "热血"),
        ),
    )

    override suspend fun browse(categoryId: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        val category = URLEncoder.encode(categoryId, Charsets.UTF_8.name())
        val pageUrl = "$BASE_URL/booklists/9/$category/3/$page.html"
        val document = Jsoup.parse(ComicHttp.get(pageUrl), BASE_URL)
        document.select("ul.list-comic-book > li").mapNotNull { element ->
            val anchor = element.selectFirst("a") ?: return@mapNotNull null
            summaryFrom(
                anchor,
                element.selectFirst("img"),
                element.selectFirst("h2")?.text().orEmpty(),
                element.selectFirst("p.process")?.text().orEmpty(),
            )
        }.distinctBy { it.id }
    }

    override suspend fun search(query: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext emptyList()
        val url = "$BASE_URL/search?searchkey=${URLEncoder.encode(query, Charsets.UTF_8.name())}"
        Jsoup.parse(ComicHttp.get(url), BASE_URL).select("div.classification").mapNotNull { element ->
            val anchor = element.selectFirst("a") ?: return@mapNotNull null
            summaryFrom(anchor, element.selectFirst("img"), anchor.text(), element.selectFirst("p.describe")?.text().orEmpty())
        }
    }

    override suspend fun details(comic: ComicSummary): ComicDetails = withContext(Dispatchers.IO) {
        val url = comic.id.takeIf { it.startsWith("http") } ?: "$BASE_URL/book/${comic.id}.html"
        val document = Jsoup.parse(ComicHttp.get(url), BASE_URL)
        val chapters = document.select("ol.chapter-list > li").mapNotNull { element ->
            val anchor = element.selectFirst("a") ?: return@mapNotNull null
            val id = element.attr("data-chapter").ifBlank {
                anchor.attr("href").substringAfterLast('/').substringBefore('.')
            }
            id.takeIf(String::isNotBlank)?.let {
                ComicChapter(it, anchor.attr("title").ifBlank { anchor.text() }.ifBlank { "章节" })
            }
        }
        ComicDetails(
            comic = comic.copy(
                title = document.selectFirst("h1.detail-title")?.text()?.trim().orEmpty().ifBlank { comic.title },
                subtitle = document.selectFirst("p.author")?.text()?.trim().orEmpty().ifBlank { comic.subtitle },
                coverUrl = imageUrl(url, document.selectFirst("div.banner-img img")) ?: comic.coverUrl,
                tags = document.select("p.ui-tag a").map { it.text().trim() }.filter(String::isNotBlank),
            ),
            description = document.selectFirst("div.detail-desc")?.text()?.trim().orEmpty(),
            chapters = chapters,
        )
    }

    override suspend fun chapter(comic: ComicSummary, chapterId: String): List<ComicPage> = withContext(Dispatchers.IO) {
        val comicId = comic.id.substringAfter("/book/").substringBefore('/')
        val pageUrl = "$BASE_URL/chapter/$comicId/$chapterId.html"
        Jsoup.parse(ComicHttp.get(pageUrl, mapOf("Referer" to "$BASE_URL/book/$comicId.html")), BASE_URL)
            .select("img.lazy").mapNotNull { imageUrl(pageUrl, it)?.let { url -> ComicPage(url, pageUrl) } }
    }

    private fun summaryFrom(anchor: Element, image: Element?, title: String, subtitle: String): ComicSummary? {
        val pageUrl = ComicHttp.resolve(BASE_URL, anchor.attr("href")) ?: return null
        if (title.isBlank()) return null
        return ComicSummary(info.id, pageUrl, title.trim(), subtitle.trim(), imageUrl(BASE_URL, image))
    }

    private fun imageUrl(base: String, image: Element?): String? = image?.let {
        ComicHttp.resolve(base, it.attr("data-src").ifBlank { it.attr("src") })
    }

    private companion object {
        const val BASE_URL = "https://ymcdnyfqdapp.ikmmh.com"
    }
}
