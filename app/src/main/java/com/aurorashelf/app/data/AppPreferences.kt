package com.aurorashelf.app.data

import android.content.Context
import androidx.core.content.edit
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.ForumPost
import com.aurorashelf.app.model.VideoItem
import org.json.JSONArray
import org.json.JSONObject

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun cachedFeed(): CachedFeed? = runCatching {
        decodeFeedCache(preferences.getString(KEY_FEED_CACHE, "").orEmpty())
    }.getOrNull()

    /**
     * Persist the last successfully loaded first page so the next cold start can paint
     * immediately instead of waiting for the network.
     */
    fun saveFeedCache(sourceUrl: String, categoryPath: String, videos: List<VideoItem>) {
        val feed = CachedFeed(
            sourceUrl = normalizeBaseUrl(sourceUrl),
            categoryPath = categoryPath,
            videos = videos.distinctBy(VideoItem::id).take(MAX_FEED_CACHE_ITEMS),
            savedAtMillis = System.currentTimeMillis(),
        )
        preferences.edit { putString(KEY_FEED_CACHE, encodeFeedCache(feed)) }
    }

    /** Forum first-page snapshot for instant first paint; ignored when older than 12 h. */
    fun cachedForumFirstPage(): CachedForum? = runCatching {
        decodeForumCache(preferences.getString(KEY_FORUM_CACHE, "").orEmpty())
    }.getOrNull()?.takeIf { isForumCacheFresh(it.savedAtMillis) }

    /**
     * Persist the last successfully loaded first forum page. The next visit paints it immediately
     * and then refreshes from the network; a failed refresh keeps the snapshot on screen.
     */
    fun saveForumFirstPage(posts: List<ForumPost>) {
        val retained = posts
            .filter { it.sourceId.isNotBlank() && it.id.isNotBlank() }
            .distinctBy { it.sourceId to it.id }
            .take(MAX_FORUM_CACHE_ITEMS)
        if (retained.isEmpty()) return
        val cache = CachedForum(posts = retained, savedAtMillis = System.currentTimeMillis())
        preferences.edit { putString(KEY_FORUM_CACHE, encodeForumCache(cache)) }
    }

    fun savedVideos(): List<VideoItem> = runCatching {
        decodeSavedVideos(preferences.getString("saved_videos", "[]").orEmpty())
    }.getOrElse {
        android.util.Log.w("AppPreferences", "Saved library could not be decoded", it)
        emptyList()
    }

    fun saveVideo(video: VideoItem) {
        if (video.id.startsWith("demo-")) return
        val retainedIds = favoriteIds() + historyIds() + video.id
        val records = (listOf(video) + savedVideos()).distinctBy(VideoItem::id)
            .filter { it.id in retainedIds }
        val array = JSONArray()
        records.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id); put("title", item.title); put("author", item.author)
                put("duration", item.duration); put("views", item.views); put("pageUrl", item.pageUrl)
                put("thumbnailUrl", item.thumbnailUrl.orEmpty())
            })
        }
        preferences.edit { putString("saved_videos", array.toString()) }
    }

    var sourceUrl: String
        // Migrate the old empty-address demo setting without erasing real favorites/history.
        get() = normalizeBaseUrl(preferences.getString(KEY_SOURCE_URL, DEFAULT_SOURCE).orEmpty()).ifBlank { DEFAULT_SOURCE }
        set(value) = preferences.edit { putString(KEY_SOURCE_URL, normalizeBaseUrl(value).ifBlank { DEFAULT_SOURCE }) }

    var comicSourceId: String
        get() = preferences.getString(KEY_COMIC_SOURCE_ID, DEFAULT_COMIC_SOURCE_ID)
            ?.takeIf(String::isNotBlank)
            ?: DEFAULT_COMIC_SOURCE_ID
        set(value) = preferences.edit {
            putString(KEY_COMIC_SOURCE_ID, value.takeIf(String::isNotBlank) ?: DEFAULT_COMIC_SOURCE_ID)
        }

    /** Whether the player may warm detail pages / the next episode address on unmetered networks. */
    var videoPreloadEnabled: Boolean
        get() = preferences.getBoolean(KEY_VIDEO_PRELOAD_ENABLED, true)
        set(value) = preferences.edit { putBoolean(KEY_VIDEO_PRELOAD_ENABLED, value) }

    /** Whether the cinematic launch intro plays on a cold start (release builds only). */
    var introAnimationEnabled: Boolean
        get() = preferences.getBoolean(KEY_INTRO_ANIMATION_ENABLED, true)
        set(value) = preferences.edit { putBoolean(KEY_INTRO_ANIMATION_ENABLED, value) }

    /** Whether the launch intro plays its original synthesized sound design. */
    var introSoundEnabled: Boolean
        get() = preferences.getBoolean(KEY_INTRO_SOUND_ENABLED, true)
        set(value) = preferences.edit { putBoolean(KEY_INTRO_SOUND_ENABLED, value) }

    fun favoriteIds(): Set<String> =
        preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().filterNot { it.startsWith("demo-") }.toSet()

    fun toggleFavorite(id: String) {
        val updated = favoriteIds().toMutableSet()
        if (!updated.add(id)) updated.remove(id)
        preferences.edit { putStringSet(KEY_FAVORITES, updated) }
    }

    fun historyIds(): List<String> =
        preferences.getString(KEY_HISTORY, "")
            .orEmpty()
            .lineSequence()
            .filter(String::isNotBlank)
            .filterNot { it.startsWith("demo-") }
            .toList()

    fun addToHistory(id: String) {
        val updated = buildList {
            add(id)
            addAll(historyIds().filterNot { it == id })
        }.take(MAX_HISTORY)
        preferences.edit { putString(KEY_HISTORY, updated.joinToString("\n")) }
    }

    companion object {
        // Read from the supplied APK's address dialog; HTTPS verified on 2026-09-05.
        const val DEFAULT_SOURCE = ContentSourceCatalog.DEFAULT_BASE_URL
        const val DEFAULT_COMIC_SOURCE_ID = "jm"
        private const val PREFERENCES_NAME = "aurora_shelf"
        private const val KEY_SOURCE_URL = "source_url"
        private const val KEY_COMIC_SOURCE_ID = "comic_source_id"
        private const val KEY_FAVORITES = "favorite_ids"
        private const val KEY_HISTORY = "history_ids"
        private const val KEY_FEED_CACHE = "feed_cache"
        private const val KEY_FORUM_CACHE = "forum_cache"
        private const val KEY_VIDEO_PRELOAD_ENABLED = "video_preload_enabled"
        private const val KEY_INTRO_ANIMATION_ENABLED = "intro_animation_enabled"
        private const val KEY_INTRO_SOUND_ENABLED = "intro_sound_enabled"
        private const val MAX_HISTORY = 50
        internal const val MAX_FEED_CACHE_ITEMS = 30
        internal const val MAX_FORUM_CACHE_ITEMS = 40
        internal const val FORUM_CACHE_TTL_MS = 12 * 60 * 60 * 1000L

        fun normalizeBaseUrl(value: String): String = value.trim().trimEnd('/')
    }
}

/**
 * Last successfully loaded first page, kept only for instant first paint. Always followed by a
 * network refresh, and ignored when the source or category no longer matches.
 */
data class CachedFeed(
    val sourceUrl: String,
    val categoryPath: String,
    val videos: List<VideoItem>,
    val savedAtMillis: Long,
)

/** Last successfully loaded first forum page (both sources merged), used for instant first paint. */
data class CachedForum(
    val posts: List<ForumPost>,
    val savedAtMillis: Long,
)

/** True while [savedAtMillis] is a positive timestamp no older than the 12 h forum cache TTL. */
internal fun isForumCacheFresh(
    savedAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean = savedAtMillis > 0 && nowMillis - savedAtMillis in 0L..AppPreferences.FORUM_CACHE_TTL_MS

internal fun encodeFeedCache(feed: CachedFeed): String = JSONObject().apply {
    put("version", 1)
    put("sourceUrl", feed.sourceUrl)
    put("categoryPath", feed.categoryPath)
    put("savedAt", feed.savedAtMillis)
    put(
        "videos",
        JSONArray().apply {
            feed.videos.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id); put("title", item.title); put("author", item.author)
                    put("duration", item.duration); put("views", item.views); put("pageUrl", item.pageUrl)
                    put("thumbnailUrl", item.thumbnailUrl.orEmpty())
                })
            }
        },
    )
}.toString()

/**
 * Decodes a cached feed defensively: a malformed record is skipped, invalid URLs and demo
 * entries are dropped, and any structurally invalid payload returns null.
 */
internal fun decodeFeedCache(json: String): CachedFeed? = runCatching {
    if (json.isBlank()) return null
    val payload = JSONObject(json)
    val sourceUrl = AppPreferences.normalizeBaseUrl(payload.optString("sourceUrl"))
    val categoryPath = payload.optString("categoryPath").trim()
    if (sourceUrl.isBlank() || categoryPath.isBlank()) return null
    val array = payload.optJSONArray("videos") ?: return null
    val videos = (0 until array.length()).mapNotNull { index ->
        runCatching {
            val item = array.getJSONObject(index)
            VideoItem(
                id = item.getString("id"), title = item.getString("title"),
                author = item.getString("author"), duration = item.getString("duration"),
                views = item.getString("views"), pageUrl = item.getString("pageUrl"),
                thumbnailUrl = item.optString("thumbnailUrl").takeIf(String::isNotBlank),
            )
        }.getOrNull()
    }
        .filter { !it.id.startsWith("demo-") && SourceAddress.resolve(it.pageUrl, it.pageUrl) != null }
        .distinctBy(VideoItem::id)
        .take(AppPreferences.MAX_FEED_CACHE_ITEMS)
    if (videos.isEmpty()) return null
    CachedFeed(
        sourceUrl = sourceUrl,
        categoryPath = categoryPath,
        videos = videos,
        savedAtMillis = payload.optLong("savedAt", 0L),
    )
}.getOrNull()

internal fun encodeForumCache(cache: CachedForum): String = JSONObject().apply {
    put("version", 1)
    put("savedAt", cache.savedAtMillis)
    put(
        "posts",
        JSONArray().apply {
            cache.posts.forEach { post ->
                put(JSONObject().apply {
                    put("sourceId", post.sourceId); put("sourceName", post.sourceName)
                    put("id", post.id); put("title", post.title); put("author", post.author)
                    put("replyCount", post.replyCount); put("updatedLabel", post.updatedLabel)
                    put("updatedEpochSeconds", post.updatedEpochSeconds); put("url", post.url)
                })
            }
        },
    )
}.toString()

/**
 * Decodes a cached forum first page defensively: records missing required fields or with an
 * unusable URL are skipped, duplicates are dropped, and invalid payloads return null.
 */
internal fun decodeForumCache(json: String): CachedForum? = runCatching {
    if (json.isBlank()) return null
    val payload = JSONObject(json)
    val array = payload.optJSONArray("posts") ?: return null
    val posts = (0 until array.length()).mapNotNull { index ->
        runCatching { decodeForumPost(array.getJSONObject(index)) }.getOrNull()
    }
        .filter { SourceAddress.resolve(it.url, it.url) != null }
        .distinctBy { it.sourceId to it.id }
        .take(AppPreferences.MAX_FORUM_CACHE_ITEMS)
    if (posts.isEmpty()) return null
    CachedForum(posts = posts, savedAtMillis = payload.optLong("savedAt", 0L))
}.getOrNull()

/** One forum post record; returns null instead of throwing when required fields are unusable. */
private fun decodeForumPost(item: JSONObject): ForumPost? {
    val sourceId = item.optString("sourceId").takeIf(String::isNotBlank) ?: return null
    val id = item.optString("id").takeIf(String::isNotBlank) ?: return null
    val title = item.optString("title").takeIf(String::isNotBlank) ?: return null
    val url = item.optString("url")
    if (url.isBlank()) return null
    return ForumPost(
        sourceId = sourceId,
        sourceName = item.optString("sourceName").ifBlank { sourceId },
        id = id,
        title = title,
        author = item.optString("author"),
        replyCount = item.optInt("replyCount"),
        updatedLabel = item.optString("updatedLabel"),
        updatedEpochSeconds = item.optLong("updatedEpochSeconds"),
        url = url,
    )
}

/**
 * Decodes the saved-video JSON blob one entry at a time, so a single corrupt record is
 * skipped instead of erasing the whole local library. Pure function, testable without
 * Android APIs; keeps the previous demo- and address-validation filtering rules.
 */
internal fun decodeSavedVideos(json: String): List<VideoItem> {
    val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        runCatching {
            val item = array.getJSONObject(index)
            VideoItem(
                id = item.getString("id"), title = item.getString("title"),
                author = item.getString("author"), duration = item.getString("duration"),
                views = item.getString("views"), pageUrl = item.getString("pageUrl"),
                thumbnailUrl = item.optString("thumbnailUrl").takeIf(String::isNotBlank),
            )
        }.getOrNull()
    }.filter { !it.id.startsWith("demo-") && SourceAddress.resolve(it.pageUrl, it.pageUrl) != null }
}
