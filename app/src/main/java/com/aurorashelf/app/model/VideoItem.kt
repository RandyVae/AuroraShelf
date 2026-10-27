package com.aurorashelf.app.model

data class VideoItem(
    val id: String,
    val title: String,
    val author: String,
    val duration: String,
    val views: String,
    val pageUrl: String,
    val thumbnailUrl: String? = null,
)

enum class FeedCategory(val label: String, val path: String, val xvideosKioskId: String? = null) {
    RECENT("最近加精", "v.php?category=rf&viewtype=basic", "latest"),
    CURRENT_HOT("当前最热", "v.php?category=hot&viewtype=basic", "best"),
    MONTHLY_HOT("本月最热", "v.php?category=mf&viewtype=basic", null),
    FAVORITES("收藏最多", "v.php?category=tf&viewtype=basic", null),
    ALL("全部分类", "v.php?viewtype=basic", null),

    CHINESE("中文", "c/chinese", "chinese"),
    AMATEUR("自拍", "c/amateur", "amateur"),
    ASIAN("亚洲", "c/asian", "asian"),
    VERIFIED("认证", "c/verified", "verified"),
    AI("AI", "c/ai", "ai"),
    MILF("熟女", "c/milf", "milf"),
    TEEN("少女", "c/teen", "teen"),
    BIG_TITS("巨乳", "c/big-tits", "big-tits"),
    BIG_ASS("美臀", "c/big-ass", "big-ass"),
    BIG_COCK("巨屌", "c/big-cock", "big-cock"),
    BLOWJOB("口交", "c/blowjob", "blowjob"),
    ANAL("肛交", "c/anal", "anal"),
    SQUIRTING("潮吹", "c/squirting", "squirting"),
    CREAMPIE("内射", "c/creampie", "creampie"),
    CUMSHOT("颜射", "c/cumshot", "cumshot"),
    STOCKINGS("丝袜", "c/stockings", "stockings"),
    LINGERIE("内衣", "c/lingerie", "lingerie"),
    BLONDE("金发", "c/blonde", "blonde"),
    BRUNETTE("棕发", "c/brunette", "brunette"),
    REDHEAD("红发", "c/redhead", "redhead"),
    BLACK("黑人", "c/black", "black"),
    LATINA("拉丁", "c/latina", "latina"),
    INDIAN("印度", "c/indian", "indian"),
    ARAB("阿拉伯", "c/arab", "arab"),
    INTERRACIAL("跨种族", "c/interracial", "interracial"),
    LESBIAN("女同", "c/lesbian", "lesbian"),
    BI("双性", "c/bi", "bi"),
    FEMDOM("女王", "c/femdom", "femdom"),
    GANGBANG("群交", "c/gangbang", "gangbang"),
    FAMILY("家庭", "c/family", "family"),
    CUCKOLD("绿帽", "c/cuckold", "cuckold"),
    BBW("丰满", "c/bbw", "bbw"),
    MATURE("成熟", "c/mature", "mature"),
    SOLO("独角戏", "c/solo", "solo"),
    OILED("油压", "c/oiled", "oiled"),
    FISTING("拳交", "c/fisting", "fisting"),
    GAPES("扩肛", "c/gapes", "gapes"),
    CAM("网络直播", "c/cam", "cam"),
    ASMR("ASMR", "c/asmr", "asmr");

    fun displayLabel(baseUrl: String): String {
        ContentSourceCatalog.kioskLabel(baseUrl, this)?.let { return it }
        if (ContentSourceCatalog.isHuavod(baseUrl)) {
            return when (this) {
                RECENT -> "电影"
                CURRENT_HOT -> "电视剧"
                MONTHLY_HOT -> "综艺"
                FAVORITES -> "动漫"
                ALL -> "短剧"
                else -> label
            }
        }
        if (ContentSourceCatalog.isBeeg(baseUrl)) {
            return when (this) {
                RECENT -> "精选"
                CURRENT_HOT -> "自拍"
                MONTHLY_HOT -> "日本"
                FAVORITES -> "熟女"
                ALL -> "亚洲"
                else -> label
            }
        }
        if (ContentSourceCatalog.is91Dongman(baseUrl)) {
            return when (this) {
                RECENT -> "里番"
                CURRENT_HOT -> "3D动漫"
                MONTHLY_HOT -> "AI动漫"
                FAVORITES -> "排行"
                ALL -> "全部"
                else -> label
            }
        }
        if (!ContentSourceCatalog.isHuangguo(baseUrl)) return label
        return when (this) {
            RECENT -> "最新"
            CURRENT_HOT -> "热门"
            MONTHLY_HOT -> "漫剧"
            FAVORITES -> "换脸"
            ALL -> "短剧"
            else -> label
        }
    }

    companion object {
        val DEFAULT_5 = listOf(RECENT, CURRENT_HOT, MONTHLY_HOT, FAVORITES, ALL)
        val XVIDEOS_CATEGORIES = listOf(
            RECENT, CURRENT_HOT, CHINESE, AMATEUR, ASIAN, VERIFIED,
            AI, MILF, TEEN, BIG_TITS, BIG_ASS, BIG_COCK,
            BLOWJOB, ANAL, SQUIRTING, CREAMPIE, CUMSHOT,
            STOCKINGS, LINGERIE, BLONDE, BRUNETTE, REDHEAD,
            BLACK, LATINA, INDIAN, ARAB, INTERRACIAL,
            LESBIAN, BI, FEMDOM, GANGBANG, FAMILY,
            CUCKOLD, BBW, MATURE, SOLO, OILED,
            FISTING, GAPES, CAM, ASMR,
        )

        fun availableFor(baseUrl: String): List<FeedCategory> {
            if (ContentSourceCatalog.isXVideos(baseUrl)) {
                return XVIDEOS_CATEGORIES
            }
            val source = ContentSourceCatalog.find(baseUrl)
            return if (source?.extractorServiceName != null) {
                DEFAULT_5.take(source.kioskIds.size.coerceAtLeast(1))
            } else {
                DEFAULT_5
            }
        }
    }
}

enum class AppDestination(val label: String) {
    HOME("首页"),
    COMICS("漫画"),
    LIVE("直播"),
    FORUM("论坛"),
    ME("我的"),
}
