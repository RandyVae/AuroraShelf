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
import com.aurorashelf.app.model.AppDestination
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.FeedCategory
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
    val category: FeedCategory = FeedCategory.MONTHLY_HOT,
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
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val message: String? = null,
)

class AuroraViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppPreferences(application)
    private val repository = VideoRepository()
    private var feedJob: Job? = null
    private var nextPage = 1
    private val initialSourceUrl = preferences.sourceUrl
    private val initialCategory = FeedCategory.MONTHLY_HOT
    private val initialCachedFeed = preferences.cachedFeed()?.takeIf {
        it.sourceUrl == initialSourceUrl &&
            it.categoryPath == initialCategory.path &&
            isFreshFeed(it.savedAtMillis)
    }
    private val _uiState = MutableStateFlow(
        AuroraUiState(
            sourceUrl = initialSourceUrl,
            category = initialCategory,
            // Paint the last-known first page immediately; refresh() replaces it silently.
            videos = initialCachedFeed?.videos.orEmpty(),
            favoriteIds = preferences.favoriteIds(),
            historyIds = preferences.historyIds(),
            // Kept synchronous: favorites/history screens must be complete on the first frame,
            // otherwise a freshly started app can briefly show an empty library.
            allKnownVideos = (initialCachedFeed?.videos.orEmpty() + preferences.savedVideos())
                .distinctBy(VideoItem::id),
        ),
    )
    val uiState: StateFlow<AuroraUiState> = _uiState.asStateFlow()

    init {
        // Warm the covers of the cached first page before Compose lays them out.
        prefetchCovers(initialCachedFeed?.videos.orEmpty())
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

    private fun isFreshFeed(savedAtMillis: Long): Boolean =
        savedAtMillis > 0 && System.currentTimeMillis() - savedAtMillis in 0..FEED_CACHE_TTL_MS

    private fun applyCachedFeed(sourceUrl: String, category: FeedCategory) {
        val cached = preferences.cachedFeed() ?: return
        if (cached.sourceUrl != sourceUrl || cached.categoryPath != category.path) return
        if (!isFreshFeed(cached.savedAtMillis)) return
        _uiState.update {
            it.copy(
                videos = cached.videos.distinctBy(VideoItem::id),
                allKnownVideos = (cached.videos + it.allKnownVideos).distinctBy(VideoItem::id),
                canLoadMore = true,
            )
        }
    }

    fun selectDestination(destination: AppDestination) {
        _uiState.update { it.copy(destination = destination, isSearchOpen = false, selectedVideo = null) }
    }

    fun selectCategory(category: FeedCategory) {
        if (_uiState.value.category == category) return
        _uiState.update { it.copy(category = category) }
        applyCachedFeed(_uiState.value.sourceUrl, category)
        refresh()
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query, searchMessage = null) }
    }

    fun setSearchOpen(isOpen: Boolean) {
        _uiState.update {
            it.copy(
                isSearchOpen = isOpen,
                query = if (isOpen) it.query else "",
                searchResults = if (isOpen) it.searchResults else emptyList(),
                searchMessage = null,
            )
        }
    }

    fun search() {
        val snapshot = _uiState.value
        if (snapshot.query.isBlank() || snapshot.isSearching) return
        if (!ContentSourceCatalog.isPipePipe(snapshot.sourceUrl)) {
            val results = localSearchMatches(snapshot.query, snapshot.allKnownVideos)
            _uiState.update {
                it.copy(
                    searchResults = results,
                    isRemoteSearch = false,
                    searchMessage = if (results.isEmpty()) "已加载内容中没有匹配结果" else null,
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchMessage = null) }
            runCatching { repository.search(snapshot.sourceUrl, snapshot.query) }
                .onSuccess { results ->
                    // Site search can return the same item twice; LazyColumn keys must stay unique.
                    val distinctResults = results.distinctBy(VideoItem::id)
                    _uiState.update {
                        it.copy(
                            searchResults = distinctResults,
                            isRemoteSearch = true,
                            allKnownVideos = (distinctResults + it.allKnownVideos).distinctBy(VideoItem::id),
                            isSearching = false,
                            searchMessage = if (distinctResults.isEmpty()) "没有找到相关视频" else null,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(isSearching = false, searchMessage = error.message ?: "搜索失败，请稍后重试")
                    }
                }
        }
    }

    fun openVideo(video: VideoItem) {
        preferences.addToHistory(video.id)
        preferences.saveVideo(video)
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
        val nextCategory = _uiState.value.category.takeIf(FeedCategory.availableFor(nextSource)::contains)
            ?: FeedCategory.availableFor(nextSource).first()
        _uiState.update {
            it.copy(
                sourceUrl = nextSource,
                category = nextCategory,
                destination = AppDestination.HOME,
                videos = emptyList(),
                searchResults = emptyList(),
                isRemoteSearch = false,
                canLoadMore = true,
            )
        }
        applyCachedFeed(nextSource, nextCategory)
        refresh()
    }

    fun refresh() {
        val snapshot = _uiState.value
        feedJob?.cancel()
        nextPage = 1
        feedJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, isLoadingMore = false, canLoadMore = true, message = null)
            }
            runCatching { repository.loadFeed(snapshot.sourceUrl, snapshot.category, page = 1) }
                .onSuccess { videos ->
                    nextPage = 2
                    preferences.saveFeedCache(snapshot.sourceUrl, snapshot.category.path, videos)
                    prefetchCovers(videos)
                    prefetchDetails(videos)
                    _uiState.update {
                        it.copy(
                            videos = videos,
                            allKnownVideos = (videos + it.allKnownVideos).distinctBy(VideoItem::id),
                            isLoading = false,
                            canLoadMore = videos.isNotEmpty(),
                            message = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            canLoadMore = false,
                            message = error.message ?: "内容加载失败，请稍后重试。",
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val snapshot = _uiState.value
        if (snapshot.isLoading || snapshot.isLoadingMore || !snapshot.canLoadMore) return
        val requestedPage = nextPage
        feedJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, message = null) }
            runCatching {
                repository.loadFeed(snapshot.sourceUrl, snapshot.category, page = requestedPage)
            }.onSuccess { pageVideos ->
                prefetchCovers(pageVideos)
                _uiState.update { current ->
                    if (pageVideos.isEmpty()) {
                        return@update current.copy(isLoadingMore = false, canLoadMore = false, message = null)
                    }
                    // The site can repeat pinned videos across pages. Only new IDs extend the feed.
                    val newVideos = distinctNewVideos(current.videos, pageVideos)
                    if (newVideos.isNotEmpty()) nextPage = requestedPage + 1
                    current.copy(
                        videos = current.videos + newVideos,
                        allKnownVideos = (newVideos + current.allKnownVideos).distinctBy(VideoItem::id),
                        isLoadingMore = false,
                        canLoadMore = newVideos.isNotEmpty(),
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        message = error.message ?: "加载更多失败，请稍后重试。",
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

        /** Process-wide de-duplication for detail-page prefetches triggered by feed refreshes. */
        private val recentDetailPrefetches = ConcurrentHashMap<String, Long>()

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
