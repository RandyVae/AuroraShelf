package com.aurorashelf.app.model

import java.net.URI
import org.schabi.newpipe.extractor.NewPipe

data class ContentSource(
    val id: String,
    val name: String,
    val description: String,
    val baseUrl: String,
    val extractorServiceName: String? = null,
    val kioskIds: List<String> = emptyList(),
    val isDirectDomestic: Boolean = false,
)

/** Preset sources shown in the app. The URL field remains an implementation detail. */
object ContentSourceCatalog {
    const val T91_BASE_URL = "https://h1014.sol148.com"
    const val XVIDEOS_BASE_URL = "https://www.xvideos.com"
    const val DEFAULT_BASE_URL = XVIDEOS_BASE_URL
    const val HUANGGUO_BASE_URL = "https://huangguoai.com"
    const val HUAVOD_BASE_URL = "https://huavod.com"
    const val BEEG_BASE_URL = "https://beeg.com"
    const val T91DONGMAN_BASE_URL = "https://91dongman.net"
    const val MISSAV_BASE_URL = "https://missav.ws"

    private val builtInSources = listOf(
        ContentSource("91", "91视频", "经典内容库 · 大陆直连稳定", T91_BASE_URL, isDirectDomestic = true),
        ContentSource("huangguo", "黄果短剧", "短剧与榜单 · 大陆直连稳定", HUANGGUO_BASE_URL, isDirectDomestic = true),
        ContentSource("huavod", "华视影院", "海量影视与综艺 · 官方直连", HUAVOD_BASE_URL, isDirectDomestic = true),
        ContentSource("91dongman", "91动漫", "海量里番、3D与AI动漫 · 官方直连", T91DONGMAN_BASE_URL, isDirectDomestic = true),
        ContentSource("beeg", "Beeg", "极简高清流媒体 · 海外直连/代理", BEEG_BASE_URL, isDirectDomestic = false),
    )

    private fun serviceDescription(serviceName: String): String = when (serviceName) {
        "MissAV" -> "高清日韩番号 · 海外直连/代理"
        "85po" -> "热门自拍与原创 · 海外直连/代理"
        "KissJAV" -> "海量综合视频 · 海外直连/代理"
        "JAV-NONI" -> "独家精选流媒体 · 海外直连/代理"
        "JAVSB" -> "亚洲精选影视 · 海外直连/代理"
        "TOKYO Motion" -> "日本同人与动漫 · 海外直连/代理"
        "MRDOUGA" -> "二次元同人动画 · 海外直连/代理"
        "OHentai" -> "动漫里番合集 · 海外直连/代理"
        "Pornhub" -> "全球主流视频库 · 需国际网络"
        "SpankBang" -> "高清精选视频 · 需国际网络"
        "xHamster" -> "全球综合社区 · 需国际网络"
        "XVideos" -> "全球大型视频库 · 默认推荐"
        "EPORNER" -> "4K超清聚合源 · 需国际网络"
        "XNXX" -> "全球高速视频库 · 需国际网络"
        else -> "MISSPipe 视频源 · 海外线路"
    }

    private val pipePipeSources: List<ContentSource> by lazy {
        NewPipe.getServices().map { service ->
            val serviceName = service.serviceInfo.name
            val kiosks = runCatching { service.kioskList.availableKiosks.toList() }.getOrDefault(emptyList())
                .sortedWith(compareBy({ KIOSK_ORDER.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }, { it }))
            ContentSource(
                id = "pipe-${serviceName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}",
                name = serviceName,
                description = serviceDescription(serviceName),
                baseUrl = service.baseUrl,
                extractorServiceName = serviceName,
                kioskIds = kiosks,
                isDirectDomestic = false,
            )
        }.sortedByDescending { it.baseUrl == XVIDEOS_BASE_URL }
    }

    val sources: List<ContentSource> get() = builtInSources + pipePipeSources

    fun find(baseUrl: String): ContentSource? = sources.firstOrNull {
        host(it.baseUrl) == host(baseUrl)
    }

    fun displayName(baseUrl: String): String = find(baseUrl)?.name ?: "自定义视频源"

    fun is91(baseUrl: String): Boolean = host(baseUrl) in setOf("h1014.sol148.com", "91porn.com", "sol148.com")

    fun isXVideos(baseUrl: String): Boolean = host(baseUrl) in setOf("xvideos.com", "xvideos-cdn.com")

    fun isHuangguo(baseUrl: String): Boolean = host(baseUrl) == "huangguoai.com"

    fun isHuavod(baseUrl: String): Boolean = host(baseUrl) in setOf("huavod.com", "huaren.live", "huarenok.com")

    fun isBeeg(baseUrl: String): Boolean = host(baseUrl) in setOf("beeg.com", "externulls.com")

    fun is91Dongman(baseUrl: String): Boolean = host(baseUrl) in setOf("91dongman.net", "91dongman.com")

    fun isMissAv(baseUrl: String): Boolean = host(baseUrl) in setOf("missav.ws", "missav.ai")

    fun isPipePipe(baseUrl: String): Boolean = find(baseUrl)?.extractorServiceName != null

    fun serviceName(baseUrl: String): String? = find(baseUrl)?.extractorServiceName

    fun kioskId(baseUrl: String, category: FeedCategory): String? {
        val source = find(baseUrl) ?: return null
        if (isXVideos(baseUrl)) {
            // Keep the semantic category-to-kiosk contract explicit. A stale extractor with only
            // the first five kiosks must not silently show "latest" for every later category.
            return category.xvideosKioskId?.takeIf { it in source.kioskIds }
        }
        return source.kioskIds.getOrNull(category.ordinal)
    }

    fun kioskLabel(baseUrl: String, category: FeedCategory): String? = when (kioskId(baseUrl, category)) {
        "latest" -> "最新"
        "best" -> "热门"
        "chinese" -> "中文"
        "amateur" -> "自拍"
        "asian" -> "亚洲"
        "verified" -> "认证"
        "ai" -> "AI"
        "milf" -> "熟女"
        "teen" -> "少女"
        "big-tits" -> "巨乳"
        "big-ass" -> "美臀"
        "big-cock" -> "巨屌"
        "blowjob" -> "口交"
        "anal" -> "肛交"
        "squirting" -> "潮吹"
        "creampie" -> "内射"
        "cumshot" -> "颜射"
        "stockings" -> "丝袜"
        "lingerie" -> "内衣"
        "blonde" -> "金发"
        "brunette" -> "棕发"
        "redhead" -> "红发"
        "black" -> "黑人"
        "latina" -> "拉丁"
        "indian" -> "印度"
        "arab" -> "阿拉伯"
        "interracial" -> "跨种族"
        "lesbian" -> "女同"
        "bi" -> "双性"
        "femdom" -> "女王"
        "gangbang" -> "群交"
        "family" -> "家庭"
        "cuckold" -> "绿帽"
        "bbw" -> "丰满"
        "mature" -> "成熟"
        "solo" -> "独角戏"
        "oiled" -> "油压"
        "fisting" -> "拳交"
        "gapes" -> "扩肛"
        "cam" -> "网络直播"
        "asmr" -> "ASMR"
        "popular" -> "热门"
        "recommended" -> "推荐"
        null -> null
        else -> kioskId(baseUrl, category)?.replaceFirstChar { it.titlecase() }
    }

    private fun host(value: String): String? = runCatching {
        URI(value).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()

    private val KIOSK_ORDER = listOf(
        "latest", "best", "chinese", "amateur", "asian", "verified",
        "ai", "milf", "teen", "big-tits", "big-ass", "big-cock",
        "blowjob", "anal", "squirting", "creampie", "cumshot",
        "stockings", "lingerie", "blonde", "brunette", "redhead",
        "black", "latina", "indian", "arab", "interracial",
        "lesbian", "bi", "femdom", "gangbang", "family",
        "cuckold", "bbw", "mature", "solo", "oiled",
        "fisting", "gapes", "cam", "asmr",
        "popular", "recommended",
    )
}
