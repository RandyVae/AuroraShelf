package com.aurorashelf.app.model

data class LiveItem(
    val id: String,
    val username: String,
    val thumbnailUrl: String,
    val viewersCount: Int,
    val countryCode: String,
    val isHd: Boolean,
    val playerUrl: String,
    /** Public HLS snapshot playlist (short VOD) used to cover live-page startup; null if absent. */
    val hlsUrl: String? = null,
)

enum class LiveCategory(
    val label: String,
    val primaryTag: String?,
) {
    WOMEN("女主播", "girls"),
    COUPLES("情侣", "couples"),
    MEN("男主播", "men"),
    TRANS("跨性别", "trans"),
}

data class LivePage(
    val items: List<LiveItem>,
    val totalCount: Int,
)

data class LiveComment(
    val id: String,
    val username: String,
    val message: String,
)
