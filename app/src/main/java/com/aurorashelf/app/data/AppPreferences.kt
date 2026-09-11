package com.aurorashelf.app.data

import android.content.Context
import androidx.core.content.edit
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.VideoItem
import org.json.JSONArray
import org.json.JSONObject

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun savedVideos(): List<VideoItem> = runCatching {
        val array = JSONArray(preferences.getString("saved_videos", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            VideoItem(
                id = item.getString("id"), title = item.getString("title"),
                author = item.getString("author"), duration = item.getString("duration"),
                views = item.getString("views"), pageUrl = item.getString("pageUrl"),
                thumbnailUrl = item.optString("thumbnailUrl").takeIf(String::isNotBlank),
            )
        }.filter { !it.id.startsWith("demo-") && SourceAddress.resolve(it.pageUrl, it.pageUrl) != null }
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
        private const val MAX_HISTORY = 50

        fun normalizeBaseUrl(value: String): String = value.trim().trimEnd('/')
    }
}
