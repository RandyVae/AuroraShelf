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

internal class Dongman91ComicSource : ComicSource {
    override val info = ComicSourceInfo(
        id = "91dongman",
        name = "91动漫",
        description = "里番漫画 · 同人志与全彩韩漫",
        categories = listOf(
            ComicCategory("tongrenzhi", "同人志"),
            ComicCategory("books", "单行本"),
            ComicCategory("hanman", "韩漫"),
            ComicCategory("ranking", "排行榜"),
        ),
    )

    override suspend fun browse(categoryId: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        val path = when (categoryId) {
            "books" -> if (page <= 1) "/manga/books/" else "/manga/books/page/$page/"
            "hanman" -> if (page <= 1) "/manga/hanman/" else "/manga/hanman/page/$page/"
            "ranking" -> if (page <= 1) "/ranking/" else "/ranking/page/$page/"
            else -> if (page <= 1) "/manga/tongrenzhi/" else "/manga/tongrenzhi/page/$page/"
        }
        val url = "$BASE_URL$path"
        val html = ComicHttp.get(url, defaultHeaders())
        parseListHtml(html)
    }

    override suspend fun search(query: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val url = if (page <= 1) "$BASE_URL/search-result/?q=$encoded" else "$BASE_URL/search-result/?q=$encoded&page=$page"
        val html = ComicHttp.get(url, defaultHeaders())
        parseListHtml(html)
    }

    override suspend fun details(comic: ComicSummary): ComicDetails = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/comic/${comic.id}/"
        val html = ComicHttp.get(url, defaultHeaders())
        val document = Jsoup.parse(html, BASE_URL)

        val title = document.selectFirst(".dm-comic-head h1, h1")?.text()?.trim()?.ifBlank { comic.title } ?: comic.title
        val author = document.selectFirst(".dm-comic-meta__item a, .dm-author")?.text()?.trim()
            ?: comic.subtitle
        val desc = document.selectFirst(".dm-comic-intro, .dm-intro, meta[name=description]")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        }?.trim().orEmpty()

        val tags = document.select(".dm-comic-tag, .dm-tag, .dm-card__tag").map { it.text().trim() }.filter(String::isNotBlank)

        val chapterLinks = document.select("a[href*=/reader/]")
        val seenChapters = mutableSetOf<String>()
        val chapters = chapterLinks.mapNotNull { a ->
            val href = a.attr("href").trim()
            val chapterId = READER_ID_REGEX.find(href)?.groupValues?.get(1) ?: return@mapNotNull null
            val rawTitle = a.text().trim()
            if (rawTitle == "开始阅读" && chapterLinks.size > 1) return@mapNotNull null
            if (!seenChapters.add(chapterId)) return@mapNotNull null
            ComicChapter(
                id = chapterId,
                title = rawTitle.ifBlank { "第 $chapterId 话" },
            )
        }.ifEmpty {
            listOf(ComicChapter(id = comic.id, title = "开始阅读"))
        }

        val updatedComic = comic.copy(
            title = title,
            subtitle = author,
            coverUrl = comic.coverUrl ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf(String::isNotBlank),
            tags = tags.ifEmpty { comic.tags },
        )

        ComicDetails(
            comic = updatedComic,
            description = desc,
            chapters = chapters,
        )
    }

    override suspend fun chapter(comic: ComicSummary, chapterId: String): List<ComicPage> = withContext(Dispatchers.IO) {
        val readerUrl = "$BASE_URL/reader/$chapterId/"
        val html = ComicHttp.get(readerUrl, defaultHeaders())
        val document = Jsoup.parse(html, BASE_URL)

        val imageElements = document.select("img[data-src], img[src]")
        imageElements.mapNotNull { img ->
            val src = img.attr("data-src").trim().takeIf(String::isNotBlank)
                ?: img.attr("src").trim().takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            if (!src.contains("upload") && !src.contains("pic.")) return@mapNotNull null
            ComicPage(
                imageUrl = ComicHttp.resolve(BASE_URL, src) ?: src,
                referer = readerUrl,
            )
        }
    }

    private fun parseListHtml(html: String): List<ComicSummary> {
        val document = Jsoup.parse(html, BASE_URL)
        val cards = document.select(".dm-card, .dm-comic-card, a.dm-card__media[href*=/comic/]")
        return cards.mapNotNull { el ->
            val anchor = if (el.tagName() == "a") el else el.selectFirst("a[href*=/comic/]") ?: return@mapNotNull null
            val href = anchor.attr("href").trim()
            val id = COMIC_ID_REGEX.find(href)?.groupValues?.get(1) ?: return@mapNotNull null

            val parent = el.parent() ?: el
            val title = parent.selectFirst(".dm-card__title h3, h3, a[title]")?.text()?.trim()
                ?: anchor.selectFirst("img")?.attr("alt")?.trim()
                ?: "Comic $id"

            val img = parent.selectFirst("img[data-src], img[src]")
            val coverUrl = img?.attr("data-src")?.takeIf(String::isNotBlank)
                ?: img?.attr("src")?.takeIf(String::isNotBlank)

            val badge = parent.selectFirst(".dm-card__badge, .dm-chip")?.text()?.trim().orEmpty()
            val tags = parent.select(".dm-card__tag").map { it.text().trim() }.filter(String::isNotBlank)

            ComicSummary(
                sourceId = info.id,
                id = id,
                title = title,
                subtitle = badge,
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
        private const val BASE_URL = "https://91dongman.net"
        private val COMIC_ID_REGEX = Regex("/comic/(\\d+)")
        private val READER_ID_REGEX = Regex("/reader/(\\d+)")
    }
}
