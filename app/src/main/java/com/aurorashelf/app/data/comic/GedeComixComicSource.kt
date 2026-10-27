package com.aurorashelf.app.data.comic

import com.aurorashelf.app.model.ComicCategory
import com.aurorashelf.app.model.ComicChapter
import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSourceInfo
import com.aurorashelf.app.model.ComicSummary
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

internal class GedeComixComicSource : ComicSource {
    override val info = ComicSourceInfo(
        id = "gedecomix",
        name = "GEDE Comix",
        description = "欧美原版 · 全彩同人志与成人漫",
        categories = listOf(
            ComicCategory("latest", "最新"),
            ComicCategory("views", "热度"),
            ComicCategory("trending", "热门"),
            ComicCategory("rating", "评分"),
        ),
    )

    override suspend fun browse(categoryId: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        val url = when (categoryId) {
            "views" -> if (page <= 1) "$BASE_URL/porn-comics/?m_orderby=views" else "$BASE_URL/porn-comics/page/$page/?m_orderby=views"
            "trending" -> if (page <= 1) "$BASE_URL/porn-comics/?m_orderby=trending" else "$BASE_URL/porn-comics/page/$page/?m_orderby=trending"
            "rating" -> if (page <= 1) "$BASE_URL/porn-comics/?m_orderby=rating" else "$BASE_URL/porn-comics/page/$page/?m_orderby=rating"
            else -> if (page <= 1) "$BASE_URL/porn-comics/" else "$BASE_URL/porn-comics/page/$page/"
        }
        val html = ComicHttp.get(url, defaultHeaders())
        parseListHtml(html)
    }

    override suspend fun search(query: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val url = if (page <= 1) "$BASE_URL/?s=$encoded&post_type=wp-manga" else "$BASE_URL/page/$page/?s=$encoded&post_type=wp-manga"
        val html = ComicHttp.get(url, defaultHeaders())
        parseListHtml(html)
    }

    override suspend fun details(comic: ComicSummary): ComicDetails = withContext(Dispatchers.IO) {
        val url = if (comic.id.startsWith("http")) comic.id else "$BASE_URL/porncomic/${comic.id}/"
        val html = ComicHttp.get(url, defaultHeaders())
        val document = Jsoup.parse(html, BASE_URL)

        val title = document.selectFirst(".post-title h1, h1")?.text()?.trim()?.ifBlank { comic.title } ?: comic.title
        val author = document.selectFirst(".author-content a, .mg_author .author-content")?.text()?.trim()
            ?: comic.subtitle
        val desc = document.selectFirst(".description-summary, .manga-excerpt")?.text()?.trim().orEmpty()
        val tags = document.select(".genres-content a, .mg_genres a").map { it.text().trim() }.filter(String::isNotBlank)

        val chapters = document.select("li.wp-manga-chapter a").mapIndexed { index, a ->
            val href = a.attr("href").trim()
            val chapTitle = a.text().trim().ifBlank { "第 ${index + 1} 话" }
            ComicChapter(
                id = href,
                title = chapTitle,
            )
        }.ifEmpty {
            listOf(ComicChapter(id = url, title = "全本"))
        }

        val updatedComic = comic.copy(
            title = title,
            subtitle = author,
            coverUrl = comic.coverUrl ?: document.selectFirst(".summary_image img")?.let { img ->
                img.attr("data-src").takeIf(String::isNotBlank) ?: img.attr("src").takeIf(String::isNotBlank)
            },
            tags = tags.ifEmpty { comic.tags },
        )

        ComicDetails(
            comic = updatedComic,
            description = desc,
            chapters = chapters,
        )
    }

    override suspend fun chapter(comic: ComicSummary, chapterId: String): List<ComicPage> = withContext(Dispatchers.IO) {
        val chapterUrl = if (chapterId.startsWith("http")) chapterId else "$BASE_URL/$chapterId"
        val html = ComicHttp.get(chapterUrl, defaultHeaders())
        val document = Jsoup.parse(html, BASE_URL)

        val imageElements = document.select(".reading-content img, .page-break img, .wp-manga-chapter-img")
        imageElements.mapNotNull { img ->
            val src = img.attr("data-src").trim().takeIf(String::isNotBlank)
                ?: img.attr("src").trim().takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            ComicPage(
                imageUrl = ComicHttp.resolve(BASE_URL, src) ?: src,
                referer = chapterUrl,
            )
        }
    }

    private fun parseListHtml(html: String): List<ComicSummary> {
        val document = Jsoup.parse(html, BASE_URL)
        val cards = document.select(".page-item-detail, .c-tabs-item__content, div[id^=manga-item-]")
        return cards.mapNotNull { card ->
            val anchor = card.selectFirst(".item-thumb a, .post-title a, a[href*=/porncomic/]") ?: return@mapNotNull null
            val href = anchor.attr("href").trim()
            val slug = SLUG_REGEX.find(href)?.groupValues?.get(1)?.trim('/') ?: return@mapNotNull null
            val title = anchor.attr("title").trim().ifBlank {
                card.selectFirst(".post-title a, h3 a, h5 a")?.text()?.trim().orEmpty()
            }.ifBlank { "Manga $slug" }

            val img = card.selectFirst("img")
            val coverUrl = img?.attr("data-src")?.takeIf(String::isNotBlank)
                ?: img?.attr("src")?.takeIf(String::isNotBlank)

            val author = card.selectFirst(".mg_author, .author-content, .post-on")?.text()?.trim().orEmpty()
            val tags = card.select(".mg_genres a, .genres-content a").map { it.text().trim() }.filter(String::isNotBlank)

            ComicSummary(
                sourceId = info.id,
                id = slug,
                title = title,
                subtitle = author,
                coverUrl = ComicHttp.resolve(BASE_URL, coverUrl),
                tags = tags,
                coverReferer = BASE_URL,
            )
        }.distinctBy(ComicSummary::id)
    }

    private fun defaultHeaders() = mapOf(
        "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "$BASE_URL/",
    )

    companion object {
        private const val BASE_URL = "https://gedecomix.com"
        private val SLUG_REGEX = Regex("/porncomic/([^/?#]+)")
    }
}
