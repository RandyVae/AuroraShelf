package com.aurorashelf.app.data.comic

import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSourceInfo
import com.aurorashelf.app.model.ComicSummary

class ComicRepository internal constructor(
    private val sources: List<ComicSource>,
) {
    constructor() : this(
        listOf(
            KomiicComicSource(),
            BaoziComicSource(),
            CopyComicSource(),
            IkmmhComicSource(),
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

    private fun source(id: String): ComicSource = sources.find { it.info.id == id }
        ?: error("未知漫画源：$id")
}
