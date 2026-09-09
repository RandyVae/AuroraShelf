package com.aurorashelf.app.data.forum

import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.model.ForumPost
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

internal class T66yForumSource : ForumSource {
    override val id = "t66y"
    override val name = "草榴社区"

    override suspend fun load(page: Int): List<ForumPost> = withContext(Dispatchers.IO) {
        require(page >= 1) { "页码必须从 1 开始" }
        val url = "$BASE_URL/thread0806.php?fid=7&page=$page"
        val document = Jsoup.connect(url)
            .userAgent(VideoRepository.USER_AGENT)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .timeout(25_000)
            .followRedirects(true)
            .get()
        val posts = parse(document)
        if (page == 1 && posts.isEmpty()) throw IOException("草榴社区页面已打开，但没有识别到帖子")
        posts
    }

    internal fun parse(document: Document): List<ForumPost> = document.select("tr.tr3.t_one")
        .mapNotNull { row ->
            val subject = row.selectFirst("td.tal h3 a[id^=t]") ?: return@mapNotNull null
            val id = subject.id().removePrefix("t").takeIf { it.all(Char::isDigit) } ?: return@mapNotNull null
            val cells = row.children()
            val author = cells.getOrNull(2)?.selectFirst("a.bl")?.text().orEmpty().ifBlank { "匿名" }
            val replies = cells.getOrNull(3)?.text()?.filter(Char::isDigit)?.toIntOrNull() ?: 0
            val lastPost = cells.getOrNull(4)?.selectFirst("a[data-timestamp]")
            ForumPost(
                sourceId = this.id,
                sourceName = name,
                id = id,
                title = subject.text().trim(),
                author = author,
                replyCount = replies,
                updatedLabel = lastPost?.text()?.trim().orEmpty(),
                updatedEpochSeconds = lastPost?.attr("data-timestamp")?.filter(Char::isDigit)?.toLongOrNull() ?: 0L,
                url = subject.absUrl("href").ifBlank { "$BASE_URL/read.php?tid=$id" },
            )
        }
        .distinctBy { it.sourceId to it.id }

    private companion object {
        const val BASE_URL = "https://t66y.com"
    }
}
