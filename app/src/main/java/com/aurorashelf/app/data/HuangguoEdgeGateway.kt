package com.aurorashelf.app.data

import android.util.Log
import com.aurorashelf.app.model.FeedCategory
import com.aurorashelf.app.model.VideoItem
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 黄果短剧 Edge 统一信封网关客户端。
 *
 * 站点将所有业务接口收敛于 `POST /api/edge/request`，
 * 通过 JSON 信封直接交互，避免下载臃肿的 HTML 网页，响应耗时通常在 300~450ms 内完成，
 * 大幅削减传输体积（从 215 KB HTML 降至 12 KB 纯净 JSON）。
 */
object HuangguoEdgeGateway {
    private const val TAG = "HuangguoEdgeGateway"
    const val DEFAULT_ORIGIN = "https://huangguoai.com"
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    internal var client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * 执行底层 Edge 信封请求并返回响应 body 字符串。
     */
    fun executeEdgeRequest(
        origin: String = DEFAULT_ORIGIN,
        method: String,
        path: String,
        body: String = "",
        contentType: String = "",
    ): String? {
        val normalizedOrigin = origin.trimEnd('/')
        val envelope = JSONObject().apply {
            put("method", method)
            put("path", path)
            put("body", body)
            put("content_type", contentType)
        }
        val requestUrl = "$normalizedOrigin/api/edge/request"
        val request = Request.Builder()
            .url(requestUrl)
            .post(envelope.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Referer", "$normalizedOrigin/")
            .header("Origin", normalizedOrigin)
            .header("User-Agent", USER_AGENT)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "edge request failed status=${response.code} path=$path")
                    null
                } else {
                    response.body?.string()
                }
            }
        }.onFailure {
            Log.w(TAG, "edge request exception path=$path message=${it.message}")
        }.getOrNull()
    }

    /**
     * 单次请求短剧全集目录，并一并提取第一集播放流（如果上游已直接下发）。
     */
    fun fetchEpisodesAndFirstPlay(
        origin: String = DEFAULT_ORIGIN,
        dramaId: String,
    ): Pair<List<PlaybackEpisode>, String?>? {
        val normalizedOrigin = origin.trimEnd('/')
        val jsonStr = executeEdgeRequest(
            origin = normalizedOrigin,
            method = "GET",
            path = "/api/videos/$dramaId",
        ) ?: return null

        return runCatching {
            val root = JSONObject(jsonStr)
            val data = root.optJSONObject("data") ?: return null
            val episodesArray = data.optJSONArray("episodes")
            val episodesList = mutableListOf<PlaybackEpisode>()

            if (episodesArray != null) {
                for (i in 0 until episodesArray.length()) {
                    val epObj = episodesArray.getJSONObject(i)
                    val epNum = epObj.optInt("ep_num", i + 1)
                    val epUrl = if (epNum == 1) {
                        "$normalizedOrigin/video/$dramaId/"
                    } else {
                        "$normalizedOrigin/video/$dramaId/ep-$epNum/"
                    }
                    episodesList.add(PlaybackEpisode(epNum, "第 $epNum 集", epUrl))
                }
            }

            if (episodesList.isEmpty()) {
                val totalEp = data.optInt("episode_count", 0)
                if (totalEp > 0) {
                    for (epNum in 1..totalEp) {
                        val epUrl = if (epNum == 1) {
                            "$normalizedOrigin/video/$dramaId/"
                        } else {
                            "$normalizedOrigin/video/$dramaId/ep-$epNum/"
                        }
                        episodesList.add(PlaybackEpisode(epNum, "第 $epNum 集", epUrl))
                    }
                }
            }

            var firstVideoUrl: String? = data.optString("video_url").takeIf { it.isNotBlank() }
            if (firstVideoUrl.isNullOrBlank()) {
                firstVideoUrl = data.optString("video_url_hevc").takeIf { it.isNotBlank() }
            }

            Pair(episodesList, firstVideoUrl)
        }.onFailure {
            Log.w(TAG, "failed parsing episodes for dramaId=$dramaId", it)
        }.getOrNull()
    }

    /**
     * 获取指定分集的真实 M3U8 播放直链。
     */
    fun fetchEpisodePlayUrl(
        origin: String = DEFAULT_ORIGIN,
        dramaId: String,
        episodeNum: Int,
    ): String? {
        val normalizedOrigin = origin.trimEnd('/')
        val jsonStr = executeEdgeRequest(
            origin = normalizedOrigin,
            method = "GET",
            path = "/api/videos/$dramaId/play?ep=$episodeNum&page_texts=0",
        ) ?: return null

        return runCatching {
            val root = JSONObject(jsonStr)
            val data = root.optJSONObject("data") ?: return null
            var url = data.optString("video_url").takeIf { it.isNotBlank() }
            if (url.isNullOrBlank()) {
                url = data.optString("video_url_hevc").takeIf { it.isNotBlank() }
            }
            url
        }.onFailure {
            Log.w(TAG, "failed parsing play url dramaId=$dramaId ep=$episodeNum", it)
        }.getOrNull()
    }

    /**
     * 获取黄果短剧分类流或推荐流。
     */
    fun fetchFeed(
        origin: String = DEFAULT_ORIGIN,
        category: FeedCategory,
        page: Int,
        size: Int = 20,
    ): List<VideoItem>? {
        val normalizedOrigin = origin.trimEnd('/')
        val p = page.coerceAtLeast(1)
        val path = when (category) {
            FeedCategory.CURRENT_HOT -> "/api/ranks/hot"
            FeedCategory.MONTHLY_HOT -> "/api/videos/category/ai-manju?page=$p&size=$size"
            FeedCategory.FAVORITES -> "/api/videos/category/ai-huanlian?page=$p&size=$size"
            FeedCategory.RECENT -> "/api/videos?page=$p&size=$size"
            FeedCategory.ALL -> "/api/videos?page=$p&size=$size"
            else -> "/api/videos?page=$p&size=$size"
        }

        val jsonStr = executeEdgeRequest(
            origin = normalizedOrigin,
            method = "GET",
            path = path,
        ) ?: return null

        return runCatching {
            val root = JSONObject(jsonStr)
            val data = root.optJSONObject("data") ?: return null
            val itemsArray = data.optJSONArray("items") ?: return null
            val result = mutableListOf<VideoItem>()

            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.getJSONObject(i)
                val idNum = item.optLong("video_id", item.optLong("id", 0L))
                if (idNum <= 0L) continue
                val dramaId = idNum.toString()
                val title = item.optString("title", "黄果短剧")
                val cover = item.optString("cover")
                val desc = item.optString("description")
                val duration = item.optString("duration")
                val totalEp = item.optInt("episode_count", 0)
                val epText = if (totalEp > 0) "全 $totalEp 集" else "短剧"

                result.add(
                    VideoItem(
                        id = "hg-$dramaId",
                        title = title,
                        author = desc.ifBlank { "黄果短剧 · $epText" },
                        duration = duration,
                        views = epText,
                        pageUrl = "$normalizedOrigin/video/$dramaId/",
                        thumbnailUrl = cover,
                    )
                )
            }
            result
        }.onFailure {
            Log.w(TAG, "failed parsing feed category=$category page=$page", it)
        }.getOrNull()
    }

    /**
     * 搜索短剧。
     */
    fun search(
        origin: String = DEFAULT_ORIGIN,
        query: String,
        page: Int = 1,
        size: Int = 20,
    ): List<VideoItem>? {
        val normalizedOrigin = origin.trimEnd('/')
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val path = "/api/search?q=$encodedQuery&page=${page.coerceAtLeast(1)}&size=$size"
        val jsonStr = executeEdgeRequest(
            origin = normalizedOrigin,
            method = "GET",
            path = path,
        ) ?: return null

        return runCatching {
            val root = JSONObject(jsonStr)
            val data = root.optJSONObject("data") ?: return null
            val itemsArray = data.optJSONArray("items") ?: return null
            val result = mutableListOf<VideoItem>()

            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.getJSONObject(i)
                val idNum = item.optLong("id", item.optLong("video_id", 0L))
                if (idNum <= 0L) continue
                val dramaId = idNum.toString()
                val title = item.optString("title", "黄果短剧")
                val cover = item.optString("cover")
                val desc = item.optString("description")
                val totalEp = item.optInt("episode_count", 0)
                val epText = if (totalEp > 0) "全 $totalEp 集" else "短剧"

                result.add(
                    VideoItem(
                        id = "hg-$dramaId",
                        title = title,
                        author = desc.ifBlank { "黄果短剧 · $epText" },
                        duration = item.optString("duration"),
                        views = epText,
                        pageUrl = "$normalizedOrigin/video/$dramaId/",
                        thumbnailUrl = cover,
                    )
                )
            }
            result
        }.onFailure {
            Log.w(TAG, "failed parsing search query=$query", it)
        }.getOrNull()
    }
}
