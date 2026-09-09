package com.aurorashelf.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurorashelf.app.data.forum.ForumRepository
import com.aurorashelf.app.model.ForumPost
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

data class ForumUiState(
    val posts: List<ForumPost> = emptyList(),
    val page: Int = 0,
    val canLoadMore: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val unavailableSources: List<String> = emptyList(),
    val error: String? = null,
    val selectedPost: ForumPost? = null,
)

class ForumViewModel(
    private val repository: ForumRepository = ForumRepository(),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ForumUiState())
    val uiState: StateFlow<ForumUiState> = mutableUiState.asStateFlow()
    private var loadJob: Job? = null

    fun ensureLoaded() {
        val state = mutableUiState.value
        if (state.page == 0 && !state.isLoading) refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableUiState.update {
                it.copy(isLoading = true, isLoadingMore = false, error = null, unavailableSources = emptyList())
            }
            runCatching { repository.load(1) }
                .onSuccess { page ->
                    mutableUiState.update {
                        it.copy(
                            posts = page.posts,
                            page = 1,
                            canLoadMore = page.canLoadMore,
                            isLoading = false,
                            unavailableSources = page.unavailableSources,
                        )
                    }
                }
                .onFailure(::handleFailure)
        }
    }

    fun loadMore() {
        val state = mutableUiState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore || state.page < 1) return
        loadJob = viewModelScope.launch {
            mutableUiState.update { it.copy(isLoadingMore = true, error = null) }
            runCatching { repository.load(state.page + 1) }
                .onSuccess { page ->
                    mutableUiState.update {
                        it.copy(
                            posts = (it.posts + page.posts)
                                .distinctBy { post -> post.sourceId to post.id }
                                .sortedWith(compareByDescending<ForumPost> { post -> post.updatedEpochSeconds }
                                    .thenByDescending { post -> post.id.toLongOrNull() ?: 0L }),
                            page = state.page + 1,
                            canLoadMore = page.canLoadMore && page.posts.isNotEmpty(),
                            isLoadingMore = false,
                            unavailableSources = page.unavailableSources,
                        )
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) return@onFailure
                    mutableUiState.update { it.copy(isLoadingMore = false, error = failure.userMessage()) }
                }
        }
    }

    fun open(post: ForumPost) = mutableUiState.update { it.copy(selectedPost = post) }
    fun closePost() = mutableUiState.update { it.copy(selectedPost = null) }

    private fun handleFailure(failure: Throwable) {
        if (failure is CancellationException) return
        mutableUiState.update { it.copy(isLoading = false, isLoadingMore = false, error = failure.userMessage()) }
    }

    private fun Throwable.userMessage(): String = when (this) {
        is UnknownHostException -> "当前网络无法连接论坛，请检查网络或 DNS 后重试"
        is SocketTimeoutException -> "论坛响应超时，请稍后重试"
        is SSLHandshakeException -> "论坛安全连接失败，请确认系统时间和网络环境"
        is IOException -> message ?: "论坛连接失败，请稍后重试"
        else -> "论坛暂时无法加载，请稍后重试"
    }
}
