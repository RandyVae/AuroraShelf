package com.aurorashelf.app

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.aurorashelf.app.data.PlaybackResolver
import com.aurorashelf.app.data.VideoRepository
import com.aurorashelf.app.data.comic.ComicRepository
import com.aurorashelf.app.data.forum.ForumRepository
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.FeedCategory
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Opt-in source inventory audit; logs only stage results, never remote content or URLs. */
class PreReleaseSourceAuditTest {
    @Test fun ikmmhReadsThroughFirstChapter(): Unit = runBlocking {
        if (!isSourceAuditEnabled()) return@runBlocking
        val repository = ComicRepository()
        val first = repository.browse("ikmmh", "全部", 1)
        check(first.isNotEmpty())
        val details = repository.details(first.first())
        check(details.chapters.isNotEmpty())
        val pages = repository.chapter(details.comic, details.chapters.first().id)
        check(pages.isNotEmpty())
        Log.i(TAG, "AUDIT|爱看漫|fixed-list-details-chapter|OK|pages=${pages.size}")
    }

    @Test fun copyComicReadsThroughFirstChapter(): Unit = runBlocking {
        if (!isSourceAuditEnabled()) return@runBlocking
        val repository = ComicRepository()
        val first = repository.browse("copy", "", 1)
        check(first.isNotEmpty())
        val second = repository.browse("copy", "", 2)
        check(second.any { candidate -> first.none { it.id == candidate.id } })
        val details = repository.details(first.first())
        check(details.chapters.isNotEmpty())
        val pages = repository.chapter(details.comic, details.chapters.first().id)
        check(pages.isNotEmpty())
        Log.i(TAG, "AUDIT|拷贝漫画|fixed-list-details-chapter|OK|pages=${pages.size}")
    }

    @Test fun repairedPaginationReturnsNewRecords() = runBlocking {
        if (!isSourceAuditEnabled()) return@runBlocking
        val expected = setOf("KissJAV", "Pornhub", "SpankBang", "EPORNER", "MRDOUGA", "XNXX", "TOKYO Motion", "85po", "JAV-NONI", "XVideos", "xHamster")
        val repository = VideoRepository()
        for (source in ContentSourceCatalog.sources.filter { it.name in expected }) {
            val first = repository.loadFeed(source.baseUrl, FeedCategory.RECENT, 1).map { it.pageUrl }.toSet()
            val second = repository.loadFeed(source.baseUrl, FeedCategory.RECENT, 2).map { it.pageUrl }.toSet()
            val novel = second - first
            Log.i(TAG, "AUDIT|${source.name}|page2-new|count=${novel.size}")
            check(novel.isNotEmpty()) { "${source.name} second page contains no new records" }
        }
    }

    @Test fun auditForumLists() = runBlocking {
        if (!isSourceAuditEnabled()) return@runBlocking
        for (page in 1..2) {
            val result = ForumRepository().load(page)
            for (sourceId in listOf("t66y", "t906")) {
                val count = result.posts.count { it.sourceId == sourceId }
                Log.i(TAG, "AUDIT|$sourceId|page$page|count=$count")
                check(count > 0) { "$sourceId returned no posts on page $page" }
            }
            check(result.unavailableSources.isEmpty()) { "Forum source unavailable" }
        }
    }

    @Test fun auditAllConfiguredSources() = runBlocking {
        if (!isSourceAuditEnabled()) return@runBlocking
        val videos = VideoRepository()
        for (source in ContentSourceCatalog.sources) {
            val records = stage(source.name, "list") {
                videos.loadFeed(source.baseUrl, FeedCategory.RECENT, 1).also { check(it.isNotEmpty()) }
            }
            stage(source.name, "page2") { videos.loadFeed(source.baseUrl, FeedCategory.RECENT, 2) }
            if (source.extractorServiceName != null) {
                stage(source.name, "search") { videos.search(source.baseUrl, "test", 1) }
            } else Log.i(TAG, "AUDIT|${source.name}|search|LOCAL_ONLY")
            if (records != null) {
                stage(source.name, "resolve") {
                    val resolver = PlaybackResolver()
                    var playable = false
                    var lastFailure: Throwable? = null
                    for (record in records.take(5)) {
                        try {
                            resolver.resolve(record.pageUrl)
                            playable = true
                            break
                        } catch (failure: Exception) {
                            lastFailure = failure
                        }
                    }
                    if (!playable) throw IllegalStateException("No playable item in bounded sample", lastFailure)
                }
            } else Log.i(TAG, "AUDIT|${source.name}|resolve|BLOCKED_LIST")
        }
        val comics = ComicRepository()
        for (source in comics.availableSources) {
            if (source.requiresAuthentication) {
                Log.i(TAG, "AUDIT|${source.name}|all|NEEDS_ACCOUNT")
                continue
            }
            val category = source.categories.first().id
            val records = stage(source.name, "list") {
                comics.browse(source.id, category, 1).also { check(it.isNotEmpty()) }
            }
            stage(source.name, "page2") { comics.browse(source.id, category, 2) }
            stage(source.name, "search") { comics.search(source.id, "test", 1) }
            if (records != null) {
                val details = stage(source.name, "details") { comics.details(records.first()) }
                if (details != null) stage(source.name, "chapter") {
                    comics.chapter(details.comic, details.chapters.first().id).also { check(it.isNotEmpty()) }
                }
            }
        }
    }

    private suspend fun <T> stage(source: String, stage: String, action: suspend () -> T): T? {
        return try {
            action().also { result ->
                val count = (result as? Collection<*>)?.size
                Log.i(TAG, "AUDIT|$source|$stage|OK" + (count?.let { "|count=$it" } ?: ""))
            }
        } catch (failure: Exception) {
            Log.i(TAG, "AUDIT|$source|$stage|FAIL|${failure.javaClass.simpleName}")
            null
        }
    }

    private fun isSourceAuditEnabled(): Boolean =
        InstrumentationRegistry.getArguments().getString("sourceAudit") == "true"

    private companion object { const val TAG = "PreReleaseAudit" }
}
