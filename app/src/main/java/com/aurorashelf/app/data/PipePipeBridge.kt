package com.aurorashelf.app.data

import android.util.Log
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.FeedCategory
import com.aurorashelf.app.model.SourceCategory
import com.aurorashelf.app.model.VideoDetailMetadata
import com.aurorashelf.app.model.VideoItem
import com.aurorashelf.app.ui.CoverImageLoader
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections
import org.jsoup.Jsoup
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Bounded thread-safe LRU cache for PipePipe pagination cursors so page N can fetch directly
 * from cached cursor (N-1) in O(1) instead of walking pages 1..N (O(N^2)).
 */
internal class PageCursorCache<P>(private val maxEntries: Int = 64) {
    private val lock = Any()
    private val entries = object : LinkedHashMap<String, P>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, P>?): Boolean =
            size > maxEntries
    }

    private fun key(scope: String, targetPage: Int): String = "$scope|page=$targetPage"

    fun getCursorForPage(scope: String, targetPage: Int): P? = synchronized(lock) {
        entries[key(scope, targetPage)]
    }

    fun putCursorForPage(scope: String, targetPage: Int, cursor: P) = synchronized(lock) {
        entries[key(scope, targetPage)] = cursor
    }

    /** Finds the highest cached targetPage <= [desiredPage] for [scope] so a cache miss walks minimal hops. */
    fun nearestCachedPredecessor(scope: String, desiredPage: Int): Pair<Int, P>? = synchronized(lock) {
        var pageNum = desiredPage
        while (pageNum >= 2) {
            val found = entries[key(scope, pageNum)]
            if (found != null) return@synchronized pageNum to found
            pageNum--
        }
        null
    }

    fun size(): Int = synchronized(lock) { entries.size }
    fun clear() = synchronized(lock) { entries.clear() }
}

/** Adapter between AuroraShelf models and the vendored PipePipe extractors. */
internal object PipePipeBridge {
    @Volatile private var isInitialized = false
    private const val MAX_METADATA_CACHE = 128
    private val detailMetadataCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, VideoDetailMetadata>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VideoDetailMetadata>?): Boolean =
                size > MAX_METADATA_CACHE
        },
    )
    internal val pageCursorCache = PageCursorCache<Page>(maxEntries = 64)

    fun loadFeed(baseUrl: String, category: FeedCategory, page: Int): List<VideoItem> {
        return loadFeed(baseUrl, ContentSourceCatalog.category(baseUrl, category), page)
    }

    fun loadFeed(baseUrl: String, category: SourceCategory, page: Int): List<VideoItem> {
        initialize()
        val safePage = page.coerceAtLeast(1)
        val source = ContentSourceCatalog.find(baseUrl) ?: throw IOException("未找到所选视频源")
        Log.i(TAG, "loadFeed service=${source.extractorServiceName} category=${category.id} label=${category.label} page=$safePage")
        val kioskId = category.kioskId ?: ContentSourceCatalog.kioskId(baseUrl, category.legacy ?: FeedCategory.RECENT)
            ?: throw IOException("该视频源不支持分类：${category.label}")
        val extractor = service(source.extractorServiceName).kioskList.getExtractorById(kioskId, null)
        val cursorScope = "feed|${source.extractorServiceName}|$kioskId"
        val result = fetchTargetPage(extractor, cursorScope, safePage) ?: return emptyList()
        Log.i(TAG, "extractor raw count=${result.items.size} types=${result.items.take(3).map { it.javaClass.simpleName }}")
        val items = result.items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideoItem() }
        Log.i(TAG, "loadFeed completed count=${items.size} hasNext=${result.hasNextPage()}")
        return items
    }

    fun search(baseUrl: String, query: String, page: Int = 1): List<VideoItem> {
        initialize()
        val safePage = page.coerceAtLeast(1)
        val source = ContentSourceCatalog.find(baseUrl) ?: throw IOException("未找到所选视频源")
        val extractor = service(source.extractorServiceName).getSearchExtractor(query)
        val cursorScope = "search|${source.extractorServiceName}|${query.trim().lowercase()}"
        val result = fetchTargetPage(extractor, cursorScope, safePage) ?: return emptyList()
        return result.items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideoItem() }
    }

    private fun <T : InfoItem> fetchTargetPage(
        extractor: ListExtractor<T>,
        cursorScope: String,
        targetPage: Int,
    ): ListExtractor.InfoItemsPage<T>? {
        if (targetPage <= 1) {
            extractor.fetchPage()
            val initial = extractor.initialPage
            if (initial.hasNextPage() && initial.nextPage != null) {
                pageCursorCache.putCursorForPage(cursorScope, 2, initial.nextPage)
            }
            return initial
        }

        // O(1) direct cursor hit for page N
        val directCursor = pageCursorCache.getCursorForPage(cursorScope, targetPage)
        if (directCursor != null) {
            val pageResult = runCatching { extractor.getPage(directCursor) }.getOrNull()
            if (pageResult != null) {
                if (pageResult.hasNextPage() && pageResult.nextPage != null) {
                    pageCursorCache.putCursorForPage(cursorScope, targetPage + 1, pageResult.nextPage)
                }
                return pageResult
            }
        }

        // Fallback: resume from nearest cached predecessor cursor (or page 1 if none cached)
        val nearest = pageCursorCache.nearestCachedPredecessor(cursorScope, targetPage)
        var currentPageNum: Int
        var currentResult: ListExtractor.InfoItemsPage<T>
        if (nearest != null) {
            val resumed = runCatching { extractor.getPage(nearest.second) }.getOrNull()
            if (resumed != null) {
                currentPageNum = nearest.first
                currentResult = resumed
                if (currentResult.hasNextPage() && currentResult.nextPage != null) {
                    pageCursorCache.putCursorForPage(cursorScope, currentPageNum + 1, currentResult.nextPage)
                }
            } else {
                extractor.fetchPage()
                currentPageNum = 1
                currentResult = extractor.initialPage
                if (currentResult.hasNextPage() && currentResult.nextPage != null) {
                    pageCursorCache.putCursorForPage(cursorScope, 2, currentResult.nextPage)
                }
            }
        } else {
            extractor.fetchPage()
            currentPageNum = 1
            currentResult = extractor.initialPage
            if (currentResult.hasNextPage() && currentResult.nextPage != null) {
                pageCursorCache.putCursorForPage(cursorScope, 2, currentResult.nextPage)
            }
        }

        while (currentPageNum < targetPage) {
            if (!currentResult.hasNextPage() || currentResult.nextPage == null) return null
            currentResult = extractor.getPage(currentResult.nextPage)
            currentPageNum++
            if (currentResult.hasNextPage() && currentResult.nextPage != null) {
                pageCursorCache.putCursorForPage(cursorScope, currentPageNum + 1, currentResult.nextPage)
            }
        }
        return currentResult
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
