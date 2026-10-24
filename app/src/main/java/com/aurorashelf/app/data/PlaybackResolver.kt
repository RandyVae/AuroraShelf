package com.aurorashelf.app.data

import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import android.util.Log
import com.aurorashelf.app.model.ContentSourceCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

data class PlaybackSource(val url: String, val referer: String)
data class PlaybackEpisode(val number: Int, val label: String, val pageUrl: String)

/** Reads the media source published inside the page's video element. Never executes remote scripts. */
class PlaybackResolver {
    suspend fun resolveEpisodes(detailUrl: String): List<PlaybackEpisode> = withContext(Dispatchers.IO) {
        val startedAtMs = System.currentTimeMillis()
        require(SourceAddress.resolve(detailUrl, detailUrl) != null) { "视频页面地址无效" }
        val cached = episodeCache.get(detailUrl)
        val episodes = cached ?: parseEpisodes(fetch(detailUrl), detailUrl).also { parsed ->
            // Failures (empty lists) stay uncached so a transient page error is retried next time.
            if (parsed.isNotEmpty()) episodeCache.put(detailUrl, parsed)
        }
        Log.i(
            TAG,
            "timing episodes ms=${System.currentTimeMillis() - startedAtMs} count=${episodes.size} cache=${cached != null}",
        )
        episodes
    }

    suspend fun resolve(pageUrl: String): PlaybackSource = withContext(Dispatchers.IO) {
        Log.i(TAG, "resolve start host=${runCatching { java.net.URI(pageUrl).host }.getOrNull()}")
        val startedAtMs = System.currentTimeMillis()
        require(SourceAddress.resolve(pageUrl, pageUrl) != null) { "视频页面地址无效" }
        val cached = playbackCache.get(pageUrl)
        if (cached != null) {
            Log.i(TAG, "timing resolve ms=${System.currentTimeMillis() - startedAtMs} cache=hit")
            return@withContext cached
        }
        if (PipePipeBridge.supports(pageUrl)) {
            runCatching { PipePipeBridge.resolve(pageUrl) }.onSuccess { source ->
                Log.i(TAG, "resolve PipePipe source host=${runCatching { URI(source.url).host }.getOrNull()} type=${mediaType(source.url)}")
                return@withContext source.rememberFor(pageUrl, startedAtMs)
            }.onFailure { error ->
                if (!ContentSourceCatalog.isMissAv(pageUrl)) throw error
                Log.w(TAG, "PipePipe resolver failed; using the compatible MissAV page parser", error)
            }
        }
        val page = fetch(pageUrl)
        parse(page)?.let {
            Log.i(TAG, "resolve direct source host=${runCatching { java.net.URI(it.url).host }.getOrNull()} type=${mediaType(it.url)}")
            return@withContext it.rememberFor(pageUrl, startedAtMs)
        }

        // 黄果详情页将播放按钮指向 /video/...，媒体地址只在该播放页的数据节点中发布。
        page.select("a.hg-web-detail__play[href], a[href*=/video/]")
            .asSequence()
            .mapNotNull { SourceAddress.resolve(page.baseUri(), it.attr("href")) }
            .distinct()
            .firstNotNullOfOrNull { playbackUrl -> parse(fetch(playbackUrl))?.let { it to playbackUrl } }
            ?.let { (source, playbackUrl) ->
                Log.i(TAG, "resolve linked source host=${runCatching { java.net.URI(source.url).host }.getOrNull()} type=${mediaType(source.url)}")
                return@withContext source.copy(referer = playbackUrl).rememberFor(pageUrl, startedAtMs)
            }

        throw IOException("页面未提供可识别的视频地址，请重试或检查站点是否要求登录")
    }

    /** Stores a successful resolution in the shared 10-minute cache (failures throw before this). */
    private fun PlaybackSource.rememberFor(pageUrl: String, startedAtMs: Long): PlaybackSource = also {
        playbackCache.put(pageUrl, it)
        Log.i(TAG, "timing resolve ms=${System.currentTimeMillis() - startedAtMs} cache=miss")
    }

    private fun mediaType(url: String): String = when {
        url.substringBefore('?').lowercase().endsWith(".m3u8") -> "hls"
        url.substringBefore('?').lowercase().endsWith(".mp4") -> "mp4"
        else -> "unknown"
    }

    private fun isMediaUrl(url: String): Boolean {
        val path = runCatching { java.net.URI(url).path.orEmpty().lowercase() }.getOrDefault("")
        return MEDIA_EXTENSIONS.any(path::endsWith)
    }

    internal fun parse(page: Document): PlaybackSource? {
        for (video in page.select("video")) {
            val references = mutableListOf(video.attr("src"))
            references += video.select("source[src]").map { it.attr("src") }
            // The observed site uses strencode2 = JavaScript unescape to write a <source> tag.
            // Limit decoding to scripts inside <video>, so ad scripts are not selected as content.
            for (script in video.select("script")) {
                ENCODED_SOURCE.findAll(script.data()).forEach { match ->
                    val html = unescape(match.groupValues[1])
                    references += Jsoup.parseBodyFragment(html).select("source[src]").map { it.attr("src") }
                }
            }
            references.firstNotNullOfOrNull { SourceAddress.resolve(page.baseUri(), it) }?.let {
                return PlaybackSource(it, page.location().ifBlank { page.baseUri() })
            }
        }
        val dataAttributes = page.select("[data-play-src], [data-video-src], [data-video-url]")
            .asSequence()
            .map { element ->
                element.attr("data-play-src").ifBlank {
                    element.attr("data-video-src").ifBlank { element.attr("data-video-url") }
                }
            }
        dataAttributes.firstNotNullOfOrNull { SourceAddress.resolve(page.baseUri(), it) }?.let {
            return PlaybackSource(it, page.location().ifBlank { page.baseUri() })
        }

        page.select("script#videoInitialData[type=application/json], script[type=application/json]")
            .asSequence()
            .flatMap { script ->
                val data = script.data()
                val directSources = sequenceOf("videoSrc", "previewSrc").flatMap { key ->
                    JSON_STRING.findAll(data).filter { it.groupValues[1] == key }.map { decodeJson(it.groupValues[2]) }
                }
                val episodeBlock = JSON_EPISODES.find(data)?.groupValues?.get(1).orEmpty()
                directSources + JSON_STRING.findAll(episodeBlock).map { decodeJson(it.groupValues[2]) }
            }
            .firstNotNullOfOrNull { SourceAddress.resolve(page.baseUri(), it) }?.let {
                return PlaybackSource(it, page.location().ifBlank { page.baseUri() })
            }

        page.select("script[type=application/ld+json]")
            .asSequence()
            .flatMap { script -> JSON_STRING.findAll(script.data()).filter { it.groupValues[1] == "contentUrl" }.map { decodeJson(it.groupValues[2]) } }
            // JSON-LD also publishes the site's logo as contentUrl. Only accept
            // media-like paths here so an image can never become the player source.
            .firstNotNullOfOrNull { reference ->
                SourceAddress.resolve(page.baseUri(), reference)?.takeIf(::isMediaUrl)
            }?.let {
                return PlaybackSource(it, page.location().ifBlank { page.baseUri() })
            }

        // MissAV publishes HLS metadata in the page HTML, sometimes URL-escaped and
        // sometimes packed into pipe-separated JavaScript tokens.
        if (ContentSourceCatalog.isMissAv(page.location().ifBlank { page.baseUri() })) {
            extractMissAvHls(page.html())?.let {
                return PlaybackSource(it, page.location().ifBlank { page.baseUri() })
            }
        }
        return null
    }

    internal fun extractMissAvHls(html: String): String? {
        DIRECT_HLS.find(cleanHlsUrl(html))?.value?.takeIf(::isValidHlsUrl)?.let { return it }
        ENCODED_HLS.find(html)?.value?.let { encoded ->
            runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8.name()) }
                .getOrDefault(encoded)
                .let(::cleanHlsUrl)
                .takeIf(::isValidHlsUrl)
                ?.let { return it }
        }

        val packed = PACKED_HLS.find(html)?.groupValues?.getOrNull(1) ?: return null
        val parts = packed.split('|').asReversed()
        for (index in 0..parts.size - 8) {
            if (parts[index] !in setOf("http", "https")) continue
            val candidate = buildString {
                append(parts[index]); append("://")
                append(parts[index + 1]); append('.'); append(parts[index + 2]); append('/')
                append(parts.subList(index + 3, index + 8).joinToString("-"))
                append("/playlist.m3u8")
            }
            if (isValidHlsUrl(candidate)) return candidate
        }
        return null
    }

    private fun cleanHlsUrl(value: String): String = value
        .replace("\\/", "/")
        .replace("\\u002F", "/", ignoreCase = true)
        .replace("\\u0026", "&", ignoreCase = true)
        .replace("&amp;", "&")

    private fun isValidHlsUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.path.lowercase().endsWith(".m3u8")
    }.getOrDefault(false)

    internal fun parseEpisodes(page: Document, fallbackBaseUrl: String = page.baseUri()): List<PlaybackEpisode> {
        return page.select(".hg-web-detail__ep-grid a[data-ep-id], .hg-web-play__ep[data-ep-id]")
            .asSequence()
            .mapNotNull { element ->
                val number = element.attr("data-ep-id").toIntOrNull() ?: return@mapNotNull null
                val address = SourceAddress.resolve(page.baseUri().ifBlank { fallbackBaseUrl }, element.attr("href"))
                    ?: return@mapNotNull null
                PlaybackEpisode(number, element.text().trim().ifBlank { "%02d".format(number) }, address)
            }
            .distinctBy { it.number }
            .sortedBy { it.number }
            .toList()
    }

    private fun fetch(url: String): Document {
        val startedAtMs = System.currentTimeMillis()
        var fetched = false
        val document = pageCache.get(url) {
            fetched = true
            Jsoup.connect(url)
                .userAgent(VideoRepository.USER_AGENT)
                .timeout(20_000)
                .followRedirects(true)
                .get()
        }
        Log.i(
            TAG,
            "timing page fetch ms=${System.currentTimeMillis() - startedAtMs} host=${runCatching { URI(url).host }.getOrNull()} fetched=$fetched",
        )
        return document
    }

    companion object {
        private const val TAG = "PlaybackResolver"
        internal const val CACHE_TTL_MS = 10 * 60 * 1000L
        internal const val CACHE_MAX_ENTRIES = 64
        internal const val PAGE_CACHE_TTL_MS = 60 * 1000L
        internal const val PAGE_CACHE_MAX_ENTRIES = 16
        // Media URLs can carry short-lived signatures; refresh them more eagerly than episode lists.
        private const val PLAYBACK_CACHE_TTL_MS = 2 * 60 * 1000L
        // Detail-page documents are shared by resolve() and resolveEpisodes() across resolver
        // instances, so one video open fetches the HTML at most once instead of twice.
        private val pageCache = PageFetchCache(PAGE_CACHE_TTL_MS, PAGE_CACHE_MAX_ENTRIES)
        private val playbackCache = CachedValue<PlaybackSource>(PLAYBACK_CACHE_TTL_MS, CACHE_MAX_ENTRIES)
        private val episodeCache = CachedValue<List<PlaybackEpisode>>(CACHE_TTL_MS, CACHE_MAX_ENTRIES)
        private val ENCODED_SOURCE = Regex("""strencode2\(\s*["']([^"']+)["']\s*\)""")
        private val ESCAPE = Regex("%u([0-9a-fA-F]{4})|%([0-9a-fA-F]{2})")
        private val JSON_STRING = Regex("""\"([^\"]+)\"\s*:\s*\"((?:\\\\.|[^\"\\\\])*)\"""")
        private val JSON_EPISODES = Regex("""\"epPlaySrcs\"\s*:\s*\{(.*?)\}""", setOf(RegexOption.DOT_MATCHES_ALL))
        private val MEDIA_EXTENSIONS = setOf(".m3u8", ".mp4", ".m4v", ".webm", ".mov", ".mkv", ".ts")
        private val DIRECT_HLS = Regex("""https?://[^\"'\s<>]+?\.m3u8[^\"'\s<>]*""", RegexOption.IGNORE_CASE)
        private val ENCODED_HLS = Regex("""https?%3A(?:%2F){2}[^\"'\s<>]+?\.m3u8[^\"'\s<>]*""", RegexOption.IGNORE_CASE)
        private val PACKED_HLS = Regex("""'m3u8(.*?)video""", RegexOption.DOT_MATCHES_ALL)
        private fun unescape(value: String): String = ESCAPE.replace(value) {
            it.groupValues[1].ifBlank { it.groupValues[2] }.toInt(16).toChar().toString()
        }
        private fun decodeJson(value: String): String = value
            .replace(Regex("\\\\u([0-9a-fA-F]{4})")) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace("\\\\&", "&")
            .replace("\\\\/", "/")
            .replace("\\\\\\\"", "\"")
            .replace("\\\\\\\\", "\\\\")
    }
}

/**
 * Tiny thread-safe TTL + LRU cache used for playback resolution results.
 * Failures are never stored by callers, so only successful values live here.
 */
internal class CachedValue<V>(
    private val ttlMs: Long,
    private val maxEntries: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry<T>(val value: T, val expiresAtMs: Long)

    private val entries = LinkedHashMap<String, Entry<V>>(16, 0.75f, true)
    private val lock = Any()

    fun get(key: String): V? = synchronized(lock) {
        val entry = entries[key]
        when {
            entry == null -> null
            entry.expiresAtMs <= clock() -> {
                entries.remove(key)
                null
            }
            else -> entry.value
        }
    }

    fun put(key: String, value: V) = synchronized(lock) {
        entries[key] = Entry(value, clock() + ttlMs)
        val iterator = entries.entries.iterator()
        while (entries.size > maxEntries && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    fun remove(key: String) {
        synchronized(lock) { entries.remove(key) }
    }

    fun clear() = synchronized(lock) {
        entries.clear()
    }

    internal fun size(): Int = synchronized(lock) { entries.size }
}

/**
 * Short-lived, thread-safe cache for detail-page documents shared by
 * [PlaybackResolver.resolve] and [PlaybackResolver.resolveEpisodes].
 *
 * Concurrent misses for the same URL are coalesced into a single fetch (single-flight), and only
 * successful documents are cached; a failed load is always retried by the next caller. The class
 * has no Android dependencies and an injectable clock so it can be unit tested on the JVM.
 */
internal class PageFetchCache(
    private val ttlMs: Long,
    private val maxEntries: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val document: Document, val expiresAtMs: Long)

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private val inFlight = HashMap<String, FutureTask<Document>>()

    fun get(url: String, load: () -> Document): Document {
        var ownsLoad = false
        val task: FutureTask<Document>
        synchronized(lock) {
            val entry = entries[url]
            if (entry != null) {
                if (entry.expiresAtMs > clock()) return entry.document
                entries.remove(url)
            }
            val pending = inFlight[url]
            if (pending != null) {
                task = pending
            } else {
                val created = FutureTask(load)
                inFlight[url] = created
                task = created
                ownsLoad = true
            }
        }
        if (ownsLoad) task.run()
        return try {
            val document = task.get()
            if (ownsLoad) store(url, document)
            document
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("页面加载被中断", interrupted)
        } finally {
            if (ownsLoad) synchronized(lock) { inFlight.remove(url) }
        }
    }

    private fun store(url: String, document: Document) {
        synchronized(lock) {
            entries[url] = Entry(document, clock() + ttlMs)
            val iterator = entries.entries.iterator()
            while (entries.size > maxEntries && iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    internal fun size(): Int = synchronized(lock) { entries.size }
}
