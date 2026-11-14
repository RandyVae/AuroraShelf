package com.aurorashelf.app.data.comic

import com.aurorashelf.app.model.ComicDetails
import com.aurorashelf.app.model.ComicPage
import com.aurorashelf.app.model.ComicSourceInfo
import com.aurorashelf.app.model.ComicSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

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
            EightMusesComicSource(),
            GedeComixComicSource(),
            Dongman91ComicSource(),
        ),
    )

    private val chapterCache = object : LinkedHashMap<String, List<ComicPage>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<ComicPage>>?): Boolean = size > 64
    }
    private val inFlightChapters = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<List<ComicPage>>>()

    val availableSources: List<ComicSourceInfo> = sources.map(ComicSource::info)

    suspend fun browse(sourceId: String, categoryId: String, page: Int): List<ComicSummary> =
        source(sourceId).browse(categoryId, page)

    suspend fun search(sourceId: String, query: String, page: Int): List<ComicSummary> =
        source(sourceId).search(query.trim(), page)

    suspend fun details(comic: ComicSummary): ComicDetails =
        withTimeout(DETAILS_TIMEOUT_MS) { source(comic.sourceId).details(comic) }

    fun getCachedChapter(comic: ComicSummary, chapterId: String): List<ComicPage>? {
        val key = "${comic.sourceId}:${comic.id}:$chapterId"
        return synchronized(chapterCache) { chapterCache[key] }
    }

    suspend fun prewarmChapter(comic: ComicSummary, chapterId: String) = coroutineScope {
        val key = "${comic.sourceId}:${comic.id}:$chapterId"
        synchronized(chapterCache) {
            if (!chapterCache[key].isNullOrEmpty()) return@coroutineScope
        }
        lateinit var deferred: kotlinx.coroutines.Deferred<List<ComicPage>>
        deferred = async(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                val pages = withTimeout(CHAPTER_TIMEOUT_MS) {
                    source(comic.sourceId).chapter(comic, chapterId)
                }
                if (pages.isNotEmpty()) {
                    synchronized(chapterCache) {
                        chapterCache[key] = pages
                    }
                }
                pages
            } finally {
                inFlightChapters.remove(key, deferred)
            }
        }
        val winner = inFlightChapters.putIfAbsent(key, deferred)
        if (winner == null) {
            deferred.start()
        } else {
            deferred.cancel()
        }
    }

    suspend fun chapter(comic: ComicSummary, chapterId: String): List<ComicPage> {
        val key = "${comic.sourceId}:${comic.id}:$chapterId"
        synchronized(chapterCache) {
            chapterCache[key]?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        inFlightChapters[key]?.takeIf { it.isActive }?.let { ongoing ->
            return try {
                withTimeout(CHAPTER_TIMEOUT_MS) { ongoing.await() }
            } catch (error: Throwable) {
                inFlightChapters.remove(key, ongoing)
                ongoing.cancel()
                throw error
            }
        }
        val pages = withTimeout(CHAPTER_TIMEOUT_MS) {
            source(comic.sourceId).chapter(comic, chapterId)
        }
        if (pages.isNotEmpty()) {
            synchronized(chapterCache) {
                chapterCache[key] = pages
            }
        }
        return pages
    }

    private companion object {
        const val DETAILS_TIMEOUT_MS = 16_000L
        const val CHAPTER_TIMEOUT_MS = 18_000L
    }

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
