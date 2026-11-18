package com.aurorashelf.app.data.forum

import com.aurorashelf.app.model.ForumPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumThreadParserTest {
    @Test
    fun parsesT66yMultiFloorInterleavedParagraphsImagesAndPagination() {
        val html = """
            <html><body>
            <div id="main">
              <div class="pages"><b>1</b><a href="read.php?tid=1001&page=2">2</a><a href="read.php?tid=1001&page=3">3</a></div>
              <div class="t t2">
                <table>
                  <tr class="tr1">
                    <th rowspan="2"><b>旅行摄影师</b></th>
                    <th>
                      <div class="tipad"><span data-timestamp="1700000000">2026-11-18 14:20</span></div>
                      <div class="tpc_content">
                        第一段前言介绍<br><br>
                        <img ess-data="https://cdn.example.com/photo1.jpg" src="https://t66y.com/none.gif">
                        <br>第二段图文说明<br>
                        <img data-src="https://cdn.example.com/photo2.jpg">
                      </div>
                    </th>
                  </tr>
                </table>
              </div>
              <div class="t t2">
                <table>
                  <tr class="tr1">
                    <th rowspan="2"><b>热心网友</b></th>
                    <th>
                      <div class="tipad"><span data-timestamp="1700000500">2026-11-18 14:28</span></div>
                      <div class="tpc_content">感谢楼主分享高清美图！</div>
                    </th>
                  </tr>
                </table>
              </div>
            </div>
            </body></html>
        """.trimIndent()

        val fallback = ForumPost(
            sourceId = "t66y_16",
            sourceName = "达盖尔的旗帜",
            id = "1001",
            title = "周末胶片纪实",
            author = "旅行摄影师",
            replyCount = 12,
            updatedLabel = "14:28",
            updatedEpochSeconds = 1700000500L,
            url = "https://t66y.com/htm_data/2611/16/1001.html",
        )

        val detail = ForumThreadParser.parse(html, fallback.url, fallback)
        assertEquals("周末胶片纪实", detail.title)
        assertEquals(2, detail.floors.size)
        assertEquals(listOf("https://cdn.example.com/photo1.jpg", "https://cdn.example.com/photo2.jpg"), detail.allImageUrls)
        assertEquals(3, detail.pageLinks.size)
        assertTrue(detail.pageLinks[0].isCurrent)
        assertEquals("https://t66y.com/htm_data/2611/16/read.php?tid=1001&page=2", detail.pageLinks[1].url)

        val opFloor = detail.floors[0]
        assertTrue(opFloor.isOp)
        assertEquals("旅行摄影师", opFloor.author)
        assertEquals(4, opFloor.blocks.size)
        assertEquals(ForumContentBlock.Paragraph("第一段前言介绍"), opFloor.blocks[0])
        assertEquals(ForumContentBlock.Image("https://cdn.example.com/photo1.jpg"), opFloor.blocks[1])
        assertEquals(ForumContentBlock.Paragraph("第二段图文说明"), opFloor.blocks[2])
        assertEquals(ForumContentBlock.Image("https://cdn.example.com/photo2.jpg"), opFloor.blocks[3])

        val replyFloor = detail.floors[1]
        assertEquals(2, replyFloor.floorNumber)
        assertEquals("热心网友", replyFloor.author)
        assertEquals(listOf(ForumContentBlock.Paragraph("感谢楼主分享高清美图！")), replyFloor.blocks)
    }

    @Test
    fun parsesDiscuzPostlistAndAttachmentGalleryWhileFilteringMetadataNoise() {
        val html = """
            <html><body>
            <div id="wrap">
              <div id="postlist">
                <div id="post_9001">
                  <div class="postinfo"><a class="posterlink">海边旅人</a><em id="authorposton9001">发表于 2026-11-18 16:05</em></div>
                  <td class="postcontent">
                    <div class="postmessage">
                      <div id="postmessage_9001" class="t_msgfont">
                        今日随拍记录<br>
                        <img file="attachments/day_261118/pic_a.jpg" src="images/common/none.gif">
                      </div>
                      <div class="postattachlist">
                        <p>128.4 KB, 下载次数: 35</p>
                        <img zoomfile="https://img.example.org/pic_b.webp">
                      </div>
                    </div>
                  </td>
                </div>
              </div>
            </div>
            </body></html>
        """.trimIndent()

        val detail = ForumThreadParser.parse(html, "https://t924.sol140.com/viewthread.php?tid=888")
        assertEquals(1, detail.floors.size)
        assertEquals("海边旅人", detail.floors[0].author)
        assertEquals("2026-11-18 16:05", detail.floors[0].timestamp)
        assertEquals(
            listOf(
                "https://t924.sol140.com/attachments/day_261118/pic_a.jpg",
                "https://img.example.org/pic_b.webp",
            ),
            detail.allImageUrls,
        )
        val paragraphs = detail.floors[0].blocks.filterIsInstance<ForumContentBlock.Paragraph>()
        assertEquals(listOf(ForumContentBlock.Paragraph("今日随拍记录")), paragraphs)
    }
}
