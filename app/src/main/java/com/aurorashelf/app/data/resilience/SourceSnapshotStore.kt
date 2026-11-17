package com.aurorashelf.app.data.resilience

import android.content.Context
import android.util.Log
import com.aurorashelf.app.model.ComicSummary
import com.aurorashelf.app.model.ForumPost
import com.aurorashelf.app.model.LiveItem
import com.aurorashelf.app.model.VideoItem
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * 多槽位 SWR（Stale-While-Revalidate）+ 断网保底（Stale-If-Error）列表快照存储（Android）。
 *
 * 解决旧架构两大卡顿痛点：
 * 1. 旧版 AppPreferences 仅保留 1 个视频/漫画槽位：用户从 A 切到 B 再切回 A 时，A 的列表立刻归零并出现骨架屏；
 * 2. 本存储为最多 32 个 (模块, 源, 分类) 槽位提供 L1 内存 + L2 原子磁盘 JSON 快照，切换/冷启均可在 <10ms 内秒画首屏，
 *    并在后台静默刷新；即使全镜像遭遇短暂波动，也能凭借 Stale-If-Error（72 小时）保障首屏可用。
 */
internal class SourceSnapshotStoreInstance(
    private val cacheDirProvider: () -> File?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    internal data class SnapshotResult<T>(
        val items: List<T>,
        val savedAtMs: Long,
        val isFresh: Boolean,
    )

    private data class MemorySlot(
        val json: String,
        val savedAtMs: Long,
    )

    private val memoryCache = ConcurrentHashMap<String, MemorySlot>()

    fun getVideoFeed(sourceUrl: String, categoryKey: String): SnapshotResult<VideoItem>? {
        val key = "video|${sourceUrl.trimEnd('/')}|$categoryKey"
        return readSlot(key, ::decodeVideos)
    }

    fun putVideoFeed(sourceUrl: String, categoryKey: String, videos: List<VideoItem>) {
        if (videos.isEmpty()) return
        val key = "video|${sourceUrl.trimEnd('/')}|$categoryKey"
        val retained = videos.distinctBy(VideoItem::id).take(MAX_ITEMS_PER_SLOT)
        writeSlot(key, encodeVideos(retained))
    }

    fun getComicFeed(sourceId: String, categoryId: String): SnapshotResult<ComicSummary>? {
        val key = "comic|$sourceId|$categoryId"
        return readSlot(key, ::decodeComics)
    }

    fun putComicFeed(sourceId: String, categoryId: String, comics: List<ComicSummary>) {
        if (comics.isEmpty()) return
        val key = "comic|$sourceId|$categoryId"
        val retained = comics.distinctBy { it.sourceId to it.id }.take(MAX_ITEMS_PER_SLOT)
        writeSlot(key, encodeComics(retained))
    }

    fun getLiveFeed(sourceId: String, categoryKey: String): SnapshotResult<LiveItem>? {
        val key = "live|$sourceId|$categoryKey"
        return readSlot(key, ::decodeLives)
    }

    fun putLiveFeed(sourceId: String, categoryKey: String, items: List<LiveItem>) {
        if (items.isEmpty()) return
        val key = "live|$sourceId|$categoryKey"
        val retained = items.distinctBy(LiveItem::id).take(MAX_ITEMS_PER_SLOT)
        writeSlot(key, encodeLives(retained))
    }

    fun getForumFeed(boardKey: String = "all"): SnapshotResult<ForumPost>? {
        val key = "forum|$boardKey"
        return readSlot(key, ::decodeForumPosts)
    }

    fun putForumFeed(boardKey: String = "all", posts: List<ForumPost>) {
        if (posts.isEmpty()) return
        val key = "forum|$boardKey"
        val retained = posts.distinctBy { it.sourceId to it.id }.take(MAX_ITEMS_PER_SLOT)
        writeSlot(key, encodeForumPosts(retained))
    }

    fun clearAll() {
        memoryCache.clear()
        runCatching {
            cacheDirProvider()?.listFiles()?.forEach { it.delete() }
        }
    }

    private fun <T> readSlot(key: String, decoder: (JSONArray) -> List<T>): SnapshotResult<T>? {
        val now = clock()
        val mem = memoryCache[key]
        val rawPayload = if (mem != null) {
            mem.json
        } else {
            val file = fileForKey(key) ?: return null
            if (!file.isFile) return null
            val text = runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return null
            val savedAt = runCatching { JSONObject(text).optLong("savedAtMs", 0L) }.getOrDefault(0L)
            memoryCache[key] = MemorySlot(text, savedAt)
            text
        }
        val obj = runCatching { JSONObject(rawPayload) }.getOrNull() ?: return null
        val savedAtMs = obj.optLong("savedAtMs", 0L)
        if (savedAtMs <= 0L || now - savedAtMs > MAX_STALE_MS) {
            memoryCache.remove(key)
            runCatching { fileForKey(key)?.delete() }
            return null
        }
        val arr = obj.optJSONArray("items") ?: return null
        val items = runCatching { decoder(arr) }.getOrDefault(emptyList())
        if (items.isEmpty()) return null
        val isFresh = now - savedAtMs in 0..FRESH_TTL_MS
        return SnapshotResult(items = items, savedAtMs = savedAtMs, isFresh = isFresh)
    }

    private fun writeSlot(key: String, itemsArray: JSONArray) {
        val now = clock()
        val envelope = JSONObject().apply {
            put("savedAtMs", now)
            put("items", itemsArray)
        }.toString()
        memoryCache[key] = MemorySlot(envelope, now)
        trimMemoryIfNeeded()

        val target = fileForKey(key) ?: return
        runCatching {
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(envelope, StandardCharsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.writeText(envelope, StandardCharsets.UTF_8)
                tmp.delete()
            }
            trimDiskIfNeeded(target.parentFile)
        }
    }

    private fun trimMemoryIfNeeded() {
        if (memoryCache.size <= MAX_SLOTS) return
        val victims = memoryCache.entries.sortedBy { it.value.savedAtMs }.take(memoryCache.size - MAX_SLOTS)
        victims.forEach { memoryCache.remove(it.key) }
    }

    private fun trimDiskIfNeeded(dir: File?) {
        if (dir == null || !dir.isDirectory) return
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return
        if (files.size <= MAX_SLOTS) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_SLOTS).forEach { it.delete() }
    }

    private fun fileForKey(key: String): File? {
        val root = cacheDirProvider() ?: return null
        return File(root, "${sha256(key)}.json")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun encodeVideos(videos: List<VideoItem>): JSONArray = JSONArray().apply {
        videos.forEach { v ->
            put(
                JSONObject().apply {
                    put("id", v.id)
                    put("title", v.title)
                    put("author", v.author)
                    put("duration", v.duration)
                    put("views", v.views)
                    put("pageUrl", v.pageUrl)
                    put("thumbnailUrl", v.thumbnailUrl.orEmpty())
                    put("heroThumbnailUrl", v.heroThumbnailUrl.orEmpty())
                },
            )
        }
    }

    private fun decodeVideos(arr: JSONArray): List<VideoItem> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pageUrl = o.optString("pageUrl")
            val title = o.optString("title")
            if (pageUrl.isBlank() || title.isBlank()) continue
            add(
                VideoItem(
                    id = o.optString("id").ifBlank { sha256(pageUrl) },
                    title = title,
                    author = o.optString("author"),
                    duration = o.optString("duration").ifBlank { "--:--" },
                    views = o.optString("views"),
                    pageUrl = pageUrl,
                    thumbnailUrl = o.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                    heroThumbnailUrl = o.optString("heroThumbnailUrl").takeIf { it.isNotBlank() },
                ),
            )
        }
    }

    private fun encodeComics(comics: List<ComicSummary>): JSONArray = JSONArray().apply {
        comics.forEach { c ->
            put(
                JSONObject().apply {
                    put("sourceId", c.sourceId)
                    put("id", c.id)
                    put("title", c.title)
                    put("subtitle", c.subtitle)
                    put("coverUrl", c.coverUrl.orEmpty())
                    put("coverReferer", c.coverReferer.orEmpty())
                    put("tags", JSONArray(c.tags))
                },
            )
        }
    }

    private fun decodeComics(arr: JSONArray): List<ComicSummary> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val sourceId = o.optString("sourceId")
            val title = o.optString("title")
            if (id.isBlank() || sourceId.isBlank() || title.isBlank()) continue
            val tagsArr = o.optJSONArray("tags")
            val tags = buildList {
                if (tagsArr != null) {
                    for (t in 0 until tagsArr.length()) {
                        tagsArr.optString(t).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }
            add(
                ComicSummary(
                    sourceId = sourceId,
                    id = id,
                    title = title,
                    subtitle = o.optString("subtitle"),
                    coverUrl = o.optString("coverUrl").takeIf { it.isNotBlank() },
                    tags = tags,
                    coverReferer = o.optString("coverReferer").takeIf { it.isNotBlank() },
                ),
            )
        }
    }

    private fun encodeLives(items: List<LiveItem>): JSONArray = JSONArray().apply {
        items.forEach { l ->
            put(
                JSONObject().apply {
                    put("id", l.id)
                    put("username", l.username)
                    put("thumbnailUrl", l.thumbnailUrl)
                    put("viewersCount", l.viewersCount)
                    put("countryCode", l.countryCode)
                    put("isHd", l.isHd)
                    put("playerUrl", l.playerUrl)
                    put("hlsStreamUrl", l.hlsStreamUrl.orEmpty())
                },
            )
        }
    }

    private fun decodeLives(arr: JSONArray): List<LiveItem> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val username = o.optString("username")
            if (id.isBlank() || username.isBlank()) continue
            add(
                LiveItem(
                    id = id,
                    username = username,
                    thumbnailUrl = o.optString("thumbnailUrl"),
                    viewersCount = o.optInt("viewersCount", 0),
                    countryCode = o.optString("countryCode"),
                    isHd = o.optBoolean("isHd", false),
                    playerUrl = o.optString("playerUrl"),
                    hlsStreamUrl = o.optString("hlsStreamUrl").takeIf { it.isNotBlank() },
                ),
            )
        }
    }

    private fun encodeForumPosts(posts: List<ForumPost>): JSONArray = JSONArray().apply {
        posts.forEach { p ->
            put(
                JSONObject().apply {
                    put("sourceId", p.sourceId)
                    put("sourceName", p.sourceName)
                    put("id", p.id)
                    put("title", p.title)
                    put("author", p.author)
                    put("replyCount", p.replyCount)
                    put("updatedLabel", p.updatedLabel)
                    put("updatedEpochSeconds", p.updatedEpochSeconds)
                    put("url", p.url)
                },
            )
        }
    }

    private fun decodeForumPosts(arr: JSONArray): List<ForumPost> = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val title = o.optString("title")
            val url = o.optString("url")
            if (id.isBlank() || title.isBlank() || url.isBlank()) continue
            add(
                ForumPost(
                    sourceId = o.optString("sourceId"),
                    sourceName = o.optString("sourceName"),
                    id = id,
                    title = title,
                    author = o.optString("author"),
                    replyCount = o.optInt("replyCount", 0),
                    updatedLabel = o.optString("updatedLabel"),
                    updatedEpochSeconds = o.optLong("updatedEpochSeconds", 0L),
                    url = url,
                ),
            )
        }
    }

    companion object {
        internal const val FRESH_TTL_MS = 90_000L
        internal const val MAX_STALE_MS = 72L * 3_600_000L
        private const val MAX_SLOTS = 32
        private const val MAX_ITEMS_PER_SLOT = 36
    }
}

internal object SourceSnapshotStore {
    @Volatile
    private var snapshotDir: File? = null

    private val instance: SourceSnapshotStoreInstance by lazy {
        SourceSnapshotStoreInstance(cacheDirProvider = { snapshotDir })
    }

    fun initialize(context: Context) {
        val dir = File(context.applicationContext.filesDir, "source_snapshots")
        runCatching { dir.mkdirs() }
        snapshotDir = dir
        runCatching { Log.i("AuroraSnapshot", "initialized dir=${dir.absolutePath}") }
    }

    fun getVideoFeed(sourceUrl: String, categoryKey: String) = instance.getVideoFeed(sourceUrl, categoryKey)
    fun putVideoFeed(sourceUrl: String, categoryKey: String, videos: List<VideoItem>) = instance.putVideoFeed(sourceUrl, categoryKey, videos)

    fun getComicFeed(sourceId: String, categoryId: String) = instance.getComicFeed(sourceId, categoryId)
    fun putComicFeed(sourceId: String, categoryId: String, comics: List<ComicSummary>) = instance.putComicFeed(sourceId, categoryId, comics)

    fun getLiveFeed(sourceId: String, categoryKey: String) = instance.getLiveFeed(sourceId, categoryKey)
    fun putLiveFeed(sourceId: String, categoryKey: String, items: List<LiveItem>) = instance.putLiveFeed(sourceId, categoryKey, items)

    fun getForumFeed(boardKey: String = "all") = instance.getForumFeed(boardKey)
    fun putForumFeed(boardKey: String = "all", posts: List<ForumPost>) = instance.putForumFeed(boardKey, posts)
}
