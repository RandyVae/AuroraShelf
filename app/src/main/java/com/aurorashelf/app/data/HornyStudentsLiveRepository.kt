package com.aurorashelf.app.data

import com.aurorashelf.app.model.LiveCategory
import com.aurorashelf.app.model.LiveItem
import com.aurorashelf.app.model.LivePage
import okhttp3.Request
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

class HornyStudentsLiveRepository {
    private val memoryCacheLock = Any()
    @Volatile private var cachedItems: List<LiveItemRaw>? = null
    @Volatile private var cacheTimestamp = 0L

    suspend fun load(category: LiveCategory, page: Int): LivePage = withContext(Dispatchers.IO) {
        require(page >= 1) { "页码必须从 1 开始" }
        val allRaw = getCachedOrFetch()
        val filtered = filterByCategory(allRaw, category)
        val pageSize = PAGE_SIZE
        val fromIndex = (page - 1) * pageSize
        val pagedItems = if (fromIndex < filtered.size) {
            val toIndex = minOf(fromIndex + pageSize, filtered.size)
            filtered.subList(fromIndex, toIndex).map { it.toLiveItem() }
        } else {
            emptyList()
        }
        LivePage(
            items = pagedItems,
            totalCount = filtered.size,
        )
    }

    private fun getCachedOrFetch(): List<LiveItemRaw> {
        val now = System.currentTimeMillis()
        cachedItems?.let {
            if (now - cacheTimestamp < CACHE_TTL_MS) return it
        }
        synchronized(memoryCacheLock) {
            cachedItems?.let {
                if (now - cacheTimestamp < CACHE_TTL_MS) return it
            }
            try {
                val xml = request(FEED_URL)
                val parsed = parseFeed(xml)
                if (parsed.isNotEmpty()) {
                    cachedItems = parsed
                    cacheTimestamp = System.currentTimeMillis()
                    return parsed
                }
            } catch (e: Exception) {
                // 网络波动或超时优先使用内存快照平滑兜底，杜绝阻断
                cachedItems?.let { return it }
                throw e
            }
            return cachedItems ?: emptyList()
        }
    }

    internal fun parseFeed(xml: String): List<LiveItemRaw> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val items = doc.select("item")
        return items.mapNotNull { item ->
            val username = item.selectFirst("cb|username")?.text()?.trim()
                ?: item.selectFirst("username")?.text()?.trim()
                ?: item.selectFirst("title")?.text()?.trim()
                ?: return@mapNotNull null
            if (username.isBlank() || !USERNAME_PATTERN.matches(username)) return@mapNotNull null
            val gender = item.selectFirst("cb|gender")?.text()?.trim()
                ?: item.selectFirst("gender")?.text()?.trim().orEmpty()
            val imageUrl = item.selectFirst("cb|image_url")?.text()?.trim()
                ?: item.selectFirst("image_url")?.text()?.trim().orEmpty()
            val viewers = item.selectFirst("cb|number_of_users_watching")?.text()?.trim()?.toIntOrNull()
                ?: item.selectFirst("number_of_users_watching")?.text()?.trim()?.toIntOrNull()
                ?: 0
            val location = item.selectFirst("cb|location")?.text()?.trim()
                ?: item.selectFirst("location")?.text()?.trim().orEmpty()
            val isPublic = item.selectFirst("cb|current_show")?.text()?.trim()
                ?: item.selectFirst("current_show")?.text()?.trim().orEmpty()
            if (isPublic.isNotBlank() && isPublic != "public") return@mapNotNull null

            LiveItemRaw(
                username = username,
                gender = gender.lowercase(),
                thumbnailUrl = imageUrl.ifBlank { "https://thumb.live.mmcdn.com/ri/$username.jpg" },
                viewersCount = viewers,
                countryCode = extractCountryCode(location),
            )
        }
    }

    internal fun filterByCategory(items: List<LiveItemRaw>, category: LiveCategory): List<LiveItemRaw> = items.filter { item ->
        when (category) {
            LiveCategory.WOMEN -> item.gender == "f"
            LiveCategory.COUPLES -> item.gender == "c"
            LiveCategory.MEN -> item.gender == "m"
            LiveCategory.TRANS -> item.gender == "s" || item.gender == "t"
        }
    }

    private fun request(address: String): String {
        val req = Request.Builder()
            .url(address)
            .header("Accept", "application/xml, text/xml, */*")
            .header("User-Agent", VideoRepository.USER_AGENT)
            .build()
        StripchatLiveRepository.okHttpClient.newCall(req).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HornyStudents 接口返回 HTTP ${response.code}")
            if (body.isBlank()) throw IOException("HornyStudents 接口返回空内容")
            return body
        }
    }

    private fun extractCountryCode(location: String): String {
        val trimmed = location.trim()
        if (trimmed.length == 2 && trimmed.all(Char::isLetter)) return trimmed.uppercase()
        val lastPart = trimmed.substringAfterLast(',').trim()
        if (lastPart.length == 2 && lastPart.all(Char::isLetter)) return lastPart.uppercase()
        return ""
    }

    internal data class LiveItemRaw(
        val username: String,
        val gender: String,
        val thumbnailUrl: String,
        val viewersCount: Int,
        val countryCode: String,
    ) {
        fun toLiveItem(): LiveItem = LiveItem(
            id = "hs-$username",
            username = username,
            thumbnailUrl = thumbnailUrl,
            viewersCount = viewersCount,
            countryCode = countryCode,
            isHd = true,
            playerUrl = "$SITE_BASE/$username",
        )
    }

    companion object {
        const val SITE_BASE = "https://www.hornystudents.com"
        private const val FEED_URL = "$SITE_BASE/feed/latest/"
        internal const val PAGE_SIZE = 30
        private const val CACHE_TTL_MS = 60_000L
        private const val REQUEST_TIMEOUT_MS = 15_000
        private val USERNAME_PATTERN = Regex("[A-Za-z0-9_-]{1,80}")

        fun prefetchDns() {
            java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                runCatching {
                    java.net.InetAddress.getAllByName("www.hornystudents.com")
                    java.net.InetAddress.getAllByName("thumb.live.mmcdn.com")
                }
            }
        }
    }
}
