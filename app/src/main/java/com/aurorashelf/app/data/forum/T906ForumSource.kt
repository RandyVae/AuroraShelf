package com.aurorashelf.app.data.forum

import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.ForumPost
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.nodes.Document

internal class T906ForumSource(
    initialBaseUrl: String = PRIMARY_BASE_URL,
) : ForumSource {
    override val id = "t906"
    override val name = "91自拍论坛"

    @Volatile
    internal var activeBaseUrl: String = initialBaseUrl

    override suspend fun load(page: Int): List<ForumPost> = withContext(Dispatchers.IO) {
        require(page >= 1) { "页码必须从 1 开始" }
        val currentUrl = activeBaseUrl
        val firstAttempt = runCatching { fetchPosts(currentUrl, page) }
        if (firstAttempt.isSuccess && (page > 1 || firstAttempt.getOrThrow().isNotEmpty())) {
            return@withContext firstAttempt.getOrThrow()
        }

        // 当前基础域名失效或未解析出帖子，启动候选域名与门户页动态探测
        val candidates = buildCandidateBaseUrls(currentUrl)
        for (candidate in candidates) {
            val result = runCatching { fetchPosts(candidate, page) }
            if (result.isSuccess && (page > 1 || result.getOrThrow().isNotEmpty())) {
                activeBaseUrl = candidate
                return@withContext result.getOrThrow()
            }
        }

        firstAttempt.getOrThrow()
    }

    private suspend fun fetchPosts(baseUrl: String, page: Int): List<ForumPost> = coroutineScope {
        val tasks = CORE_FIDS.map { fid ->
            async(Dispatchers.IO) {
                runCatching {
                    val url = "$baseUrl/forumdisplay.php?fid=$fid&page=$page"
                    val document = ForumHttp.fetchDocument(url)
                    parse(document, baseUrl)
                }.getOrDefault(emptyList())
            }
        }
        val allPosts = tasks.flatMap { it.await() }
            .distinctBy { it.sourceId to it.id }
            .sortedWith(
                compareByDescending<ForumPost> { it.updatedEpochSeconds }
                    .thenByDescending { it.id.toLongOrNull() ?: 0L },
            )
        android.util.Log.i("T906ForumSource", "fetchPosts baseUrl=$baseUrl page=$page parsed=${allPosts.size}")
        if (page == 1 && allPosts.isEmpty()) {
            throw IOException("91自拍论坛页面已打开，但没有识别到帖子 (baseUrl=$baseUrl)")
        }
        allPosts
    }

    private fun buildCandidateBaseUrls(failedUrl: String): List<String> {
        val candidates = linkedSetOf<String>()
        // 1. 尝试从默认视频源门户主页动态提取最新论坛入口
        discoverBaseUrlFromPortal()?.let { candidates.add(it) }
        // 2. 预置候选域名列表
        CANDIDATE_BASE_URLS.forEach { candidates.add(it) }
        candidates.remove(failedUrl)
        return candidates.toList()
    }

    internal fun discoverBaseUrlFromPortal(
        portalBaseUrl: String = ContentSourceCatalog.T91_BASE_URL,
    ): String? = runCatching {
        val url = "$portalBaseUrl/index.php"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15")
            .header("Cookie", "language=cn_CN; watch_times=1")
            .build()
        ForumHttp.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            extractForumBaseUrl(body)
        }
    }.getOrNull()

    internal fun parse(document: Document, fallbackBaseUrl: String = activeBaseUrl): List<ForumPost> = document.select("tbody[id^=normalthread_]")
        .mapNotNull { row ->
            val subject = row.selectFirst("span[id^=thread_] a[href*=viewthread]")
                ?: row.selectFirst("th.subject a[href*=viewthread]")
                ?: return@mapNotNull null
            val id = row.id().removePrefix("normalthread_").takeIf { it.all(Char::isDigit) } ?: return@mapNotNull null
            val author = row.selectFirst("td.author cite a")?.text().orEmpty().ifBlank {
                row.selectFirst("div.new_tr cite a")?.text().orEmpty().ifBlank { "匿名" }
            }
            val replies = row.selectFirst("td.nums strong")?.text()?.filter(Char::isDigit)?.toIntOrNull()
                ?: row.select("div.new_tr em").firstOrNull()?.text()?.filter(Char::isDigit)?.toIntOrNull()
                ?: 0
            val lastUpdate = row.selectFirst("td.lastpost em span")
                ?: row.selectFirst("div.new_tr em span")
            val exactUpdate = lastUpdate?.attr("title").orEmpty()
            ForumPost(
                sourceId = this.id,
                sourceName = name,
                id = id,
                title = subject.text().trim(),
                author = author,
                replyCount = replies,
                updatedLabel = lastUpdate?.text()?.trim().orEmpty(),
                updatedEpochSeconds = parseEpoch(exactUpdate),
                url = subject.absUrl("href").ifBlank { "$fallbackBaseUrl/viewthread.php?tid=$id" },
            )
        }
        .distinctBy { it.sourceId to it.id }

    private fun parseEpoch(value: String): Long = runCatching {
        LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-M-d H:mm"))
            .atZone(ZoneId.systemDefault())
            .toEpochSecond()
    }.getOrDefault(0L)

    internal companion object {
        internal const val PRIMARY_BASE_URL = "https://t924.sol140.com"
        internal const val LEGACY_BASE_URL = "https://t906.abc2507.cc"
        internal const val BASE_URL = PRIMARY_BASE_URL

        /**
         * 91 官方论坛核心板块列表：
         * 4: 原创自拍区（核心主力自拍板块）
         * 21: 我爱我妻（原创自拍夫妻板块）
         * 33: 性趣闲谈（交流闲聊板块）
         * 34: 两性健康（两性问答板块）
         * 36: 海外专区（海外自拍交流板块）
         */
        internal val CORE_FIDS = listOf(4, 21, 33, 34, 36)

        internal val CANDIDATE_BASE_URLS = listOf(
            PRIMARY_BASE_URL,
            "https://t925.sol140.com",
            LEGACY_BASE_URL,
        )

        private val FORUM_LINK_REGEX = Regex("""href=["'](https?://[^\s"'<>]+)["']?[^>]*>\s*自拍论坛""")

        internal fun extractForumBaseUrl(html: String): String? {
            return FORUM_LINK_REGEX.find(html)?.groupValues?.get(1)?.trimEnd('/')
        }
    }
}
