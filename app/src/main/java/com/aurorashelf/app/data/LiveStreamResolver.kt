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
    OFFLINE_OR_PRIVATE,
    INVALID_PAYLOAD,
    TRANSIENT_NETWORK_ERROR,
}

class LiveStreamResolver internal constructor(
    private val verifyConstructedStream: Boolean,
    private val streamProbe: ((url: String, referer: String) -> StreamProbeOutcome)? = null,
) {
    constructor() : this(verifyConstructedStream = false, streamProbe = null)
    constructor(verifyConstructedStream: Boolean) : this(verifyConstructedStream = verifyConstructedStream, streamProbe = null)

    suspend fun resolve(item: LiveItem): LiveStreamResolveResult = withContext(Dispatchers.IO) {
        try {
            val isHornyStudents = item.playerUrl.contains("hornystudents.com", ignoreCase = true)

            // 1. 如果已携带官方 HLS 直链，直接校验后秒起播
            val existingHls = item.hlsStreamUrl
            if (!existingHls.isNullOrBlank() && isHttpsUrl(existingHls)) {
                val referer = if (isHornyStudents) {
                    "https://chaturbate.com/"
                } else {
                    "https://zh.stripchat.com/"
                }
                if (verifyConstructedStream && !isHornyStudents) {
                    when (probeHlsManifest(existingHls, referer)) {
                        StreamProbeOutcome.OFFLINE_OR_PRIVATE ->
                            return@withContext LiveStreamResolveResult.StatusError("主播当前已离线、下播或正在进行私密秀")
                        StreamProbeOutcome.INVALID_PAYLOAD ->
                            return@withContext LiveStreamResolveResult.Failure(IOException("直播流暂不可用，正在尝试备用视口"))
                        StreamProbeOutcome.LIVE_MANIFEST,
                        StreamProbeOutcome.TRANSIENT_NETWORK_ERROR -> Unit
                    }
                }
                return@withContext LiveStreamResolveResult.Success(existingHls, referer)
            }

            // 2. Stripchat 专属直链构造 (基于主播数字 ID)
            if (!isHornyStudents) {
                val numericId = item.id.replace(Regex("[^0-9]"), "")
                if (numericId.isNotBlank()) {
                    val directUrl = "https://edge-hls.doppiocdn.media/hls/$numericId/master/$numericId.m3u8"
                    val referer = "https://zh.stripchat.com/"
                    if (verifyConstructedStream) {
                        when (probeHlsManifest(directUrl, referer)) {
                            StreamProbeOutcome.OFFLINE_OR_PRIVATE ->
                                return@withContext LiveStreamResolveResult.StatusError("主播当前已离线、下播或正在进行私密秀")
                            StreamProbeOutcome.INVALID_PAYLOAD ->
                                return@withContext LiveStreamResolveResult.Failure(IOException("直播流暂不可用，正在尝试备用视口"))
                            StreamProbeOutcome.LIVE_MANIFEST,
                            StreamProbeOutcome.TRANSIENT_NETWORK_ERROR -> Unit
                        }
                    }
                    return@withContext LiveStreamResolveResult.Success(directUrl, referer)
                }
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
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", VideoRepository.USER_AGENT)
                .header("Referer", referer)
                .header("Accept", "application/vnd.apple.mpegurl,application/x-mpegURL,*/*")
                .build()
            fastProbeClient.newCall(req).execute().use { resp ->
                when (resp.code) {
                    in 200..299 -> {
                        val head = resp.body?.byteStream()?.use { input ->
                            val buf = ByteArray(1024)
                            val n = input.read(buf)
                            if (n > 0) String(buf, 0, n, Charsets.UTF_8) else ""
                        }.orEmpty()
                        if (head.contains("#EXTM3U", ignoreCase = true)) {
                            StreamProbeOutcome.LIVE_MANIFEST
                        } else {
                            StreamProbeOutcome.INVALID_PAYLOAD
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
        private const val PROBE_TIMEOUT_MS = 2_500L
        private val HLS_REGEX = Regex(""""hls_source"\s*:\s*"(https?://[^"\s\\]+?\.m3u8[^"\s\\]*)"""")

        private val fastProbeClient: OkHttpClient by lazy {
            StripchatLiveRepository.okHttpClient.newBuilder()
                .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()
        }
    }
}
