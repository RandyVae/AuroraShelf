package com.aurorashelf.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurorashelf.app.data.AppPreferences
import com.aurorashelf.app.data.PREFETCH_LOG_TAG
import com.aurorashelf.app.data.PlaybackResolver
import com.aurorashelf.app.data.isPrefetchEnabled
import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.data.SourceAddress
import com.aurorashelf.app.data.resilience.SourceHealthRegistry
import com.aurorashelf.app.data.resilience.SourceSnapshotStore
import com.aurorashelf.app.model.AppDestination
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.FeedCategory
import com.aurorashelf.app.model.SourceCategory
import com.aurorashelf.app.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

data class AuroraUiState(
    val destination: AppDestination = AppDestination.HOME,
    val category: SourceCategory = SourceCategory.FALLBACK,
    val videos: List<VideoItem> = emptyList(),
    val allKnownVideos: List<VideoItem> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val historyIds: List<String> = emptyList(),
    val sourceUrl: String = "",
    val query: String = "",
    val searchResults: List<VideoItem> = emptyList(),
    val isSearching: Boolean = false,
    val searchMessage: String? = null,
    /** True when [searchResults] came from a site-wide network search instead of local matching. */
    val isRemoteSearch: Boolean = false,
    val selectedVideo: VideoItem? = null,
    val isSearchOpen: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val loadMoreError: String? = null,
    val message: String? = null,
)

class AuroraViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppPreferences.get(application)
    private val repository = VideoRepository()
    private var refreshJob: Job? = null
    private var loadMoreJob: Job? = null
    private var feedGeneration: Long = 0L
    private var searchJob: Job? = null
    private var searchGeneration: Long = 0L
    private var nextPage = 1
    private val initialSourceUrl = preferences.sourceUrl
    private val initialCategory = ContentSourceCatalog.sourceCategories(initialSourceUrl).let { available ->
        available.firstOrNull { it.legacy == FeedCategory.MONTHLY_HOT }
            ?: available.firstOrNull()
            ?: SourceCategory.FALLBACK
    }
    private val initialSnapshotVideos: List<VideoItem> =
        SourceSnapshotStore.getVideoFeed(initialSourceUrl, initialCategory.cacheKey)?.items
            ?: preferences.cachedFeed()?.takeIf {
                it.sourceUrl == initialSourceUrl &&
                    (it.categoryPath == initialCategory.cacheKey || it.categoryPath == initialCategory.path) &&
                    isFreshFeed(it.savedAtMillis)
            }?.videos.orEmpty()
    private val _uiState = MutableStateFlow(
        AuroraUiState(
            sourceUrl = initialSourceUrl,
            category = initialCategory,
            // Paint the last-known first page immediately; refresh() replaces it silently.
            videos = initialSnapshotVideos,
            favoriteIds = preferences.favoriteIds(),
            historyIds = preferences.historyIds(),
            // Kept synchronous: favorites/history screens must be complete on the first frame,
            // otherwise a freshly started app can briefly show an empty library.
            allKnownVideos = capKnownVideos(initialSnapshotVideos, preferences.savedVideos()),
        ),
    )
    val uiState: StateFlow<AuroraUiState> = _uiState.asStateFlow()

    init {
        if (initialSnapshotVideos.isNotEmpty()) {
            Log.i("AuroraPerf", "video_swr_init ms=1 count=${initialSnapshotVideos.size}")
        }
        // Warm the covers of the cached first page before Compose lays them out.
        prefetchCovers(initialSnapshotVideos)
        prefetchLeadingStream(initialSnapshotVideos)
        prefetchHuavodStreams(initialSnapshotVideos)
        refresh()
    }

    /**
     * Downloads the first few covers off the main thread so the first visible cards can paint
     * from the cache. CoverImageLoader deduplicates identical in-flight requests, so this never
     * doubles the network work the composables would do anyway.
     */
    private fun prefetchCovers(videos: List<VideoItem>) {
        val targets = videos.asSequence()
            .mapNotNull { video -> video.thumbnailUrl?.let { it to video.pageUrl } }
            .distinctBy { it.first }
            .take(COVER_PREFETCH_LIMIT)
            .toList()
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            targets.forEach { (url, referer) -> CoverImageLoader.load(url, referer) }
        }
    }

    /**
     * Warms the detail pages of the first two feed items shortly after the first page lands, so a
     * tap has a hot 60 s page cache. Honours the prefetch setting and the metered-network policy,
     * runs off the main thread, and never repeats the same page within the cache window.
     */
    private fun prefetchDetails(videos: List<VideoItem>) {
        val targets = videos.asSequence()
            .map(VideoItem::pageUrl)
            .filter(String::isNotBlank)
            .distinct()
            .take(DETAIL_PREFETCH_COUNT)
            .toList()
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            delay(DETAIL_PREFETCH_DELAY_MS)
            val appContext = getApplication<Application>()
            if (!isPrefetchEnabled(appContext)) {
                Log.i(PREFETCH_LOG_TAG, "detail skipped")
                return@launch
            }
            val now = System.currentTimeMillis()
            val freshTargets = targets.filter { pageUrl ->
                now - (recentDetailPrefetches[pageUrl] ?: 0L) > DETAIL_PREFETCH_DEDUPE_MS
            }
            if (freshTargets.isEmpty()) return@launch
            freshTargets.forEach { pageUrl -> recentDetailPrefetches[pageUrl] = now }
            Log.i(PREFETCH_LOG_TAG, "detail start count=${freshTargets.size}")
            val resolver = PlaybackResolver()
            freshTargets.forEach { pageUrl -> resolver.prefetchDetail(pageUrl) }
            Log.i(PREFETCH_LOG_TAG, "detail done count=${freshTargets.size}")
        }
    }

    /**
     * Pre-resolves the media stream URL for the leading Hero video into [PlaybackResolver]'s cache,
     * so that tapping the prominent banner starts playback with zero resolution delay.
     */
    private fun prefetchLeadingStream(videos: List<VideoItem>) {
        val lead = videos.firstOrNull()?.pageUrl?.takeIf(String::isNotBlank) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            delay(DETAIL_PREFETCH_DELAY_MS + 200L)
            val appContext = getApplication<Application>()
            if (!isPrefetchEnabled(appContext)) return@launch
            runCatching {
                PlaybackResolver().resolve(lead)
            }.onSuccess { source ->
                Log.i(PREFETCH_LOG_TAG, "leading stream prefetch ready host=${runCatching { java.net.URI(source.url).host }.getOrNull()}")
            }
        }
    }

    /**
     * Proactively pre-warms playback streams for the top Huavod videos in the background.
     * Starts the countdown early so tapping any prominent Huavod video starts with zero or near-zero wait.
     */
    private fun prefetchHuavodStreams(videos: List<VideoItem>) {
        val targets = videos.asSequence()
            .filter { ContentSourceCatalog.isHuavod(it.pageUrl) }
            .take(1)
            .toList()
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            delay(DETAIL_PREFETCH_DELAY_MS)
            val appContext = getApplication<Application>()
            if (!isPrefetchEnabled(appContext)) return@launch
            val resolver = PlaybackResolver()
            targets.forEach { video ->
                resolver.prewarmHuavod(video.pageUrl)
            }
        }
    }

    private fun isFreshFeed(savedAtMillis: Long): Boolean =
        savedAtMillis > 0 && System.currentTimeMillis() - savedAtMillis in 0..FEED_CACHE_TTL_MS

    private fun applyCachedFeed(sourceUrl: String, category: SourceCategory) {
        val startedAt = System.currentTimeMillis()
        val snapshot = SourceSnapshotStore.getVideoFeed(sourceUrl, category.cacheKey)
        val cachedVideos = if (snapshot != null && snapshot.items.isNotEmpty()) {
            snapshot.items
        } else {
            val legacy = preferences.cachedFeed()
            if (legacy != null &&
                legacy.sourceUrl == sourceUrl &&
                (legacy.categoryPath == category.cacheKey || legacy.categoryPath == category.path) &&
                isFreshFeed(legacy.savedAtMillis)
            ) {
                legacy.videos
            } else {
                emptyList()
            }
        }
        if (cachedVideos.isEmpty()) return
        val distinct = cachedVideos.distinctBy(VideoItem::id)
        prefetchCovers(distinct)
        _uiState.update {
            it.copy(
                videos = distinct,
                allKnownVideos = capKnownVideos(distinct, it.allKnownVideos, preferences.savedVideos()),
                canLoadMore = true,
                message = null,
            )
        }
        Log.i("AuroraPerf", "video_swr_hit ms=${System.currentTimeMillis() - startedAt} count=${distinct.size} fresh=${snapshot?.isFresh ?: false}")
    }

    fun selectDestination(destination: AppDestination) {
        _uiState.update { it.copy(destination = destination, isSearchOpen = false, selectedVideo = null) }
    }

    fun selectCategory(category: SourceCategory) {
        if (_uiState.value.category == category) return
        _uiState.update { it.copy(category = category) }
        applyCachedFeed(_uiState.value.sourceUrl, category)
        refresh()
    }

    fun updateQuery(query: String) {
        if (query.isBlank()) {
            searchJob?.cancel()
            searchJob = null
            searchGeneration++
            _uiState.update {
                it.copy(
                    query = query,
                    searchResults = emptyList(),
                    isRemoteSearch = false,
                    isSearching = false,
                    searchMessage = null,
                )
            }
        } else {
            _uiState.update { it.copy(query = query, searchMessage = null) }
        }
    }

    fun setSearchOpen(isOpen: Boolean) {
        if (!isOpen) {
            searchJob?.cancel()
            searchJob = null
            searchGeneration++
        }
        _uiState.update {
            it.copy(
                isSearchOpen = isOpen,
                query = if (isOpen) it.query else "",
                searchResults = if (isOpen) it.searchResults else emptyList(),
                isRemoteSearch = if (isOpen) it.isRemoteSearch else false,
                isSearching = if (isOpen) it.isSearching else false,
                searchMessage = null,
            )
        }
    }

    fun search() {
        val targetQuery = _uiState.value.query.trim()
        if (targetQuery.isBlank()) return
        searchJob?.cancel()
        val gen = ++searchGeneration
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearching = true,
                    searchResults = emptyList(),
                    isRemoteSearch = false,
                    searchMessage = null,
                )
            }
            runCatching { repository.searchAll(targetQuery) }
                .onSuccess { results ->
                    if (searchGeneration != gen || _uiState.value.query.trim() != targetQuery) return@onSuccess
                    // Site search can return the same item twice; LazyColumn keys must stay unique.
                    val distinctResults = results.distinctBy(VideoItem::id)
                    _uiState.update {
                        it.copy(
                            searchResults = distinctResults,
                            isRemoteSearch = true,
                            allKnownVideos = capKnownVideos(distinctResults, it.allKnownVideos, preferences.savedVideos()),
                            isSearching = false,
                            searchMessage = if (distinctResults.isEmpty()) "全网视频源未找到相关视频" else null,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (searchGeneration != gen) return@onFailure
                    _uiState.update {
                        it.copy(
                            searchResults = emptyList(),
                            isRemoteSearch = false,
                            isSearching = false,
                            searchMessage = "搜索失败：${VideoRepository.friendlyLoadErrorMessage(error, "网络异常")}",
                        )
                    }
                }
        }
    }

    fun openVideo(video: VideoItem) {
        preferences.addToHistory(video.id)
        preferences.saveVideo(video)
        // 关键落地：用户点击卡片进入详情的瞬间，后台立即静默启动华视首集换链倒计时
        if (ContentSourceCatalog.isHuavod(video.pageUrl)) {
            PlaybackResolver().prewarmHuavod(video.pageUrl)
        }
        _uiState.update {
            it.copy(selectedVideo = video, historyIds = preferences.historyIds(), message = null)
        }
    }

    fun closeVideo() {
        _uiState.update { it.copy(selectedVideo = null) }
    }

    fun toggleFavorite(video: VideoItem) {
        preferences.toggleFavorite(video.id)
        preferences.saveVideo(video)
        _uiState.update { it.copy(favoriteIds = preferences.favoriteIds()) }
    }

    fun saveSourceUrl(value: String) {
        if (SourceAddress.error(value) != null) return
        preferences.sourceUrl = value
        val nextSource = preferences.sourceUrl
        val availableCategories = ContentSourceCatalog.sourceCategories(nextSource)
        val previousCategory = _uiState.value.category
        val nextCategory = availableCategories.firstOrNull { it.id == previousCategory.id }
            ?: availableCategories.firstOrNull()
            ?: SourceCategory.FALLBACK
        val preloadedVideos = SourceSnapshotStore.getVideoFeed(nextSource, nextCategory.cacheKey)?.items
            ?.distinctBy(VideoItem::id)
            .orEmpty()
        _uiState.update {
            it.copy(
                sourceUrl = nextSource,
                category = nextCategory,
                destination = AppDestination.HOME,
                videos = preloadedVideos,
                allKnownVideos = capKnownVideos(preloadedVideos, it.allKnownVideos, preferences.savedVideos()),
                searchResults = emptyList(),
                isRemoteSearch = false,
                canLoadMore = true,
                message = null,
            )
        }
        if (preloadedVideos.isNotEmpty()) {
            prefetchCovers(preloadedVideos)
            Log.i("AuroraPerf", "video_switch_swr_hit source=${ContentSourceCatalog.displayName(nextSource)} count=${preloadedVideos.size}")
        } else {
            applyCachedFeed(nextSource, nextCategory)
        }
        refresh()
    }

    fun refresh() {
        val snapshot = _uiState.value
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        val gen = ++feedGeneration
        nextPage = if (snapshot.videos.isNotEmpty()) 2 else 1
        val startedAt = System.currentTimeMillis()
        refreshJob = viewModelScope.launch {
            _uiState.update {
                // 若当前已有 SWR 缓存内容，保持静默后台刷新而不清空首屏，同时用 isRefreshing 阻断 frame-1 并发翻页竞争
                it.copy(
                    isLoading = it.videos.isEmpty(),
                    isRefreshing = true,
                    isLoadingMore = false,
                    canLoadMore = true,
                    loadMoreError = null,
                    message = null,
                )
            }
            runCatching { repository.loadFeed(snapshot.sourceUrl, snapshot.category, page = 1) }
                .onSuccess { videos ->
                    if (feedGeneration != gen) return@onSuccess
                    nextPage = 2
                    SourceSnapshotStore.putVideoFeed(snapshot.sourceUrl, snapshot.category.cacheKey, videos)
                    preferences.saveFeedCache(snapshot.sourceUrl, snapshot.category.cacheKey, videos)
                    Log.i(
                        "AuroraPerf",
                        "video_net_load source=${ContentSourceCatalog.displayName(snapshot.sourceUrl)} ms=${System.currentTimeMillis() - startedAt} count=${videos.size} health=${SourceHealthRegistry.telemetrySummary()}",
                    )
                    prefetchCovers(videos)
                    prefetchDetails(videos)
                    prefetchLeadingStream(videos)
                    prefetchHuavodStreams(videos)
                    _uiState.update {
                        it.copy(
                            videos = videos,
                            allKnownVideos = capKnownVideos(videos, it.allKnownVideos, preferences.savedVideos()),
                            isLoading = false,
                            isRefreshing = false,
                            canLoadMore = videos.isNotEmpty(),
                            loadMoreError = null,
                            message = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    if (feedGeneration != gen) return@onFailure
                    val staleFallback = SourceSnapshotStore.getVideoFeed(snapshot.sourceUrl, snapshot.category.cacheKey)?.items.orEmpty()
                    _uiState.update {
                        val retainedVideos = if (it.videos.isNotEmpty()) it.videos else staleFallback
                        if (retainedVideos.isNotEmpty()) nextPage = nextPage.coerceAtLeast(2)
                        it.copy(
                            videos = retainedVideos,
                            isLoading = false,
                            isRefreshing = false,
                            canLoadMore = retainedVideos.isNotEmpty(),
                            loadMoreError = null,
                            message = if (retainedVideos.isNotEmpty()) null else VideoRepository.friendlyLoadErrorMessage(error, "内容加载失败，请稍后重试。"),
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val snapshot = _uiState.value
        if (snapshot.isLoading || snapshot.isRefreshing || snapshot.isLoadingMore || !snapshot.canLoadMore || refreshJob?.isActive == true) return
        val gen = feedGeneration
        val startPage = nextPage.coerceAtLeast(2)
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null, message = null) }
            runCatching {
                var requestedPage = startPage
                var overlapRetries = 0
                var lastRawSize = 0
                var collectedNew: List<VideoItem> = emptyList()
                // Tolerate up to MAX_CONSECUTIVE_OVERLAP_PAGES consecutive overlapping pages (e.g. pinned
                // or rolling hot videos) so infinite scroll never terminates prematurely after 1 duplicate page.
                while (overlapRetries < MAX_CONSECUTIVE_OVERLAP_PAGES) {
                    val pageVideos = repository.loadFeed(snapshot.sourceUrl, snapshot.category, page = requestedPage)
                    lastRawSize = pageVideos.size
                    if (pageVideos.isEmpty()) {
                        break
                    }
                    val currentExisting = _uiState.value.videos
                    val fresh = distinctNewVideos(currentExisting, pageVideos)
                    if (fresh.isNotEmpty()) {
                        collectedNew = fresh
                        nextPage = requestedPage + 1
                        break
                    }
                    requestedPage++
                    nextPage = requestedPage
                    overlapRetries++
                }
                Triple(collectedNew, lastRawSize, requestedPage)
            }.onSuccess { (newVideos, lastRawSize, _) ->
                if (feedGeneration != gen) return@onSuccess
                if (newVideos.isNotEmpty()) {
                    prefetchCovers(newVideos)
                }
                _uiState.update { current ->
                    if (feedGeneration != gen) return@update current
                    if (newVideos.isEmpty()) {
                        return@update current.copy(
                            isLoadingMore = false,
                            canLoadMore = false,
                            loadMoreError = null,
                            message = null,
                        )
                    }
                    val dedupedNew = distinctNewVideos(current.videos, newVideos)
                    current.copy(
                        videos = current.videos + dedupedNew,
                        allKnownVideos = capKnownVideos(dedupedNew, current.allKnownVideos, preferences.savedVideos()),
                        isLoadingMore = false,
                        canLoadMore = lastRawSize > 0,
                        loadMoreError = null,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (feedGeneration != gen) return@onFailure
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        loadMoreError = VideoRepository.friendlyLoadErrorMessage(error, "加载更多失败，点击重试"),
                    )
                }
            }
        }
    }


    companion object {
        /** First-page snapshot older than this is not worth painting. */
        internal const val FEED_CACHE_TTL_MS = 24 * 60 * 60 * 1000L

        /** How many above-the-fold covers to warm before the first layout pass. */
        private const val COVER_PREFETCH_LIMIT = 8

        /** How many leading feed items get a detail-page warm-up after the first page loads. */
        private const val DETAIL_PREFETCH_COUNT = 2

        /** Let the first frame and cover work finish before spending network on prefetch. */
        private const val DETAIL_PREFETCH_DELAY_MS = 1_500L

        /** One detail prefetch per page per cache window keeps repeated refreshes cheap. */
        private const val DETAIL_PREFETCH_DEDUPE_MS = 60_000L
        private const val MAX_KNOWN_VIDEOS = 2_000
        internal const val MAX_CONSECUTIVE_OVERLAP_PAGES = 3

        /** Process-wide de-duplication for detail-page prefetches triggered by feed refreshes. */
        private val recentDetailPrefetches = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, Long>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
                    size > 256
            },
        )

        internal fun capKnownVideos(
            primary: List<VideoItem>,
            existing: List<VideoItem>,
            pinnedSaved: List<VideoItem> = emptyList(),
        ): List<VideoItem> {
            val savedFirst = pinnedSaved.distinctBy(VideoItem::id)
            val rest = (primary + existing).distinctBy(VideoItem::id)
                .filterNot { candidate -> savedFirst.any { it.id == candidate.id } }
            return (savedFirst + rest).take(MAX_KNOWN_VIDEOS.coerceAtLeast(savedFirst.size))
        }

        /** Local search only: network results are already matched by the site search endpoint. */
        internal fun localSearchMatches(query: String, videos: List<VideoItem>): List<VideoItem> =
            videos.asSequence()
                .filter {
                    it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true)
                }
                .distinctBy(VideoItem::id)
                .toList()

        internal fun distinctNewVideos(
            existing: List<VideoItem>,
            incoming: List<VideoItem>,
        ): List<VideoItem> {
            val existingIds = existing.asSequence().map(VideoItem::id).toHashSet()
            return incoming.distinctBy(VideoItem::id).filterNot { it.id in existingIds }
        }
    }
}
