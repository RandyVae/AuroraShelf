package com.aurorashelf.app.data

import com.aurorashelf.app.model.LiveCategory
import com.aurorashelf.app.model.LiveItem
import com.aurorashelf.app.model.LivePage
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class StripchatLiveRepository {
    suspend fun load(category: LiveCategory, page: Int): LivePage = withContext(Dispatchers.IO) {
        require(page >= 1) { "页码必须从 1 开始" }
        try {
            val response = request(buildUrl(category, page, SITE_BASE), SITE_BASE)
            parsePage(response, SITE_BASE)
        } catch (e: Exception) {
            // 主线路失败时自动尝试国际主站备用线路
            val fallbackResponse = request(buildUrl(category, page, FALLBACK_SITE_BASE), FALLBACK_SITE_BASE)
            parsePage(fallbackResponse, FALLBACK_SITE_BASE)
        }
    }

    internal fun parsePage(payload: String, siteBase: String = SITE_BASE): LivePage {
        val root = JSONObject(payload)
        val models = root.optJSONArray("models") ?: throw IOException("直播接口没有返回列表")
        val items = buildList {
            for (index in 0 until models.length()) {
                val model = models.optJSONObject(index) ?: continue
                if (!model.optBoolean("isLive") || model.optString("status") != PUBLIC_STATUS) continue
                val id = model.optString("id").trim()
                val username = model.optString("username").trim()
                if (id.isBlank() || !USERNAME_PATTERN.matches(username)) continue
                val thumbnail = model.optString("previewUrlThumbSmall").takeIf(::isHttpsUrl)
                    ?: model.optString("avatarUrl").takeIf(::isHttpsUrl)
                    ?: continue
                add(
                    LiveItem(
                        id = id,
                        username = username,
                        thumbnailUrl = thumbnail,
                        viewersCount = model.optInt("viewersCount").coerceAtLeast(0),
                        countryCode = model.optString("country").uppercase().takeIf {
                            it.length == 2 && it.all(Char::isLetter)
                        }.orEmpty(),
                        isHd = model.optBoolean("isHd"),
                        playerUrl = "$siteBase/$username",
                    ),
                )
            }
        }
        return LivePage(
            items = items.distinctBy(LiveItem::id),
            totalCount = root.optInt("filteredCount", items.size).coerceAtLeast(items.size),
        )
    }

    internal fun buildUrl(category: LiveCategory, page: Int, siteBase: String = SITE_BASE): String {
        val offset = (page - 1) * PAGE_SIZE
        val primaryTag = (category.primaryTag ?: "girls").let { "&primaryTag=$it" }
        val apiBase = "$siteBase/api/front/models"
        return "$apiBase?improveTs=false&removeShows=true&limit=$PAGE_SIZE&offset=$offset" +
            "$primaryTag&sortBy=stripRanking&rcmGrp=A&rbCnGr=true&prxCnGr=false&nic=false"
    }

    private fun request(address: String, originBase: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = REQUEST_TIMEOUT_MS
            connection.readTimeout = REQUEST_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            connection.setRequestProperty("Origin", originBase)
            connection.setRequestProperty("Referer", "$originBase/")
            connection.setRequestProperty("User-Agent", VideoRepository.USER_AGENT)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("Stripchat 接口返回 HTTP $status")
            if (body.isBlank()) throw IOException("Stripchat 接口返回空内容")
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun isHttpsUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    companion object {
        const val SITE_BASE = "https://zh.stripchat.com"
        const val FALLBACK_SITE_BASE = "https://stripchat.com"
        internal const val PAGE_SIZE = 30
        private const val REQUEST_TIMEOUT_MS = 15_000
        private const val PUBLIC_STATUS = "public"
        private val USERNAME_PATTERN = Regex("[A-Za-z0-9_-]{1,80}")

        /** Asynchronously warms DNS and TLS connection for live stream hosts to eliminate handshake delay. */
        fun prefetchDns() {
            java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                runCatching {
                    java.net.InetAddress.getAllByName("zh.stripchat.com")
                    val conn = URL("$SITE_BASE/api/front/models?limit=1").openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.requestMethod = "HEAD"
                    conn.connect()
                    conn.disconnect()
                }
            }
        }
    }
}
