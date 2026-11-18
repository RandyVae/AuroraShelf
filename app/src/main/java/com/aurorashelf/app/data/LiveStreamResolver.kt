package com.aurorashelf.app.data

import com.aurorashelf.app.model.LiveItem
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

sealed class LiveStreamResolveResult {
    data class Success(val hlsUrl: String, val referer: String) : LiveStreamResolveResult()
    data class StatusError(val message: String) : LiveStreamResolveResult()
    data class Failure(val cause: Throwable) : LiveStreamResolveResult()
}

internal enum class StreamProbeOutcome {
    LIVE_MANIFEST,
    MOUFLON_LOCKED,
    OFFLINE_OR_PRIVATE,
    INVALID_PAYLOAD,
    TRANSIENT_NETWORK_ERROR,
}

class LiveStreamResolver internal constructor(
    private val verifyConstructedStream: Boolean,
    private val streamProbe: ((url: String, referer: String) -> StreamProbeOutcome)? = null,
    private val pkeyRefresher: ((force: Boolean) -> String?)? = null,
) {
    constructor() : this(verifyConstructedStream = false, streamProbe = null, pkeyRefresher = null)
    constructor(verifyConstructedStream: Boolean) : this(
        verifyConstructedStream = verifyConstructedStream,
        streamProbe = null,
        pkeyRefresher = null,
    )

    suspend fun resolve(item: LiveItem): LiveStreamResolveResult = withContext(Dispatchers.IO) {
        try {
            val isHornyStudents = item.playerUrl.contains("hornystudents.com", ignoreCase = true)

            // 1. Stripchat 直链解析（升级为自适应高清 _auto.m3u8 + Mouflon pkey 动态鉴权 + 切片真流核验）
            if (!isHornyStudents) {
                val numericId = item.id.replace(Regex("[^0-9]"), "")
                val existingHls = item.hlsStreamUrl?.takeIf(::isHttpsUrl)
                val baseMasterUrl = normalizeStripchatMasterUrl(existingHls, numericId)
                if (!baseMasterUrl.isNullOrBlank()) {
                    val referer = "https://zh.stripchat.com/"
                    var activePkey = currentStripchatPkey()
                    var authenticatedUrl = attachStripchatPkey(baseMasterUrl, activePkey)
                    if (verifyConstructedStream) {
                        var outcome = probeHlsManifest(authenticatedUrl, referer)
                        if (outcome == StreamProbeOutcome.MOUFLON_LOCKED) {
                            val refreshed = (pkeyRefresher?.invoke(true) ?: refreshStripchatPkey(force = true))
                            if (!refreshed.isNullOrBlank() && refreshed != activePkey) {
                                activePkey = refreshed
                                authenticatedUrl = attachStripchatPkey(baseMasterUrl, activePkey)
                                outcome = probeHlsManifest(authenticatedUrl, referer)
                            }
                        }
                        when (outcome) {
                            StreamProbeOutcome.OFFLINE_OR_PRIVATE ->
                                return@withContext LiveStreamResolveResult.StatusError("主播当前已离线、下播或正在进行私密秀")
                            StreamProbeOutcome.INVALID_PAYLOAD,
                            StreamProbeOutcome.MOUFLON_LOCKED ->
                                return@withContext LiveStreamResolveResult.Failure(IOException("直播流受加密保护，正在切换至 WebRTC 视口"))
                            StreamProbeOutcome.LIVE_MANIFEST,
                            StreamProbeOutcome.TRANSIENT_NETWORK_ERROR -> Unit
                        }
                    }
                    return@withContext LiveStreamResolveResult.Success(authenticatedUrl, referer)
                }
            }

            // 2. HornyStudents 如果已携带官方 HLS 直链，直接返回
            val existingHls = item.hlsStreamUrl
            if (!existingHls.isNullOrBlank() && isHttpsUrl(existingHls)) {
                return@withContext LiveStreamResolveResult.Success(existingHls, "https://chaturbate.com/")
            }

            // 3. Chaturbate / HornyStudents 官方轻量接口解析 (~2KB，响应 <200ms)
            val username = item.username.trim()
            if (username.isBlank()) {
                return@withContext LiveStreamResolveResult.StatusError("主播用户名无效")
            }

            val apiUrl = "https://chaturbate.com/api/chatvideocontext/$username/"
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", VideoRepository.USER_AGENT)
                .header("Referer", "https://chaturbate.com/$username/")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Cookie", "agreeterms=1; isAdult=1; hasConfirmedAge=1; lang=en;")
                .build()

            val responseBody = runCatching {
                StripchatLiveRepository.okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string().orEmpty() else null
                }
            }.getOrNull()

            if (!responseBody.isNullOrBlank()) {
                val json = runCatching { JSONObject(responseBody) }.getOrNull()
                if (json != null) {
                    val status = json.optString("room_status").lowercase()
                    when (status) {
                        "private" -> return@withContext LiveStreamResolveResult.StatusError("主播当前正在进行私密秀（需付费观看）")
                        "offline" -> return@withContext LiveStreamResolveResult.StatusError("主播当前已离线或暂未开播")
                        "away" -> return@withContext LiveStreamResolveResult.StatusError("主播暂时离开，请稍后刷新")
                    }

                    val hlsSource = json.optString("hls_source").trim()
                    if (hlsSource.isNotBlank() && isHttpsUrl(hlsSource)) {
                        return@withContext LiveStreamResolveResult.Success(hlsSource, "https://chaturbate.com/")
                    }
                }
            }

            // 4. 备用线路：拉取 HTML 页面提取 hls_source
            val candidatePages = listOf(
                "https://chaturbate.com/$username/",
                "https://www.hornystudents.com/$username/",
            )
            for (pageUrl in candidatePages) {
                val pageReq = Request.Builder()
                    .url(pageUrl)
                    .header("User-Agent", VideoRepository.USER_AGENT)
                    .header("Referer", "https://chaturbate.com/")
                    .header("Cookie", "agreeterms=1; isAdult=1; hasConfirmedAge=1; lang=en;")
                    .build()

                val pageHtml = runCatching {
                    StripchatLiveRepository.okHttpClient.newCall(pageReq).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string().orEmpty() else null
                    }
                }.getOrNull()

                if (!pageHtml.isNullOrBlank()) {
                    val unescaped = unescapeUnicode(pageHtml)
                    if (unescaped.contains("\"room_status\": \"private\"") || unescaped.contains("\"room_status\":\"private\"")) {
                        return@withContext LiveStreamResolveResult.StatusError("主播当前正在进行私密秀（需付费观看）")
                    }
                    if (unescaped.contains("\"room_status\": \"offline\"") || unescaped.contains("\"room_status\":\"offline\"")) {
                        return@withContext LiveStreamResolveResult.StatusError("主播当前已离线或暂未开播")
                    }
                    if (unescaped.contains("\"room_status\": \"away\"") || unescaped.contains("\"room_status\":\"away\"")) {
                        return@withContext LiveStreamResolveResult.StatusError("主播暂时离开，请稍后刷新")
                    }

                    val hlsMatch = HLS_REGEX.find(unescaped)
                    if (hlsMatch != null) {
                        val matchedUrl = hlsMatch.groupValues[1]
                        if (isHttpsUrl(matchedUrl)) {
                            return@withContext LiveStreamResolveResult.Success(matchedUrl, "https://chaturbate.com/")
                        }
                    }
                }
            }

            LiveStreamResolveResult.Failure(IOException("未检测到直链，平滑降级至备用视口"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            LiveStreamResolveResult.Failure(e)
        }
    }

    private fun probeHlsManifest(url: String, referer: String): StreamProbeOutcome {
        streamProbe?.let { return it(url, referer) }
        return try {
            val origin = referer.trimEnd('/')
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", VideoRepository.USER_AGENT)
                .header("Referer", referer)
                .header("Origin", origin)
                .header("Accept", "application/vnd.apple.mpegurl,application/x-mpegURL,*/*")
                .build()
            fastProbeClient.newCall(req).execute().use { resp ->
                when (resp.code) {
                    in 200..299 -> {
                        val manifest = resp.body?.byteStream()?.use { input ->
                            val buf = ByteArray(4096)
                            var total = 0
                            while (total < buf.size) {
                                val n = input.read(buf, total, buf.size - total)
                                if (n <= 0) break
                                total += n
                            }
                            if (total > 0) String(buf, 0, total, Charsets.UTF_8) else ""
                        }.orEmpty()
                        if (!manifest.contains("#EXTM3U", ignoreCase = true)) {
                            return@use StreamProbeOutcome.INVALID_PAYLOAD
                        }
                        if (isMouflonLockedPlaylist(manifest)) {
                            return@use StreamProbeOutcome.MOUFLON_LOCKED
                        }
                        val firstVariantUrl = manifest.lineSequence()
                            .map(String::trim)
                            .firstOrNull { it.startsWith("https://", ignoreCase = true) && it.contains(".m3u8", ignoreCase = true) }
                        if (firstVariantUrl != null && firstVariantUrl.contains("doppiocdn", ignoreCase = true)) {
                            probeChildVariantManifest(firstVariantUrl, referer, origin)
                        } else {
                            StreamProbeOutcome.LIVE_MANIFEST
                        }
                    }
                    403, 404, 410 -> StreamProbeOutcome.OFFLINE_OR_PRIVATE
                    else -> StreamProbeOutcome.TRANSIENT_NETWORK_ERROR
                }
            }
        } catch (_: IOException) {
            StreamProbeOutcome.TRANSIENT_NETWORK_ERROR
        }
    }

    private fun probeChildVariantManifest(variantUrl: String, referer: String, origin: String): StreamProbeOutcome {
        return try {
            val req = Request.Builder()
                .url(variantUrl)
                .header("User-Agent", VideoRepository.USER_AGENT)
                .header("Referer", referer)
                .header("Origin", origin)
                .header("Accept", "application/vnd.apple.mpegurl,application/x-mpegURL,*/*")
                .build()
            fastProbeClient.newCall(req).execute().use { resp ->
                when (resp.code) {
                    in 200..299 -> {
                        val head = resp.body?.byteStream()?.use { input ->
                            val buf = ByteArray(2048)
                            var total = 0
                            while (total < buf.size) {
                                val n = input.read(buf, total, buf.size - total)
                                if (n <= 0) break
                                total += n
                            }
                            if (total > 0) String(buf, 0, total, Charsets.UTF_8) else ""
                        }.orEmpty()
                        when {
                            !head.contains("#EXTM3U", ignoreCase = true) -> StreamProbeOutcome.INVALID_PAYLOAD
                            isMouflonLockedPlaylist(head) -> StreamProbeOutcome.MOUFLON_LOCKED
                            else -> StreamProbeOutcome.LIVE_MANIFEST
                        }
                    }
                    403, 404, 410 -> StreamProbeOutcome.OFFLINE_OR_PRIVATE
                    else -> StreamProbeOutcome.TRANSIENT_NETWORK_ERROR
                }
            }
        } catch (_: IOException) {
            StreamProbeOutcome.TRANSIENT_NETWORK_ERROR
        }
    }

    internal fun unescapeUnicode(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                val next = input[i + 1]
                if (next == 'u' && i + 5 < input.length) {
                    val hex = input.substring(i + 2, i + 6)
                    val code = hex.toIntOrNull(16)
                    if (code != null) {
                        sb.append(code.toChar())
                        i += 6
                        continue
                    }
                } else if (next == '/') {
                    sb.append('/')
                    i += 2
                    continue
                } else if (next == '"') {
                    sb.append('"')
                    i += 2
                    continue
                } else if (next == '\\') {
                    sb.append('\\')
                    i += 2
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun isHttpsUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    companion object {
        private const val PROBE_TIMEOUT_MS = 3_500L
        private const val PKEY_REFRESH_TTL_MS = 30 * 60 * 1000L
        private const val PKEY_RETRY_COOLDOWN_MS = 15_000L
        internal const val DEFAULT_STRIPCHAT_PKEY = "B0p93vi8Uj6AYyZb"
        private const val DEFAULT_MMP_ORIGIN = "https://mmp.doppiocdn.com/player/mmp"

        private val HLS_REGEX = Regex(""""hls_source"\s*:\s*"(https?://[^"\s\\]+?\.m3u8[^"\s\\]*)"""")
        private val DOPPIO_MASTER_REGEX = Regex(
            """^(https://[^/]*doppiocdn\.[^/]+/hls/([0-9]+)/master/)[0-9]+(?:_[0-9]+p)?\.m3u8(\?.*)?$""",
            RegexOption.IGNORE_CASE,
        )
        private val MMP_VERSION_REGEX = Regex(""""mmpVersion"\s*:\s*"(v[0-9]+\.[0-9]+\.[0-9][^"]*)"""")
        private val MMP_ORIGIN_REGEX = Regex(""""MMPExternalUnitedSourceOrigin"\s*:\s*"(https://[^"]+)"""")
        private val MMP_PKEY_REGEX = Regex(
            """searchParams\.set\(\s*["']pkey["']\s*,\s*["']([A-Za-z0-9_-]{8,64})["']\s*\)""",
        )

        @Volatile
        private var cachedStripchatPkey: String = DEFAULT_STRIPCHAT_PKEY

        @Volatile
        private var lastPkeyRefreshEpochMs: Long = 0L

        private val pkeyLock = Any()

        internal fun currentStripchatPkey(): String = cachedStripchatPkey

        internal fun isMouflonLockedPlaylist(manifest: String): Boolean {
            return manifest.contains("#EXT-X-MOUFLON-ADVERT", ignoreCase = true) ||
                manifest.contains("#EXT-X-MOUFLON:FILE:", ignoreCase = true)
        }

        internal fun normalizeStripchatMasterUrl(rawUrl: String?, fallbackNumericId: String = ""): String? {
            if (!rawUrl.isNullOrBlank()) {
                val trimmed = rawUrl.trim()
                val match = DOPPIO_MASTER_REGEX.matchEntire(trimmed)
                if (match != null) {
                    val prefix = match.groupValues[1]
                    val streamId = match.groupValues[2]
                    val query = match.groupValues[3]
                    return "${prefix}${streamId}_auto.m3u8$query"
                }
                return trimmed
            }
            val cleanId = fallbackNumericId.filter(Char::isDigit)
            if (cleanId.isNotBlank()) {
                return "https://edge-hls.doppiocdn.media/hls/$cleanId/master/${cleanId}_auto.m3u8"
            }
            return null
        }

        internal fun attachStripchatPkey(masterUrl: String, pkey: String): String {
            if (pkey.isBlank() || !masterUrl.contains("doppiocdn.", ignoreCase = true)) {
                return masterUrl
            }
            val base = masterUrl.substringBefore('?')
            val existingQuery = masterUrl.substringAfter('?', missingDelimiterValue = "")
            val filteredParams = existingQuery
                .split('&')
                .filter { it.isNotBlank() && !it.startsWith("pkey=", ignoreCase = true) }
                .toMutableList()
            filteredParams.add("pkey=$pkey")
            return "$base?${filteredParams.joinToString("&")}"
        }

        internal fun extractMmpBundleUrlFromHtml(rawHtml: String): String? {
            val normalized = rawHtml
                .replace("\\u002F", "/")
                .replace("\\/", "/")
                .replace("\\u0022", "\"")
            val version = MMP_VERSION_REGEX.find(normalized)?.groupValues?.getOrNull(1)?.trim()
                ?: return null
            val origin = MMP_ORIGIN_REGEX.find(normalized)?.groupValues?.getOrNull(1)?.trim()?.trimEnd('/')
                ?: DEFAULT_MMP_ORIGIN
            return "$origin/$version/main.js"
        }

        internal fun extractPkeyFromMmpJs(jsSource: String): String? {
            return MMP_PKEY_REGEX.find(jsSource)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)
        }

        internal fun refreshStripchatPkeyIfStale(): String {
            val now = System.currentTimeMillis()
            if (now - lastPkeyRefreshEpochMs < PKEY_REFRESH_TTL_MS) {
                return cachedStripchatPkey
            }
            return refreshStripchatPkey(force = false) ?: cachedStripchatPkey
        }

        internal fun refreshStripchatPkey(force: Boolean): String? = synchronized(pkeyLock) {
            val now = System.currentTimeMillis()
            if (!force && now - lastPkeyRefreshEpochMs < PKEY_REFRESH_TTL_MS) {
                return cachedStripchatPkey
            }
            if (force && now - lastPkeyRefreshEpochMs < PKEY_RETRY_COOLDOWN_MS && lastPkeyRefreshEpochMs > 0L) {
                return cachedStripchatPkey
            }
            lastPkeyRefreshEpochMs = now

            val candidateBases = listOf(StripchatLiveRepository.SITE_BASE, StripchatLiveRepository.FALLBACK_SITE_BASE)
            for (siteBase in candidateBases) {
                val mmpBundleUrl = runCatching {
                    val req = Request.Builder()
                        .url("$siteBase/")
                        .header("User-Agent", VideoRepository.USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .build()
                    StripchatLiveRepository.okHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) extractMmpBundleUrlFromHtml(resp.body?.string().orEmpty()) else null
                    }
                }.getOrNull() ?: continue

                val extractedKey = runCatching {
                    val req = Request.Builder()
                        .url(mmpBundleUrl)
                        .header("User-Agent", VideoRepository.USER_AGENT)
                        .header("Origin", siteBase)
                        .header("Referer", "$siteBase/")
                        .header("Accept", "*/*")
                        .build()
                    StripchatLiveRepository.okHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) extractPkeyFromMmpJs(resp.body?.string().orEmpty()) else null
                    }
                }.getOrNull()

                if (!extractedKey.isNullOrBlank()) {
                    cachedStripchatPkey = extractedKey
                    return extractedKey
                }
            }
            return null
        }

        private val fastProbeClient: OkHttpClient by lazy {
            StripchatLiveRepository.okHttpClient.newBuilder()
                .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(PROBE_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS)
                .build()
        }
    }
}
