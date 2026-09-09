package com.aurorashelf.app.data.forum

import com.aurorashelf.app.model.ForumPost
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ForumSourcesTest {
    @Test
    fun livePublicPagesStillMatchParsers() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE_FORUM_TESTS") == "1")

        val result = ForumRepository().load(1)

        assertTrue(result.posts.any { it.sourceId == "t66y" })
        assertTrue(result.posts.any { it.sourceId == "t906" })
        assertTrue(result.posts.all { it.title.isNotBlank() && it.url.startsWith("https://") })
    }

    @Test
    fun t66yParserReadsTopicMetadata() {
        val document = Jsoup.parse(
            """
            <table><tr class="tr3 t_one tac">
              <td><span>25</span></td>
              <td class="tal"><h3><a id="t7418210" href="/htm_data/2609/7/7418210.html">测试主题</a></h3></td>
              <td><a class="bl">测试作者</a></td>
              <td>18</td>
              <td><a data-timestamp="1788958123">09-09 20:48</a></td>
            </tr></table>
            """.trimIndent(),
            "https://t66y.com/thread0806.php?fid=7",
        )

        val post = T66yForumSource().parse(document).single()

        assertEquals("7418210", post.id)
        assertEquals("测试主题", post.title)
        assertEquals("测试作者", post.author)
        assertEquals(18, post.replyCount)
        assertEquals(1788958123L, post.updatedEpochSeconds)
        assertEquals("https://t66y.com/htm_data/2609/7/7418210.html", post.url)
    }

    @Test
    fun t906ParserReadsNormalThreadAndExactUpdateTime() {
        val document = Jsoup.parse(
            """
            <table><tbody id="normalthread_848700"><tr>
              <th class="subject"><span id="thread_848700"><a href="viewthread.php?tid=848700">合并论坛测试</a></span></th>
              <td class="author"><cite><a>作者甲</a></cite><em>2026-9-9</em></td>
              <td class="nums"><strong>11</strong></td>
              <td class="lastpost"><em><a><span title="2026-9-9 20:17">9 分钟前</span></a></em></td>
            </tr></tbody></table>
            """.trimIndent(),
            "https://t906.abc2507.cc/forumdisplay.php?fid=33",
        )

        val post = T906ForumSource().parse(document).single()

        assertEquals("848700", post.id)
        assertEquals("合并论坛测试", post.title)
        assertEquals("作者甲", post.author)
        assertEquals(11, post.replyCount)
        assertTrue(post.updatedEpochSeconds > 0)
        assertEquals("https://t906.abc2507.cc/viewthread.php?tid=848700", post.url)
    }

    @Test
    fun repositoryMergesSourcesAndKeepsPartialResult() = runBlocking {
        val working = FakeSource("one", "来源一", listOf(post("one", "1", 100)))
        val failing = FakeSource("two", "来源二", failure = IOException("offline"))

        val result = ForumRepository(listOf(working, failing)).load(1)

        assertEquals(listOf("1"), result.posts.map { it.id })
        assertEquals(listOf("来源二"), result.unavailableSources)
    }

    @Test
    fun repositorySortsMergedUpdatesNewestFirst() = runBlocking {
        val first = FakeSource("one", "来源一", listOf(post("one", "1", 100)))
        val second = FakeSource("two", "来源二", listOf(post("two", "2", 200)))

        val result = ForumRepository(listOf(first, second)).load(1)

        assertEquals(listOf("2", "1"), result.posts.map { it.id })
    }

    private fun post(sourceId: String, id: String, update: Long) = ForumPost(
        sourceId = sourceId,
        sourceName = sourceId,
        id = id,
        title = id,
        author = "author",
        replyCount = 0,
        updatedLabel = "now",
        updatedEpochSeconds = update,
        url = "https://example.com/$id",
    )

    private class FakeSource(
        override val id: String,
        override val name: String,
        private val posts: List<ForumPost> = emptyList(),
        private val failure: Throwable? = null,
    ) : ForumSource {
        override suspend fun load(page: Int): List<ForumPost> {
            failure?.let { throw it }
            return posts
        }
    }
}
