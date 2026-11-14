package com.aurorashelf.app.data

import com.aurorashelf.app.model.FeedCategory
import com.aurorashelf.app.model.VideoItem
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native TanStack Start / Seroval API client for https://134x.com.
 *
 * Uses `_serverFn` endpoints (`latest`, `popular`, `featured`, `search`, and per-tweet metadata `za`)
 * over a pooled HTTP client, with automatic `AppShell-*.js` hash refresh and SSR HTML fallback.
 */
internal object X134Repository {
    const val BASE_URL = "https://134x.com"
    private const val META_CACHE_TTL_MS = 15 * 60 * 1000L
    private const val META_CACHE_MAX = 512
    private const val CURSOR_CACHE_MAX = 128

    internal data class ServerFnHashes(
        val latest: String,
        val ranking: String,
        val search: String,
        val detail: String,
    )

    internal data class TweetMetadata(
        val posterUrl: String?,
        val hlsUrl: String?,
        val mp4Url: String?,
        val updatedAtMs: Long = System.currentTimeMillis(),
    )

    internal val DEFAULT_HASHES = ServerFnHashes(
        latest = "7cd2fdc202217221aea24ce98c8911133744658e2b912656f645ea53849d6f17",
        ranking = "25c25b734ef2228ea3f8f49f4067988ab39ff3e50799a02452f080931c889002",
        search = "51bd2bec8cfd50e218d50fb96a0f5a94bbdeb6e6835dec38fab33818af78324d",
        detail = "9c9bc9391d885f2f66683866157820d7de7d05082825aa01d1999a181d3c1e99",
    )

    @Volatile
    private var activeHashes: ServerFnHashes = DEFAULT_HASHES
    private val hashRefreshLock = Any()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val metaCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, TweetMetadata>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TweetMetadata>?): Boolean =
                size > META_CACHE_MAX
        }
    )

    private val nextCursorCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
                size > CURSOR_CACHE_MAX
        }
    )

    suspend fun loadFeed(category: FeedCategory, page: Int): List<VideoItem> = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        val ranking = when (category) {
            FeedCategory.CURRENT_HOT -> "popular"
            FeedCategory.MONTHLY_HOT -> "featured"
            else -> null
        }
        val modeKey = ranking ?: "latest"
        val args = LinkedHashMap<String, Any?>().apply {
            put("page", safePage)
            if (ranking != null) {
                put("ranking", ranking)
            }
            if (safePage > 1) {
                nextCursorCache["$modeKey:${safePage - 1}"]?.let { cursor ->
                    put("cursor", cursor)
                    put("direction", "next")
                }
            }
        }

        val result = callServerFnWithRefresh(
            fnSelector = { if (ranking == null) it.latest else it.ranking },
            data = args,
        )
        val nextCursor = (result["nextCursor"] as? String)?.takeIf { it.isNotBlank() }
        if (nextCursor != null) {
            nextCursorCache["$modeKey:$safePage"] = nextCursor
        }
        val rawTweets = asTweetList(result["tweets"])
        val hydrated = hydrateTweets(rawTweets)
        val items = parseFeedItems(rawTweets, hydrated)
        if (safePage == 1 && items.isEmpty()) {
            throw IOException("134X 视频源已连接，但当前分类没有返回视频")
        }
        items
    }

    suspend fun search(query: String, page: Int): List<VideoItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        require(trimmed.isNotBlank()) { "请输入搜索内容" }
        val safePage = page.coerceAtLeast(1)
        val args = mapOf<String, Any?>(
            "query" to trimmed,
            "page" to safePage,
        )
        val result = callServerFnWithRefresh(
            fnSelector = { it.search },
            data = args,
        )
        val rawTweets = asTweetList(result["tweets"])
        val hydrated = hydrateTweets(rawTweets)
        parseFeedItems(rawTweets, hydrated)
    }

    suspend fun resolvePlayback(pageUrl: String): PlaybackSource = withContext(Dispatchers.IO) {
        val tweetId = extractTweetId(pageUrl)
            ?: throw IOException("无法识别 134X 视频编号: $pageUrl")

        val cachedMeta = getFreshMeta(tweetId)
        val cachedStream = cachedMeta?.hlsUrl?.takeIf { it.isNotBlank() }
            ?: cachedMeta?.mp4Url?.takeIf { it.isNotBlank() }
        if (cachedStream != null) {
            return@withContext PlaybackSource(cachedStream, referer = "$BASE_URL/")
        }

        val fetchedMeta = runCatching { fetchTweetMetadata(tweetId) }.getOrNull()
        val fetchedStream = fetchedMeta?.hlsUrl?.takeIf { it.isNotBlank() }
            ?: fetchedMeta?.mp4Url?.takeIf { it.isNotBlank() }
        if (fetchedStream != null) {
            return@withContext PlaybackSource(fetchedStream, referer = "$BASE_URL/")
        }

        // Fallback: parse SSR detail HTML directly in case `_serverFn` changed unexpectedly.
        val htmlStream = resolveStreamFromDetailHtml(tweetId)
        if (htmlStream != null) {
            return@withContext PlaybackSource(htmlStream, referer = "$BASE_URL/")
        }

        throw IOException("无法解析 134X 视频播放地址")
    }

    private suspend fun hydrateTweets(tweets: List<Map<String, Any?>>): Map<String, TweetMetadata> =
        coroutineScope {
            val semaphore = Semaphore(6)
            tweets.mapNotNull { tweet ->
                val tweetId = (tweet["tweetId"] as? String)?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val listMp4 = (tweet["media"] as? String)?.trim()?.takeIf { it.startsWith("http") }
                val existingPoster = (tweet["posterUrl"] as? String)?.trim()?.takeIf { it.isNotBlank() }
                val existingHls = extractFirstNonBlankStringValue(tweet["hlsUrls"])
                val existingMeta = getFreshMeta(tweetId)

                async {
                    if (existingPoster != null) {
                        val merged = TweetMetadata(
                            posterUrl = existingPoster,
                            hlsUrl = existingHls ?: existingMeta?.hlsUrl,
                            mp4Url = listMp4 ?: existingMeta?.mp4Url,
                        )
                        metaCache[tweetId] = merged
                        return@async tweetId to merged
                    }
                    if (existingMeta?.posterUrl != null) {
                        val merged = existingMeta.copy(mp4Url = listMp4 ?: existingMeta.mp4Url)
                        metaCache[tweetId] = merged
                        return@async tweetId to merged
                    }
                    semaphore.withPermit {
                        val meta = withTimeoutOrNull(4_500L) {
                            runCatching { fetchTweetMetadata(tweetId, listMp4) }.getOrNull()
                        } ?: TweetMetadata(posterUrl = null, hlsUrl = null, mp4Url = listMp4)
                        metaCache[tweetId] = meta
                        tweetId to meta
                    }
                }
            }.awaitAll().toMap()
        }

    private fun fetchTweetMetadata(tweetId: String, fallbackMp4: String? = null): TweetMetadata? {
        val result = callServerFnWithRefresh(
            fnSelector = { it.detail },
            data = mapOf("tweetId" to tweetId),
        )
        val posterUrl = (result["posterUrl"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        val hlsMap = asStringAnyMap(result["hlsUrls"])
        val hlsUrl = hlsMap?.values?.firstNotNullOfOrNull { (it as? String)?.trim()?.takeIf(String::isNotBlank) }
        val hlsKeyMp4 = hlsMap?.keys?.firstOrNull { it.trim().isNotBlank() }?.trim()
        val variantsMp4 = extractBestVariantMp4(result["videoVariants"])
        val prev = getFreshMeta(tweetId)
        val merged = TweetMetadata(
            posterUrl = posterUrl ?: prev?.posterUrl,
            hlsUrl = hlsUrl ?: prev?.hlsUrl,
            mp4Url = hlsKeyMp4 ?: variantsMp4 ?: fallbackMp4 ?: prev?.mp4Url,
        )
        metaCache[tweetId] = merged
        return merged
    }

    private fun extractBestVariantMp4(variantsNode: Any?): String? {
        val map = asStringAnyMap(variantsNode) ?: return null
        var bestUrl: String? = null
        var bestScore = -1L
        for (value in map.values) {
            val list = value as? List<*> ?: continue
            for (entry in list) {
                val item = asStringAnyMap(entry) ?: continue
                val url = (item["url"] as? String)?.trim()?.takeIf { it.startsWith("http") } ?: continue
                val width = (item["width"] as? Number)?.toLong() ?: 0L
                val bitrate = (item["bitrate"] as? Number)?.toLong() ?: 0L
                val score = width * 1_000_000L + bitrate
                if (score > bestScore) {
                    bestScore = score
                    bestUrl = url
                }
            }
        }
        return bestUrl
    }

    private fun getFreshMeta(tweetId: String): TweetMetadata? {
        val cached = metaCache[tweetId] ?: return null
        if (System.currentTimeMillis() - cached.updatedAtMs > META_CACHE_TTL_MS) {
            metaCache.remove(tweetId)
            return null
        }
        return cached
    }

    private fun callServerFnWithRefresh(
        fnSelector: (ServerFnHashes) -> String,
        data: Map<String, Any?>,
    ): Map<String, Any?> {
        val currentHash = fnSelector(activeHashes)
        try {
            return executeServerFn(currentHash, data)
        } catch (firstError: HttpStatusFailure) {
            if (firstError.code != 404 && firstError.code != 400 && firstError.code != 500) {
                throw firstError
            }
            val refreshed = refreshServerFnHashes()
            val nextHash = refreshed?.let(fnSelector)
            if (nextHash == null || nextHash == currentHash) {
                throw firstError
            }
            return executeServerFn(nextHash, data)
        }
    }

    private fun executeServerFn(fnId: String, data: Map<String, Any?>): Map<String, Any?> {
        val payload = encodePayload(mapOf("data" to data))
        val encodedPayload = URLEncoder.encode(payload, StandardCharsets.UTF_8.name())
        val url = "$BASE_URL/_serverFn/$fnId?payload=$encodedPayload"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", VideoRepository.USER_AGENT)
            .header("Accept", "application/json")
            .header("Referer", "$BASE_URL/")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Dest", "empty")
            .header("x-tsr-serverFn", "true")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw HttpStatusFailure(response.code, "134X 接口请求失败 (HTTP ${response.code})")
            }
            val decoded = decodeSeroval(body)
            val root = asStringAnyMap(decoded)
                ?: throw IOException("134X 接口返回格式异常")
            val error = root["error"]
            if (error != null) {
                throw IOException("134X 接口返回错误: $error")
            }
            return asStringAnyMap(root["result"])
                ?: throw IOException("134X 接口未包含 result 字段")
        }
    }

    private fun refreshServerFnHashes(): ServerFnHashes? = synchronized(hashRefreshLock) {
        return@synchronized runCatching {
            val homeReq = Request.Builder()
                .url("$BASE_URL/")
                .header("User-Agent", VideoRepository.USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .build()
            val homeHtml = client.newCall(homeReq).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string().orEmpty()
            }
            val appShellPath = Regex("""["']/?(assets/AppShell-[A-Za-z0-9_-]+\.js)["']""")
                .find(homeHtml)?.groupValues?.get(1) ?: return@runCatching null
            val appShellUrl = "$BASE_URL/${appShellPath.trimStart('/')}"
            val jsReq = Request.Builder()
                .url(appShellUrl)
                .header("User-Agent", VideoRepository.USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .build()
            val jsText = client.newCall(jsReq).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string().orEmpty()
            }
            extractHashesFromAppShell(jsText)?.also { activeHashes = it }
        }.getOrNull()
    }

    private fun resolveStreamFromDetailHtml(tweetId: String): String? = runCatching {
        val req = Request.Builder()
            .url("$BASE_URL/video/$tweetId")
            .header("User-Agent", VideoRepository.USER_AGENT)
            .header("Referer", "$BASE_URL/")
            .build()
        val html = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            resp.body?.string().orEmpty()
        }
        val m3u8 = Regex("""https://video\.twimg\.com/[^"'\s<>\\]+\.m3u8[^"'\s<>\\]*""")
            .find(html)?.value
        if (!m3u8.isNullOrBlank()) return@runCatching m3u8
        Regex("""https://video\.twimg\.com/[^"'\s<>\\]+\.mp4[^"'\s<>\\]*""")
            .find(html)?.value
    }.getOrNull()

    internal fun extractHashesFromAppShell(jsText: String): ServerFnHashes? {
        val blockMatch = Regex("""handler\([A-Za-z0-9_$]+\([`"']([0-9a-f]{64})[`"']\)\)""")
        val ordered = blockMatch.findAll(jsText).map { it.groupValues[1] }.toList()
        if (ordered.size < 8) return null
        return ServerFnHashes(
            latest = ordered[1],
            ranking = ordered[3],
            search = ordered[4],
            detail = ordered[7],
        )
    }

    internal fun parseFeedItems(
        rawTweets: List<Map<String, Any?>>,
        metadataByTweetId: Map<String, TweetMetadata> = emptyMap(),
    ): List<VideoItem> {
        val items = mutableListOf<VideoItem>()
        for (tweet in rawTweets) {
            val tweetId = (tweet["tweetId"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: continue
            val name = (tweet["name"] as? String)?.trim().orEmpty()
            val screenName = (tweet["screenName"] as? String)?.trim().orEmpty()
            val author = when {
                name.isNotBlank() -> name
                screenName.isNotBlank() -> "@$screenName"
                else -> "134X"
            }
            val rawText = (tweet["text"] as? String).orEmpty()
            val title = cleanTweetTitle(rawText, author)
            val durationMs = (tweet["durationMillis"] as? Number)?.toLong() ?: 0L
            val playTimes = (tweet["playTimes"] as? Number)?.toLong() ?: 0L
            val meta = metadataByTweetId[tweetId] ?: metaCache[tweetId]
            val avatarFallback = (tweet["profileImage"] as? String)?.trim()
                ?.replace("_normal.", "_400x400.")
                ?.takeIf { it.startsWith("http") }
            val thumbnailUrl = meta?.posterUrl
                ?: (tweet["posterUrl"] as? String)?.trim()?.takeIf { it.startsWith("http") }
                ?: avatarFallback

            items += VideoItem(
                id = "134x-$tweetId",
                title = title,
                author = author,
                duration = formatDurationMillis(durationMs),
                views = formatPlayTimes(playTimes),
                pageUrl = "$BASE_URL/video/$tweetId",
                thumbnailUrl = thumbnailUrl,
            )
        }
        return items.distinctBy(VideoItem::pageUrl)
    }

    internal fun extractTweetId(pageUrl: String): String? {
        val match = Regex("""(?:134x\.com/video/|tweetId=)(\d+)""").find(pageUrl)
            ?: Regex("""/(\d{10,})(?:[/?#]|$)""").find(pageUrl)
        return match?.groupValues?.get(1)
    }

    internal fun cleanTweetTitle(rawText: String, fallbackAuthor: String): String {
        val stripped = rawText
            .replace(Regex("""\\r\\n|\\n|\\r|\\t"""), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace(Regex("""https?://t\.co/\S+"""), " ")
            .replace(Regex("""[\r\n\t]+"""), " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        return stripped.ifBlank { fallbackAuthor.ifBlank { "134X 视频" } }
    }

    internal fun formatDurationMillis(durationMillis: Long): String {
        if (durationMillis <= 0L) return "--:--"
        val totalSeconds = durationMillis / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    internal fun formatPlayTimes(playTimes: Long): String = when {
        playTimes <= 0L -> "长视频 · 134X"
        playTimes >= 10_000L -> String.format(Locale.US, "%.1f万 次播放", playTimes / 10_000.0)
        else -> "$playTimes 次播放"
    }

    // MARK: - Seroval Wire Codec

    internal fun encodePayload(value: Map<String, Any?>): String {
        val counter = AtomicInteger(0)
        val root = encodeNode(value, counter)
        return JSONObject().apply {
            put("t", root)
            put("f", 127)
            put("m", JSONArray())
        }.toString()
    }

    private fun encodeNode(value: Any?, counter: AtomicInteger): JSONObject = when (value) {
        null -> JSONObject().apply {
            put("t", 2)
            put("s", 0)
        }
        is Boolean -> JSONObject().apply {
            put("t", 2)
            put("s", if (value) 2 else 3)
        }
        is Number -> JSONObject().apply {
            put("t", 0)
            put("s", value)
        }
        is String -> JSONObject().apply {
            put("t", 1)
            put("s", value)
        }
        is List<*> -> {
            val id = counter.getAndIncrement()
            val arr = JSONArray()
            for (item in value) {
                arr.put(encodeNode(item, counter))
            }
            JSONObject().apply {
                put("t", 9)
                put("i", id)
                put("a", arr)
                put("o", 0)
            }
        }
        is Map<*, *> -> {
            val id = counter.getAndIncrement()
            val keys = JSONArray()
            val values = JSONArray()
            for ((k, v) in value) {
                keys.put(k?.toString().orEmpty())
                values.put(encodeNode(v, counter))
            }
            val props = JSONObject().apply {
                put("k", keys)
                put("v", values)
            }
            JSONObject().apply {
                put("t", 10)
                put("i", id)
                put("p", props)
                put("o", 0)
            }
        }
        else -> JSONObject().apply {
            put("t", 1)
            put("s", value.toString())
        }
    }

    internal fun decodeSeroval(jsonText: String): Any? {
        val rootObj = JSONObject(jsonText)
        val refs = HashMap<Int, Any?>()
        return decodeNode(rootObj, refs)
    }

    private fun decodeNode(node: Any?, refs: MutableMap<Int, Any?>): Any? {
        if (node == null || node === JSONObject.NULL) return null
        if (node !is JSONObject) return node
        val type = node.optInt("t", -1)
        return when (type) {
            0 -> node.opt("s")?.takeUnless { it === JSONObject.NULL }
            1 -> node.opt("s")?.takeUnless { it === JSONObject.NULL }?.toString()?.let(::unescapeSerovalString)
            2 -> when (node.optInt("s", -1)) {
                0, 1 -> null
                2 -> true
                3 -> false
                else -> null
            }
            4 -> {
                val refId = node.optInt("i", -1)
                if (refId >= 0) refs[refId] else null
            }
            9 -> {
                val list = ArrayList<Any?>()
                if (node.has("i")) {
                    refs[node.optInt("i")] = list
                }
                val arr = node.optJSONArray("a")
                if (arr != null) {
                    for (idx in 0 until arr.length()) {
                        list.add(decodeNode(arr.opt(idx), refs))
                    }
                }
                list
            }
            10, 11 -> {
                val map = LinkedHashMap<String, Any?>()
                if (node.has("i")) {
                    refs[node.optInt("i")] = map
                }
                val p = node.optJSONObject("p")
                val keys = p?.optJSONArray("k")
                val values = p?.optJSONArray("v")
                if (keys != null && values != null) {
                    val count = minOf(keys.length(), values.length())
                    for (idx in 0 until count) {
                        val key = unescapeSerovalString(keys.optString(idx))
                        map[key] = decodeNode(values.opt(idx), refs)
                    }
                }
                map
            }
            else -> if (node.has("s")) {
                val raw = node.opt("s")
                if (raw is String) unescapeSerovalString(raw) else raw
            } else null
        }
    }

    internal fun unescapeSerovalString(raw: String): String {
        if ('\\' !in raw) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    'n' -> { out.append('\n'); i += 2 }
                    'r' -> { out.append('\r'); i += 2 }
                    't' -> { out.append('\t'); i += 2 }
                    'b' -> { out.append('\b'); i += 2 }
                    'f' -> { out.append('\u000C'); i += 2 }
                    '\\' -> { out.append('\\'); i += 2 }
                    '"' -> { out.append('"'); i += 2 }
                    '\'' -> { out.append('\''); i += 2 }
                    'u' -> {
                        val hex = if (i + 5 < raw.length) raw.substring(i + 2, i + 6).toIntOrNull(16) else null
                        if (hex != null) {
                            out.append(hex.toChar())
                            i += 6
                        } else {
                            out.append(next)
                            i += 2
                        }
                    }
                    'x' -> {
                        val hex = if (i + 3 < raw.length) raw.substring(i + 2, i + 4).toIntOrNull(16) else null
                        if (hex != null) {
                            out.append(hex.toChar())
                            i += 4
                        } else {
                            out.append(next)
                            i += 2
                        }
                    }
                    else -> { out.append(next); i += 2 }
                }
            } else {
                out.append(ch)
                i += 1
            }
        }
        return out.toString()
    }

    private fun asTweetList(value: Any?): List<Map<String, Any?>> {
        val list = value as? List<*> ?: return emptyList()
        return list.mapNotNull { asStringAnyMap(it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun asStringAnyMap(value: Any?): Map<String, Any?>? = value as? Map<String, Any?>

    private fun extractFirstNonBlankStringValue(node: Any?): String? {
        val map = asStringAnyMap(node) ?: return null
        return map.values.firstNotNullOfOrNull { (it as? String)?.trim()?.takeIf(String::isNotBlank) }
    }

    private class HttpStatusFailure(val code: Int, message: String) : IOException(message)
}
