package com.aurorashelf.app.data.comic

import com.aurorashelf.app.model.ComicChapter
import com.aurorashelf.app.model.ComicCategory
import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSourceInfo
import com.aurorashelf.app.model.ComicSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class KomiicComicSource : ComicSource {
    override val info = ComicSourceInfo(
        "komiic",
        "Komiic",
        "繁体中文 · 连载更新",
        listOf(
            ComicCategory("0", "全部"), ComicCategory("1", "爱情"),
            ComicCategory("8", "冒险"), ComicCategory("11", "魔幻"),
            ComicCategory("17", "科幻"), ComicCategory("21", "热血"),
            ComicCategory("27", "恐怖"), ComicCategory("9", "百合"),
        ),
    )

    override suspend fun browse(categoryId: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        val variables = JSONObject().put(
            "pagination",
            pagination(limit = 24, offset = (page - 1) * 24),
        )
        if (categoryId == "0") {
            parseComics(query("recentUpdate", variables, RECENT_QUERY).getJSONObject("data").getJSONArray("recentUpdate"))
        } else {
            variables.put("categoryId", categoryId)
            parseComics(query("comicByCategory", variables, CATEGORY_QUERY).getJSONObject("data").getJSONArray("comicByCategory"))
        }
    }

    override suspend fun search(query: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext emptyList()
        val variables = JSONObject().put("keyword", query)
        val result = query("searchComicAndAuthorQuery", variables, SEARCH_QUERY)
            .getJSONObject("data").getJSONObject("searchComicsAndAuthors").getJSONArray("comics")
        parseComics(result)
    }

    override suspend fun details(comic: ComicSummary): ComicDetails = withContext(Dispatchers.IO) {
        val comicArray = query(
            "comicByIds",
            JSONObject().put("comicIds", JSONArray().put(comic.id)),
            COMIC_QUERY,
        ).getJSONObject("data").getJSONArray("comicByIds")
        val updatedComic = parseComics(comicArray).firstOrNull() ?: comic
        val chapterArray = query(
            "chapterByComicId",
            JSONObject().put("comicId", comic.id),
            CHAPTERS_QUERY,
        ).getJSONObject("data").getJSONArray("chaptersByComicId")
        ComicDetails(
            comic = updatedComic,
            chapters = List(chapterArray.length()) { index ->
                val chapter = chapterArray.getJSONObject(index)
                val prefix = if (chapter.optString("type") == "book") "卷" else "第"
                ComicChapter(chapter.getString("id"), "$prefix${chapter.optString("serial")}")
            },
        )
    }

    override suspend fun chapter(comic: ComicSummary, chapterId: String): List<ComicPage> = withContext(Dispatchers.IO) {
        val images = query(
            "imagesByChapterId",
            JSONObject().put("chapterId", chapterId),
            IMAGES_QUERY,
        ).getJSONObject("data").getJSONArray("imagesByChapterId")
        val referer = "https://komiic.com/comic/${comic.id}/chapter/$chapterId/images/all"
        List(images.length()) { index ->
            ComicPage("https://komiic.com/api/image/${images.getJSONObject(index).getString("kid")}", referer)
        }
    }

    private fun query(operationName: String, variables: JSONObject, graphQuery: String): JSONObject {
        val body = JSONObject()
            .put("operationName", operationName)
            .put("variables", variables)
            .put("query", graphQuery)
            .toString()
        val json = JSONObject(ComicHttp.postJson(API, body, HEADERS))
        json.optJSONArray("errors")?.takeIf { it.length() > 0 }?.let {
            error(it.optJSONObject(0)?.optString("message").orEmpty().ifBlank { "Komiic 返回未知错误" })
        }
        return json
    }

    private fun parseComics(items: JSONArray): List<ComicSummary> = List(items.length()) { index ->
        val item = items.getJSONObject(index)
        ComicSummary(
            sourceId = info.id,
            id = item.getString("id"),
            title = item.getString("title"),
            subtitle = item.optJSONArray("authors")?.optJSONObject(0)?.optString("name").orEmpty(),
            coverUrl = item.optString("imageUrl").takeIf(String::isNotBlank),
            tags = item.optJSONArray("categories")?.let { tags ->
                List(tags.length()) { tags.optJSONObject(it)?.optString("name").orEmpty() }.filter(String::isNotBlank)
            }.orEmpty(),
        )
    }

    private fun pagination(limit: Int, offset: Int) = JSONObject()
        .put("limit", limit).put("offset", offset).put("orderBy", "DATE_UPDATED")
        .put("status", "").put("asc", true)

    private companion object {
        const val API = "https://komiic.com/api/query"
        val HEADERS = mapOf("Referer" to "https://komiic.com/")
        const val COMIC_FIELDS = "id title status year imageUrl authors { id name } categories { id name } dateUpdated"
        const val RECENT_QUERY = "query recentUpdate(\$pagination: Pagination!) { recentUpdate(pagination: \$pagination) { $COMIC_FIELDS } }"
        const val CATEGORY_QUERY = "query comicByCategory(\$categoryId: ID!, \$pagination: Pagination!) { comicByCategory(categoryId: \$categoryId, pagination: \$pagination) { $COMIC_FIELDS } }"
        const val SEARCH_QUERY = "query searchComicAndAuthorQuery(\$keyword: String!) { searchComicsAndAuthors(keyword: \$keyword) { comics { $COMIC_FIELDS } } }"
        const val COMIC_QUERY = "query comicByIds(\$comicIds: [ID]!) { comicByIds(comicIds: \$comicIds) { $COMIC_FIELDS } }"
        const val CHAPTERS_QUERY = "query chapterByComicId(\$comicId: ID!) { chaptersByComicId(comicId: \$comicId) { id serial type dateUpdated size } }"
        const val IMAGES_QUERY = "query imagesByChapterId(\$chapterId: ID!) { imagesByChapterId(chapterId: \$chapterId) { id kid height width } }"
    }
}
