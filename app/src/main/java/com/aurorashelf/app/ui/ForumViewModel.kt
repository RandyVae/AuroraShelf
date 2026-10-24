package com.aurorashelf.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurorashelf.app.data.AppPreferences
import com.aurorashelf.app.data.forum.ForumRepository
import com.aurorashelf.app.model.ForumPage
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

class ForumViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ForumRepository()
    private val preferences = AppPreferences(application)
    private val mutableUiState = MutableStateFlow(ForumUiState())
    val uiState: StateFlow<ForumUiState> = mutableUiState.asStateFlow()

    private var loadJob: Job? = null
    private var prefetchJob: Job? = null
    private var prefetchedPage: Int = 0
    private var prefetchedResult: ForumPage? = null
    private var prefetchedAtMillis: Long = 0L

    /**
     * First visit: paint the 12 h first-page snapshot (if any) instantly, then always refresh.
     * A failed refresh keeps the cached posts visible together with the error message.
     */
    fun ensureLoaded() {
        val state = mutableUiState.value
        if (state.page != 0 || state.isLoading) return
        val cached = preferences.cachedForumFirstPage()
        if (cached != null && cached.posts.isNotEmpty() && state.posts.isEmpty()) {
            mutableUiState.update {
                it.copy(posts = cached.posts, canLoadMore = false, error = null)
            }
            logSafely { Log.i(TAG, "first-screen cache posts=${cached.posts.size}") }
        }
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        prefetchJob?.cancel()
        clearPrefetched()
        loadJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            var loggedFirstResult = false
            mutableUiState.update {
                it.copy(isLoading = true, isLoadingMore = false, error = null, unavailableSources = emptyList())
            }
            runCatching {
                repository.loadProgressively(1) { posts, unavailable, canLoadMore ->
                    if (posts.isEmpty()) return@loadProgressively
                    if (!loggedFirstResult) {
                        loggedFirstResult = true
                        logSafely {
                            Log.i(TAG, "first-screen network ms=${System.currentTimeMillis() - startedAt} posts=${posts.size}")
                        }
                    }
                    mutableUiState.update {
                        it.copy(
                            posts = posts,
                            page = 1,
                            canLoadMore = canLoadMore,
                            unavailableSources = unavailable,
                        )
                    }
                }
            }.onSuccess { page ->
                mutableUiState.update {
                    it.copy(
                        posts = page.posts,
                        page = 1,
                        canLoadMore = page.canLoadMore,
                        isLoading = false,
                        unavailableSources = page.unavailableSources,
                    )
                }
                if (page.posts.isNotEmpty()) preferences.saveForumFirstPage(page.posts)
                if (page.canLoadMore && page.posts.isNotEmpty()) prefetchPage(2)
            }.onFailure(::handleFailure)
        }
    }

    fun loadMore() {
        val state = mutableUiState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore || state.page < 1) return
        val targetPage = state.page + 1
        loadJob = viewModelScope.launch {
            mutableUiState.update { it.copy(isLoadingMore = true, error = null) }
            // Wait for an in-flight prefetch instead of issuing a duplicate network request.
            prefetchJob?.join()
            val prefetched = takeFreshPrefetched(targetPage)
            val result = prefetched?.let { Result.success(it) } ?: runCatching { repository.load(targetPage) }
            result.onSuccess { page ->
                mutableUiState.update { current ->
                    current.copy(
                        posts = (current.posts + page.posts)
                            .distinctBy { post -> post.sourceId to post.id }
                            .sortedWith(
                                compareByDescending<ForumPost> { post -> post.updatedEpochSeconds }
                                    .thenByDescending { post -> post.id.toLongOrNull() ?: 0L },
                            ),
                        page = targetPage,
                        canLoadMore = page.canLoadMore && page.posts.isNotEmpty(),
                        isLoadingMore = false,
                        unavailableSources = page.unavailableSources,
                    )
                }
                val updated = mutableUiState.value
                if (updated.page == targetPage && updated.canLoadMore && page.posts.isNotEmpty()) {
                    prefetchPage(targetPage + 1)
                }
            }.onFailure { failure ->
                if (failure is CancellationException) return@onFailure
                mutableUiState.update { it.copy(isLoadingMore = false, error = failure.userMessage()) }
            }
        }
    }

    fun open(post: ForumPost) = mutableUiState.update { it.copy(selectedPost = post) }
    fun closePost() = mutableUiState.update { it.copy(selectedPost = null) }

    /**
     * Prefetch exactly one next page in the background (2 min in-memory TTL). The slot is
     * single-purpose by design: a newer prefetch replaces the previous one.
     */
    private fun prefetchPage(page: Int) {
        if (page <= 1) return
        prefetchJob?.cancel()
        clearPrefetched()
        prefetchJob = viewModelScope.launch {
            runCatching { repository.load(page) }
                .onSuccess { result ->
                    if (result.posts.isEmpty()) return@onSuccess
                    prefetchedPage = page
                    prefetchedResult = result
                    prefetchedAtMillis = System.currentTimeMillis()
                    logSafely { Log.i(TAG, "prefetch page=$page posts=${result.posts.size}") }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) return@onFailure
                    logSafely { Log.i(TAG, "prefetch page=$page failed") }
                }
        }
    }

    private fun takeFreshPrefetched(page: Int): ForumPage? {
        val result = prefetchedResult
        val fresh = result != null && prefetchedPage == page &&
            System.currentTimeMillis() - prefetchedAtMillis <= PREFETCH_TTL_MS
        clearPrefetched()
        return result?.takeIf { fresh }
    }

    private fun clearPrefetched() {
        prefetchedPage = 0
        prefetchedResult = null
        prefetchedAtMillis = 0L
    }

    private fun handleFailure(failure: Throwable) {
        if (failure is CancellationException) return
        mutableUiState.update { it.copy(isLoading = false, isLoadingMore = false, error = failure.userMessage()) }
    }

    private fun logSafely(block: () -> Unit) {
        runCatching(block)
    }

    private fun Throwable.userMessage(): String = when (this) {
        is UnknownHostException -> "当前网络无法连接论坛，请检查网络或 DNS 后重试"
        is SocketTimeoutException -> "论坛响应超时，请稍后重试"
        is SSLHandshakeException -> "论坛安全连接失败，请确认系统时间和网络环境"
        is IOException -> message ?: "论坛连接失败，请稍后重试"
        else -> "论坛暂时无法加载，请稍后重试"
    }

    private companion object {
        private const val TAG = "ForumViewModel"
        private const val PREFETCH_TTL_MS = 2 * 60 * 1000L
    }
}
