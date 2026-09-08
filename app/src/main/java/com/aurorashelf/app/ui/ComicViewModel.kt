package com.aurorashelf.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComicReaderSession(
    val details: ComicDetails,
    val chapterIndex: Int,
    val pages: List<ComicPage>,
) {
    val chapter get() = details.chapters[chapterIndex]
}

data class ComicUiState(
    val sources: List<ComicSourceInfo> = emptyList(),
    val selectedSourceId: String = "komiic",
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
    val reader: ComicReaderSession? = null,
    val isReaderLoading: Boolean = false,
    val readerError: String? = null,
    val isSourceAuthenticated: Boolean = true,
    val isAuthorizing: Boolean = false,
    val authError: String? = null,
)

class ComicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ComicRepository(EncryptedComicAuthStore(application))
    private val mutableUiState = MutableStateFlow(
        ComicUiState(sources = repository.availableSources),
    )
    val uiState: StateFlow<ComicUiState> = mutableUiState.asStateFlow()

    private var listJob: Job? = null
    private var detailsJob: Job? = null
    private var readerJob: Job? = null

    init {
        refresh()
    }

    fun selectSource(sourceId: String) {
        if (sourceId == mutableUiState.value.selectedSourceId) return
        listJob?.cancel()
        detailsJob?.cancel()
        readerJob?.cancel()
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
                reader = null,
                isSourceAuthenticated = repository.isAuthenticated(sourceId),
                isAuthorizing = false,
                authError = null,
            )
        }
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
        mutableUiState.update { it.copy(selectedCategoryId = categoryId, query = "", activeQuery = "") }
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
        detailsJob = viewModelScope.launch {
            mutableUiState.update { it.copy(isDetailsLoading = true, error = null) }
            runCatching { repository.details(comic) }
                .onSuccess { details -> mutableUiState.update { it.copy(details = details, isDetailsLoading = false) } }
                .onFailure { failure ->
                    if (failure is CancellationException) return@onFailure
                    mutableUiState.update { it.copy(isDetailsLoading = false, error = failure.userMessage()) }
                }
        }
    }

    fun closeDetails() {
        detailsJob?.cancel()
        mutableUiState.update { it.copy(details = null, isDetailsLoading = false, error = null) }
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
        mutableUiState.update { it.copy(reader = null, isReaderLoading = false, readerError = null) }
    }

    private fun loadFirstPage(query: String) {
        listJob?.cancel()
        val sourceId = mutableUiState.value.selectedSourceId
        val categoryId = mutableUiState.value.selectedCategoryId
        listJob = viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    comics = emptyList(),
                    activeQuery = query,
                    page = 0,
                    canLoadMore = true,
                    isLoading = true,
                    isLoadingMore = false,
                    error = null,
                )
            }
            runCatching { load(sourceId, categoryId, query, 1) }
                .onSuccess { result ->
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

    private fun loadChapter(details: ComicDetails, index: Int) {
        if (index !in details.chapters.indices) return
        val chapter = details.chapters[index]
        readerJob?.cancel()
        mutableUiState.update {
            it.copy(
                reader = ComicReaderSession(details, index, emptyList()),
                isReaderLoading = true,
                readerError = null,
            )
        }
        readerJob = viewModelScope.launch {
            runCatching { repository.chapter(details.comic, chapter.id) }
                .onSuccess { pages ->
                    mutableUiState.update {
                        it.copy(
                            reader = ComicReaderSession(details, index, pages),
                            isReaderLoading = false,
                            readerError = if (pages.isEmpty()) "该章节没有返回图片" else null,
                        )
                    }
                }
                .onFailure { failure ->
                    if (failure is CancellationException) return@onFailure
                    mutableUiState.update { it.copy(isReaderLoading = false, readerError = failure.userMessage()) }
                }
        }
    }

    private suspend fun load(sourceId: String, categoryId: String, query: String, page: Int) =
        if (query.isBlank()) repository.browse(sourceId, categoryId, page) else repository.search(sourceId, query, page)

    private fun Throwable.userMessage(): String {
        val causes = generateSequence(this as Throwable?) { it.cause }.toList()
        return when {
            causes.any { it is SSLHandshakeException || it is CertPathValidatorException } ->
                "漫画源证书验证失败，请切换来源或稍后重试"
            causes.any { it is UnknownHostException } ->
                "无法连接漫画源，请检查网络或切换来源"
            this is IOException -> message ?: "漫画源网络连接失败"
            else -> message?.takeIf(String::isNotBlank) ?: "漫画源解析失败"
        }
    }
}
