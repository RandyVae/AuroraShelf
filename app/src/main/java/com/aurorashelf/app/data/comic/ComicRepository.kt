package com.aurorashelf.app.data.comic

import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSourceInfo
import com.aurorashelf.app.model.ComicSummary

class ComicRepository internal constructor(
    private val sources: List<ComicSource>,
) {
    constructor() : this(EmptyComicAuthStore)

    internal constructor(authStore: ComicAuthStore) : this(
        listOf(
            KomiicComicSource(),
            BaoziComicSource(),
            CopyComicSource(),
            IkmmhComicSource(),
            PicacgComicSource(authStore),
            EhentaiComicSource(),
            JmComicSource(),
        ),
    )

    val availableSources: List<ComicSourceInfo> = sources.map(ComicSource::info)

    suspend fun browse(sourceId: String, categoryId: String, page: Int): List<ComicSummary> =
        source(sourceId).browse(categoryId, page)

    suspend fun search(sourceId: String, query: String, page: Int): List<ComicSummary> =
        source(sourceId).search(query.trim(), page)

    suspend fun details(comic: ComicSummary): ComicDetails = source(comic.sourceId).details(comic)

    suspend fun chapter(comic: ComicSummary, chapterId: String): List<ComicPage> =
        source(comic.sourceId).chapter(comic, chapterId)

    fun isAuthenticationRequired(sourceId: String): Boolean = source(sourceId) is AuthenticatedComicSource

    fun isAuthenticated(sourceId: String): Boolean =
        (source(sourceId) as? AuthenticatedComicSource)?.isAuthenticated ?: true

    suspend fun authenticate(sourceId: String, account: String, password: String) {
        val authenticatedSource = source(sourceId) as? AuthenticatedComicSource
            ?: error("该漫画源不需要账号授权")
        authenticatedSource.authenticate(account, password)
    }

    fun signOut(sourceId: String) {
        (source(sourceId) as? AuthenticatedComicSource)?.signOut()
    }

    private fun source(id: String): ComicSource = sources.find { it.info.id == id }
        ?: error("未知漫画源：$id")
}
