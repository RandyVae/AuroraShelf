package com.aurorashelf.app.data

import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import android.util.Log
import com.aurorashelf.app.model.ContentSourceCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

data class PlaybackSource(val url: String, val referer: String)
data class PlaybackEpisode(val number: Int, val label: String, val pageUrl: String)
data class HuavodTokenEntry(
    val token: String,
    val adDurationMs: Long,
    val issuedAtMs: Long,
    val channelKey: String?,
)

/** Reads the media source published inside the page's video element. Never executes remote scripts. */
class PlaybackResolver {
    suspend fun resolveEpisodes(detailUrl: String): List<PlaybackEpisode> = withContext(Dispatchers.IO) {
        val startedAtMs = System.currentTimeMillis()
        require(SourceAddress.resolve(detailUrl, detailUrl) != null) { "视频页面地址无效" }
        val cached = episodeCache.get(detailUrl)
        val episodes = cached ?: (
            if (ContentSourceCatalog.isBeeg(detailUrl)) {
                listOf(PlaybackEpisode(1, "正片", detailUrl))
            } else if (ContentSourceCatalog.isHuavod(detailUrl)) {
                parseHuavodEpisodes(fetch(detailUrl), detailUrl)
            } else {
                parseEpisodes(fetch(detailUrl), detailUrl)
            }
        ).also { parsed ->
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
        val cached = huavodPlaybackCache.get(pageUrl) ?: playbackCache.get(pageUrl)
        if (cached != null) {
            Log.i(TAG, "timing resolve ms=${System.currentTimeMillis() - startedAtMs} cache=hit")
            return@withContext cached
        }
        if (ContentSourceCatalog.isBeeg(pageUrl)) {
            return@withContext resolveBeeg(pageUrl, startedAtMs)
        }
        if (ContentSourceCatalog.is91Dongman(pageUrl)) {
            return@withContext resolve91Dongman(pageUrl, startedAtMs)
        }
        if (ContentSourceCatalog.isHuavod(pageUrl)) {
            return@withContext resolveHuavod(pageUrl, startedAtMs)
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

    /**
     * Warms only the shared 60 s detail-page cache for [pageUrl]. It does not parse a media URL and
     * never requests media data, so a prefetch cannot start playback on its own. Best effort:
     * every failure is swallowed (and only timed in logcat) because prefetching is optional.
     */
    suspend fun prefetchDetail(pageUrl: String) {
        if (pageCache.isFresh(pageUrl)) {
            Log.i(PREFETCH_LOG_TAG, "detail hit=true ms=0")
            return
        }
        val startedAtMs = System.currentTimeMillis()
        try {
            withContext(Dispatchers.IO) { fetch(pageUrl) }
            Log.i(PREFETCH_LOG_TAG, "detail hit=false ms=${System.currentTimeMillis() - startedAtMs}")
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            Log.i(PREFETCH_LOG_TAG, "detail failed ms=${System.currentTimeMillis() - startedAtMs}")
        }
    }

    /**
     * Resolves and caches the playable source of [pageUrl] ahead of time (used for the next
     * episode) so the real tap hits the shared 2 min playback cache. Best effort: failures are
     * only timed in logcat and never bubble into playback.
     */
    suspend fun prefetchPlayback(pageUrl: String) {
        val startedAtMs = System.currentTimeMillis()
        val alreadyCached = playbackCache.get(pageUrl) != null
        try {
            resolve(pageUrl)
            Log.i(PREFETCH_LOG_TAG, "playback hit=$alreadyCached ms=${System.currentTimeMillis() - startedAtMs}")
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            Log.i(PREFETCH_LOG_TAG, "playback failed ms=${System.currentTimeMillis() - startedAtMs}")
        }
    }

    /** Stores a successful resolution in the shared 10-minute cache (failures throw before this). */
    private fun PlaybackSource.rememberFor(pageUrl: String, startedAtMs: Long): PlaybackSource = also {
        playbackCache.put(pageUrl, it)
        Log.i(TAG, "timing resolve ms=${System.currentTimeMillis() - startedAtMs} cache=miss")
    }

    internal fun resolve91Dongman(pageUrl: String, startedAtMs: Long): PlaybackSource {
        val document = fetch(pageUrl)
        val html = document.html()
        val videoUrlAttr = document.selectFirst("div[data-video-url]")?.attr("data-video-url")?.takeIf(String::isNotBlank)
            ?: Regex("""data-video-url=["']([^"']+)["']""").find(html)?.groupValues?.get(1)
            ?: Regex("""var\s+url\s*=\s*["']([^"']+\.m3u8[^"']*)["']""").find(html)?.groupValues?.get(1)
            ?: document.selectFirst("link[type='application/vnd.apple.mpegurl']")?.attr("href")

        val rawUrl = videoUrlAttr ?: throw IOException("页面未提供 91动漫 视频播放流")
        val cleanUrl = Parser.unescapeEntities(cleanHlsUrl(rawUrl), false)
        val source = PlaybackSource(
            url = cleanUrl,
            referer = pageUrl,
        )
        Log.i(TAG, "resolve 91dongman host=${runCatching { URI(source.url).host }.getOrNull()} type=${mediaType(source.url)}")
        return source.rememberFor(pageUrl, startedAtMs)
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

    internal fun parseHuavodEpisodes(page: Document, fallbackBaseUrl: String = page.baseUri()): List<PlaybackEpisode> {
        val matches = page.select("a[href*=/vodplay/]")
        val seen = mutableSetOf<String>()
        val result = mutableListOf<PlaybackEpisode>()
        var fallbackIndex = 1
        for (element in matches) {
            val href = element.attr("href").trim()
            val address = SourceAddress.resolve(page.baseUri().ifBlank { fallbackBaseUrl }, href) ?: continue
            val parts = href.substringBefore(".html").split('-')
            val sid = parts.getOrNull(1) ?: "1"
            val nid = parts.getOrNull(2)?.toIntOrNull() ?: fallbackIndex
            val key = "$sid-$nid"
            if (seen.add(key)) {
                val label = element.text().trim().ifBlank { "第${nid}集" }
                result += PlaybackEpisode(nid, label, address)
                fallbackIndex++
            }
        }
        return result.sortedBy { it.number }
    }

    internal suspend fun resolveBeeg(pageUrl: String, startedAtMs: Long): PlaybackSource = withContext(Dispatchers.IO) {
        val fileId = Regex("""(?:beeg\.com/(?:-0?|video/)?|/|^)(\d+)""").find(pageUrl)?.groupValues?.get(1)
            ?: throw IOException("無法從鏈接中識別 Beeg 視頻 ID: $pageUrl")

        val endpoint = "https://store.externulls.com/video/play_url/$fileId"
        val playPath = try {
            Jsoup.connect(endpoint)
                .ignoreContentType(true)
                .userAgent(VideoRepository.USER_AGENT)
                .timeout(15_000)
                .execute()
                .body()
                .trim()
        } catch (e: Exception) {
            throw IOException("請求 Beeg 播放地址失敗: ${e.message}", e)
        }

        if (playPath.isBlank() || (playPath.startsWith("{") && playPath.contains("error"))) {
            throw IOException("Beeg 未返回有效的視頻流地址")
        }

        val masterUrl = "https://video.beeg.com/" + playPath.removePrefix("/")
        PlaybackSource(masterUrl, referer = "https://beeg.com/").rememberFor(pageUrl, startedAtMs)
    }

    internal fun extractHuavodEpisodeParam(pageUrl: String): String? {
        val vodPlayMatch = Regex("""/vodplay/(\d+-\d+-\d+)""").find(pageUrl)
        if (vodPlayMatch != null) return vodPlayMatch.groupValues[1]
        val vodDetailMatch = Regex("""/voddetail/(\d+)""").find(pageUrl)
        if (vodDetailMatch != null) return "${vodDetailMatch.groupValues[1]}-1-1"
        val path = runCatching { URI(pageUrl).path }.getOrNull() ?: pageUrl
        val parts = path.substringAfterLast('/').substringBefore(".html").split('-')
        return if (parts.size >= 3) "${parts[0]}-${parts[1]}-${parts[2]}" else null
    }

    internal suspend fun resolveHuavod(pageUrl: String, startedAtMs: Long): PlaybackSource = withContext(Dispatchers.IO) {
        val episodeParam = extractHuavodEpisodeParam(pageUrl)
            ?: runCatching {
                val detailDoc = fetch(pageUrl)
                parseHuavodEpisodes(detailDoc, pageUrl).firstOrNull()?.pageUrl?.let { extractHuavodEpisodeParam(it) }
            }.getOrNull() ?: throw IOException("无法识别影片集数编号")

        val canonicalPlayUrl = "https://huavod.com/vodplay/$episodeParam.html"
        val cached = huavodPlaybackCache.get(pageUrl)
            ?: huavodPlaybackCache.get(canonicalPlayUrl)
            ?: playbackCache.get(pageUrl)
            ?: playbackCache.get(canonicalPlayUrl)
        if (cached != null) {
            Log.i(TAG, "huavod cache hit for $pageUrl in ${System.currentTimeMillis() - startedAtMs}ms")
            return@withContext cached
        }

        // Single-flight deduplication: if already in-flight (e.g. from prewarm), join and await it
        val inFlight = huavodInFlight[episodeParam]
        if (inFlight != null && inFlight.isActive) {
            Log.i(TAG, "huavod joining in-flight resolution for $episodeParam")
            return@withContext inFlight.await().rememberHuavod(pageUrl, canonicalPlayUrl, startedAtMs)
        }

        val deferred = huavodScope.async(Dispatchers.IO) {
            executeHuavodResolution(pageUrl, canonicalPlayUrl, episodeParam, startedAtMs)
        }
        huavodInFlight[episodeParam] = deferred
        try {
            deferred.await().rememberHuavod(pageUrl, canonicalPlayUrl, startedAtMs)
        } finally {
            huavodInFlight.remove(episodeParam)
        }
    }

    private suspend fun executeHuavodResolution(
        pageUrl: String,
        canonicalPlayUrl: String,
        episodeParam: String,
        startedAtMs: Long,
    ): PlaybackSource {
        val cachedToken = huavodTokenCache.get(episodeParam)
        val (token, adDurationMs, issuedAtMs, channelKey) = cachedToken ?: run {
            val nextUrl = "https://huavod.com/Player/next?episode=$episodeParam"
            val responseText = Jsoup.connect(nextUrl)
                .userAgent(VideoRepository.USER_AGENT)
                .header("Referer", "https://huavod.com/Player/ec?episode=$episodeParam")
                .header("Accept", "application/json")
                .ignoreContentType(true)
                .timeout(15_000)
                .execute()
                .body()
            val mt = Regex(""""mt"\s*:\s*"([^"]+)"""").find(responseText)?.groupValues?.get(1)
                ?: throw IOException("未能获取播放令牌")
            val adMs = Regex(""""ad_duration_ms"\s*:\s*(\d+)""").find(responseText)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val ck = Regex(""""channel_key"\s*:\s*"([^"]+)"""").find(responseText)?.groupValues?.get(1)?.replace("\\/", "/")
            val entry = HuavodTokenEntry(mt, adMs, System.currentTimeMillis(), ck)
            huavodTokenCache.put(episodeParam, entry)
            entry
        }

        // Direct fast path: if channelKey is not BunnyCDN, play immediately without waiting!
        if (!channelKey.isNullOrBlank() && !channelKey.contains("b-cdn.net") && (channelKey.endsWith(".m3u8") || channelKey.contains(".m3u8"))) {
            Log.i(TAG, "huavod direct channel_key fast path: $channelKey")
            return PlaybackSource(channelKey, "https://huavod.com/")
        }

        val elapsedMs = System.currentTimeMillis() - issuedAtMs
        val targetWaitMs = (adDurationMs - 2000L).coerceAtLeast(0L)
        val remainingWaitMs = (targetWaitMs - elapsedMs).coerceAtLeast(0L)
        if (remainingWaitMs > 0L) {
            Log.i(TAG, "huavod waiting countdown: ${remainingWaitMs}ms (total ${adDurationMs}ms)")
            delay(remainingWaitMs)
        }

        val resolveUrl = "https://huavod.com/Player/resolveUrl"
        val resolveResp = Jsoup.connect(resolveUrl)
            .userAgent(VideoRepository.USER_AGENT)
            .header("Referer", "https://huavod.com/Player/ec?episode=$episodeParam")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .data("token", token)
            .ignoreContentType(true)
            .timeout(15_000)
            .method(org.jsoup.Connection.Method.POST)
            .execute()
            .body()

        val directUrl = Regex(""""url"\s*:\s*"([^"]+)"""").find(resolveResp)?.groupValues?.get(1)?.replace("\\/", "/")
            ?: throw IOException("未能解析到播放直链")

        return PlaybackSource(directUrl, "https://huavod.com/")
    }

    private fun PlaybackSource.rememberHuavod(pageUrl: String, canonicalPlayUrl: String, startedAtMs: Long): PlaybackSource = also {
        huavodPlaybackCache.put(pageUrl, it)
        huavodPlaybackCache.put(canonicalPlayUrl, it)
        playbackCache.put(pageUrl, it)
        playbackCache.put(canonicalPlayUrl, it)
        Log.i(TAG, "huavod resolved & cached for $pageUrl ($canonicalPlayUrl) in ${System.currentTimeMillis() - startedAtMs}ms")
    }

    fun prewarmHuavod(pageUrl: String) {
        val episodeParam = extractHuavodEpisodeParam(pageUrl) ?: return
        val canonicalPlayUrl = "https://huavod.com/vodplay/$episodeParam.html"
        if (huavodPlaybackCache.get(pageUrl) != null || huavodPlaybackCache.get(canonicalPlayUrl) != null) return
        if (huavodInFlight.containsKey(episodeParam)) return

        huavodScope.launch(Dispatchers.IO) {
            runCatching {
                resolveHuavod(pageUrl, System.currentTimeMillis())
            }.onSuccess { source ->
                Log.i(TAG, "huavod prewarm ready for $episodeParam -> ${source.url.take(50)}")
            }.onFailure { e ->
                Log.w(TAG, "huavod prewarm failed for $episodeParam: ${e.message}")
            }
        }
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
        internal const val HUAVOD_PLAYBACK_CACHE_TTL_MS = 2 * 60 * 60 * 1000L // 2 hours for Huavod signed URLs
        // Detail-page documents are shared by resolve() and resolveEpisodes() across resolver
        // instances, so one video open fetches the HTML at most once instead of twice.
        private val pageCache = PageFetchCache(PAGE_CACHE_TTL_MS, PAGE_CACHE_MAX_ENTRIES)
        private val playbackCache = CachedValue<PlaybackSource>(PLAYBACK_CACHE_TTL_MS, CACHE_MAX_ENTRIES)
        private val huavodPlaybackCache = CachedValue<PlaybackSource>(HUAVOD_PLAYBACK_CACHE_TTL_MS, CACHE_MAX_ENTRIES)
        private val episodeCache = CachedValue<List<PlaybackEpisode>>(CACHE_TTL_MS, CACHE_MAX_ENTRIES)
        private val huavodTokenCache = CachedValue<HuavodTokenEntry>(CACHE_TTL_MS, CACHE_MAX_ENTRIES)
        private val huavodInFlight = ConcurrentHashMap<String, Deferred<PlaybackSource>>()
        private val huavodScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

    /** True while a non-expired document is cached for [url]; never triggers a fetch. */
    internal fun isFresh(url: String): Boolean = synchronized(lock) {
        val entry = entries[url] ?: return@synchronized false
        if (entry.expiresAtMs > clock()) {
            true
        } else {
            entries.remove(url)
            false
        }
    }

    internal fun size(): Int = synchronized(lock) { entries.size }
}
