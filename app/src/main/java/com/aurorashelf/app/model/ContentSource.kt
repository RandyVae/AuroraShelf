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

/**
 * A source-owned category. The legacy [FeedCategory] is retained only for routes that still use
 * the original direct-source adapters; PipePipe categories are represented by their real kiosk ID.
 */
data class SourceCategory(
    val id: String,
    val label: String,
    val path: String? = null,
    val kioskId: String? = null,
    val legacy: FeedCategory? = null,
) {
    val cacheKey: String
        get() = legacy?.path ?: kioskId ?: path ?: id

    val isAll: Boolean
        get() = legacy == FeedCategory.ALL || id.equals("all", ignoreCase = true)

    companion object {
        val FALLBACK = SourceCategory(
            id = "all",
            label = "全部",
            legacy = FeedCategory.ALL,
        )
    }
}

/** Preset sources shown in the app. The URL field remains an implementation detail. */
object ContentSourceCatalog {
    const val T91_BASE_URL = "https://h1014.sol148.com"
    const val XVIDEOS_BASE_URL = "https://www.xvideos.com"
    const val HUANGGUO_BASE_URL = "https://huangguoai.com"
    const val HUAVOD_BASE_URL = "https://huavod.com"
    const val BEEG_BASE_URL = "https://beeg.com"
    const val T91DONGMAN_BASE_URL = "https://91dongman.net"
    const val MISSAV_BASE_URL = "https://missav.media"
    const val X134_BASE_URL = "https://134x.com"
    // Keep cold start on the source that does not require a timed playback token.
    // Huavod remains available in the picker, but its signed player handshake can
    // legitimately reject an immediate first request while the token is too early.
    const val DEFAULT_BASE_URL = T91DONGMAN_BASE_URL

    private val builtInSources = listOf(
        ContentSource("91", "91视频", "经典内容库 · 大陆直连稳定", T91_BASE_URL, isDirectDomestic = true),
        ContentSource("huangguo", "黄果短剧", "短剧与榜单 · 大陆直连稳定", HUANGGUO_BASE_URL, isDirectDomestic = true),
        ContentSource("huavod", "华视影院", "海量影视与综艺 · 官方直连", HUAVOD_BASE_URL, isDirectDomestic = true),
        ContentSource("91dongman", "91动漫", "海量里番、3D与AI动漫 · 官方直连", T91DONGMAN_BASE_URL, isDirectDomestic = true),
        ContentSource("beeg", "Beeg", "极简高清流媒体 · 海外直连/代理", BEEG_BASE_URL, isDirectDomestic = false),
        ContentSource("134x", "134X", "无广告高清长视频 · X 长片直连", X134_BASE_URL, isDirectDomestic = false),
    )

    private fun serviceDescription(serviceName: String): String = when (serviceName) {
        "MissAV" -> "高清日韩番号 · 海外直连/代理"
        "85po" -> "热门自拍与原创 · 海外直连/代理"
        "KissJAV" -> "海量综合视频 · 海外直连/代理"
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

    private val removedServiceNames = setOf("JAV-NONI", "JAVSB")

    private val pipePipeSources: List<ContentSource> by lazy {
        NewPipe.getServices()
            .filterNot { it.serviceInfo.name in removedServiceNames }
            .map { service ->
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
        host(it.baseUrl) == host(baseUrl) || (isMissAv(it.baseUrl) && isMissAv(baseUrl))
    }

    fun displayName(baseUrl: String): String = find(baseUrl)?.name ?: "自定义视频源"

    fun inferSourceName(pageUrl: String): String = when {
        find(pageUrl) != null -> find(pageUrl)!!.name
        is91(pageUrl) -> "91视频"
        isHuangguo(pageUrl) -> "黄果短剧"
        isHuavod(pageUrl) -> "华视影院"
        is91Dongman(pageUrl) -> "91动漫"
        isBeeg(pageUrl) -> "Beeg"
        is134x(pageUrl) -> "134X"
        isXVideos(pageUrl) -> "XVideos"
        isMissAv(pageUrl) -> "MissAV"
        else -> host(pageUrl) ?: "其他视频源"
    }

    fun is91(baseUrl: String): Boolean = host(baseUrl) in setOf("h1014.sol148.com", "91porn.com", "sol148.com")

    fun isXVideos(baseUrl: String): Boolean = host(baseUrl) in setOf("xvideos.com", "xvideos-cdn.com")

    fun isHuangguo(baseUrl: String): Boolean = host(baseUrl) == "huangguoai.com"

    fun isHuavod(baseUrl: String): Boolean = host(baseUrl) in setOf("huavod.com", "huaren.live", "huarenok.com")

    fun isBeeg(baseUrl: String): Boolean = host(baseUrl) in setOf("beeg.com", "externulls.com")

    fun is134x(baseUrl: String): Boolean = host(baseUrl) == "134x.com"

    fun is91Dongman(baseUrl: String): Boolean = host(baseUrl) in setOf("91dongman.net", "91dongman.com")

    fun isMissAv(baseUrl: String): Boolean = host(baseUrl) in setOf("missav.media", "missav.ws", "missav.ai", "missav123.com")

    fun isPipePipe(baseUrl: String): Boolean = find(baseUrl)?.extractorServiceName != null

    fun serviceName(baseUrl: String): String? = find(baseUrl)?.extractorServiceName

    /**
     * Returns the exact category set exposed by the selected source. PipePipe sources are driven
     * by every kiosk ID reported by the extractor; they must never be truncated to a generic
     * five-item list because the upstream site's taxonomy is source-specific.
     */
    fun sourceCategories(baseUrl: String): List<SourceCategory> {
        val source = find(baseUrl)
        if (source?.extractorServiceName != null) {
            return source.kioskIds.distinct().map { kioskId ->
                val legacy = FeedCategory.entries.firstOrNull { it.xvideosKioskId == kioskId }
                SourceCategory(
                    id = kioskId,
                    label = kioskLabelForService(source.extractorServiceName, kioskId),
                    path = legacy?.path,
                    kioskId = kioskId,
                    legacy = legacy,
                )
            }
        }

        if (isHuavod(baseUrl)) {
            return listOf(
                SourceCategory("movie", "电影", path = "/vodshow/1.html", legacy = FeedCategory.RECENT),
                SourceCategory("tv", "电视剧", path = "/vodshow/2.html", legacy = FeedCategory.CURRENT_HOT),
                SourceCategory("variety", "综艺", path = "/vodshow/3.html", legacy = FeedCategory.MONTHLY_HOT),
                SourceCategory("anime", "动漫", path = "/vodshow/4.html", legacy = FeedCategory.FAVORITES),
                SourceCategory("short", "短剧", path = "/vodshow/5.html", legacy = FeedCategory.ALL),
                SourceCategory("douban", "豆瓣高分", path = "/vodshow/1/by/douban.html"),
                SourceCategory("cn-tv", "大陆剧", path = "/vodshow/14.html"),
                SourceCategory("hk-tv", "港台剧", path = "/vodshow/15.html"),
                SourceCategory("jk-tv", "日韩剧", path = "/vodshow/16.html"),
                SourceCategory("us-tv", "欧美剧", path = "/vodshow/17.html"),
                SourceCategory("scifi", "奇幻科幻", path = "/vodshow/6.html"),
                SourceCategory("action", "动作冒险", path = "/vodshow/7.html"),
                SourceCategory("thriller", "悬疑惊悚", path = "/vodshow/8.html"),
                SourceCategory("comedy", "爱情喜剧", path = "/vodshow/9.html"),
                SourceCategory("anim-movie", "动画电影", path = "/vodshow/11.html"),
            )
        }
        if (isHuangguo(baseUrl)) {
            return listOf(
                SourceCategory("newest", "最近上新", path = "/newest", legacy = FeedCategory.RECENT),
                SourceCategory("recommend", "热门推荐", path = "/recommend", legacy = FeedCategory.CURRENT_HOT),
                SourceCategory("original", "黄果原创", path = "/original/"),
                SourceCategory("duanju", "短剧", path = "/ai-duanju/", legacy = FeedCategory.ALL),
                SourceCategory("manju", "漫剧", path = "/ai-manju/", legacy = FeedCategory.MONTHLY_HOT),
            )
        }
        if (is91Dongman(baseUrl)) {
            return listOf(
                SourceCategory("lifan", "里番", path = "/anime/lifan/", legacy = FeedCategory.RECENT),
                SourceCategory("paomian", "泡面番", path = "/anime/paomian/"),
                SourceCategory("3d", "3D动漫", path = "/anime/3d/", legacy = FeedCategory.CURRENT_HOT),
                SourceCategory("ai", "AI动漫", path = "/anime/ai/", legacy = FeedCategory.MONTHLY_HOT),
                SourceCategory("ca", "纯爱", path = "/anime/tag/ca/"),
                SourceCategory("jqx", "剧情向", path = "/anime/tag/jqx/"),
                SourceCategory("dp", "短篇", path = "/anime/tag/dp/"),
                SourceCategory("ranking", "热门排行", path = "/ranking/", legacy = FeedCategory.FAVORITES),
                SourceCategory("all", "全部总览", path = "/anime/", legacy = FeedCategory.ALL),
            )
        }
        if (is91(baseUrl)) {
            return listOf(
                SourceCategory("top", "本月最热", path = "v.php?category=top&viewtype=basic", legacy = FeedCategory.MONTHLY_HOT),
                SourceCategory("tf", "收藏最多", path = "v.php?category=tf&viewtype=basic", legacy = FeedCategory.FAVORITES),
                SourceCategory("top-prev", "上月最热", path = "v.php?category=top&m=-1&viewtype=basic"),
                SourceCategory("hot", "当前最热", path = "v.php?category=hot&viewtype=basic", legacy = FeedCategory.CURRENT_HOT),
                SourceCategory("rf", "最近加精", path = "v.php?category=rf&viewtype=basic", legacy = FeedCategory.RECENT),
                SourceCategory("ori", "91原创", path = "v.php?category=ori&viewtype=basic"),
                SourceCategory("long", "10分钟以上", path = "v.php?category=long&viewtype=basic"),
                SourceCategory("longer", "20分钟以上", path = "v.php?category=longer&viewtype=basic"),
                SourceCategory("mf", "本月收藏", path = "v.php?category=mf&viewtype=basic"),
                SourceCategory("md", "本月讨论", path = "v.php?category=md&viewtype=basic"),
                SourceCategory("hd", "高清", path = "v.php?category=hd&viewtype=basic"),
                SourceCategory("all", "最新上传", path = "v.php?next=watch&viewtype=basic", legacy = FeedCategory.ALL),
            )
        }
        if (isBeeg(baseUrl)) {
            return listOf(
                SourceCategory("index", "精选", path = "index", legacy = FeedCategory.RECENT),
                SourceCategory("amateur", "自拍", path = "amateur", legacy = FeedCategory.CURRENT_HOT),
                SourceCategory("japanese", "日本", path = "japanese", legacy = FeedCategory.MONTHLY_HOT),
                SourceCategory("asian", "亚洲", path = "asian", legacy = FeedCategory.ALL),
                SourceCategory("milf", "熟女", path = "milf", legacy = FeedCategory.FAVORITES),
                SourceCategory("teen", "少女", path = "teen"),
                SourceCategory("korean", "韩国", path = "korean"),
                SourceCategory("european", "欧美", path = "european"),
            )
        }

        val categories = FeedCategory.availableFor(baseUrl).map { category ->
            SourceCategory(
                id = category.xvideosKioskId ?: category.name.lowercase(),
                label = category.displayLabel(baseUrl),
                path = category.path,
                legacy = category,
            )
        }
        return categories.ifEmpty { listOf(SourceCategory.FALLBACK) }
    }

    fun category(baseUrl: String, legacy: FeedCategory): SourceCategory =
        sourceCategories(baseUrl).firstOrNull { it.legacy == legacy }
            ?: SourceCategory(
                id = legacy.xvideosKioskId ?: legacy.name.lowercase(),
                label = legacy.displayLabel(baseUrl),
                path = legacy.path,
                legacy = legacy,
            )

    fun kioskId(baseUrl: String, category: FeedCategory): String? {
        val source = find(baseUrl) ?: return null
        if (isXVideos(baseUrl)) {
            // Keep the semantic category-to-kiosk contract explicit. A stale extractor with only
            // the first five kiosks must not silently show "latest" for every later category.
            return category.xvideosKioskId?.takeIf { it in source.kioskIds }
        }
        return source.kioskIds.getOrNull(category.ordinal)
    }

    fun kioskLabel(baseUrl: String, category: FeedCategory): String? =
        kioskId(baseUrl, category)?.let { kioskLabelForService(serviceName(baseUrl), it) }

    fun kioskLabelForService(serviceName: String?, kioskId: String?): String = when (serviceName) {
        "MissAV" -> when (kioskId) {
            "latest" -> "最近更新"
            "release" -> "新作上市"
            "chinese-subtitle" -> "中文字幕"
            "today-hot" -> "今日热门"
            "weekly-hot" -> "本周热门"
            "monthly-hot" -> "本月热门"
            "uncensored-leak" -> "无码流出"
            "siro" -> "素人 SIRO"
            "fc2" -> "FC2"
            else -> kioskLabel(kioskId)
        }
        "TOKYO Motion" -> when (kioskId) {
            "latest" -> "最新上传"
            "watching" -> "正在观看"
            "popular" -> "最多观看"
            "recommended" -> "最高评分"
            "favorited" -> "最多收藏"
            "longest" -> "最长片长"
            else -> kioskLabel(kioskId)
        }
        "EPORNER" -> when (kioskId) {
            "latest" -> "最新发布"
            "popular" -> "最多观看"
            "recommended" -> "最高评分"
            "weekly" -> "本周热门"
            "monthly" -> "本月热门"
            "longest" -> "最长片长"
            "4k" -> "4K 超清"
            else -> kioskLabel(kioskId)
        }
        "SpankBang" -> when (kioskId) {
            "trending" -> "热门趋势"
            "latest" -> "最新上传"
            "popular" -> "最受欢迎"
            "upcoming" -> "即将热门"
            else -> kioskLabel(kioskId)
        }
        "xHamster" -> when (kioskId) {
            "best" -> "最佳热门"
            "latest" -> "最新发布"
            "4k" -> "4K 超清"
            "hd" -> "高清 HD"
            "full-length" -> "完整长片"
            "subtitles" -> "带字幕"
            else -> kioskLabel(kioskId)
        }
        "OHentai" -> when (kioskId) {
            "latest" -> "最新发布"
            "popular" -> "最多观看"
            "recommended" -> "最多喜欢"
            "uncensored" -> "无码精选"
            "1080p" -> "1080P"
            "3d" -> "3D 动画"
            else -> kioskLabel(kioskId)
        }
        "XNXX" -> when (kioskId) {
            "today" -> "今日精选"
            "hits" -> "热门点击"
            "weekly" -> "本周热门"
            "monthly" -> "本月热门"
            "best" -> "月度最佳"
            else -> kioskLabel(kioskId)
        }
        "MRDOUGA" -> when (kioskId) {
            "latest" -> "最新更新"
            "today-hot" -> "今日热门"
            "popular" -> "总热门"
            "jav" -> "JAV 专区"
            "jvip" -> "JVIP 精选"
            "fc2ppv" -> "FC2PPV"
            else -> kioskLabel(kioskId)
        }
        "Pornhub" -> when (kioskId) {
            "trending" -> "当下热门"
            "latest" -> "最新上线"
            "popular" -> "最多观看"
            "recommended" -> "最高评分"
            "longest" -> "最长片长"
            else -> kioskLabel(kioskId)
        }
        "KissJAV", "85po" -> when (kioskId) {
            "latest" -> "最新更新"
            "popular" -> "最受欢迎"
            "recommended" -> "最高评分"
            else -> kioskLabel(kioskId)
        }
        else -> kioskLabel(kioskId)
    }

    fun kioskLabel(kioskId: String?): String = when (kioskId) {
        "latest" -> "最新"
        "best" -> "热门"
        "trending" -> "当下热门"
        "watching" -> "正在观看"
        "favorited" -> "最多收藏"
        "longest" -> "最长片长"
        "weekly" -> "本周热门"
        "monthly" -> "本月热门"
        "today" -> "今日精选"
        "hits" -> "热门点击"
        "upcoming" -> "即将热门"
        "release" -> "新作上市"
        "chinese-subtitle" -> "中文字幕"
        "today-hot" -> "今日热门"
        "weekly-hot" -> "本周热门"
        "monthly-hot" -> "本月热门"
        "uncensored-leak" -> "无码流出"
        "siro" -> "素人 SIRO"
        "fc2" -> "FC2"
        "4k" -> "4K 超清"
        "hd" -> "高清 HD"
        "full-length" -> "完整长片"
        "subtitles" -> "带字幕"
        "uncensored" -> "无码精选"
        "1080p" -> "1080P"
        "3d" -> "3D 动画"
        "jav" -> "JAV 专区"
        "jvip" -> "JVIP 精选"
        "fc2ppv" -> "FC2PPV"
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
        null -> "全部"
        else -> kioskId
            .replace('-', ' ')
            .replace('_', ' ')
            .split(' ')
            .filter(String::isNotBlank)
            .joinToString(" ") { token -> token.replaceFirstChar { it.titlecase() } }
    }

    private fun host(value: String): String? = runCatching {
        URI(value).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()

    private val KIOSK_ORDER = listOf(
        "trending", "today", "hits", "best", "latest",
        "release", "chinese-subtitle", "today-hot", "weekly-hot", "monthly-hot",
        "uncensored-leak", "siro", "fc2",
        "watching", "popular", "recommended", "favorited",
        "weekly", "monthly", "longest", "upcoming",
        "4k", "hd", "full-length", "subtitles",
        "uncensored", "1080p", "3d",
        "jav", "jvip", "fc2ppv",
        "chinese", "amateur", "asian", "verified",
        "ai", "milf", "teen", "big-tits", "big-ass", "big-cock",
        "blowjob", "anal", "squirting", "creampie", "cumshot",
        "stockings", "lingerie", "blonde", "brunette", "redhead",
        "black", "latina", "indian", "arab", "interracial",
        "lesbian", "bi", "femdom", "gangbang", "family",
        "cuckold", "bbw", "mature", "solo", "oiled",
        "fisting", "gapes", "cam", "asmr",
    )
}
