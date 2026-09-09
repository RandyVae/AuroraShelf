package com.aurorashelf.app.data.forum

import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.model.ForumPost
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

internal class T906ForumSource : ForumSource {
    override val id = "t906"
    override val name = "91自拍论坛"

    override suspend fun load(page: Int): List<ForumPost> = withContext(Dispatchers.IO) {
        require(page >= 1) { "页码必须从 1 开始" }
        val url = "$BASE_URL/forumdisplay.php?fid=33&page=$page"
        val document = Jsoup.connect(url)
            .userAgent(VideoRepository.USER_AGENT)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .timeout(25_000)
            .followRedirects(true)
            .get()
        val posts = parse(document)
        if (page == 1 && posts.isEmpty()) throw IOException("91自拍论坛页面已打开，但没有识别到帖子")
        posts
    }

    internal fun parse(document: Document): List<ForumPost> = document.select("tbody[id^=normalthread_]")
        .mapNotNull { row ->
            val subject = row.selectFirst("span[id^=thread_] > a[href*=viewthread]") ?: return@mapNotNull null
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
                url = subject.absUrl("href").ifBlank { "$BASE_URL/viewthread.php?tid=$id" },
            )
        }
        .distinctBy { it.sourceId to it.id }

    private fun parseEpoch(value: String): Long = runCatching {
        LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-M-d H:mm"))
            .atZone(ZoneId.systemDefault())
            .toEpochSecond()
    }.getOrDefault(0L)

    private companion object {
        const val BASE_URL = "https://t906.abc2507.cc"
    }
}
