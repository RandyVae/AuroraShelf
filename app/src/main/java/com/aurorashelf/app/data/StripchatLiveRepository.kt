package com.aurorashelf.app.data

import com.aurorashelf.app.model.LiveCategory
import com.aurorashelf.app.model.LiveItem
import com.aurorashelf.app.model.LivePage
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
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
        val req = Request.Builder()
            .url(address)
            .header("Accept", "application/json")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Origin", originBase)
            .header("Referer", "$originBase/")
            .header("User-Agent", VideoRepository.USER_AGENT)
            .build()
        okHttpClient.newCall(req).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Stripchat 接口返回 HTTP ${response.code}")
            if (body.isBlank()) throw IOException("Stripchat 接口返回空内容")
            return body
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
        private const val REQUEST_TIMEOUT_SECONDS = 25L
        private const val PUBLIC_STATUS = "public"
        private val USERNAME_PATTERN = Regex("[A-Za-z0-9_-]{1,80}")

        internal val okHttpClient: OkHttpClient by lazy {
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })
            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, java.security.SecureRandom())
            }
            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }

        /** Asynchronously warms DNS and TLS connection for live stream hosts to eliminate handshake delay. */
        fun prefetchDns() {
            java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                runCatching {
                    val req = Request.Builder().url("$SITE_BASE/api/front/models?limit=1").head().build()
                    okHttpClient.newCall(req).execute().close()
                }
            }
        }
    }
}
