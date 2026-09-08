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
)

/** Preset sources shown in the app. The URL field remains an implementation detail. */
object ContentSourceCatalog {
    const val DEFAULT_BASE_URL = "https://h1014.sol148.com"
    const val HUANGGUO_BASE_URL = "https://huangguoai.com"
    const val MISSAV_BASE_URL = "https://missav.ws"

    private val builtInSources = listOf(
        ContentSource("default", "默认视频源", "经典内容库", DEFAULT_BASE_URL),
        ContentSource("huangguo", "黄果短剧", "短剧与榜单", HUANGGUO_BASE_URL),
    )

    private val pipePipeSources: List<ContentSource> by lazy {
        NewPipe.getServices().map { service ->
            val serviceName = service.serviceInfo.name
            val kiosks = runCatching { service.kioskList.availableKiosks.toList() }.getOrDefault(emptyList())
                .sortedWith(compareBy({ KIOSK_ORDER.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }, { it }))
            ContentSource(
                id = "pipe-${serviceName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}",
                name = serviceName,
                description = "MISSPipe 视频源",
                baseUrl = service.baseUrl,
                extractorServiceName = serviceName,
                kioskIds = kiosks,
            )
        }
    }

    val sources: List<ContentSource> get() = builtInSources + pipePipeSources

    fun find(baseUrl: String): ContentSource? = sources.firstOrNull {
        host(it.baseUrl) == host(baseUrl)
    }

    fun displayName(baseUrl: String): String = find(baseUrl)?.name ?: "自定义视频源"

    fun isHuangguo(baseUrl: String): Boolean = host(baseUrl) == "huangguoai.com"

    fun isMissAv(baseUrl: String): Boolean = host(baseUrl) in setOf("missav.ws", "missav.ai")

    fun isPipePipe(baseUrl: String): Boolean = find(baseUrl)?.extractorServiceName != null

    fun serviceName(baseUrl: String): String? = find(baseUrl)?.extractorServiceName

    fun kioskId(baseUrl: String, category: FeedCategory): String? {
        val source = find(baseUrl) ?: return null
        return source.kioskIds.getOrNull(category.ordinal)
    }

    fun kioskLabel(baseUrl: String, category: FeedCategory): String? = when (kioskId(baseUrl, category)) {
        "latest" -> "最新"
        "popular" -> "热门"
        "recommended" -> "推荐"
        null -> null
        else -> kioskId(baseUrl, category)?.replaceFirstChar { it.titlecase() }
    }

    private fun host(value: String): String? = runCatching {
        URI(value).host?.lowercase()?.removePrefix("www.")
    }.getOrNull()

    private val KIOSK_ORDER = listOf("latest", "popular", "recommended")
}
