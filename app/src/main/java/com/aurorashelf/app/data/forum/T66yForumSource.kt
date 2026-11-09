package com.aurorashelf.app.data.forum

import com.aurorashelf.app.model.ForumPost
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document

internal class T66yForumSource(
    val fid: Int = 7,
    override val id: String = if (fid == 7) "t66y" else "t66y_$fid",
    override val name: String = if (fid == 7) "草榴社区" else "达盖尔的旗帜",
) : ForumSource {
    override suspend fun load(page: Int): List<ForumPost> = withContext(Dispatchers.IO) {
        require(page >= 1) { "页码必须从 1 开始" }
        val url = "$BASE_URL/thread0806.php?fid=$fid&page=$page"
        val document = ForumHttp.fetchDocument(url)
        val posts = parse(document)
        if (page == 1 && posts.isEmpty()) throw IOException("$name 页面已打开，但没有识别到帖子")
        posts
    }

    internal fun parse(document: Document): List<ForumPost> = document.select("tr.tr3.t_one")
        .mapNotNull { row ->
            val subject = row.selectFirst("td.tal h3 a[id^=t]") ?: return@mapNotNull null
            val title = subject.text().trim()
            if (ForumNoiseFilter.isNoisePost(title)) return@mapNotNull null
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

    internal companion object {
        internal const val BASE_URL = "https://t66y.com"
    }
}
