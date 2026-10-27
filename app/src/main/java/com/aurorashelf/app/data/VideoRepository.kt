package com.aurorashelf.app.data

import com.aurorashelf.app.model.FeedCategory
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.VideoItem
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

class VideoRepository {
    suspend fun loadFeed(baseUrl: String, category: FeedCategory, page: Int = 1): List<VideoItem> =
        withContext(Dispatchers.IO) {
            require(SourceAddress.error(baseUrl) == null) { "站点地址无效" }
            require(page >= 1) { "页码必须从 1 开始" }
            if (ContentSourceCatalog.isBeeg(baseUrl)) {
                return@withContext loadBeegFeed(category, page)
            }
            if (ContentSourceCatalog.isPipePipe(baseUrl)) {
                // MissAV blocks the extractor's generic desktop request with a challenge page;
                // keep its proven mobile HTML adapter as a transparent fallback.
                if (!ContentSourceCatalog.isMissAv(baseUrl)) {
                    val pipeItems = PipePipeBridge.loadFeed(baseUrl, category, page)
                    if (page == 1 && pipeItems.isEmpty()) {
                        throw IOException("视频源已连接，但当前分类没有返回视频")
                    }
                    return@withContext pipeItems
                }
                val pipeItems = runCatching { PipePipeBridge.loadFeed(baseUrl, category, page) }
                if (pipeItems.getOrNull()?.isNotEmpty() == true) {
                    return@withContext pipeItems.getOrThrow()
                }
            }
            val requestUrl = pageUrl(baseUrl, category, page)
            val document = try {
                fetchDocument(requestUrl, baseUrl)
            } catch (error: HttpStatusException) {
                // Huangguo ranking pages or end-of-feed numbered URLs return 404.
                if (page > 1 && error.statusCode == 404 && ContentSourceCatalog.isHuangguo(baseUrl)) return@withContext emptyList()
                throw error
            }
            val records = parseDocument(document, baseUrl)
            if (page == 1 && records.isEmpty()) {
                throw IOException("页面已打开，但没有识别到视频条目。请检查站点地址或页面结构。")
            }
            records
        }

    suspend fun search(baseUrl: String, query: String, page: Int = 1): List<VideoItem> =
        withContext(Dispatchers.IO) {
            require(query.isNotBlank()) { "请输入搜索内容" }
            require(page >= 1) { "页码必须从 1 开始" }
            if (ContentSourceCatalog.isHuavod(baseUrl)) {
                val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
                val searchPath = if (page <= 1) {
                    "/vodsearch/-------------.html?wd=$encodedQuery"
                } else {
                    "/vodsearch/page/$page/wd/$encodedQuery.html"
                }
                val searchUrl = URI("$baseUrl/").resolve(searchPath).toString()
                val document = fetchDocument(searchUrl, baseUrl)
                return@withContext parseDocument(document, baseUrl)
            }
            if (ContentSourceCatalog.isBeeg(baseUrl)) {
                return@withContext searchBeeg(query, page)
            }
            if (ContentSourceCatalog.is91Dongman(baseUrl)) {
                val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
                val searchPath = if (page <= 1) {
                    "/search-result/?q=$encodedQuery"
                } else {
                    "/search-result/?q=$encodedQuery&page=$page"
                }
                val searchUrl = URI("$baseUrl/").resolve(searchPath).toString()
                val document = fetchDocument(searchUrl, baseUrl)
                return@withContext parseDocument(document, baseUrl)
            }
            if (ContentSourceCatalog.isHuangguo(baseUrl)) {
                val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
                val searchPath = if (page <= 1) "/search?keyword=$encodedQuery" else "/search/video/$encodedQuery/?page=$page"
                val searchUrl = URI("$baseUrl/").resolve(searchPath).toString()
                val document = fetchDocument(searchUrl, baseUrl)
                return@withContext parseDocument(document, baseUrl)
            }
            if (ContentSourceCatalog.is91(baseUrl)) {
                val encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
                val searchPath = if (page <= 1) "/search_result.php?search_id=$encodedQuery" else "/search_result.php?search_id=$encodedQuery&page=$page"
                val searchUrl = URI("$baseUrl/").resolve(searchPath).toString()
                val document = fetchDocument(searchUrl, baseUrl)
                return@withContext parseDocument(document, baseUrl)
            }
            if (!ContentSourceCatalog.isPipePipe(baseUrl)) {
                throw IOException("当前视频源暂不支持联网搜索")
            }
            PipePipeBridge.search(baseUrl, query.trim(), page)
        }

    /**
     * 全局搜索：并发跨所有视频源统一搜索同一关键词，聚合结果并按视频源归属清晰标明。
     */
    suspend fun searchAll(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        require(trimmed.isNotBlank()) { "请输入搜索内容" }
        val sources = ContentSourceCatalog.sources
        coroutineScope {
            val deferreds = sources.map { source ->
                async {
                    runCatching {
                        withTimeout(6_000L) {
                            search(source.baseUrl, trimmed, 1)
                        }
                    }.getOrDefault(emptyList()).map { item ->
                        val sourcePrefix = source.name
                        if (!item.views.startsWith(sourcePrefix)) {
                            item.copy(views = "$sourcePrefix · ${item.views}")
                        } else item
                    }
                }
            }
            val perSourceLists = deferreds.awaitAll().filter { it.isNotEmpty() }
            interleave(perSourceLists).distinctBy(VideoItem::id)
        }
    }

    internal fun interleave(lists: List<List<VideoItem>>): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        var maxLen = 0
        for (l in lists) {
            if (l.size > maxLen) maxLen = l.size
        }
        for (i in 0 until maxLen) {
            for (l in lists) {
                if (i < l.size) {
                    result.add(l[i])
                }
            }
        }
        return result
    }

    internal fun pageUrl(baseUrl: String, category: FeedCategory, page: Int): String {
        val categoryUrl = URI("$baseUrl/").resolve(categoryPath(baseUrl, category, page))
        if (ContentSourceCatalog.isHuangguo(baseUrl) || ContentSourceCatalog.isHuavod(baseUrl) || ContentSourceCatalog.isBeeg(baseUrl) || ContentSourceCatalog.is91Dongman(baseUrl)) return categoryUrl.toString()
        val params = categoryUrl.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }
            .filterNot { it.substringBefore('=').equals("page", ignoreCase = true) }
            .toMutableList()
        if (page > 1) {
            params += "page=$page"
        }
        return URI(
            categoryUrl.scheme,
            categoryUrl.rawUserInfo,
            categoryUrl.host,
            categoryUrl.port,
            categoryUrl.rawPath,
            params.joinToString("&").takeIf { it.isNotBlank() },
            categoryUrl.rawFragment,
        ).toString()
    }

    private fun categoryPath(baseUrl: String, category: FeedCategory, page: Int): String {
        if (ContentSourceCatalog.isBeeg(baseUrl)) {
            val tag = when (category) {
                FeedCategory.RECENT -> "index"
                FeedCategory.CURRENT_HOT -> "amateur"
                FeedCategory.MONTHLY_HOT -> "japanese"
                FeedCategory.FAVORITES -> "milf"
                FeedCategory.ALL -> "asian"
                else -> "index"
            }
            val limit = 20
            val offset = (page - 1) * limit
            return "/tag/videos/$tag?limit=$limit&offset=$offset"
        }
        if (ContentSourceCatalog.isHuavod(baseUrl)) {
            val typeId = when (category) {
                FeedCategory.RECENT -> 1
                FeedCategory.CURRENT_HOT -> 2
                FeedCategory.MONTHLY_HOT -> 3
                FeedCategory.FAVORITES -> 4
                FeedCategory.ALL -> 5
                else -> 1
            }
            return if (page <= 1) "/vodshow/$typeId.html" else "/vodshow/$typeId/page/$page.html"
        }
        if (ContentSourceCatalog.is91Dongman(baseUrl)) {
            val path = when (category) {
                FeedCategory.RECENT -> "/anime/lifan/"
                FeedCategory.CURRENT_HOT -> "/anime/3d/"
                FeedCategory.MONTHLY_HOT -> "/anime/ai/"
                FeedCategory.FAVORITES -> "/ranking/"
                FeedCategory.ALL -> "/anime/"
                else -> "/anime/lifan/"
            }
            return if (page <= 1) path else "${path.trimEnd('/')}/page/$page/"
        }
        if (ContentSourceCatalog.isMissAv(baseUrl)) {
            return when (category) {
                FeedCategory.CURRENT_HOT -> "/ja/release"
                else -> "/ja"
            }
        }
        if (!ContentSourceCatalog.isHuangguo(baseUrl)) return category.path
        val path = when (category) {
            FeedCategory.RECENT -> "/newest"
            FeedCategory.CURRENT_HOT -> "/recommend"
            FeedCategory.MONTHLY_HOT -> "/ai-manju/"
            FeedCategory.FAVORITES -> "/ai-huanlian/"
            FeedCategory.ALL -> "/ai-duanju/"
            else -> "/newest"
        }
        return if (page <= 1) path else "${path.trimEnd('/')}/$page/"
    }

    internal fun parseDocument(document: Document, baseUrl: String): List<VideoItem> {
        if (ContentSourceCatalog.is91Dongman(baseUrl)) {
            val dmItems = document.select(".dm-card, .dm-comic-card, a.dm-card__media[href*=/anime/]")
                .asSequence()
                .mapNotNull { it.to91DongmanVideoItem(baseUrl) }
                .distinctBy(VideoItem::pageUrl)
                .toList()
            if (dmItems.isNotEmpty()) return dmItems
        }
        if (ContentSourceCatalog.isHuavod(baseUrl)) {
            val huavodItems = document.select(".public-list-box")
                .asSequence()
                .mapNotNull { it.toHuavodVideoItem(baseUrl) }
                .distinctBy(VideoItem::pageUrl)
                .toList()
            if (huavodItems.isNotEmpty()) return huavodItems
        }
        if (ContentSourceCatalog.isMissAv(baseUrl)) {
            return document.select("a[href*=/ja/], a[href*=/en/]")
                .asSequence()
                .mapNotNull { it.toMissAvVideoItem(baseUrl) }
                .distinctBy(VideoItem::pageUrl)
                .toList()
        }
        // Huangguo's ranking pages use a different row structure from its card grids.
        // Parse those rows first so the cover anchor and title stay associated with the
        // same item, instead of relying on whichever detail link appears first.
        val rankItems = document.select(".hg-rank-item")
            .asSequence()
            .mapNotNull { it.toRankVideoItem(baseUrl) }
            .distinctBy(VideoItem::pageUrl)
            .toList()
        if (rankItems.isNotEmpty()) return rankItems

        return document.select("a[href*=view_video], a[href*=video], a[href*=detail]")
            .asSequence()
            .mapNotNull { anchor -> anchor.toVideoItem(baseUrl) }
            .distinctBy(VideoItem::pageUrl)
            .toList()
    }

    private fun fetchDocument(requestUrl: String, baseUrl: String): Document {
        fun request(): Document = Jsoup.connect(requestUrl)
            .userAgent(USER_AGENT)
            .header("Accept-Language", if (ContentSourceCatalog.isMissAv(baseUrl)) "ja,en-US;q=0.8,en;q=0.6" else "zh-CN,zh;q=0.9")
            .timeout(REQUEST_TIMEOUT_MS)
            .followRedirects(true)
            .get()

        return try {
            request()
        } catch (failure: IOException) {
            if (!failure.isTransientTransportFailure()) throw failure
            request()
        }
    }

    private fun Element.toMissAvVideoItem(baseUrl: String): VideoItem? {
        val image = selectFirst("img[data-src], img[src]") ?: return null
        val pageUrl = SourceAddress.resolve(documentBase(baseUrl), attr("href").trim()) ?: return null
        val pathParts = runCatching { URI(pageUrl).path.trim('/').split('/') }.getOrDefault(emptyList())
        if (pathParts.size != 2 || pathParts.first() !in MISSAV_LANGUAGES || pathParts.last() in MISSAV_RESERVED_PATHS) {
            return null
        }
        val title = image.attr("alt").ifBlank { attr("title") }.ifBlank {
            selectFirst("h1, h2, h3, .my-2, .text-secondary, a[title]")?.text().orEmpty()
        }.trim().let { Parser.unescapeEntities(it, false) }.replace(Regex("\\s+"), " ")
        if (title.length < MIN_TITLE_LENGTH) return null
        val thumbnailReference = image.attr("data-src").ifBlank { image.attr("src") }
        val thumbnail = SourceAddress.resolve(documentBase(baseUrl), thumbnailReference)?.takeIf(String::isNotBlank)

        return VideoItem(
            id = pageUrl.sha256(),
            title = title,
            author = "MissAV",
            duration = "--:--",
            views = "MISSPipe 视频源",
            pageUrl = pageUrl,
            thumbnailUrl = thumbnail,
        )
    }

    private fun Element.to91DongmanVideoItem(baseUrl: String): VideoItem? {
        val anchor = if (tagName() == "a") this else selectFirst("a[href*=/anime/]") ?: return null
        val href = anchor.attr("href").trim()
        val pageUrl = SourceAddress.resolve(documentBase(baseUrl), href) ?: return null
        val parent = if (tagName() == "a") parent() ?: this else this
        val title = parent.selectFirst(".dm-card__title h3, h3, a[title]")?.text()?.trim()
            ?: anchor.selectFirst("img")?.attr("alt")?.trim()
            ?: return null
        if (title.isBlank()) return null
        val img = parent.selectFirst("img[data-src], img[src]")
        val thumbnail = img?.let {
            val reference = it.attr("data-src").ifBlank { it.attr("src") }
            SourceAddress.resolve(documentBase(baseUrl), reference)
        }?.takeIf(String::isNotBlank)
        val duration = parent.selectFirst(".dm-card__duration span")?.text()?.trim() ?: "--:--"
        val views = parent.selectFirst(".dm-card__views span")?.text()?.trim() ?: "热播中"
        val author = parent.selectFirst(".dm-card__badge, .dm-chip")?.text()?.trim() ?: "91动漫"
        val id = Regex("/anime/[^/]+/(\\d+)").find(pageUrl)?.groupValues?.get(1) ?: pageUrl.hashCode().toString()
        return VideoItem(
            id = id,
            title = Parser.unescapeEntities(title, false),
            author = author,
            duration = duration,
            views = views,
            pageUrl = pageUrl,
            thumbnailUrl = thumbnail,
        )
    }

    private fun Element.toHuavodVideoItem(baseUrl: String): VideoItem? {
        val link = selectFirst("a[href*=/voddetail/]") ?: return null
        val href = link.attr("href").trim()
        val pageUrl = SourceAddress.resolve(documentBase(baseUrl), href) ?: return null
        val image = link.selectFirst("img") ?: selectFirst("img")
        val title = link.attr("title").ifBlank { selectFirst(".time-title")?.text().orEmpty() }
            .ifBlank { image?.attr("alt").orEmpty().removeSuffix("封面图") }
            .trim()
            .let { Parser.unescapeEntities(it, false) }
            .replace(Regex("\\s+"), " ")
        if (title.length < MIN_TITLE_LENGTH) return null

        val thumbnail = image?.let {
            val reference = it.attr("data-src").ifBlank { it.attr("src") }
            SourceAddress.resolve(documentBase(baseUrl), reference)
        }?.takeIf(String::isNotBlank)

        val score = selectFirst(".ft4")?.text()?.trim()?.takeIf(String::isNotBlank)
        val tag = selectFirst(".public-prt")?.text()?.trim()?.takeIf(String::isNotBlank)
        val views = score?.let { "评分 $it" } ?: (tag ?: "全网热播")

        return VideoItem(
            id = pageUrl.sha256(),
            title = title,
            author = "华视影院",
            duration = "--:--",
            views = views,
            pageUrl = pageUrl,
            thumbnailUrl = thumbnail,
        )
    }

    private fun Element.toRankVideoItem(baseUrl: String): VideoItem? {
        val cover = selectFirst("a.hg-rank-item__cover") ?: return null
        val href = cover.attr("href").trim()
        val pageUrl = SourceAddress.resolve(documentBase(baseUrl), href) ?: return null
        val image = cover.selectFirst("img")
        val title = selectFirst(".hg-rank-item__title")?.text().orEmpty()
            .ifBlank { image?.attr("alt").orEmpty() }
            .trim()
            .let { Parser.unescapeEntities(it, false) }
            .replace(Regex("\\s+"), " ")
        if (title.length < MIN_TITLE_LENGTH) return null

        val thumbnail = image?.let {
            val reference = it.attr("data-src").ifBlank { it.attr("src") }
            SourceAddress.resolve(documentBase(baseUrl), reference)
        }?.takeIf(String::isNotBlank)
        val heat = selectFirst(".hg-rank-item__heat-value, .hg-rank-metric-value")?.text()
            ?.trim()
            ?.takeIf(String::isNotBlank)

        return VideoItem(
            id = pageUrl.sha256(),
            title = title,
            author = "黄果榜单",
            duration = "--:--",
            views = heat?.let { "热度 $it" } ?: "榜单更新中",
            pageUrl = pageUrl,
            thumbnailUrl = thumbnail,
        )
    }

    private fun Element.toVideoItem(baseUrl: String): VideoItem? {
        val href = attr("href").trim()
        if (href.isBlank()) return null

        val image = selectFirst("img") ?: parent()?.selectFirst("img")
        // Generic pagination/navigation links also contain "video" on the source site.
        // Keep fallback site compatibility, but never turn a text-only navigation link into a card.
        if (!href.contains("view_video", ignoreCase = true) && image == null) return null
        val title = selectFirst(".video-title, .hg-drama-card__title")?.text().orEmpty()
            .ifBlank { attr("title") }
            .ifBlank { image?.attr("alt").orEmpty() }
            .ifBlank { text() }
            .trim()
            .let { Parser.unescapeEntities(it, false) }
            .replace(Regex("\\s+"), " ")
        if (title.length < MIN_TITLE_LENGTH) return null

        val contextText = parent()?.text().orEmpty()
        val dramaEpisode = parent()?.selectFirst(".hg-drama-card__episode")?.text()?.trim()
            ?: parent()?.parent()?.selectFirst(".hg-drama-card__episode")?.text()?.trim()
        val dramaScore = parent()?.selectFirst(".hg-drama-card__score")?.text()?.trim()
            ?: parent()?.parent()?.selectFirst(".hg-drama-card__score")?.text()?.trim()

        val duration = selectFirst(".duration, .hg-drama-card__duration")?.text()?.trim()
            ?: dramaEpisode
            ?: DURATION_REGEX.find(contextText)?.value ?: "--:--"
        val author = AUTHOR_REGEX.find(contextText)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            .ifBlank { if (ContentSourceCatalog.isHuangguo(baseUrl)) "黄果短剧" else "匿名作者" }
        val views = dramaScore?.let { "评分 $it" }
            ?: VIEWS_REGEX.find(contextText)?.groupValues?.getOrNull(1)?.trim()?.let { "热度 $it" }
            ?: if (ContentSourceCatalog.isHuangguo(baseUrl)) "精选短剧" else "热度更新中"
        val pageUrl = SourceAddress.resolve(documentBase(baseUrl), href) ?: return null
        val thumbnail = image?.let {
            val reference = it.attr("data-src").ifBlank { it.attr("src") }
            SourceAddress.resolve(documentBase(baseUrl), reference)
        }?.takeIf(String::isNotBlank)

        return VideoItem(
            id = pageUrl.sha256(),
            title = title,
            author = author,
            duration = duration,
            views = views,
            pageUrl = pageUrl,
            thumbnailUrl = thumbnail,
        )
    }

    private suspend fun loadBeegFeed(category: FeedCategory, page: Int): List<VideoItem> = withContext(Dispatchers.IO) {
        val tag = when (category) {
            FeedCategory.RECENT -> "index"
            FeedCategory.CURRENT_HOT -> "amateur"
            FeedCategory.MONTHLY_HOT -> "japanese"
            FeedCategory.FAVORITES -> "milf"
            FeedCategory.ALL -> "asian"
            else -> "index"
        }
        val limit = 20
        val offset = (page - 1) * limit
        val url = "https://store.externulls.com/tag/videos/$tag?limit=$limit&offset=$offset"
        val jsonText = try {
            fetchJson(url)
        } catch (error: HttpStatusException) {
            if (page > 1 && (error.statusCode == 404 || error.statusCode == 400)) return@withContext emptyList()
            throw error
        }
        val records = parseBeegJson(jsonText, ContentSourceCatalog.BEEG_BASE_URL)
        if (page == 1 && records.isEmpty()) {
            throw IOException("视频源已连接，但当前分类没有返回视频")
        }
        records
    }

    private suspend fun searchBeeg(query: String, page: Int): List<VideoItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val candidateTags = listOf(
            trimmed.replace(Regex("\\s+"), ""),
            trimmed.split(Regex("\\s+")).firstOrNull().orEmpty(),
        ).filter { it.isNotBlank() }.distinct()

        val limit = 20
        val offset = (page - 1) * limit
        for (tag in candidateTags) {
            val url = "https://store.externulls.com/tag/videos/$tag?limit=$limit&offset=$offset"
            val records = runCatching {
                val jsonText = fetchJson(url)
                parseBeegJson(jsonText, ContentSourceCatalog.BEEG_BASE_URL)
            }.getOrDefault(emptyList())
            if (records.isNotEmpty()) return@withContext records
        }
        emptyList()
    }

    internal fun parseBeegJson(jsonText: String, baseUrl: String): List<VideoItem> {
        val array = runCatching { JSONArray(jsonText) }.getOrNull() ?: return emptyList()
        val items = mutableListOf<VideoItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            obj.toBeegVideoItem(baseUrl)?.let { items.add(it) }
        }
        return items.distinctBy(VideoItem::pageUrl)
    }

    private fun JSONObject.toBeegVideoItem(baseUrl: String): VideoItem? {
        val fileObj = optJSONObject("file") ?: return null
        val fileId = fileObj.optLong("id", 0L).takeIf { it > 0 } ?: return null
        val durationSec = fileObj.optInt("fl_duration", 0)
        val durationStr = if (durationSec > 0) {
            val m = durationSec / 60
            val s = durationSec % 60
            String.format(java.util.Locale.US, "%02d:%02d", m, s)
        } else "--:--"

        var title = "Beeg Video"
        val dataArr = fileObj.optJSONArray("data")
        if (dataArr != null) {
            for (i in 0 until dataArr.length()) {
                val d = dataArr.optJSONObject(i)
                if (d?.optString("cd_column") == "sf_name") {
                    val value = d.optString("cd_value").trim()
                    if (value.isNotBlank()) {
                        title = value
                        break
                    }
                }
            }
        }
        title = Parser.unescapeEntities(title, false)

        var author = "Beeg"
        val tagsArr = optJSONArray("tags")
        if (tagsArr != null && tagsArr.length() > 0) {
            for (i in 0 until tagsArr.length()) {
                val tag = tagsArr.optJSONObject(i)
                if (tag != null && (tag.optBoolean("is_owner", false) || tag.optBoolean("is_person", false))) {
                    val name = tag.optString("tg_name").trim()
                    if (name.isNotBlank()) {
                        author = name
                        break
                    }
                }
            }
            if (author == "Beeg") {
                val firstName = tagsArr.optJSONObject(0)?.optString("tg_name")?.trim().orEmpty()
                if (firstName.isNotBlank()) author = firstName
            }
        }

        val factsArr = optJSONArray("fc_facts")
        val viewsCount = factsArr?.optJSONObject(0)?.optLong("fc_st_views", 0L) ?: 0L
        val viewsStr = if (viewsCount >= 10000) {
            String.format(java.util.Locale.US, "%.1f萬次播放", viewsCount / 10000.0)
        } else if (viewsCount > 0) {
            "$viewsCount 次播放"
        } else "Beeg 視頻源"

        val thumbOffset = factsArr?.optJSONObject(0)?.optJSONArray("fc_thumbs")?.optInt(0, 0) ?: 0
        val thumbUrl = "https://thumbs.externulls.com/videos/$fileId/$thumbOffset.webp"
        val pageUrl = "https://beeg.com/-0$fileId"

        return VideoItem(
            id = fileId.toString(),
            title = title,
            author = author,
            duration = durationStr,
            views = viewsStr,
            pageUrl = pageUrl,
            thumbnailUrl = thumbUrl,
        )
    }

    private fun fetchJson(requestUrl: String): String {
        return Jsoup.connect(requestUrl)
            .userAgent(USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .ignoreContentType(true)
            .timeout(REQUEST_TIMEOUT_MS)
            .execute()
            .body()
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS = 15_000
        private const val MIN_TITLE_LENGTH = 2
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36"
        private val DURATION_REGEX = Regex("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b")
        private val AUTHOR_REGEX = Regex("(?:作者|上传者)[:：]\\s*([^\\s·|]+)")
        private val VIEWS_REGEX = Regex("(?:热度|观看|播放)[:：]?\\s*([\\d,.万]+)")
        private val MISSAV_LANGUAGES = setOf("ja", "en")
        private val MISSAV_RESERVED_PATHS = setOf(
            "popular", "recommended", "new", "genres", "actresses", "makers", "labels", "series",
        )

        private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it) }
    }

    private fun Element.documentBase(fallback: String): String = baseUri().ifBlank { "$fallback/" }

    private fun isHuangguoHost(baseUrl: String): Boolean = ContentSourceCatalog.isHuangguo(baseUrl)
}
