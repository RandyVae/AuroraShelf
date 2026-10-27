package com.aurorashelf.app.model

data class LiveSourceInfo(
    val id: String,
    val name: String,
    val description: String,
    val baseUrl: String,
)

object LiveSourceCatalog {
    val STRIPCHAT = LiveSourceInfo(
        id = "stripchat",
        name = "Stripchat",
        description = "全球主流高清直播 · 国际线路",
        baseUrl = "https://zh.stripchat.com",
    )

    val HORNYSTUDENTS = LiveSourceInfo(
        id = "hornystudents",
        name = "HornyStudents",
        description = "校园风情成人直播 · 真实互动",
        baseUrl = "https://www.hornystudents.com",
    )

    val sources = listOf(STRIPCHAT, HORNYSTUDENTS)

    fun find(id: String): LiveSourceInfo = sources.firstOrNull { it.id == id } ?: STRIPCHAT
}
