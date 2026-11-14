package com.aurorashelf.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aurorashelf.app.data.AppPreferences
import com.aurorashelf.app.data.comic.EncryptedComicAuthStore
import com.aurorashelf.app.data.comic.ComicRepository
import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSourceInfo
import com.aurorashelf.app.model.ComicSummary
import java.io.IOException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class ComicReaderSession(
    val details: ComicDetails,
    val chapterIndex: Int,
    val pages: List<ComicPage>,
) {
    val chapter get() = details.chapters[chapterIndex]
}

data class ComicUiState(
    val sources: List<ComicSourceInfo> = emptyList(),
    val selectedSourceId: String = AppPreferences.DEFAULT_COMIC_SOURCE_ID,
    val selectedCategoryId: String = "0",
    val comics: List<ComicSummary> = emptyList(),
    val query: String = "",
    val activeQuery: String = "",
    val page: Int = 0,
    val canLoadMore: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val details: ComicDetails? = null,
    val isDetailsLoading: Boolean = false,
    val detailsError: String? = null,
    val pendingComicForRetry: ComicSummary? = null,
    val reader: ComicReaderSession? = null,
    val isReaderLoading: Boolean = false,
    val readerError: String? = null,
    val isSourceAuthenticated: Boolean = true,
    val isAuthorizing: Boolean = false,
    val authError: String? = null,
)

class ComicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ComicRepository(EncryptedComicAuthStore(application))
    private val preferences = AppPreferences(application)
    val downloadManager = com.aurorashelf.app.data.comic.ComicDownloadManager.getInstance(application)
    val downloadedChapters = downloadManager.chapters
    private val initialSourceId = preferences.comicSourceId.takeIf { savedId ->
        repository.availableSources.any { it.id == savedId }
    } ?: AppPreferences.DEFAULT_COMIC_SOURCE_ID
    private val initialCategoryId = repository.availableSources.find { it.id == initialSourceId }
        ?.categories?.firstOrNull()?.id.orEmpty()
    private val initialCached = preferences.cachedComicFeed(initialSourceId, initialCategoryId)
    private val mutableUiState = MutableStateFlow(
        ComicUiState(
            sources = repository.availableSources,
            selectedSourceId = initialSourceId,
            selectedCategoryId = initialCategoryId,
            comics = initialCached?.comics.orEmpty(),
            page = if (initialCached != null) 1 else 0,
            isSourceAuthenticated = repository.isAuthenticated(initialSourceId),
        ),
    )
    val uiState: StateFlow<ComicUiState> = mutableUiState.asStateFlow()

    private var listJob: Job? = null
    private var detailsJob: Job? = null
    private var readerJob: Job? = null
    private var detailsRequestToken: Long = 0L
    private var readerRequestToken: Long = 0L

    /** Loads the comic feed once the comics tab is actually shown; safe to call repeatedly. */
    fun ensureLoaded() {
        val state = mutableUiState.value
        if (state.page == 0 && !state.isLoading) refresh()
    }

    fun selectSource(sourceId: String) {
        if (sourceId == mutableUiState.value.selectedSourceId) return
        if (repository.availableSources.none { it.id == sourceId }) return
        listJob?.cancel()
        detailsJob?.cancel()
        readerJob?.cancel()
        detailsRequestToken++
        readerRequestToken++
        val firstCategory = repository.availableSources.find { it.id == sourceId }
            ?.categories?.firstOrNull()?.id.orEmpty()
        mutableUiState.update {
            it.copy(
                selectedSourceId = sourceId,
                selectedCategoryId = firstCategory,
                comics = emptyList(),
                activeQuery = "",
                page = 0,
                canLoadMore = true,
                error = null,
                details = null,
                isDetailsLoading = false,
                detailsError = null,
                pendingComicForRetry = null,
                reader = null,
                isReaderLoading = false,
                readerError = null,
                isSourceAuthenticated = repository.isAuthenticated(sourceId),
                isAuthorizing = false,
                authError = null,
            )
        }
        preferences.comicSourceId = sourceId
        refresh()
    }

    fun authenticate(account: String, password: String) {
        val sourceId = mutableUiState.value.selectedSourceId
        if (!repository.isAuthenticationRequired(sourceId) || mutableUiState.value.isAuthorizing) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isAuthorizing = true, authError = null) }
            runCatching { repository.authenticate(sourceId, account, password) }
                .onSuccess {
                    mutableUiState.update { it.copy(isSourceAuthenticated = true, isAuthorizing = false, authError = null) }
                    refresh()
                }
                .onFailure { failure ->
                    if (failure is CancellationException) return@onFailure
                    mutableUiState.update { it.copy(isAuthorizing = false, authError = failure.userMessage()) }
                }
        }
    }

    fun signOut() {
        val sourceId = mutableUiState.value.selectedSourceId
        repository.signOut(sourceId)
        listJob?.cancel()
        mutableUiState.update {
            it.copy(
                comics = emptyList(),
                page = 0,
                isLoading = false,
                isLoadingMore = false,
                isSourceAuthenticated = repository.isAuthenticated(sourceId),
                authError = null,
            )
        }
    }

    fun selectCategory(categoryId: String) {
        if (categoryId == mutableUiState.value.selectedCategoryId) return
        detailsJob?.cancel()
        readerJob?.cancel()
        detailsRequestToken++
        readerRequestToken++
        val sourceId = mutableUiState.value.selectedSourceId
        val cached = preferences.cachedComicFeed(sourceId, categoryId)
        mutableUiState.update {
            it.copy(
                selectedCategoryId = categoryId,
                query = "",
                activeQuery = "",
                comics = cached?.comics ?: it.comics,
                page = if (cached != null) 1 else 0,
                details = null,
                isDetailsLoading = false,
                detailsError = null,
                pendingComicForRetry = null,
                reader = null,
                isReaderLoading = false,
                readerError = null,
            )
        }
        loadFirstPage("")
    }

    fun updateQuery(query: String) {
        mutableUiState.update { it.copy(query = query) }
    }

    fun submitSearch() {
        val query = mutableUiState.value.query.trim()
        loadFirstPage(query)
    }

    fun refresh() {
        val sourceId = mutableUiState.value.selectedSourceId
        if (!repository.isAuthenticated(sourceId)) {
            mutableUiState.update {
                it.copy(
                    comics = emptyList(),
                    page = 0,
                    isLoading = false,
                    isLoadingMore = false,
                    isSourceAuthenticated = false,
                )
            }
            return
        }
        loadFirstPage(mutableUiState.value.activeQuery)
    }

    fun loadMore() {
        val state = mutableUiState.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore || state.page == 0) return
        val nextPage = state.page + 1
        listJob = viewModelScope.launch {
            mutableUiState.update { it.copy(isLoadingMore = true, error = null) }
            runCatching { load(state.selectedSourceId, state.selectedCategoryId, state.activeQuery, nextPage) }
                .onSuccess { result ->
                    mutableUiState.update {
                        if (
                            it.selectedSourceId != state.selectedSourceId ||
                            it.selectedCategoryId != state.selectedCategoryId ||
                            it.activeQuery != state.activeQuery
                        ) it
                        else it.copy(
                            comics = (it.comics + result).distinctBy { comic -> comic.sourceId to comic.id },
                            page = nextPage,
                            canLoadMore = result.isNotEmpty(),
                            isLoadingMore = false,
                        )
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) return@onFailure
                    mutableUiState.update { it.copy(isLoadingMore = false, error = failure.userMessage()) }
                }
        }
    }

    fun openComic(comic: ComicSummary) {
        detailsJob?.cancel()
        val requestToken = ++detailsRequestToken
        detailsJob = viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isDetailsLoading = true,
                    detailsError = null,
                    pendingComicForRetry = comic,
                    error = null,
                )
            }
            runCatching { withTimeout(16_000L) { repository.details(comic) } }
                .onSuccess { details ->
                    if (detailsRequestToken != requestToken) return@onSuccess
                    if (details.chapters.isEmpty()) {
                        mutableUiState.update {
                            it.copy(
                                isDetailsLoading = false,
                                detailsError = "此漫画来源暂未提供章节列表。",
                            )
                        }
                        return@onSuccess
                    }
                    mutableUiState.update {
                        it.copy(
                            details = details,
                            isDetailsLoading = false,
                            detailsError = null,
                            pendingComicForRetry = null,
                        )
                    }
                    // 静默预热第 1 话，用户点击「开始阅读」时 0ms 瞬间进入
                    launch { runCatching { repository.prewarmChapter(details.comic, details.chapters[0].id) } }
                }
                .onFailure { failure ->
                    if (failure is CancellationException && failure !is TimeoutCancellationException) return@onFailure
                    if (detailsRequestToken != requestToken) return@onFailure
                    mutableUiState.update {
                        it.copy(
                            isDetailsLoading = false,
                            detailsError = failure.userMessage(),
                        )
                    }
                }
        }
    }

    fun cancelOpeningComic() {
        detailsJob?.cancel()
        detailsRequestToken++
        mutableUiState.update {
            it.copy(
                isDetailsLoading = false,
                detailsError = null,
                pendingComicForRetry = null,
            )
        }
    }

    fun dismissDetailsError() {
        mutableUiState.update {
            it.copy(
                detailsError = null,
                pendingComicForRetry = null,
            )
        }
    }

    fun retryOpeningComic() {
        val comic = mutableUiState.value.pendingComicForRetry ?: return
        openComic(comic)
    }

    fun closeDetails() {
        detailsJob?.cancel()
        detailsRequestToken++
        mutableUiState.update {
            it.copy(
                details = null,
                isDetailsLoading = false,
                detailsError = null,
                pendingComicForRetry = null,
                error = null,
            )
        }
    }

    fun openChapter(index: Int) {
        val details = mutableUiState.value.details ?: return
        if (index !in details.chapters.indices) return
        loadChapter(details, index)
    }

    fun previousChapter() {
        val reader = mutableUiState.value.reader ?: return
        loadChapter(reader.details, reader.chapterIndex - 1)
    }

    fun nextChapter() {
        val reader = mutableUiState.value.reader ?: return
        loadChapter(reader.details, reader.chapterIndex + 1)
    }

    fun retryChapter() {
        val reader = mutableUiState.value.reader ?: return
        loadChapter(reader.details, reader.chapterIndex)
    }

    fun closeReader() {
        readerJob?.cancel()
        readerRequestToken++
        mutableUiState.update { it.copy(reader = null, isReaderLoading = false, readerError = null) }
    }

    private fun loadFirstPage(query: String) {
        listJob?.cancel()
        val sourceId = mutableUiState.value.selectedSourceId
        val categoryId = mutableUiState.value.selectedCategoryId
        val cached = if (query.isBlank()) preferences.cachedComicFeed(sourceId, categoryId) else null
        listJob = viewModelScope.launch {
            mutableUiState.update { current ->
                val fallbackComics = when {
                    query.isNotBlank() -> emptyList()
                    current.selectedSourceId == sourceId && current.selectedCategoryId == categoryId && current.comics.isNotEmpty() -> current.comics
                    cached != null -> cached.comics
                    else -> emptyList()
                }
                current.copy(
                    comics = fallbackComics,
                    activeQuery = query,
                    page = if (fallbackComics.isNotEmpty()) 1 else 0,
                    canLoadMore = true,
                    isLoading = true,
                    isLoadingMore = false,
                    error = null,
                )
            }
            runCatching { load(sourceId, categoryId, query, 1) }
                .onSuccess { result ->
                    if (query.isBlank() && result.isNotEmpty()) {
                        preferences.saveComicFeed(sourceId, categoryId, result)
                    }
                    mutableUiState.update {
                        if (
                            it.selectedSourceId != sourceId ||
                            it.selectedCategoryId != categoryId ||
                            it.activeQuery != query
                        ) it
                        else it.copy(comics = result, page = 1, canLoadMore = result.isNotEmpty(), isLoading = false)
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) return@onFailure
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            error = failure.userMessage(),
                            isSourceAuthenticated = repository.isAuthenticated(sourceId),
                        )
                    }
                }
        }
    }

    fun downloadChapter(chapter: com.aurorashelf.app.model.ComicChapter) {
        val details = mutableUiState.value.details ?: mutableUiState.value.reader?.details ?: return
        downloadManager.startDownload(details.comic, chapter, repository)
    }

    fun downloadAllChapters() {
        val details = mutableUiState.value.details ?: mutableUiState.value.reader?.details ?: return
        downloadManager.startDownloadAll(details, repository)
    }

    fun removeComicDownload(id: String) {
        downloadManager.removeDownload(id)
    }

    private fun loadChapter(details: ComicDetails, index: Int) {
        if (index !in details.chapters.indices) return
        val chapter = details.chapters[index]
        readerJob?.cancel()

        // 1. 离线优先拦截：若本章节已完整下载，直接加载本地沙盒图片，实现 100% 离线秒开
        if (downloadManager.isDownloaded(details.comic.id, chapter.id)) {
            val localPages = downloadManager.getLocalPages(details.comic, chapter)
            if (localPages.isNotEmpty()) {
                mutableUiState.update {
                    it.copy(
                        reader = ComicReaderSession(details, index, localPages),
                        isReaderLoading = false,
                        readerError = null,
                    )
                }
                prewarmNextChapterIfNeeded(details, index)
                return
            }
        }

        // 2. 内存秒开拦截：若前置预热或历史已解析此章节，0ms 瞬间渲染
        val cachedPages = repository.getCachedChapter(details.comic, chapter.id)
        if (!cachedPages.isNullOrEmpty()) {
            mutableUiState.update {
                it.copy(
                    reader = ComicReaderSession(details, index, cachedPages),
                    isReaderLoading = false,
                    readerError = null,
                )
            }
            prewarmNextChapterIfNeeded(details, index)
            return
        }

        val requestToken = ++readerRequestToken
        mutableUiState.update {
            it.copy(
                reader = ComicReaderSession(details, index, emptyList()),
                isReaderLoading = true,
                readerError = null,
            )
        }
        readerJob = viewModelScope.launch {
            runCatching { withTimeout(18_000L) { repository.chapter(details.comic, chapter.id) } }
                .onSuccess { pages ->
                    if (readerRequestToken != requestToken) return@onSuccess
                    mutableUiState.update {
                        it.copy(
                            reader = ComicReaderSession(details, index, pages),
                            isReaderLoading = false,
                            readerError = if (pages.isEmpty()) "该章节没有返回图片" else null,
                        )
                    }
                    if (pages.isNotEmpty()) {
                        prewarmNextChapterIfNeeded(details, index)
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException && failure !is TimeoutCancellationException) return@onFailure
                    if (readerRequestToken != requestToken) return@onFailure
                    // 网络失败时，若本地有部分或全部缓存图片，进行兜底恢复
                    val localPages = downloadManager.getLocalPages(details.comic, chapter)
                    if (localPages.isNotEmpty()) {
                        mutableUiState.update {
                            it.copy(
                                reader = ComicReaderSession(details, index, localPages),
                                isReaderLoading = false,
                                readerError = null,
                            )
                        }
                    } else {
                        mutableUiState.update { it.copy(isReaderLoading = false, readerError = failure.userMessage()) }
                    }
                }
        }
    }

    private fun prewarmNextChapterIfNeeded(details: ComicDetails, index: Int) {
        if (index + 1 in details.chapters.indices) {
            viewModelScope.launch {
                runCatching {
                    repository.prewarmChapter(details.comic, details.chapters[index + 1].id)
                }
            }
        }
    }

    private suspend fun load(sourceId: String, categoryId: String, query: String, page: Int) =
        if (query.isBlank()) repository.browse(sourceId, categoryId, page) else repository.search(sourceId, query, page)

    private fun Throwable.userMessage(): String {
        val causes = generateSequence(this as Throwable?) { it.cause }.toList()
        return when {
            causes.any { it is TimeoutCancellationException || it is java.net.SocketTimeoutException } ->
                "漫画源响应超时，请稍后重试或切换来源"
            causes.any { it is SSLHandshakeException || it is CertPathValidatorException } ->
                "漫画源证书验证失败，请切换来源或稍后重试"
            causes.any { it is UnknownHostException } ->
                "无法连接漫画源，请检查网络或切换来源"
            this is IOException -> com.aurorashelf.app.data.VideoRepository.friendlyLoadErrorMessage(this, "漫画源网络连接失败")
            else -> com.aurorashelf.app.data.VideoRepository.friendlyLoadErrorMessage(this, "漫画源解析失败")
        }
    }
}
