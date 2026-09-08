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
import org.json.JSONObject
import org.jsoup.Jsoup

internal class BaoziComicSource : ComicSource {
    override val info = ComicSourceInfo(
        "baozi",
        "包子漫画",
        "简体中文 · 分类与连载",
        listOf(
            ComicCategory("all", "全部"), ComicCategory("lianai", "恋爱"),
            ComicCategory("chunai", "纯爱"), ComicCategory("gufeng", "古风"),
            ComicCategory("dushi", "都市"), ComicCategory("rexie", "热血"),
            ComicCategory("gaoxiao", "搞笑"),
        ),
    )

    override suspend fun browse(categoryId: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/api/bzmhq/amp_comic_list?type=$categoryId&region=all&state=all&filter=%2a&page=$page&limit=36&language=cn&__amp_source_origin=https%3A%2F%2Fcn.baozimh.com"
        val items = JSONObject(ComicHttp.get(url)).getJSONArray("items")
        List(items.length()) { index ->
            val item = items.getJSONObject(index)
            val image = item.optString("topic_img")
            ComicSummary(
                sourceId = info.id,
                id = item.getString("comic_id"),
                title = item.getString("name"),
                subtitle = item.optString("author"),
                coverUrl = image.takeIf(String::isNotBlank)?.let {
                    "https://static-tw.baozimh.com/cover/$it?w=570&h=750&q=90"
                },
                tags = item.optJSONArray("type_names")?.let { tags ->
                    List(tags.length()) { tags.optString(it) }.filter(String::isNotBlank)
                }.orEmpty(),
            )
        }
    }

    override suspend fun search(query: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext emptyList()
        val document = Jsoup.parse(
            ComicHttp.get("$BASE_URL/search?q=${URLEncoder.encode(query, Charsets.UTF_8.name())}"),
            BASE_URL,
        )
        document.select("div.comics-card").mapNotNull(::parseCard)
    }

    override suspend fun details(comic: ComicSummary): ComicDetails = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/comic/${comic.id}"
        val document = Jsoup.parse(ComicHttp.get(url), BASE_URL)
        val chapters = buildList {
            document.select("div#chapter-items div.comics-chapters a, div#chapters_other_list div.comics-chapters a")
                .forEachIndexed { index, element ->
                    val title = element.selectFirst("span")?.text()?.trim().orEmpty()
                    add(ComicChapter(index.toString(), title.ifBlank { "第 ${index + 1} 话" }))
                }
        }
        val updatedComic = comic.copy(
            title = document.selectFirst("h1.comics-detail__title")?.text()?.trim().orEmpty().ifBlank { comic.title },
            subtitle = document.selectFirst("h2.comics-detail__author")?.text()?.trim().orEmpty().ifBlank { comic.subtitle },
            coverUrl = ComicHttp.resolve(url, document.selectFirst("div.l-content amp-img")?.attr("src")) ?: comic.coverUrl,
            tags = document.select("div.tag-list span").map { it.text().trim() }.filter(String::isNotBlank),
        )
        ComicDetails(
            comic = updatedComic,
            description = document.selectFirst("p.comics-detail__desc")?.text()?.trim().orEmpty(),
            chapters = chapters,
        )
    }

    override suspend fun chapter(comic: ComicSummary, chapterId: String): List<ComicPage> = withContext(Dispatchers.IO) {
        val pageUrl = "https://cn.czmanga.com/comic/chapter/${comic.id}/0_$chapterId.html"
        val document = Jsoup.parse(ComicHttp.get(pageUrl), pageUrl)
        document.select("ul.comic-contain amp-img").mapNotNull { element ->
            ComicHttp.resolve(pageUrl, element.attr("src"))?.let { ComicPage(it, pageUrl) }
        }
    }

    private fun parseCard(element: org.jsoup.nodes.Element): ComicSummary? {
        val anchor = element.selectFirst("a") ?: return null
        val id = anchor.attr("href").substringAfterLast('/').substringBefore('?').takeIf(String::isNotBlank) ?: return null
        val title = element.selectFirst("h3")?.text()?.trim().orEmpty().takeIf(String::isNotBlank) ?: return null
        return ComicSummary(
            sourceId = info.id,
            id = id,
            title = title,
            coverUrl = ComicHttp.resolve(BASE_URL, anchor.selectFirst("amp-img, img")?.let { it.attr("src").ifBlank { it.attr("data-src") } }),
            tags = element.select("div.tabs span").map { it.text().trim() }.filter(String::isNotBlank),
        )
    }

    private companion object {
        const val BASE_URL = "https://cn.baozimh.com"
    }
}
