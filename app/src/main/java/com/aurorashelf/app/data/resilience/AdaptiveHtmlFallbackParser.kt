package com.aurorashelf.app.data.resilience

import android.util.Log
import com.aurorashelf.app.data.SourceAddress
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.VideoItem
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/**
 * Tier-2 / Tier-3 结构化启发式 DOM 兜底解析器：
 * 当源站点改版更换 CSS 类名（如 91 从 `.video-title` -> `.vname` -> `.vcap` -> `.new-card-title`）
 * 导致主解析器返回 0 条结果时，自动按 DOM 结构特征（视频详情特征链接 + 内嵌或邻近图片 + 去角标标题）恢复卡片。
 */
internal object AdaptiveHtmlFallbackParser {
    private const val TAG = "AuroraDomSelfHeal"
    private val DURATION_REGEX = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b""")
    private val CSS_BG_REGEX = Regex("""url\(['"]?([^)'"\s]+)['"]?\)""", RegexOption.IGNORE_CASE)
    private val NOISE_BADGE_SELECTOR = ".duration, .thumb-overlay, .hd-text-icon, .original-text-icon, .badge, .public-list-prb"
    private val VIDEO_HREF_PATTERNS = listOf(
        "view_video.php", "viewkey=", "/voddetail/", "/vodplay/", "/video/", "/videos/",
        "/watch", "/drama/", "/anime/", "/detail/", "/v/",
    )

    fun extractFallbackVideoItems(
        document: Document,
        baseUrl: String,
        sourceKey: String? = SourceHealthRegistry.sourceKeyForUrl(baseUrl),
    ): List<VideoItem> {
        // 1. 优先尝试远端热更新清单下发的自定义选择器
        val selectorHints = sourceKey?.let { RemoteSourceManifest.cardSelectorsFor(it) }.orEmpty()
        for (selector in selectorHints) {
            val matched = extractByCardElements(document.select(selector), baseUrl)
            if (matched.isNotEmpty()) {
                runCatching { Log.i(TAG, "manifest selector healed [$baseUrl] count=${matched.size}") }
                return matched
            }
        }

        // 2. 结构化通用启发式兜底：匹配携带视频链接且包裹/伴随封面图的卡片链接
        val anchors = document.select("a[href]")
        val candidates = buildList {
            for (anchor in anchors) {
                val href = anchor.attr("href").trim()
                if (!looksLikeVideoHref(href, baseUrl)) continue
                val item = parseCardFromAnchorOrParent(anchor, baseUrl) ?: continue
                add(item)
            }
        }.distinctBy(VideoItem::pageUrl)

        if (candidates.isNotEmpty()) {
            runCatching { Log.i(TAG, "structural fallback healed [$baseUrl] count=${candidates.size}") }
        }
        return candidates
    }

    private fun extractByCardElements(elements: List<Element>, baseUrl: String): List<VideoItem> {
        return elements.mapNotNull { el ->
            val anchor = if (el.tagName().equals("a", ignoreCase = true)) el else el.selectFirst("a[href]")
            anchor?.let { parseCardFromAnchorOrParent(it, baseUrl, containerOverride = el) }
        }.distinctBy(VideoItem::pageUrl)
    }

    private fun parseCardFromAnchorOrParent(
        anchor: Element,
        baseUrl: String,
        containerOverride: Element? = null,
    ): VideoItem? {
        val href = anchor.attr("href").trim()
        val pageUrl = SourceAddress.resolve(baseUrl.trimEnd('/') + "/", href) ?: return null
        val container = containerOverride ?: anchor.parent() ?: anchor

        val image = anchor.selectFirst("img") ?: container.selectFirst("img")
        val thumbFromImg = image?.let { img ->
            val raw = img.attr("data-src")
                .ifBlank { img.attr("data-original") }
                .ifBlank { img.attr("data-lazy-src") }
                .ifBlank { img.attr("src") }
                .trim()
            if (raw.startsWith("data:image", ignoreCase = true) || raw.contains("placeholder", ignoreCase = true)) {
                null
            } else {
                SourceAddress.resolve(baseUrl.trimEnd('/') + "/", raw)
            }
        }
        val thumbFromBg = if (thumbFromImg == null) {
            val styled = anchor.selectFirst("[style*=background]") ?: container.selectFirst("[style*=background]")
            val bgUrl = styled?.attr("style")?.let { CSS_BG_REGEX.find(it)?.groupValues?.getOrNull(1) }
            bgUrl?.let { SourceAddress.resolve(baseUrl.trimEnd('/') + "/", it) }
        } else {
            null
        }
        val thumbnailUrl = thumbFromImg ?: thumbFromBg

        // 若完全没有图片且链接本身不是 view_video，大概率是导航菜单而非内容卡片
        if (thumbnailUrl == null && !href.contains("view_video", ignoreCase = true)) {
            return null
        }

        val cleanedContainerText = container.clone().apply {
            select(NOISE_BADGE_SELECTOR).remove()
        }.text().trim()
        val cleanedAnchorText = anchor.clone().apply {
            select(NOISE_BADGE_SELECTOR).remove()
        }.text().trim()

        val rawTitle = anchor.attr("title").trim()
            .ifBlank { image?.attr("alt")?.trim().orEmpty() }
            .ifBlank { cleanedAnchorText }
            .ifBlank { cleanedContainerText }
        val title = Parser.unescapeEntities(rawTitle, false)
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (title.length < 2) return null

        val duration = container.selectFirst(".duration, .public-list-prb")?.text()?.trim()
            ?.takeIf { DURATION_REGEX.containsMatchIn(it) }
            ?: DURATION_REGEX.find(container.text())?.value
            ?: "--:--"

        val sourceName = ContentSourceCatalog.inferSourceName(baseUrl)
        return VideoItem(
            id = sha256(pageUrl),
            title = title,
            author = sourceName,
            duration = duration,
            views = "自动修复加载",
            pageUrl = pageUrl,
            thumbnailUrl = thumbnailUrl,
        )
    }

    private fun looksLikeVideoHref(href: String, baseUrl: String): Boolean {
        if (href.isBlank() || href == "#" || href.startsWith("javascript:", ignoreCase = true)) return false
        val lower = href.lowercase()
        if (lower.contains("/login") || lower.contains("/register") || lower.contains("/search") || lower.contains("/advert")) {
            return false
        }
        if (VIDEO_HREF_PATTERNS.any { lower.contains(it) }) return true
        // MissAV-style /{lang}/{code} routes
        if (ContentSourceCatalog.isMissAv(baseUrl) && (lower.contains("/cn/") || lower.contains("/ja/"))) return true
        return false
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
