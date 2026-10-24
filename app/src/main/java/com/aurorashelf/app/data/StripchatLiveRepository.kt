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
        val response = request(buildUrl(category, page))
        parsePage(response)
    }

    internal fun parsePage(payload: String): LivePage {
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
                        playerUrl = "$SITE_BASE/$username",
                    ),
                )
            }
        }
        return LivePage(
            items = items.distinctBy(LiveItem::id),
            totalCount = root.optInt("filteredCount", items.size).coerceAtLeast(items.size),
        )
    }

    internal fun buildUrl(category: LiveCategory, page: Int): String {
        val offset = (page - 1) * PAGE_SIZE
        val primaryTag = category.primaryTag?.let { "&primaryTag=$it" }.orEmpty()
        return "$API_BASE?improveTs=false&removeShows=true&limit=$PAGE_SIZE&offset=$offset" +
            "$primaryTag&sortBy=stripRanking&rcmGrp=A&rbCnGr=true&prxCnGr=false&nic=false"
    }

    private fun request(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = REQUEST_TIMEOUT_MS
            connection.readTimeout = REQUEST_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            connection.setRequestProperty("Origin", SITE_BASE)
            connection.setRequestProperty("Referer", "$SITE_BASE/")
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
        private const val API_BASE = "$SITE_BASE/api/front/models"
        internal const val PAGE_SIZE = 30
        private const val REQUEST_TIMEOUT_MS = 15_000
        private const val PUBLIC_STATUS = "public"
        private val USERNAME_PATTERN = Regex("[A-Za-z0-9_-]{1,80}")
    }
}
