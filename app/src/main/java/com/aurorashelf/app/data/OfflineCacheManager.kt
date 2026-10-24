package com.aurorashelf.app.data

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import com.aurorashelf.app.model.VideoItem
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class OfflineCacheState {
    RESOLVING,
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    REMOVING,
}

data class OfflineVideo(
    val id: String,
    val videoId: String,
    val title: String,
    val episodeLabel: String?,
    val pageUrl: String,
    val thumbnailUrl: String?,
    val mediaUrl: String?,
    val state: OfflineCacheState,
    val percentDownloaded: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val contentLength: Long = 0L,
) {
    val isPlayableOffline: Boolean get() = state == OfflineCacheState.COMPLETED && mediaUrl != null

    fun asVideoItem(): VideoItem = VideoItem(
        id = videoId,
        title = if (episodeLabel.isNullOrBlank()) title else "$title · $episodeLabel",
        author = "离线缓存",
        duration = "",
        views = "",
        pageUrl = pageUrl,
        thumbnailUrl = thumbnailUrl,
    )
}

/** Metadata persisted inside the Media3 [DownloadRequest] payload, decoupled from Android types. */
internal data class OfflineMetadata(
    val videoId: String,
    val title: String,
    val episodeLabel: String?,
    val pageUrl: String,
    val thumbnailUrl: String?,
)

/**
 * Pure JSON codec for [OfflineMetadata]. Decoding returns null instead of throwing so a
 * corrupted or truncated payload can only affect its own entry.
 */
internal object OfflineMetadataCodec {
    internal const val DEFAULT_TITLE = "已缓存视频"

    fun encode(metadata: OfflineMetadata): ByteArray = JSONObject().apply {
        put("videoId", metadata.videoId)
        put("title", metadata.title)
        put("episodeLabel", metadata.episodeLabel.orEmpty())
        put("pageUrl", metadata.pageUrl)
        put("thumbnailUrl", metadata.thumbnailUrl.orEmpty())
    }.toString().toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): OfflineMetadata? = runCatching {
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        OfflineMetadata(
            videoId = json.optString("videoId"),
            title = json.optString("title", DEFAULT_TITLE),
            episodeLabel = json.optString("episodeLabel").takeIf(String::isNotBlank),
            pageUrl = json.optString("pageUrl"),
            thumbnailUrl = json.optString("thumbnailUrl").takeIf(String::isNotBlank),
        )
    }.getOrNull()
}

/** Application-scoped Media3 download store shared by downloads and playback. */
@OptIn(UnstableApi::class)
class OfflineCacheManager private constructor(context: Context) {
    private val applicationContext = context.applicationContext
    private val databaseProvider = StandaloneDatabaseProvider(applicationContext)
    val cache = SimpleCache(
        File(applicationContext.filesDir, CACHE_DIRECTORY),
        NoOpCacheEvictor(),
        databaseProvider,
    )
    private val upstreamFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(VideoRepository.USER_AGENT)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
        .setAllowCrossProtocolRedirects(true)
    private val downloadExecutor = Executors.newFixedThreadPool(2)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transientEntries = ConcurrentHashMap<String, OfflineVideo>()
    private val resolveJobs = ConcurrentHashMap<String, Job>()
    private var refreshJob: Job? = null

    val downloadManager = DownloadManager(
        applicationContext,
        databaseProvider,
        cache,
        upstreamFactory,
        downloadExecutor,
    ).apply {
        maxParallelDownloads = 2
        minRetryCount = 3
    }

    private val _entries = MutableStateFlow<List<OfflineVideo>>(emptyList())
    val entries: StateFlow<List<OfflineVideo>> = _entries.asStateFlow()

    init {
        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) = requestRefresh()

            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) {
                transientEntries.remove(download.request.id)
                requestRefresh()
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                transientEntries.remove(download.request.id)
                requestRefresh()
            }
        })
        downloadManager.resumeDownloads()
        requestRefresh()
        scope.launch {
            while (isActive) {
                if (_entries.value.any { it.state == OfflineCacheState.DOWNLOADING }) requestRefresh()
                delay(PROGRESS_REFRESH_MS)
            }
        }
    }

    fun enqueue(video: VideoItem, pageUrl: String, episodeLabel: String?) {
        val id = idFor(pageUrl)
        // A retried entry replaces its previous resolver; never let both add a download.
        resolveJobs.remove(id)?.cancel()
        val pending = OfflineVideo(
            id = id,
            videoId = video.id,
            title = video.title,
            episodeLabel = episodeLabel,
            pageUrl = pageUrl,
            thumbnailUrl = video.thumbnailUrl,
            mediaUrl = null,
            state = OfflineCacheState.RESOLVING,
        )
        transientEntries[id] = pending
        publishWithTransient()
        val resolveJob = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext[Job]
            try {
                runCatching { withContext(Dispatchers.IO) { PlaybackResolver().resolve(pageUrl) } }
                    .onSuccess { source ->
                        // remove(id)/clearAll() may have run while this coroutine was suspended.
                        // Re-check cancellation and the pending entry before scheduling the
                        // download, so a deleted task can never come back as a download item.
                        coroutineContext.ensureActive()
                        if (!shouldAcceptResolvedDownload(id, transientEntries.keys)) return@onSuccess
                        val metadata = OfflineMetadata(
                            videoId = video.id,
                            title = video.title,
                            episodeLabel = episodeLabel,
                            pageUrl = pageUrl,
                            thumbnailUrl = video.thumbnailUrl,
                        )
                        val request = DownloadRequest.Builder(id, Uri.parse(source.url))
                            .setMimeType(mimeType(source.url))
                            .setData(OfflineMetadataCodec.encode(metadata))
                            .apply {
                                if (mimeType(source.url) != MimeTypes.APPLICATION_M3U8) {
                                    setCustomCacheKey(id)
                                }
                            }
                            .build()
                        downloadManager.addDownload(request)
                        downloadManager.resumeDownloads()
                    }
                    .onFailure { failure ->
                        // Cancellation is expected when the user deletes or clears the entry.
                        if (failure is CancellationException) return@onFailure
                        // A newer enqueue for the same id owns the transient entry now.
                        if (transientEntries[id] !== pending) return@onFailure
                        transientEntries[id] = pending.copy(state = OfflineCacheState.FAILED)
                        publishWithTransient()
                    }
            } finally {
                // Remove only our own entry: a retry may already have registered a new job.
                if (self != null) resolveJobs.remove(id, self)
            }
        }
        resolveJobs[id] = resolveJob
        resolveJob.start()
    }

    fun remove(id: String) {
        // Cancel the resolver first; a completed resolve must not resurrect this download.
        resolveJobs.remove(id)?.cancel()
        transientEntries.remove(id)
        downloadManager.removeDownload(id)
        requestRefresh()
    }

    fun retry(entry: OfflineVideo) {
        enqueue(
            video = VideoItem(
                id = entry.videoId,
                title = entry.title,
                author = "离线缓存",
                duration = "",
                views = "",
                pageUrl = entry.pageUrl,
                thumbnailUrl = entry.thumbnailUrl,
            ),
            pageUrl = entry.pageUrl,
            episodeLabel = entry.episodeLabel,
        )
    }

    fun clearAll() {
        // Cancel every pending resolver so none of them can add a download afterwards.
        resolveJobs.values.forEach { it.cancel() }
        resolveJobs.clear()
        transientEntries.clear()
        downloadManager.removeAllDownloads()
        requestRefresh()
    }

    fun playbackDataSourceFactory(upstream: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            // Only explicit downloads may write, so browsing cannot silently consume storage.
            .setCacheWriteDataSinkFactory(null)

    fun completedFor(pageUrl: String): OfflineVideo? =
        _entries.value.firstOrNull { it.pageUrl == pageUrl && it.isPlayableOffline }

    private fun requestRefresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            val indexed = withContext(Dispatchers.IO) { readIndexedEntries() }
            _entries.value = (indexed + transientEntries.values)
                .distinctBy(OfflineVideo::id)
                .sortedByDescending { it.state == OfflineCacheState.DOWNLOADING || it.state == OfflineCacheState.RESOLVING }
        }
    }

    private fun publishWithTransient() {
        _entries.value = (_entries.value.filterNot { transientEntries.containsKey(it.id) } + transientEntries.values)
            .sortedByDescending { it.state == OfflineCacheState.DOWNLOADING || it.state == OfflineCacheState.RESOLVING }
    }

    private fun readIndexedEntries(): List<OfflineVideo> {
        return runCatching {
            buildList {
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        // Corrupt metadata must drop only its own entry, not the whole index.
                        runCatching { cursor.download.toOfflineVideo() }
                            .getOrNull()
                            ?.let { entry -> add(entry) }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun Download.toOfflineVideo(): OfflineVideo {
        // Corrupt metadata must only lose the descriptive fields, never the whole entry.
        val metadata = OfflineMetadataCodec.decode(request.data)
        return OfflineVideo(
            id = request.id,
            videoId = metadata?.videoId?.takeIf(String::isNotBlank) ?: request.id,
            title = metadata?.title?.takeIf(String::isNotBlank) ?: OfflineMetadataCodec.DEFAULT_TITLE,
            episodeLabel = metadata?.episodeLabel,
            pageUrl = metadata?.pageUrl.orEmpty(),
            thumbnailUrl = metadata?.thumbnailUrl,
            mediaUrl = request.uri.toString(),
            state = when (state) {
                Download.STATE_QUEUED, Download.STATE_STOPPED -> OfflineCacheState.QUEUED
                Download.STATE_DOWNLOADING, Download.STATE_RESTARTING -> OfflineCacheState.DOWNLOADING
                Download.STATE_COMPLETED -> OfflineCacheState.COMPLETED
                Download.STATE_FAILED -> OfflineCacheState.FAILED
                Download.STATE_REMOVING -> OfflineCacheState.REMOVING
                else -> OfflineCacheState.FAILED
            },
            percentDownloaded = percentDownloaded.takeIf { it >= 0f } ?: 0f,
            bytesDownloaded = bytesDownloaded,
            contentLength = contentLength.coerceAtLeast(0L),
        )
    }

    companion object {
        private const val CACHE_DIRECTORY = "offline_media"
        private const val PROGRESS_REFRESH_MS = 750L
        @Volatile private var instance: OfflineCacheManager? = null

        fun get(context: Context): OfflineCacheManager = instance ?: synchronized(this) {
            instance ?: OfflineCacheManager(context).also { instance = it }
        }

        fun idFor(pageUrl: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(pageUrl.toByteArray(Charsets.UTF_8))
            return "offline-" + digest.take(16).joinToString("") { "%02x".format(it) }
        }

        /**
         * A resolved download is scheduled only while its transient entry is still pending.
         * Extracted as a pure predicate so the remove/clearAll semantics stay unit-testable.
         */
        internal fun shouldAcceptResolvedDownload(id: String, pendingIds: Set<String>): Boolean =
            id in pendingIds

        private fun mimeType(url: String): String? = when {
            url.substringBefore('?').lowercase().endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            url.substringBefore('?').lowercase().endsWith(".mp4") -> MimeTypes.VIDEO_MP4
            else -> null
        }
    }
}
