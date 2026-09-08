package com.aurorashelf.app.data

import com.aurorashelf.app.model.FeedCategory
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.VideoItem
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            // MISSPipe's MissAV kiosk intentionally exposes a single server-curated page.
            // Returning an end signal prevents the first page from being duplicated forever.
            if (page > 1 && ContentSourceCatalog.isMissAv(baseUrl)) return@withContext emptyList()
            val requestUrl = pageUrl(baseUrl, category, page)
            val document = try {
                Jsoup.connect(requestUrl)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", if (ContentSourceCatalog.isMissAv(baseUrl)) "ja,en-US;q=0.8,en;q=0.6" else "zh-CN,zh;q=0.9")
                    .timeout(REQUEST_TIMEOUT_MS)
                    .followRedirects(true)
                    .get()
            } catch (error: HttpStatusException) {
                // Huangguo ranking pages are fixed TOP 20 lists. Their numbered URL
                // returns 404, which is a normal end-of-feed signal for pagination.
                if (page > 1 && error.statusCode == 404 && isHuangguoHost(baseUrl)) return@withContext emptyList()
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
            if (!ContentSourceCatalog.isPipePipe(baseUrl)) {
                throw IOException("当前视频源暂不支持联网搜索")
            }
            PipePipeBridge.search(baseUrl, query.trim(), page)
        }

    internal fun pageUrl(baseUrl: String, category: FeedCategory, page: Int): String {
        val categoryUrl = URI("$baseUrl/").resolve(categoryPath(baseUrl, category, page))
        if (ContentSourceCatalog.isHuangguo(baseUrl) || ContentSourceCatalog.isMissAv(baseUrl)) return categoryUrl.toString()
        val params = categoryUrl.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }
            .filterNot { it.substringBefore('=').equals("page", ignoreCase = true) }
            .toMutableList()
        params += "page=$page"
        return URI(
            categoryUrl.scheme,
            categoryUrl.rawUserInfo,
            categoryUrl.host,
            categoryUrl.port,
            categoryUrl.rawPath,
            params.joinToString("&"),
            categoryUrl.rawFragment,
        ).toString()
    }

    private fun categoryPath(baseUrl: String, category: FeedCategory, page: Int): String {
        if (ContentSourceCatalog.isMissAv(baseUrl)) {
            return when (category) {
                FeedCategory.CURRENT_HOT -> "/ja/popular"
                else -> "/ja"
            }
        }
        if (!ContentSourceCatalog.isHuangguo(baseUrl)) return category.path
        val path = when (category) {
            FeedCategory.RECENT -> "/newest"
            FeedCategory.CURRENT_HOT -> "/ranks/hot/"
            FeedCategory.MONTHLY_HOT -> "/ranks/recommend/"
            FeedCategory.FAVORITES -> "/ranks/potential/"
            FeedCategory.ALL -> "/ai-duanju/"
        }
        return if (page <= 1) path else "${path.trimEnd('/')}/$page/"
    }

    internal fun parseDocument(document: Document, baseUrl: String): List<VideoItem> {
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
        val duration = selectFirst(".duration, .hg-drama-card__duration")?.text()?.trim()
            ?: DURATION_REGEX.find(contextText)?.value ?: "--:--"
        val author = AUTHOR_REGEX.find(contextText)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            .ifBlank { "匿名作者" }
        val views = VIEWS_REGEX.find(contextText)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            .ifBlank { "热度更新中" }
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
