package com.aurorashelf.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurorashelf.app.data.AppPreferences
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

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
    private val _uiState = MutableStateFlow(
        AuroraUiState(
            sourceUrl = preferences.sourceUrl,
            videos = emptyList(),
            favoriteIds = preferences.favoriteIds(),
            historyIds = preferences.historyIds(),
            allKnownVideos = preferences.savedVideos(),
        ),
    )
    val uiState: StateFlow<AuroraUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectDestination(destination: AppDestination) {
        _uiState.update { it.copy(destination = destination, isSearchOpen = false, selectedVideo = null) }
    }

    fun selectCategory(category: FeedCategory) {
        if (_uiState.value.category == category) return
        _uiState.update { it.copy(category = category) }
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
            val results = snapshot.allKnownVideos.filter {
                it.title.contains(snapshot.query, ignoreCase = true) ||
                    it.author.contains(snapshot.query, ignoreCase = true)
            }
            _uiState.update {
                it.copy(
                    searchResults = results,
                    searchMessage = if (results.isEmpty()) "已加载内容中没有匹配结果" else null,
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchMessage = null) }
            runCatching { repository.search(snapshot.sourceUrl, snapshot.query) }
                .onSuccess { results ->
                    _uiState.update {
                        it.copy(
                            searchResults = results,
                            allKnownVideos = (results + it.allKnownVideos).distinctBy(VideoItem::id),
                            isSearching = false,
                            searchMessage = if (results.isEmpty()) "没有找到相关视频" else null,
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
        _uiState.update {
            val supportedCategories = FeedCategory.availableFor(preferences.sourceUrl)
            it.copy(
                sourceUrl = preferences.sourceUrl,
                category = it.category.takeIf(supportedCategories::contains) ?: supportedCategories.first(),
                destination = AppDestination.HOME,
                videos = emptyList(),
                searchResults = emptyList(),
                canLoadMore = true,
            )
        }
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
        internal fun distinctNewVideos(
            existing: List<VideoItem>,
            incoming: List<VideoItem>,
        ): List<VideoItem> {
            val existingIds = existing.asSequence().map(VideoItem::id).toHashSet()
            return incoming.distinctBy(VideoItem::id).filterNot { it.id in existingIds }
        }
    }
}
