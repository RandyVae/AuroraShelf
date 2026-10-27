package com.aurorashelf.app.data.forum

import android.util.Log
import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.model.ForumPage
import com.aurorashelf.app.model.ForumPost
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class ForumRepository internal constructor(
    private val sources: List<ForumSource>,
) {
    constructor() : this(listOf(T66yForumSource(), T906ForumSource()))

    /** Existing all-at-once API, kept for compatibility and existing tests. */
    suspend fun load(page: Int): ForumPage = coroutineScope {
        require(page >= 1) { "页码必须从 1 开始" }
        val outcomes = sources.mapIndexed { index, source ->
            val task = async { loadSource(source, page) }
            Triple(index, source, task)
        }.map { (index, source, task) -> SourceOutcome(index, source, task.await()) }
        pageOrThrow(outcomes)
    }

    /**
     * Progressive variant: [onUpdate] fires as soon as each source completes, with the merged
     * (de-duplicated, newest-first) view of everything finished so far. The fastest source can
     * paint the first screen while the slowest one is still loading.
     */
    suspend fun loadProgressively(
        page: Int,
        onUpdate: suspend (posts: List<ForumPost>, unavailable: List<String>, canLoadMore: Boolean) -> Unit,
    ): ForumPage = coroutineScope {
        require(page >= 1) { "页码必须从 1 开始" }
        val completions = Channel<SourceOutcome>(sources.size.coerceAtLeast(1))
        sources.mapIndexed { index, source ->
            async {
                val outcome = SourceOutcome(index, source, loadSource(source, page))
                completions.send(outcome)
            }
        }
        val outcomes = mutableListOf<SourceOutcome>()
        repeat(sources.size) {
            outcomes += completions.receive()
            val merged = mergePage(outcomes)
            if (merged.posts.isNotEmpty()) {
                onUpdate(merged.posts, merged.unavailableSources, merged.canLoadMore)
            }
        }
        completions.close()
        pageOrThrow(outcomes)
    }

    private suspend fun loadSource(source: ForumSource, page: Int): Result<List<ForumPost>> {
        val startedAt = System.currentTimeMillis()
        val result = runCatching { source.load(page) }
        val elapsedMs = System.currentTimeMillis() - startedAt
        logSafely {
            val err = result.exceptionOrNull()
            if (err != null) {
                Log.e(TAG, "source=${source.id} page=$page ms=$elapsedMs FAILED: ${err.javaClass.simpleName}: ${err.message}", err)
            } else {
                Log.i(TAG, "source=${source.id} page=$page ms=$elapsedMs posts=${result.getOrNull()?.size ?: 0}")
            }
        }
        return result
    }

    private data class SourceOutcome(
        val index: Int,
        val source: ForumSource,
        val posts: Result<List<ForumPost>>,
    )

    /** Shared merge rules: de-dupe by (source, id), newest first, aggregate failures/flags. */
    private fun mergePage(outcomes: List<SourceOutcome>): ForumPage {
        val ordered = outcomes.sortedBy { it.index }
        val posts = ordered.flatMap { it.posts.getOrDefault(emptyList()) }
            .distinctBy { it.sourceId to it.id }
            .sortedWith(
                compareByDescending<ForumPost> { it.updatedEpochSeconds }
                    .thenByDescending { it.id.toLongOrNull() ?: 0L },
            )
        val failures = ordered.mapNotNull { outcome -> outcome.source.name.takeIf { outcome.posts.isFailure } }
        return ForumPage(
            posts = posts,
            unavailableSources = failures,
            canLoadMore = ordered.any { it.posts.getOrNull()?.isNotEmpty() == true },
        )
    }

    private fun pageOrThrow(outcomes: List<SourceOutcome>): ForumPage {
        val page = mergePage(outcomes)
        if (page.posts.isEmpty() && page.unavailableSources.size == sources.size) {
            throw outcomes.sortedBy { it.index }.firstNotNullOf { it.posts.exceptionOrNull() }
        }
        return page
    }

    /** Android's Log is not mocked in plain JVM tests; never let observability break loading. */
    private fun logSafely(block: () -> Unit) {
        runCatching(block)
    }

    private companion object {
        private const val TAG = "ForumRepository"
    }
}

/**
 * Shared HTML fetcher for forum sources: one OkHttp connection pool replaces the per-page
 * `Jsoup.connect` connections, so paging/refresh reuse DNS and TLS sessions.
 */
internal object ForumHttp {
    internal val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .build()
    }

    fun fetchDocument(url: String, mobileLayout: Boolean = false): Document {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", VideoRepository.USER_AGENT)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .apply {
                // t66y serves its mobile layout only when the ismob cookie is present.
                if (mobileLayout && url.contains("t66y.com", ignoreCase = true)) {
                    header("Cookie", "ismob=1")
                }
            }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("论坛返回 HTTP ${response.code}")
            val body = response.body ?: throw IOException("论坛返回空响应")
            // charsetName = null lets Jsoup sniff <meta charset>; legacy GBK pages stay readable.
            return Jsoup.parse(body.byteStream(), null, response.request.url.toString())
        }
    }
}
