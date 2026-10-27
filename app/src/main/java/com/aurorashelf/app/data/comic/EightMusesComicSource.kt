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
import org.jsoup.nodes.Element

internal class EightMusesComicSource : ComicSource {
    override val info = ComicSourceInfo(
        id = "8muses",
        name = "8Muses",
        description = "欧美精选 · 3D与全彩成人漫",
        categories = listOf(
            ComicCategory("popular", "热门"),
            ComicCategory("new", "最新"),
            ComicCategory("top", "榜单"),
            ComicCategory("browse", "全部"),
        ),
    )

    override suspend fun browse(categoryId: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        val path = when (categoryId) {
            "popular" -> if (page <= 1) "/popular" else "/popular?page=$page"
            "new" -> if (page <= 1) "/new" else "/new?page=$page"
            "top" -> if (page <= 1) "/top" else "/top?page=$page"
            else -> if (page <= 1) "/browse" else "/browse?page=$page"
        }
        val url = "$BASE_URL$path"
        val html = ComicHttp.get(url, defaultHeaders())
        parseListHtml(html)
    }

    override suspend fun search(query: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val url = if (page <= 1) "$BASE_URL/search?q=$encoded" else "$BASE_URL/search?q=$encoded&page=$page"
        val html = ComicHttp.get(url, defaultHeaders())
        parseListHtml(html)
    }

    override suspend fun details(comic: ComicSummary): ComicDetails = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/g/${comic.id}"
        val html = ComicHttp.get(url, defaultHeaders())
        val document = Jsoup.parse(html, BASE_URL)

        val title = document.selectFirst("h1")?.text()?.trim()?.ifBlank { comic.title } ?: comic.title
        val author = document.selectFirst(".detail-hero__content a[href*=/artist/]")?.text()?.trim()
            ?: comic.subtitle
        val desc = document.selectFirst("meta[name=description]")?.attr("content")?.trim().orEmpty()

        // Extract total page count from meta/JSON or HTML length indicator
        val numberOfPagesRegex = Regex("\"numberOfPages\"\\s*:\\s*(\\d+)")
        val lengthTextRegex = Regex("(\\d+)\\s*(?:<!--\\s*-->)?\\s*pages", RegexOption.IGNORE_CASE)
        val pageCount = numberOfPagesRegex.find(html)?.groupValues?.get(1)?.toIntOrNull()
            ?: lengthTextRegex.find(html)?.groupValues?.get(1)?.toIntOrNull()
            ?: document.select(".detail-peek__grid button").size.takeIf { it > 0 }
            ?: 1

        val updatedComic = comic.copy(
            title = title,
            subtitle = author,
            coverUrl = comic.coverUrl ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf(String::isNotBlank),
            tags = document.select(".detail-badges .detail-badge, .detail-genre-row a").map { it.text().trim() }.filter(String::isNotBlank),
        )

        val chapter = ComicChapter(
            id = pageCount.toString(),
            title = "全本 ($pageCount 页)",
        )

        ComicDetails(
            comic = updatedComic,
            description = desc,
            chapters = listOf(chapter),
        )
    }

    override suspend fun chapter(comic: ComicSummary, chapterId: String): List<ComicPage> = withContext(Dispatchers.IO) {
        val totalPages = chapterId.toIntOrNull() ?: 1
        val referer = "$BASE_URL/g/${comic.id}"
        (1..totalPages).map { pageIndex ->
            ComicPage(
                imageUrl = "$BASE_URL/api/legacy-media/${comic.id}/$pageIndex",
                referer = referer,
            )
        }
    }

    private fun parseListHtml(html: String): List<ComicSummary> {
        val document = Jsoup.parse(html, BASE_URL)
        return document.select("a[href*=/g/]").mapNotNull { anchor ->
            val href = anchor.attr("href").trim()
            val id = ID_REGEX.find(href)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = anchor.selectFirst("strong")?.text()?.trim()
                ?: anchor.selectFirst("img")?.attr("alt")?.trim()
                ?: "Comic $id"
            val author = anchor.selectFirst("small")?.text()?.trim().orEmpty()
            val coverUrl = anchor.selectFirst("img.cover__image, img[src]")?.let { img ->
                img.attr("src").takeIf(String::isNotBlank)
                    ?: img.attr("data-src").takeIf(String::isNotBlank)
            }
            ComicSummary(
                sourceId = info.id,
                id = id,
                title = title,
                subtitle = author,
                coverUrl = ComicHttp.resolve(BASE_URL, coverUrl),
                coverReferer = BASE_URL,
            )
        }.distinctBy(ComicSummary::id)
    }

    private fun defaultHeaders() = mapOf(
        "User-Agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "$BASE_URL/",
    )

    companion object {
        private const val BASE_URL = "https://8muses.io"
        private val ID_REGEX = Regex("/g/(\\d+)")
    }
}
