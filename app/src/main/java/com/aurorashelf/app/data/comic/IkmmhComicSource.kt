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
import org.json.JSONObject

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
        IkmmhHttp.warmup(BASE_URL)
        val category = URLEncoder.encode(categoryId, Charsets.UTF_8.name())
        val pageUrl = "$BASE_URL/booklists/9/$category/3/$page.html"
        val document = Jsoup.parse(IkmmhHttp.get(pageUrl), BASE_URL)
        document.select("li.item.comic-item, ul.list-comic-book > li").mapNotNull { element ->
            val anchor = element.selectFirst("a") ?: return@mapNotNull null
            summaryFrom(
                anchor,
                element.selectFirst("img.img, img"),
                element.selectFirst("p.title, h2")?.text().orEmpty().ifBlank { anchor.attr("title").substringBefore(',') },
                element.selectFirst("p.process")?.text().orEmpty(),
            )
        }.distinctBy { it.id }
    }

    override suspend fun search(query: String, page: Int): List<ComicSummary> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext emptyList()
        IkmmhHttp.warmup(BASE_URL)
        val url = "$BASE_URL/search?searchkey=${URLEncoder.encode(query, Charsets.UTF_8.name())}"
        Jsoup.parse(IkmmhHttp.get(url), BASE_URL).select("li.item.comic-item, div.classification").mapNotNull { element ->
            val anchor = element.selectFirst("a") ?: return@mapNotNull null
            summaryFrom(anchor, element.selectFirst("img"), anchor.text(), element.selectFirst("p.describe")?.text().orEmpty())
        }
    }

    override suspend fun details(comic: ComicSummary): ComicDetails = withContext(Dispatchers.IO) {
        IkmmhHttp.warmup(BASE_URL)
        val url = comic.id.takeIf { it.startsWith("http") } ?: "$BASE_URL/book/${comic.id}.html"
        val document = Jsoup.parse(IkmmhHttp.get(url), BASE_URL)
        val bookId = url.trimEnd('/').substringAfterLast('/')
        val chapterJson = JSONObject(IkmmhHttp.get(
            "$BASE_URL/api/comic/zyz/chapters?ph=1&tempid=3&zpid=$bookId&page=0&line=48&orderby=desc",
        ).replace("\\,", ","))
        val chapterArray = chapterJson.optJSONArray("length")
        val chapters = List(chapterArray?.length() ?: 0) { index ->
            val chapter = chapterArray!!.getJSONObject(index)
            ComicChapter(chapter.getString("url"), chapter.optString("name").ifBlank { "章节" })
        }
        ComicDetails(
            comic = comic.copy(
                title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty().ifBlank { comic.title },
                subtitle = document.selectFirst("meta[property=og:cartoon:author]")?.attr("content")?.replace("\\,", ",")?.trim().orEmpty().ifBlank { comic.subtitle },
                coverUrl = document.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf(String::isNotBlank)
                    ?: imageUrl(url, document.selectFirst("div.banner-img img")) ?: comic.coverUrl,
                tags = document.selectFirst("meta[property=og:cartoon:category]")?.attr("content")
                    ?.split(',')?.map(String::trim)?.filter(String::isNotBlank).orEmpty(),
            ),
            description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim().orEmpty(),
            chapters = chapters,
        )
    }

    override suspend fun chapter(comic: ComicSummary, chapterId: String): List<ComicPage> = withContext(Dispatchers.IO) {
        IkmmhHttp.warmup(BASE_URL)
        val parts = chapterId.trim('/').split('/')
        require(parts.size == 3 && parts[0] == "chapter") { "爱看漫章节地址无效" }
        val referer = ComicHttp.resolve(BASE_URL, chapterId) ?: error("爱看漫章节地址无效")
        val pages = mutableListOf<ComicPage>()
        var offset = 0
        while (pages.size < MAX_PAGES) {
            val response = JSONObject(IkmmhHttp.postForm(
                "$BASE_URL/api/comic/read/pics",
                mapOf("id" to parts[2].substringBefore('.'), "aid" to parts[1],
                    "offset" to offset.toString(), "limit" to BATCH_SIZE.toString()),
            )).getJSONObject("data")
            val batch = response.getJSONArray("pic")
            if (batch.length() == 0) break
            repeat(batch.length()) { index ->
                batch.getJSONObject(index).optString("pic").takeIf(String::isNotBlank)?.let { image ->
                    ComicHttp.resolve(BASE_URL, image)?.let { pages += ComicPage(it, referer) }
                }
            }
            offset += batch.length()
            if (response.optInt("total") in 1..offset) break
        }
        pages
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
        const val BATCH_SIZE = 10
        const val MAX_PAGES = 1_000
    }
}

private object IkmmhHttp {
    @Volatile private var sessionCookie: String? = null
    private val client = okhttp3.OkHttpClient.Builder()
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun get(url: String): String = execute(okhttp3.Request.Builder().url(url).get())

    @Synchronized
    fun warmup(baseUrl: String) {
        if (sessionCookie == null) runCatching { get("${baseUrl.trimEnd('/')}/") }
    }

    fun postForm(url: String, values: Map<String, String>): String {
        val form = okhttp3.FormBody.Builder().apply { values.forEach(::add) }.build()
        return execute(okhttp3.Request.Builder().url(url).post(form))
    }

    private fun execute(builder: okhttp3.Request.Builder): String {
        val request = builder
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
            .apply { sessionCookie?.let { header("Cookie", it) } }
            .build()
        return client.newCall(request).execute().use { response ->
            response.headers("Set-Cookie").firstOrNull { it.startsWith("PHPSESSID=") }
                ?.substringBefore(';')?.let { sessionCookie = it }
            if (!response.isSuccessful) throw java.io.IOException("漫画源返回 HTTP ${response.code}")
            response.body?.string() ?: throw java.io.IOException("漫画源返回空响应")
        }
    }

    private const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
}
