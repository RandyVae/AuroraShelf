package com.aurorashelf.app.data.forum

import com.aurorashelf.app.model.ForumPost
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal sealed class ForumContentBlock {
    data class Paragraph(val text: String) : ForumContentBlock()
    data class Image(val url: String) : ForumContentBlock()
    data class ExternalLink(val label: String, val url: String) : ForumContentBlock()
}

internal data class ForumFloor(
    val floorNumber: Int,
    val author: String,
    val timestamp: String,
    val isOp: Boolean,
    val blocks: List<ForumContentBlock>,
)

internal data class ForumPageLink(
    val label: String,
    val url: String,
    val isCurrent: Boolean,
)

internal data class ForumThreadDetail(
    val title: String,
    val floors: List<ForumFloor>,
    val allImageUrls: List<String>,
    val pageLinks: List<ForumPageLink>,
)

/**
 * Parses legacy T66Y (草榴社区 / 达盖尔的旗帜) and Discuz! (91自拍论坛) HTML thread pages
 * into structured multi-floor native content blocks while preserving exact interleaved
 * paragraph/image reading order, clean line breaks, and thread pagination links.
 */
internal object ForumThreadParser {
    private val IMAGE_ATTR_PRIORITY = listOf(
        "ess-data",
        "data-link",
        "file",
        "zoomfile",
        "data-src",
        "data-original",
        "src",
    )

    private val IGNORED_IMAGE_TOKENS = listOf(
        "smiley",
        "/smiles/",
        "/static/image/common/",
        "avatar",
        "none.gif",
        "adblo_ck.jpg",
        "attachimg.gif",
        "ding.png",
        "ding_big.png",
        "star_level",
        "userinfo.gif",
        "logo.png",
        "desktop.png",
        "mobile.png",
        "back.gif",
    )

    private val BLOCK_TAGS = setOf(
        "p", "div", "li", "tr", "blockquote", "pre", "h1", "h2", "h3", "h4", "h5", "h6", "section", "article",
    )

    private val POSTED_TIME_REGEX = Regex("""(\d{4}-\d{1,2}-\d{1,2}\s+\d{1,2}:\d{2}(?::\d{2})?)""")

    fun parse(
        html: String,
        baseUri: String,
        fallbackPost: ForumPost? = null,
    ): ForumThreadDetail {
        val doc = Jsoup.parse(html, baseUri)
        return parse(doc, fallbackPost)
    }

    fun parse(
        doc: Document,
        fallbackPost: ForumPost? = null,
    ): ForumThreadDetail {
        doc.select("script, style, noscript, iframe, .tipad > span:contains(举报), [id^=ad_thread], .threadad").remove()

        val baseUri = doc.baseUri()
        val isT66y = baseUri.contains("t66y.com", ignoreCase = true) ||
            doc.selectFirst("#main .tpc_content, div.tpc_content") != null

        val floors = if (isT66y) {
            parseT66yFloors(doc, fallbackPost)
        } else {
            parseDiscuzFloors(doc, fallbackPost)
        }.ifEmpty {
            parseFallbackFloor(doc, fallbackPost)
        }

        val allImages = floors
            .flatMap { floor -> floor.blocks.filterIsInstance<ForumContentBlock.Image>().map(ForumContentBlock.Image::url) }
            .let(::deduplicateForumImageUrls)

        val pageLinks = parsePagination(doc)
        val resolvedTitle = fallbackPost?.title?.takeIf(String::isNotBlank)
            ?: doc.selectFirst("h4.f16, #threadtitle h1, title")?.text()?.trim().orEmpty()

        return ForumThreadDetail(
            title = resolvedTitle,
            floors = floors,
            allImageUrls = allImages,
            pageLinks = pageLinks,
        )
    }

    private fun parseT66yFloors(doc: Document, fallbackPost: ForumPost?): List<ForumFloor> {
        val containers = doc.select("#main > .t2, div.t.t2").ifEmpty {
            doc.select("div.tpc_content").mapNotNull { it.parent() }
        }
        val opAuthor = fallbackPost?.author?.takeIf(String::isNotBlank)
        val floors = mutableListOf<ForumFloor>()

        containers.forEachIndexed { idx, container ->
            val contentEl = container.selectFirst(".tpc_content") ?: return@forEachIndexed
            val rawAuthor = container.selectFirst("tr.tr1 th[rowspan=2] b, .r_two b, .aurora-post-meta")
                ?.text()
                ?.substringBefore('·')
                ?.trim()
                .orEmpty()
                .ifBlank { if (idx == 0) opAuthor.orEmpty() else "网友" }
            val timestamp = container.selectFirst(".tipad [data-timestamp]")?.text()?.trim()
                ?: POSTED_TIME_REGEX.find(container.selectFirst(".tipad")?.text().orEmpty())?.value
                ?: if (idx == 0) fallbackPost?.updatedLabel.orEmpty() else ""

            contentEl.select(".aurora-post-meta, .aurora-img-save-btn").remove()
            val blocks = extractBlocks(contentEl, doc.baseUri())
            if (blocks.isNotEmpty()) {
                val floorNumber = floors.size + 1
                val isOp = floorNumber == 1 || (!opAuthor.isNullOrBlank() && rawAuthor == opAuthor)
                floors += ForumFloor(
                    floorNumber = floorNumber,
                    author = rawAuthor.ifBlank { "匿名" },
                    timestamp = timestamp,
                    isOp = isOp,
                    blocks = blocks,
                )
            }
        }
        return floors
    }

    private fun parseDiscuzFloors(doc: Document, fallbackPost: ForumPost?): List<ForumFloor> {
        val containers = doc.select("#postlist > div[id^=post_], #postlist > div:has(.postmessage), table[id^=pid]")
        val opAuthor = fallbackPost?.author?.takeIf(String::isNotBlank)
        val floors = mutableListOf<ForumFloor>()

        if (containers.isEmpty()) {
            val messageCells = doc.select("td[id^=postmessage_], div.t_msgfont, div.postmessage")
            messageCells.forEachIndexed { idx, contentEl ->
                val blocks = extractBlocks(contentEl, doc.baseUri())
                if (blocks.isNotEmpty()) {
                    floors += ForumFloor(
                        floorNumber = idx + 1,
                        author = if (idx == 0) opAuthor ?: "楼主" else "网友",
                        timestamp = if (idx == 0) fallbackPost?.updatedLabel.orEmpty() else "",
                        isOp = idx == 0,
                        blocks = blocks,
                    )
                }
            }
            return floors
        }

        containers.forEachIndexed { idx, container ->
            val author = container.selectFirst(".postinfo .posterlink, .postauthor .postinfo a, .authi a, cite a")
                ?.text()
                ?.trim()
                .orEmpty()
                .ifBlank { if (idx == 0) opAuthor.orEmpty() else "网友" }
            val rawTime = container.selectFirst("em[id^=authorposton], .authi em")?.text().orEmpty()
            val timestamp = POSTED_TIME_REGEX.find(rawTime)?.value
                ?: rawTime.removePrefix("发表于").trim().ifBlank {
                    if (idx == 0) fallbackPost?.updatedLabel.orEmpty() else ""
                }

            val bodyRoot = container.selectFirst("td.postcontent, .plc") ?: container
            bodyRoot.select(
                ".postinfo, .postact, .postbottom, .signatures, .pstatus, .aurora-img-save-btn, " +
                    "dl.t_attachlist dt, p.imgtitle, div.tip, span[id^=attach_]",
            ).remove()
            val messageRoot = bodyRoot.selectFirst("div.postmessage, td[id^=postmessage_], div.t_msgfont") ?: bodyRoot
            val blocks = mutableListOf<ForumContentBlock>()
            blocks += extractBlocks(messageRoot, doc.baseUri())

            // Also pick up Discuz attachment gallery images placed outside postmessage_X
            if (messageRoot !== bodyRoot) {
                bodyRoot.select(".postattachlist img, dl.t_attachlist img").forEach { attachImg ->
                    resolveImageUrl(attachImg, doc.baseUri())?.let { imgUrl ->
                        if (blocks.none { it is ForumContentBlock.Image && it.url == imgUrl }) {
                            blocks += ForumContentBlock.Image(imgUrl)
                        }
                    }
                }
            }

            if (blocks.isNotEmpty()) {
                val floorNumber = floors.size + 1
                val isOp = floorNumber == 1 || (!opAuthor.isNullOrBlank() && author == opAuthor)
                floors += ForumFloor(
                    floorNumber = floorNumber,
                    author = author.ifBlank { "匿名" },
                    timestamp = timestamp,
                    isOp = isOp,
                    blocks = blocks,
                )
            }
        }
        return floors
    }

    private fun parseFallbackFloor(doc: Document, fallbackPost: ForumPost?): List<ForumFloor> {
        val root = doc.selectFirst("article, main, #main, #wrap, body") ?: return emptyList()
        val blocks = extractBlocks(root, doc.baseUri())
        if (blocks.isEmpty()) return emptyList()
        return listOf(
            ForumFloor(
                floorNumber = 1,
                author = fallbackPost?.author?.ifBlank { "楼主" } ?: "楼主",
                timestamp = fallbackPost?.updatedLabel.orEmpty(),
                isOp = true,
                blocks = blocks,
            ),
        )
    }

    private fun extractBlocks(root: Element, baseUri: String): List<ForumContentBlock> {
        val rawBlocks = mutableListOf<ForumContentBlock>()
        val textBuffer = StringBuilder()

        fun flushParagraphs() {
            val raw = textBuffer.toString()
            textBuffer.setLength(0)
            val paragraphs = raw
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .split('\n')
                .map { line ->
                    line.replace(Regex("[ \\t\\u00A0]+"), " ").trim()
                }
                .filter { line ->
                    line.isNotEmpty() && !isAttachmentMetadataNoise(line)
                }
            if (paragraphs.isNotEmpty()) {
                // Group consecutive short lines into readable paragraphs (max ~6 lines per block)
                for (chunk in paragraphs.chunked(6)) {
                    rawBlocks += ForumContentBlock.Paragraph(chunk.joinToString("\n"))
                }
            }
        }

        fun walk(node: Node) {
            when (node) {
                is TextNode -> {
                    textBuffer.append(node.wholeText.replace('\n', ' '))
                }
                is Element -> {
                    val tag = node.normalName()
                    if (tag in setOf("script", "style", "noscript", "svg", "form", "input", "select", "button")) {
                        return
                    }
                    if (node.hasClass("aurora-img-save-btn") || node.hasClass("aurora-post-meta")) {
                        return
                    }
                    if (tag == "br") {
                        textBuffer.append('\n')
                        return
                    }
                    if (tag == "img") {
                        resolveImageUrl(node, baseUri)?.let { imageUrl ->
                            flushParagraphs()
                            if (rawBlocks.none { it is ForumContentBlock.Image && it.url == imageUrl }) {
                                rawBlocks += ForumContentBlock.Image(imageUrl)
                            }
                        }
                        return
                    }
                    if (tag == "a") {
                        // If anchor wraps an image, walk children so the image is emitted cleanly
                        if (node.selectFirst("img") != null) {
                            node.childNodes().forEach(::walk)
                            return
                        }
                        val href = node.absUrl("href").trim()
                        val label = node.text().trim()
                        if (href.startsWith("https://", ignoreCase = true) &&
                            !href.contains("aurorashelf://", ignoreCase = true) &&
                            label.isNotBlank() &&
                            !href.contains("javascript:", ignoreCase = true)
                        ) {
                            textBuffer.append(label)
                            // Surface standalone download / external reference links explicitly
                            if (!isSameForumThreadNavigation(href) && label.length <= 120) {
                                flushParagraphs()
                                rawBlocks += ForumContentBlock.ExternalLink(label = label, url = href)
                            }
                            return
                        }
                    }

                    val isBlock = tag in BLOCK_TAGS
                    if (isBlock && textBuffer.isNotEmpty() && textBuffer.last() != '\n') {
                        textBuffer.append('\n')
                    }
                    node.childNodes().forEach(::walk)
                    if (isBlock && textBuffer.isNotEmpty() && textBuffer.last() != '\n') {
                        textBuffer.append('\n')
                    }
                }
            }
        }

        walk(root)
        flushParagraphs()
        return rawBlocks
    }

    private fun resolveImageUrl(img: Element, baseUri: String): String? {
        val widthAttr = img.attr("width").toIntOrNull()
        val heightAttr = img.attr("height").toIntOrNull()
        if (widthAttr != null && heightAttr != null && widthAttr in 1..47 && heightAttr in 1..47) {
            return null
        }

        for (attr in IMAGE_ATTR_PRIORITY) {
            val rawVal = img.attr(attr).trim()
            if (rawVal.isEmpty()) continue
            if (rawVal.startsWith("data:image/", ignoreCase = true)) {
                return if (rawVal.length > 256) rawVal else null
            }
            if (rawVal.startsWith("data:", ignoreCase = true) || rawVal.startsWith("javascript:", ignoreCase = true)) {
                continue
            }
            val resolved = img.absUrl(attr).trim().ifEmpty {
                when {
                    rawVal.startsWith("//") -> "https:$rawVal"
                    rawVal.startsWith("http://", ignoreCase = true) || rawVal.startsWith("https://", ignoreCase = true) -> rawVal
                    baseUri.startsWith("http", ignoreCase = true) -> runCatching {
                        java.net.URI(baseUri).resolve(rawVal).toString()
                    }.getOrDefault("")
                    else -> ""
                }
            }
            if (resolved.isEmpty()) continue
            val httpsCandidate = if (resolved.startsWith("http://", ignoreCase = true)) {
                "https://" + resolved.substring(7)
            } else {
                resolved
            }
            if (!httpsCandidate.startsWith("https://", ignoreCase = true)) continue
            val lower = httpsCandidate.lowercase()
            if (IGNORED_IMAGE_TOKENS.any { lower.contains(it) }) continue
            return httpsCandidate
        }
        return null
    }

    private fun parsePagination(doc: Document): List<ForumPageLink> {
        val bar = doc.selectFirst(".aurora-pages .pages, .pages, .pg") ?: return emptyList()
        val seenUrls = LinkedHashSet<String>()
        val result = mutableListOf<ForumPageLink>()

        for (el in bar.children()) {
            val tag = el.normalName()
            val label = el.text().trim()
            if (label.isEmpty() || label.length > 16) continue
            if (el.selectFirst("input, select") != null) continue
            when {
                tag == "a" -> {
                    val url = el.absUrl("href").trim()
                    val isCurrent = el.hasClass("active") || el.hasClass("current") || url.startsWith("javascript:", ignoreCase = true)
                    if (isCurrent) {
                        if (label.all(Char::isDigit)) {
                            result += ForumPageLink(label = label, url = doc.baseUri(), isCurrent = true)
                        }
                    } else if (url.startsWith("https://", ignoreCase = true) && seenUrls.add(url)) {
                        result += ForumPageLink(label = label, url = url, isCurrent = false)
                    }
                }
                tag in setOf("b", "strong", "u", "span") && label.all(Char::isDigit) -> {
                    result += ForumPageLink(label = label, url = doc.baseUri(), isCurrent = true)
                }
            }
        }
        return if (result.count { !it.isCurrent } > 0) result.take(16) else emptyList()
    }

    private fun isAttachmentMetadataNoise(line: String): Boolean {
        if (line.contains("下载次数") && (line.contains("KB") || line.contains("MB") || line.contains("Bytes"))) {
            return true
        }
        if (line == "下载附件" || line == "保存到相册" || line == "保存原图") {
            return true
        }
        return false
    }

    private fun isSameForumThreadNavigation(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("forumdisplay.php") ||
            lower.contains("thread0806.php") ||
            lower.contains("logging.php") ||
            lower.contains("register.php") ||
            lower.contains("space.php") ||
            lower.contains("profile.php") ||
            lower.contains("post.php")
    }
}
