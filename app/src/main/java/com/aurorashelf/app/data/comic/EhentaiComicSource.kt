package com.aurorashelf.app.data.comic

import com.aurorashelf.app.model.ComicCategory
import com.aurorashelf.app.model.ComicChapter
import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSourceInfo
import com.aurorashelf.app.model.ComicSummary
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

internal class EhentaiComicSource : ComicSource {
    override val info = ComicSourceInfo(
        id = "ehentai",
        name = "E-Hentai",
        description = "E-Hentai 公共画廊 · 匿名浏览",
        categories = listOf(
            ComicCategory("0", "全部"),
            ComicCategory("2", "同人志"),
            ComicCategory("4", "漫画"),
            ComicCategory("8", "艺术家 CG"),
            ComicCategory("16", "游戏 CG"),
            ComicCategory("512", "西方"),
            ComicCategory("256", "无 H"),
            ComicCategory("32", "图片集"),
            ComicCategory("64", "Cosplay"),
        ),
    )

    private val cursors = ConcurrentHashMap<String, MutableMap<Int, String>>()

    override suspend fun browse(categoryId: String, page: Int): List<ComicSummary> =
        load(categoryId, query = "", page = page)

    override suspend fun search(query: String, page: Int): List<ComicSummary> =
        load(categoryId = "0", query = query, page = page)

    private suspend fun load(categoryId: String, query: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        val key = "$categoryId\u0000$query"
        val pageCursors = cursors.getOrPut(key) { ConcurrentHashMap<Int, String>() }
        if (page == 1) pageCursors.clear()
        val url = if (page == 1) listUrl(categoryId, query) else pageCursors[page] ?: return@withContext emptyList()
        val document = document(url)
        document.selectFirst("#dnext[href]")?.absUrl("href")?.takeIf(String::isNotBlank)?.let {
            pageCursors[page + 1] = it
        }
        document.select(".itg tr").mapNotNull { row ->
            val link = row.selectFirst(".glname a[href*=/g/]") ?: return@mapNotNull null
            val href = link.absUrl("href")
            val match = GALLERY_PATTERN.find(href) ?: return@mapNotNull null
            val title = row.selectFirst(".glink")?.text().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            val image = row.selectFirst(".glthumb img")
            val cover = image?.attr("data-src").orEmpty().ifBlank { image?.absUrl("src").orEmpty() }
            val category = row.selectFirst(".glcat .cn, .glthumb .cn")?.text().orEmpty()
            val pageCount = row.select(".glthumb div, .gl4c div").asSequence()
                .map { it.text() }.firstOrNull { PAGES_PATTERN.containsMatchIn(it) }.orEmpty()
            ComicSummary(
                sourceId = info.id,
                id = "${match.groupValues[1]}/${match.groupValues[2]}",
                title = title,
                subtitle = listOf(category, pageCount).filter(String::isNotBlank).distinct().joinToString(" · "),
                coverUrl = cover.takeIf { it.startsWith("https://") },
                tags = row.select(".gt, .gtl, .gtr").map { it.attr("title").ifBlank(it::text) }.filter(String::isNotBlank),
            )
        }
    }

    override suspend fun details(comic: ComicSummary): ComicDetails = withContext(Dispatchers.IO) {
        val url = galleryUrl(comic)
        val document = document(url)
        val title = document.selectFirst("#gn")?.text().orEmpty().ifBlank { comic.title }
        val alternateTitle = document.selectFirst("#gj")?.text().orEmpty()
        val author = document.selectFirst("#gdn a")?.text().orEmpty()
        val cover = COVER_PATTERN.find(document.selectFirst("#gd1 div")?.attr("style").orEmpty())?.groupValues?.get(1)
        val tags = document.select("#taglist .gt, #taglist .gtl, #taglist .gtr")
            .map { it.attr("title").ifBlank(it::text) }.filter(String::isNotBlank)
        ComicDetails(
            comic = comic.copy(
                title = title,
                subtitle = author.ifBlank { comic.subtitle },
                coverUrl = cover ?: comic.coverUrl,
                tags = tags.ifEmpty { comic.tags },
            ),
            description = alternateTitle.takeIf { it != title }.orEmpty(),
            chapters = listOf(ComicChapter(comic.id, "完整画廊")),
        )
    }

    override suspend fun chapter(comic: ComicSummary, chapterId: String): List<ComicPage> = withContext(Dispatchers.IO) {
        val galleryUrl = galleryUrl(comic)
        val first = document(galleryUrl)
        val pageCount = first.select("#gdd tr").asSequence()
            .map { it.text() }
            .firstOrNull { it.startsWith("Length:", ignoreCase = true) }
            ?.let { PAGES_PATTERN.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: 0
        val previewPageCount = maxOf(1, (pageCount + PREVIEWS_PER_PAGE - 1) / PREVIEWS_PER_PAGE)
        val pageLinks = buildList {
            repeat(previewPageCount) { previewPage ->
                val current = if (previewPage == 0) first else document("$galleryUrl?p=$previewPage")
                addAll(current.select("#gdt a[href*=/s/]").map { it.absUrl("href") })
            }
        }.filter(String::isNotBlank).distinct().sortedBy { PAGE_NUMBER_PATTERN.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        pageLinks.map { pageUrl -> ComicPage(imageUrl = "", referer = galleryUrl, resolutionUrl = pageUrl) }
    }

    private fun listUrl(categoryId: String, query: String): String {
        val parameters = buildList {
            if (categoryId != "0") add("f_cats=${ALL_CATEGORIES xor categoryId.toInt()}")
            if (query.isNotBlank()) add("f_search=${ComicHttp.encode(query)}")
        }
        return if (parameters.isEmpty()) ROOT else "$ROOT?${parameters.joinToString("&")}"
    }

    private fun galleryUrl(comic: ComicSummary) = "$ROOT/g/${comic.id.trim('/')}/"

    private fun document(url: String): Document = Jsoup.parse(ComicHttp.get(url, HEADERS), url)

    private companion object {
        const val ROOT = "https://e-hentai.org"
        const val ALL_CATEGORIES = 0x3ff
        const val PREVIEWS_PER_PAGE = 20
        val HEADERS = mapOf("Cookie" to "nw=1", "Referer" to "$ROOT/")
        val GALLERY_PATTERN = Regex("/g/(\\d+)/([a-f0-9]+)")
        val PAGES_PATTERN = Regex("(\\d+)\\s+pages?", RegexOption.IGNORE_CASE)
        val PAGE_NUMBER_PATTERN = Regex("-(\\d+)(?:[?#]|$)")
        val COVER_PATTERN = Regex("url\\((?:'|\")?(https://[^)'\"]+)")
    }
}
