package com.aurorashelf.app.data

import com.aurorashelf.app.model.LiveItem
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class LiveStreamResolveResult {
    data class Success(val hlsUrl: String, val referer: String) : LiveStreamResolveResult()
    data class StatusError(val message: String) : LiveStreamResolveResult()
    data class Failure(val cause: Throwable) : LiveStreamResolveResult()
}

class LiveStreamResolver {
    suspend fun resolve(item: LiveItem): LiveStreamResolveResult = withContext(Dispatchers.IO) {
        try {
            // 1. 如果已携带官方 HLS 直链，直接校验后秒起播
            val existingHls = item.hlsStreamUrl
            if (!existingHls.isNullOrBlank() && isHttpsUrl(existingHls)) {
                val referer = if (item.playerUrl.contains("hornystudents.com")) {
                    "https://chaturbate.com/"
                } else {
                    "https://zh.stripchat.com/"
                }
                return@withContext LiveStreamResolveResult.Success(existingHls, referer)
            }

            // 2. Stripchat 专属直链构造 (基于主播数字 ID)
            if (!item.playerUrl.contains("hornystudents.com")) {
                val numericId = item.id.replace(Regex("[^0-9]"), "")
                if (numericId.isNotBlank()) {
                    val directUrl = "https://edge-hls.doppiocdn.media/hls/$numericId/master/$numericId.m3u8"
                    return@withContext LiveStreamResolveResult.Success(directUrl, "https://zh.stripchat.com/")
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
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
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
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
                    .header("Referer", "https://chaturbate.com/")
                    .header("Cookie", "agreeterms=1; isAdult=1; hasConfirmedAge=1; lang=en;")
                    .build()

                val pageHtml = runCatching {
                    StripchatLiveRepository.okHttpClient.newCall(pageReq).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string().orEmpty() else null
                    }
                }.getOrNull()

                if (!pageHtml.isNullOrBlank()) {
                    if (pageHtml.contains("\"room_status\": \"private\"") || pageHtml.contains("\"room_status\":\"private\"")) {
                        return@withContext LiveStreamResolveResult.StatusError("主播当前正在进行私密秀（需付费观看）")
                    }
                    if (pageHtml.contains("\"room_status\": \"offline\"") || pageHtml.contains("\"room_status\":\"offline\"")) {
                        return@withContext LiveStreamResolveResult.StatusError("主播当前已离线或暂未开播")
                    }

                    val hlsMatch = HLS_REGEX.find(pageHtml)
                    if (hlsMatch != null) {
                        val matchedUrl = hlsMatch.groupValues[1]
                        if (isHttpsUrl(matchedUrl)) {
                            return@withContext LiveStreamResolveResult.Success(matchedUrl, "https://chaturbate.com/")
                        }
                    }
                }
            }

            LiveStreamResolveResult.Failure(IOException("未检测到直链，平滑降级至备用视口"))
        } catch (e: Exception) {
            LiveStreamResolveResult.Failure(e)
        }
    }

    private fun isHttpsUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    companion object {
        private val HLS_REGEX = Regex(""""hls_source"\s*:\s*"(https?:\\?/\\?/[^"\s\\]+?\.m3u8[^"\s\\]*)"""")
    }
}
