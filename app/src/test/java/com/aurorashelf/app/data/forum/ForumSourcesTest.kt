package com.aurorashelf.app.data.forum

import com.aurorashelf.app.model.ForumPost
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun t66yDaguerreSourceUsesFid16AndCustomMetadata() {
        val source = T66yForumSource(fid = 16, id = "t66y_16", name = "达盖尔的旗帜")
        assertEquals(16, source.fid)
        assertEquals("t66y_16", source.id)
        assertEquals("达盖尔的旗帜", source.name)

        val document = Jsoup.parse(
            """
            <table><tr class="tr3 t_one tac">
              <td><span>1</span></td>
              <td class="tal"><h3><a id="t7419999" href="/htm_data/2611/16/7419999.html">达盖尔唯美图集</a></h3></td>
              <td><a class="bl">摄影师</a></td>
              <td>52</td>
              <td><a data-timestamp="1788959999">11-08 12:00</a></td>
            </tr></table>
            """.trimIndent(),
            "https://t66y.com/thread0806.php?fid=16",
        )
        val post = source.parse(document).single()
        assertEquals("7419999", post.id)
        assertEquals("达盖尔唯美图集", post.title)
        assertEquals("摄影师", post.author)
        assertEquals(52, post.replyCount)
        assertEquals("t66y_16", post.sourceId)
        assertEquals("达盖尔的旗帜", post.sourceName)
        assertEquals("https://t66y.com/htm_data/2611/16/7419999.html", post.url)
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
    fun t906ExtractsForumBaseUrlFromPortalHtml() {
        val portalHtml = """
            <ul class="nav navbar-nav navbar-right">
              <li><a href="upload.php">上传</a></li>
              <li><a href="v.php">视频</a></li>
              <li><a target=blank href="https://t924.sol140.com" >自拍论坛</a></li>
              <li><a target=blank href="webcam.php">美女视频</a></li>
            </ul>
        """.trimIndent()

        val extracted = T906ForumSource.extractForumBaseUrl(portalHtml)
        assertEquals("https://t924.sol140.com", extracted)
    }

    @Test
    fun t906FallsBackWhenInitialDomainFails() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE_FORUM_TESTS") == "1")
        val source = T906ForumSource(initialBaseUrl = "https://t906.abc2507.cc")
        val posts = source.load(1)
        assertTrue(posts.isNotEmpty())
        assertEquals("https://t924.sol140.com", source.activeBaseUrl)
    }

    @Test
    fun t906ParserReadsActiveDomainPosts() {
        val document = Jsoup.parse(
            """
            <table><tbody id="normalthread_856978"><tr>
              <th class="subject new"><span id="thread_856978"><a href="viewthread.php?tid=856978&amp;extra=page%3D1">自拍帖子测试</a></span></th>
              <td class="author"><cite><a href="space.php?uid=756473">测试作者</a></cite><em>2026-10-26</em></td>
              <td class="nums"><strong>12</strong></td>
              <td class="lastpost"><em><a><span title="2026-10-26 15:21">11 分钟前</span></a></em></td>
            </tr></tbody></table>
            """.trimIndent(),
            "https://t924.sol140.com/forumdisplay.php?fid=33",
        )

        val post = T906ForumSource().parse(document).single()

        assertEquals("856978", post.id)
        assertEquals("自拍帖子测试", post.title)
        assertEquals("测试作者", post.author)
        assertEquals(12, post.replyCount)
        assertTrue(post.updatedEpochSeconds > 0)
        assertEquals("https://t924.sol140.com/viewthread.php?tid=856978&extra=page%3D1", post.url)
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

    @Test
    fun forumNoiseFilterBlocksNoticeAndSpamTitles() {
        // 用户明确要求排除的无用标题
        assertTrue(ForumNoiseFilter.isNoisePost("91网须知"))
        assertTrue(ForumNoiseFilter.isNoisePost("91论坛须知与总则"))
        assertTrue(ForumNoiseFilter.isNoisePost("论坛手机版的使用详情"))
        assertTrue(ForumNoiseFilter.isNoisePost("如何获取91论坛的邀请码"))
        assertTrue(ForumNoiseFilter.isNoisePost("获取91论坛邀请码方法"))
        assertTrue(ForumNoiseFilter.isNoisePost("【置顶公告】本版发帖须知与新手指南"))
        assertTrue(ForumNoiseFilter.isNoisePost("[公告] 永久最新发布页与防封指南"))

        // 广告、博彩与引流垃圾信息
        assertTrue(ForumNoiseFilter.isNoisePost("澳门银河真人视讯开元棋牌代理加盟"))
        assertTrue(ForumNoiseFilter.isNoisePost("加微信进福利群，每天更新"))
        assertTrue(ForumNoiseFilter.isNoisePost("高薪兼职加V信扫码进群"))
        assertTrue(ForumNoiseFilter.isNoisePost("充值USDT代充话费优惠返利"))

        // 正常网友原创分享与交流讨论必须完好保留
        assertFalse(ForumNoiseFilter.isNoisePost("周末带老婆去海边自拍散步留念"))
        assertFalse(ForumNoiseFilter.isNoisePost("记录和女友相识两周年的生活日常"))
        assertFalse(ForumNoiseFilter.isNoisePost("两性健康与日常保养心得交流分享"))
        assertFalse(ForumNoiseFilter.isNoisePost("第一次发帖分享下两人的甜蜜瞬间"))
    }

    private fun post(sourceId: String, id: String, update: Long) = ForumPost(
        sourceId = sourceId,
        sourceName = sourceId,
        id = id,
        title = "测试自拍帖子标题 $id",
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
