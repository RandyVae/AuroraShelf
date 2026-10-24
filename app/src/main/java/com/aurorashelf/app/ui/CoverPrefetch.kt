package com.aurorashelf.app.ui

import android.content.Context
import android.util.Log
import coil3.SingletonImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.size.Size
import com.aurorashelf.app.data.StripchatLiveRepository
import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.data.isPrefetchEnabled
import com.aurorashelf.app.model.ComicSummary
import com.aurorashelf.app.model.LiveItem
import com.aurorashelf.app.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val COVER_PREFETCH_DEDUPE_TTL_MS = 10 * 60 * 1000L

/** Stable Coil memory cache key for cover bytes; blank/null addresses are never cached. */
internal fun coverMemoryCacheKey(url: String?): String? = url?.trim()?.takeIf { it.isNotEmpty() }

/** First [limit] unique videos that actually have a cover URL. */
internal fun videoPrefetchTargets(videos: List<VideoItem>, limit: Int): List<VideoItem> {
    if (limit <= 0) return emptyList()
    return videos.asSequence()
        .filter { !it.thumbnailUrl.isNullOrBlank() }
        .distinctBy { it.thumbnailUrl }
        .take(limit)
        .toList()
}

/** First [limit] unique comics that actually have a cover URL. */
internal fun comicPrefetchTargets(comics: List<ComicSummary>, limit: Int): List<ComicSummary> {
    if (limit <= 0) return emptyList()
    return comics.asSequence()
        .filter { !it.coverUrl.isNullOrBlank() }
        .distinctBy { it.coverUrl }
        .take(limit)
        .toList()
}

/** First [limit] unique live rooms that have a preview thumbnail. */
internal fun livePrefetchTargets(items: List<LiveItem>, limit: Int): List<LiveItem> {
    if (limit <= 0) return emptyList()
    return items.asSequence()
        .filter { it.thumbnailUrl.isNotBlank() }
        .distinctBy { it.thumbnailUrl }
        .take(limit)
        .toList()
}

/**
 * The exact cover request used by `ComicCoverImage` and the comic prefetcher, so the warm-up
 * always populates the same memory/disk keys as the visible list.
 */
internal fun comicCoverRequest(context: Context, comic: ComicSummary): ImageRequest? {
    val url = comic.coverUrl ?: return null
    return ImageRequest.Builder(context)
        .data(url)
        .memoryCacheKey(url)
        .diskCacheKey(url)
        .apply {
            comic.coverReferer?.let { referer ->
                httpHeaders(
                    NetworkHeaders.Builder()
                        .set("Referer", referer)
                        .set("User-Agent", VideoRepository.USER_AGENT)
                        .apply { if (comic.sourceId == "ehentai") set("Cookie", "nw=1") }
                        .build(),
                )
            }
        }
        .build()
}

/**
 * Process-wide, fire-and-forget list warm-up:
 * 1. byte warm-up through [CoverImageLoader] (bounded to 4 concurrent fetches),
 * 2. decode warm-up by enqueuing a Coil request with the same stable memory key the UI uses.
 *
 * A short-lived dedupe table keeps recomposition/list updates from repeating the same work.
 * Respects the shared Wi-Fi-only prefetch policy.
 */
internal object CoverPrefetcher {
    private const val TAG = "CoverPrefetch"
    private const val MAX_PARALLEL = 4
    private const val MAX_DECODE_PIXELS = 1080

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(MAX_PARALLEL)
    private val deduplicator = PrefetchDeduplicator(COVER_PREFETCH_DEDUPE_TTL_MS)

    fun prefetchVideos(context: Context, videos: List<VideoItem>, limit: Int = 8) {
        if (!isPrefetchEnabled(context.applicationContext)) return
        val targets = videoPrefetchTargets(videos, limit).mapNotNull { video ->
            val url = coverMemoryCacheKey(video.thumbnailUrl) ?: return@mapNotNull null
            if (!deduplicator.markStartedIfAbsent(url)) return@mapNotNull null
            WarmTarget(
                cacheKey = url,
                fetch = { CoverImageLoader.load(url, video.pageUrl) },
            )
        }
        warmBytesThenDecode(context, "videos", targets)
    }

    fun prefetchLive(context: Context, items: List<LiveItem>, limit: Int = 8) {
        if (!isPrefetchEnabled(context.applicationContext)) return
        val targets = livePrefetchTargets(items, limit).mapNotNull { item ->
            val url = coverMemoryCacheKey(item.thumbnailUrl) ?: return@mapNotNull null
            if (!deduplicator.markStartedIfAbsent(url)) return@mapNotNull null
            WarmTarget(
                cacheKey = url,
                fetch = { CoverImageLoader.load(url, "${StripchatLiveRepository.SITE_BASE}/") },
            )
        }
        warmBytesThenDecode(context, "live", targets)
    }

    fun prefetchComicCovers(context: Context, comics: List<ComicSummary>, limit: Int = 10) {
        if (!isPrefetchEnabled(context.applicationContext)) return
        val appContext = context.applicationContext
        val targets = comicPrefetchTargets(comics, limit).filter { comic ->
            deduplicator.markStartedIfAbsent(comic.coverUrl!!)
        }
        if (targets.isEmpty()) return
        val startedAt = System.currentTimeMillis()
        scope.launch {
            runCatching {
                val imageLoader = SingletonImageLoader.get(appContext)
                targets.forEach { comic ->
                    comicCoverRequest(appContext, comic)?.let { imageLoader.enqueue(it) }
                }
                logWarmUp("comics", targets.size, startedAt)
            }
        }
    }

    private fun warmBytesThenDecode(context: Context, kind: String, targets: List<WarmTarget>) {
        if (targets.isEmpty()) return
        val appContext = context.applicationContext
        val startedAt = System.currentTimeMillis()
        scope.launch {
            // Fire-and-forget: any fetch/decode failure only drops that one cover, never crashes
            // the caller or leaks an uncaught coroutine exception.
            runCatching {
                val warmed = targets.map { target ->
                    async {
                        target to runCatching { semaphore.withPermit { target.fetch() } }.getOrNull()
                    }
                }.awaitAll()
                warmed.forEach { (target, bytes) ->
                    if (bytes != null && bytes.isNotEmpty()) enqueueDecode(appContext, bytes, target.cacheKey)
                }
                logWarmUp(kind, targets.size, startedAt)
            }
        }
    }

    private fun enqueueDecode(context: Context, bytes: ByteArray, cacheKey: String) {
        val key = coverMemoryCacheKey(cacheKey) ?: return
        // Full-screen sized decode: it matches full-width heroes/live cards within 1 px (Coil's
        // EXACT precision accepts that) while keeping memory bounded for grid thumbnails.
        val size = decodeTargetSize(context)
        val request = ImageRequest.Builder(context)
            .data(bytes)
            .memoryCacheKey(key)
            .size(size)
            .build()
        runCatching { SingletonImageLoader.get(context).enqueue(request) }
    }

    private fun decodeTargetSize(context: Context): Size {
        val pixels = context.resources.displayMetrics.widthPixels.coerceIn(1, MAX_DECODE_PIXELS)
        return Size(pixels, pixels)
    }

    private fun logWarmUp(kind: String, count: Int, startedAtMs: Long) {
        // Never log URLs or other content; only kind/count/duration for baseline comparison.
        runCatching {
            Log.i(TAG, "kind=$kind count=$count ms=${System.currentTimeMillis() - startedAtMs}")
        }
    }

    private data class WarmTarget(
        val cacheKey: String,
        val fetch: suspend () -> ByteArray?,
    )
}

/**
 * Remembers recently started warm-up keys for [ttlMs]: repeated list updates/recompositions must
 * not re-download or re-decode the same cover. Pure JVM (injectable clock), unit tested directly.
 */
internal class PrefetchDeduplicator(
    private val ttlMs: Long = COVER_PREFETCH_DEDUPE_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val lastStartedAt = HashMap<String, Long>()

    fun markStartedIfAbsent(key: String): Boolean = synchronized(lock) {
        val now = clock()
        val previous = lastStartedAt[key]
        if (previous != null && now - previous < ttlMs) {
            false
        } else {
            lastStartedAt[key] = now
            true
        }
    }

    internal fun trackedKeys(): Int = synchronized(lock) { lastStartedAt.size }
}
