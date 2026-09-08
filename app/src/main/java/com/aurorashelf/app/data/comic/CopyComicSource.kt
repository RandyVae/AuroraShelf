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
import org.json.JSONArray
import org.json.JSONObject

internal class CopyComicSource : ComicSource {
    override val info = ComicSourceInfo(
        "copy",
        "拷贝漫画",
        "中文漫画 · 热门与更新",
        listOf(
            ComicCategory("", "全部"), ComicCategory("aiqing", "爱情"),
            ComicCategory("huanlexiang", "欢乐向"), ComicCategory("maoxian", "冒险"),
            ComicCategory("qihuan", "奇幻"), ComicCategory("baihe", "百合"),
            ComicCategory("xiaoyuan", "校园"), ComicCategory("kehuan", "科幻"),
        ),
    )

    override suspend fun browse(categoryId: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        val offset = (page - 1) * PAGE_SIZE
        loadList("$API/comics?limit=$PAGE_SIZE&offset=$offset&ordering=-datetime_updated&theme=$categoryId&top=&platform=3")
    }

    override suspend fun search(query: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        val offset = (page - 1) * PAGE_SIZE
        val keyword = URLEncoder.encode(query, Charsets.UTF_8.name())
        loadList("$API/search/comic?limit=$PAGE_SIZE&offset=$offset&q=$keyword&q_type=&platform=3")
    }

    override suspend fun details(comic: ComicSummary): ComicDetails = withContext(Dispatchers.IO) {
        val comicResult = JSONObject(ComicHttp.get("$API/comic2/${comic.id}?platform=3", HEADERS))
            .getJSONObject("results").getJSONObject("comic")
        val chapterResult = JSONObject(
            ComicHttp.get("$API/comic/${comic.id}/group/default/chapters?limit=500&offset=0&platform=3", HEADERS),
        ).getJSONObject("results").getJSONArray("list")
        val authors = comicResult.optJSONArray("author").namesFromObjects("name")
        val tags = comicResult.optJSONArray("theme").namesFromObjects("name")
        ComicDetails(
            comic = comic.copy(
                title = comicResult.optString("name").ifBlank { comic.title },
                subtitle = authors.firstOrNull().orEmpty().ifBlank { comic.subtitle },
                coverUrl = comicResult.optString("cover").takeIf(String::isNotBlank) ?: comic.coverUrl,
                tags = tags,
            ),
            description = comicResult.optString("brief"),
            chapters = List(chapterResult.length()) { index ->
                val chapter = chapterResult.getJSONObject(index)
                ComicChapter(chapter.getString("uuid"), chapter.optString("name").ifBlank { "第 ${index + 1} 话" })
            },
        )
    }

    override suspend fun chapter(comic: ComicSummary, chapterId: String): List<ComicPage> = withContext(Dispatchers.IO) {
        val chapter = JSONObject(
            ComicHttp.get("$API/comic/${comic.id}/chapter2/$chapterId?platform=3", HEADERS),
        ).getJSONObject("results").getJSONObject("chapter")
        val contents = chapter.getJSONArray("contents")
        val words = chapter.optJSONArray("words")
        val ordered = MutableList<String?>(contents.length()) { null }
        repeat(contents.length()) { index ->
            val image = contents.getJSONObject(index).optString("url")
            val order = words?.optInt(index, index) ?: index
            if (order in ordered.indices) ordered[order] = image else ordered[index] = image
        }
        val referer = "https://www.copymanga.site/comic/${comic.id}/chapter/$chapterId"
        ordered.mapNotNull { it?.takeIf(String::isNotBlank)?.let { url -> ComicPage(url, referer) } }
    }

    private fun loadList(url: String): List<ComicSummary> {
        val list = JSONObject(ComicHttp.get(url, HEADERS)).getJSONObject("results").getJSONArray("list")
        return List(list.length()) { index -> parseSummary(list.getJSONObject(index)) }
    }

    private fun parseSummary(raw: JSONObject): ComicSummary {
        val comic = raw.optJSONObject("comic") ?: raw
        return ComicSummary(
            sourceId = info.id,
            id = comic.getString("path_word"),
            title = comic.getString("name"),
            subtitle = comic.optJSONArray("author").namesFromObjects("name").firstOrNull().orEmpty(),
            coverUrl = comic.optString("cover").takeIf(String::isNotBlank),
            tags = comic.optJSONArray("theme").namesFromObjects("name"),
        )
    }

    private fun JSONArray?.namesFromObjects(key: String): List<String> {
        if (this == null) return emptyList()
        return List(length()) { index -> optJSONObject(index)?.optString(key).orEmpty() }.filter(String::isNotBlank)
    }

    private companion object {
        const val API = "https://api.copymanga.tv/api/v3"
        const val PAGE_SIZE = 24
        val HEADERS = mapOf(
            "User-Agent" to "COPY/2.2.0",
            "source" to "copyApp",
            "webp" to "1",
            "region" to "1",
            "version" to "2.2.0",
            "authorization" to "Token",
            "platform" to "3",
        )
    }
}
