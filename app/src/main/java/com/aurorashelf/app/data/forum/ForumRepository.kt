package com.aurorashelf.app.data.forum

import com.aurorashelf.app.model.ForumPage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class ForumRepository internal constructor(
    private val sources: List<ForumSource>,
) {
    constructor() : this(listOf(T66yForumSource(), T906ForumSource()))

    suspend fun load(page: Int): ForumPage = coroutineScope {
        require(page >= 1) { "页码必须从 1 开始" }
        val results = sources.map { source ->
            source to async { runCatching { source.load(page) } }
        }.map { (source, task) -> source to task.await() }

        val posts = results.flatMap { it.second.getOrDefault(emptyList()) }
            .distinctBy { it.sourceId to it.id }
            .sortedWith(compareByDescending<com.aurorashelf.app.model.ForumPost> { it.updatedEpochSeconds }
                .thenByDescending { it.id.toLongOrNull() ?: 0L })
        val failures = results.mapNotNull { (source, result) -> source.name.takeIf { result.isFailure } }
        if (posts.isEmpty() && failures.size == sources.size) {
            throw results.firstNotNullOf { it.second.exceptionOrNull() }
        }
        ForumPage(
            posts = posts,
            unavailableSources = failures,
            canLoadMore = results.any { it.second.getOrNull()?.isNotEmpty() == true },
        )
    }
}
