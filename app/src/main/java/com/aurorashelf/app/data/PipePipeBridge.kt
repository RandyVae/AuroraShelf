package com.aurorashelf.app.data

import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.FeedCategory
import com.aurorashelf.app.model.SourceCategory
import com.aurorashelf.app.model.VideoDetailMetadata
import com.aurorashelf.app.model.VideoItem
import com.aurorashelf.app.ui.CoverImageLoader
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import android.util.Log
import org.jsoup.Jsoup
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

/** Adapter between AuroraShelf models and the vendored PipePipe extractors. */
internal object PipePipeBridge {
    @Volatile private var isInitialized = false
    private val detailMetadataCache = ConcurrentHashMap<String, VideoDetailMetadata>()

    fun loadFeed(baseUrl: String, category: FeedCategory, page: Int): List<VideoItem> {
        return loadFeed(baseUrl, ContentSourceCatalog.category(baseUrl, category), page)
    }

    fun loadFeed(baseUrl: String, category: SourceCategory, page: Int): List<VideoItem> {
        initialize()
        val source = ContentSourceCatalog.find(baseUrl) ?: throw IOException("未找到所选视频源")
        Log.i(TAG, "loadFeed service=${source.extractorServiceName} category=${category.id} label=${category.label} page=$page")
        val kioskId = category.kioskId ?: ContentSourceCatalog.kioskId(baseUrl, category.legacy ?: FeedCategory.RECENT)
            ?: throw IOException("该视频源不支持分类：${category.label}")
        val extractor = service(source.extractorServiceName).kioskList.getExtractorById(kioskId, null)
        extractor.fetchPage()
        var result = extractor.initialPage
        repeat(page - 1) {
            if (!result.hasNextPage()) return emptyList()
            result = extractor.getPage(result.nextPage)
        }
        Log.i(TAG, "extractor raw count=${result.items.size} types=${result.items.take(3).map { it.javaClass.simpleName }}")
        val items = result.items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideoItem() }
        Log.i(TAG, "loadFeed completed count=${items.size} hasNext=${result.hasNextPage()}")
        return items
    }

    fun search(baseUrl: String, query: String, page: Int = 1): List<VideoItem> {
        initialize()
        val source = ContentSourceCatalog.find(baseUrl) ?: throw IOException("未找到所选视频源")
        val extractor = service(source.extractorServiceName).getSearchExtractor(query)
        extractor.fetchPage()
        var result: ListExtractor.InfoItemsPage<InfoItem> = extractor.initialPage
        repeat(page - 1) {
            if (!result.hasNextPage()) return emptyList()
            result = extractor.getPage(result.nextPage)
        }
        return result.items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideoItem() }
    }

    fun supports(pageUrl: String): Boolean {
        initialize()
        return runCatching { NewPipe.getServiceByUrl(pageUrl) }.isSuccess
    }

    fun resolve(pageUrl: String): PlaybackSource {
        initialize()
        val info = StreamInfo.getInfo(pageUrl)
        extractAndCacheMetadata(pageUrl, info)
        val directHls = info.hlsUrl.orEmpty().takeIf(String::isNotBlank)
        if (directHls != null) return PlaybackSource(directHls, pageUrl)
        val stream = info.videoStreams
            .asSequence()
            .filter(VideoStream::isUrl)
            .maxByOrNull { it.qualityScore() }
            ?: throw IOException("该视频源没有提供可播放的视频流")
        return PlaybackSource(stream.content, pageUrl)
    }

    fun getDetailMetadata(pageUrl: String): VideoDetailMetadata? {
        detailMetadataCache[pageUrl]?.let { return it }
        return runCatching {
            initialize()
            val info = StreamInfo.getInfo(pageUrl)
            extractAndCacheMetadata(pageUrl, info)
        }.getOrNull()
    }

    private fun extractAndCacheMetadata(pageUrl: String, info: StreamInfo): VideoDetailMetadata? {
        val directors = info.uploaderName?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= 40 }
            ?.let(::listOf)
            ?: emptyList()
        val tags = info.tags.orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)
        val rawDesc = info.description?.content.orEmpty()
        val synopsis = if (rawDesc.isNotBlank()) {
            Jsoup.parse(rawDesc).text().replace(Regex("\\s+"), " ").trim()
        } else {
            ""
        }
        val meta = VideoDetailMetadata(
            actors = emptyList(),
            directors = directors,
            tags = tags,
            synopsis = synopsis,
        )
        return if (meta.hasContent) {
            detailMetadataCache[pageUrl] = meta
            meta
        } else {
            null
        }
    }

    @Synchronized
    private fun initialize() {
        if (isInitialized) return
        Log.i(TAG, "initializing PipePipe extractor")
        NewPipe.init(PipePipeDownloader())
        isInitialized = true
    }

    private fun service(name: String?): StreamingService {
        if (name.isNullOrBlank()) throw IOException("视频源配置不完整")
        return NewPipe.getServices().firstOrNull { it.serviceInfo.name == name }
            ?: throw IOException("MISSPipe 不支持视频源：$name")
    }

    private fun StreamInfoItem.toVideoItem(): VideoItem? {
        val address = url?.takeIf(String::isNotBlank) ?: return null
        val cleanTitle = name?.trim().orEmpty()
        if (cleanTitle.isBlank()) return null
        return VideoItem(
            id = address.sha256(),
            title = cleanTitle,
            author = uploaderName?.takeIf(String::isNotBlank) ?: "未知作者",
            duration = duration.takeIf { it >= 0 }?.asDuration() ?: "--:--",
            views = viewCount.takeIf { it >= 0 }?.let(::formatViews) ?: "来源：${serviceName(serviceId)}",
            pageUrl = address,
            thumbnailUrl = thumbnailUrl?.takeIf(String::isNotBlank)?.let(CoverImageLoader::normalizeThumbnailUrl),
        )
    }

    private fun VideoStream.qualityScore(): Int {
        val pixels = Regex("(\\d{3,4})p").find(getResolution().orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val deliveryBonus = if (deliveryMethod == DeliveryMethod.HLS) 1 else 0
        return pixels * 10 + deliveryBonus
    }

    private fun serviceName(serviceId: Int): String = NewPipe.getServices()
        .firstOrNull { it.serviceId == serviceId }?.serviceInfo?.name ?: "MISSPipe"

    private fun Long.asDuration(): String {
        val hours = this / 3600
        val minutes = (this % 3600) / 60
        val seconds = this % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
    }

    private fun formatViews(value: Long): String = when {
        value >= 100_000_000 -> "%.1f亿".format(value / 100_000_000.0)
        value >= 10_000 -> "%.1f万".format(value / 10_000.0)
        else -> value.toString()
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .take(12)
        .joinToString("") { "%02x".format(it) }

    private const val TAG = "PipePipeBridge"
}
