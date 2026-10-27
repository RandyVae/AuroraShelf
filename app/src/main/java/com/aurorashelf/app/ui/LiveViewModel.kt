package com.aurorashelf.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurorashelf.app.data.AppPreferences
import com.aurorashelf.app.data.HornyStudentsLiveRepository
import com.aurorashelf.app.data.StripchatLiveRepository
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
    private val initialCached = preferences.cachedLiveFeed(initialSourceId, LiveCategory.WOMEN.name)
    private val mutableUiState = MutableStateFlow(
        LiveUiState(
            selectedSourceId = initialSourceId,
            items = initialCached?.items.orEmpty(),
            page = if (initialCached != null) 1 else 0,
        ),
    )
    val uiState: StateFlow<LiveUiState> = mutableUiState.asStateFlow()
    private var loadJob: Job? = null
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
        mutableUiState.update {
            it.copy(
                selectedSourceId = sourceId,
                items = emptyList(),
                page = 0,
                isLoading = true,
                error = null,
            )
        }
        load(1, replace = true)
    }

    fun selectCategory(category: LiveCategory) {
        if (category == mutableUiState.value.selectedCategory) return
        val cached = preferences.cachedLiveFeed(mutableUiState.value.selectedSourceId, category.name)
        mutableUiState.update {
            it.copy(
                selectedCategory = category,
                items = cached?.items ?: it.items,
                isLoading = true,
                error = null,
            )
        }
        load(1, replace = true)
    }

    fun refresh() {
        loadJob?.cancel()
        mutableUiState.update { it.copy(isLoading = true, isLoadingMore = false, error = null) }
        load(1, replace = true)
    }

    fun loadMore() {
        val state = mutableUiState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore || state.page < 1) return
        mutableUiState.update { it.copy(isLoadingMore = true, error = null) }
        load(state.page + 1, replace = false)
    }

    fun open(item: LiveItem) = mutableUiState.update { it.copy(selectedItem = item) }
    fun close() = mutableUiState.update { it.copy(selectedItem = null) }

    private fun load(page: Int, replace: Boolean) {
        loadJob?.cancel()
        val sourceId = mutableUiState.value.selectedSourceId
        val category = mutableUiState.value.selectedCategory
        loadJob = viewModelScope.launch {
            val result = runCatching {
                if (sourceId == LiveSourceCatalog.HORNYSTUDENTS.id) {
                    hornyStudentsRepository.load(category, page)
                } else {
                    stripchatRepository.load(category, page)
                }
            }
            result.onSuccess { res ->
                if (page == 1 && res.items.isNotEmpty()) {
                    preferences.saveLiveFeed(sourceId, category.name, res.items)
                }
                mutableUiState.update { current ->
                    val update = nextLivePage(
                        currentItems = current.items,
                        incomingItems = res.items,
                        totalCount = res.totalCount,
                        replace = replace,
                    )
                    current.copy(
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
                mutableUiState.update { current ->
                    current.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = when (failure) {
                            is UnknownHostException -> "无法连接直播服务器，请检查网络连接"
                            is SocketTimeoutException -> "连接直播服务器超时，请稍后重试"
                            is SSLHandshakeException -> "直播连接加密失败，请稍后重试"
                            is IOException -> failure.message ?: "网络连接异常"
                            else -> "加载直播失败：${failure.message ?: "未知异常"}"
                        },
                    )
                }
            }
        }
    }

    private fun Throwable.userMessage(): String = when (this) {
        is UnknownHostException -> "当前网络无法连接 Stripchat，请检查网络或 DNS"
        is SocketTimeoutException -> "Stripchat 响应超时，请稍后重试"
        is SSLHandshakeException -> "Stripchat 安全连接失败，请确认系统时间和网络"
        is IOException -> message ?: "Stripchat 连接失败，请稍后重试"
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
