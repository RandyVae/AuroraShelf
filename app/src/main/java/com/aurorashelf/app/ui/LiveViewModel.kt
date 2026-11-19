package com.aurorashelf.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurorashelf.app.data.AppPreferences
import com.aurorashelf.app.data.HornyStudentsLiveRepository
import com.aurorashelf.app.data.StripchatLiveRepository
import com.aurorashelf.app.data.resilience.SourceSnapshotStore
import com.aurorashelf.app.model.LiveCategory
import com.aurorashelf.app.model.LiveItem
import com.aurorashelf.app.model.LiveSourceCatalog
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveUiState(
    val selectedSourceId: String = LiveSourceCatalog.STRIPCHAT.id,
    val selectedCategory: LiveCategory = LiveCategory.WOMEN,
    val items: List<LiveItem> = emptyList(),
    val page: Int = 0,
    val canLoadMore: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val selectedItem: LiveItem? = null,
    val isSearchOpen: Boolean = false,
    val searchQuery: String = "",
)

internal data class LivePageUpdate(
    val items: List<LiveItem>,
    val canLoadMore: Boolean,
)

class LiveViewModel(
    application: Application,
    private val stripchatRepository: StripchatLiveRepository,
    private val hornyStudentsRepository: HornyStudentsLiveRepository,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        StripchatLiveRepository(),
        HornyStudentsLiveRepository(),
    )

    private val preferences = AppPreferences(application)
    private val initialSourceId = preferences.liveSourceId
    private val initialCachedItems =
        SourceSnapshotStore.getLiveFeed(initialSourceId, LiveCategory.WOMEN.name)?.items
            ?: preferences.cachedLiveFeed(initialSourceId, LiveCategory.WOMEN.name)?.items.orEmpty()
    private val mutableUiState = MutableStateFlow(
        LiveUiState(
            selectedSourceId = initialSourceId,
            items = initialCachedItems,
            page = if (initialCachedItems.isNotEmpty()) 1 else 0,
        ),
    )
    val uiState: StateFlow<LiveUiState> = mutableUiState.asStateFlow()
    private var refreshJob: Job? = null
    private var loadMoreJob: Job? = null
    private var feedGeneration: Long = 0L
    private var initialLoadRequested = false

    /** 首次进入直播页时才请求数据，重复调用保持幂等。 */
    fun ensureLoaded() {
        if (initialLoadRequested) return
        initialLoadRequested = true
        refresh()
    }

    fun selectSource(sourceId: String) {
        if (sourceId == mutableUiState.value.selectedSourceId) return
        preferences.liveSourceId = sourceId
        val category = mutableUiState.value.selectedCategory
        val cached = SourceSnapshotStore.getLiveFeed(sourceId, category.name)?.items
            ?: preferences.cachedLiveFeed(sourceId, category.name)?.items.orEmpty()
        mutableUiState.update {
            it.copy(
                selectedSourceId = sourceId,
                items = cached,
                page = if (cached.isNotEmpty()) 1 else 0,
                canLoadMore = true,
                isLoading = cached.isEmpty(),
                isLoadingMore = false,
                error = null,
            )
        }
        load(1, replace = true)
    }

    fun selectCategory(category: LiveCategory) {
        if (category == mutableUiState.value.selectedCategory) return
        val sourceId = mutableUiState.value.selectedSourceId
        val cached = SourceSnapshotStore.getLiveFeed(sourceId, category.name)?.items
            ?: preferences.cachedLiveFeed(sourceId, category.name)?.items.orEmpty()
        mutableUiState.update {
            it.copy(
                selectedCategory = category,
                items = cached,
                page = if (cached.isNotEmpty()) 1 else 0,
                canLoadMore = true,
                isLoading = cached.isEmpty(),
                isLoadingMore = false,
                error = null,
            )
        }
        load(1, replace = true)
    }

    fun refresh() {
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        mutableUiState.update { it.copy(isLoading = it.items.isEmpty(), isLoadingMore = false, error = null) }
        load(1, replace = true)
    }

    fun loadMore() {
        val state = mutableUiState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore || state.page < 1 || refreshJob?.isActive == true) return
        mutableUiState.update { it.copy(isLoadingMore = true, error = null) }
        load(state.page + 1, replace = false)
    }

    fun open(item: LiveItem) = mutableUiState.update { it.copy(selectedItem = item) }
    fun close() = mutableUiState.update { it.copy(selectedItem = null) }
    fun setSearchOpen(open: Boolean) = mutableUiState.update {
        it.copy(isSearchOpen = open, searchQuery = if (open) it.searchQuery else "")
    }
    fun updateSearchQuery(query: String) = mutableUiState.update { it.copy(searchQuery = query) }

    private fun load(page: Int, replace: Boolean) {
        val gen = if (replace) {
            refreshJob?.cancel()
            loadMoreJob?.cancel()
            ++feedGeneration
        } else {
            loadMoreJob?.cancel()
            feedGeneration
        }
        val sourceId = mutableUiState.value.selectedSourceId
        val category = mutableUiState.value.selectedCategory
        val job = viewModelScope.launch {
            var activeSourceId = sourceId
            val result = runCatching {
                if (sourceId == LiveSourceCatalog.HORNYSTUDENTS.id) {
                    hornyStudentsRepository.load(category, page)
                } else {
                    try {
                        stripchatRepository.load(category, page)
                    } catch (primaryErr: Exception) {
                        if (primaryErr is CancellationException) throw primaryErr
                        if (!replace || page != 1) throw primaryErr
                        // 仅在首屏加载 Stripchat 失败时自动降级到备用源 HornyStudents
                        activeSourceId = LiveSourceCatalog.HORNYSTUDENTS.id
                        hornyStudentsRepository.load(category, page)
                    }
                }
            }
            result.onSuccess { res ->
                if (feedGeneration != gen) return@onSuccess
                if (page == 1 && res.items.isNotEmpty()) {
                    SourceSnapshotStore.putLiveFeed(activeSourceId, category.name, res.items)
                    preferences.saveLiveFeed(activeSourceId, category.name, res.items)
                }
                mutableUiState.update { current ->
                    if (feedGeneration != gen) return@update current
                    val update = nextLivePage(
                        currentItems = current.items,
                        incomingItems = res.items,
                        totalCount = res.totalCount,
                        replace = replace,
                    )
                    current.copy(
                        selectedSourceId = activeSourceId,
                        items = update.items,
                        page = page,
                        canLoadMore = update.canLoadMore,
                        isLoading = false,
                        isLoadingMore = false,
                        error = null,
                    )
                }
            }.onFailure { failure ->
                if (failure is CancellationException) return@onFailure
                if (feedGeneration != gen) return@onFailure
                val cached = preferences.cachedLiveFeed(activeSourceId, category.name)
                mutableUiState.update { current ->
                    val fallbackItems = if (current.items.isNotEmpty()) current.items else (cached?.items.orEmpty())
                    current.copy(
                        items = fallbackItems,
                        isLoading = false,
                        isLoadingMore = false,
                        error = if (replace && fallbackItems.isNotEmpty()) null else failure.userMessage(),
                    )
                }
            }
        }
        if (replace) refreshJob = job else loadMoreJob = job
    }

    private fun Throwable.userMessage(): String = when (this) {
        is UnknownHostException -> "当前网络无法连接直播服务器，请检查网络或 DNS"
        is SocketTimeoutException -> "直播服务器响应超时，请稍后重试"
        is SSLHandshakeException -> "直播安全连接失败，请确认系统时间和网络"
        is IOException -> com.aurorashelf.app.data.VideoRepository.friendlyLoadErrorMessage(this, "直播连接失败，请稍后重试")
        else -> "直播暂时无法加载，请稍后重试"
    }

    companion object {
        /**
         * 计算加载一页后的列表与是否还能继续翻页。
         * 只有本次请求真正带来新条目、且未达到接口返回的总数时才允许继续请求，
         * 避免接口忽略 offset 返回重复页或空页时无限加载。
         */
        internal fun nextLivePage(
            currentItems: List<LiveItem>,
            incomingItems: List<LiveItem>,
            totalCount: Int,
            replace: Boolean,
        ): LivePageUpdate {
            val incoming = incomingItems.distinctBy(LiveItem::id)
            val items = if (replace) incoming else (currentItems + incoming).distinctBy(LiveItem::id)
            val addedCount = if (replace) items.size else items.size - currentItems.size
            val canLoadMore = incoming.isNotEmpty() && addedCount > 0 && items.size < totalCount
            return LivePageUpdate(items = items, canLoadMore = canLoadMore)
        }
    }
}
