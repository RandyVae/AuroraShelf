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

enum class FeedCategory(val label: String, val path: String) {
    RECENT("最近加精", "v.php?category=rf&viewtype=basic"),
    CURRENT_HOT("当前最热", "v.php?category=hot&viewtype=basic"),
    MONTHLY_HOT("本月最热", "v.php?category=mf&viewtype=basic"),
    FAVORITES("收藏最多", "v.php?category=tf&viewtype=basic"),
    ALL("全部分类", "v.php?viewtype=basic");

    fun displayLabel(baseUrl: String): String {
        ContentSourceCatalog.kioskLabel(baseUrl, this)?.let { return it }
        if (!ContentSourceCatalog.isHuangguo(baseUrl)) return label
        return when (this) {
            RECENT -> "最新发布"
            CURRENT_HOT -> "热播榜"
            MONTHLY_HOT -> "推荐榜"
            FAVORITES -> "潜力榜"
            ALL -> "AI成人短剧"
        }
    }

    companion object {
        fun availableFor(baseUrl: String): List<FeedCategory> {
            val source = ContentSourceCatalog.find(baseUrl)
            return if (source?.extractorServiceName != null) entries.take(source.kioskIds.size.coerceAtLeast(1)) else entries
        }
    }
}

enum class AppDestination(val label: String) {
    HOME("首页"),
    COMICS("漫画"),
    FAVORITES("收藏"),
    HISTORY("历史"),
    SETTINGS("设置"),
}
