package com.aurorashelf.app.data.comic

import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSourceInfo
import com.aurorashelf.app.model.ComicSummary

internal interface ComicSource {
    val info: ComicSourceInfo

    suspend fun browse(categoryId: String, page: Int): List<ComicSummary>

    suspend fun search(query: String, page: Int): List<ComicSummary>

    suspend fun details(comic: ComicSummary): ComicDetails

    suspend fun chapter(comic: ComicSummary, chapterId: String): List<ComicPage>
}

internal interface AuthenticatedComicSource : ComicSource {
    val isAuthenticated: Boolean

    suspend fun authenticate(account: String, password: String)

    fun signOut()
}
