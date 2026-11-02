package com.aurorashelf.app.data

import com.aurorashelf.app.data.comic.ComicRepository
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.FeedCategory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class FullSystemSourceProbeTest {

    @Test
    fun probeAllVideoAndComicSourcesConcurrently() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE_SOURCE_TESTS") == "1")

        println("=================================================================")
        println("       AURORA SYSTEM FULL AUDIT: VIDEO & COMIC SOURCES           ")
        println("=================================================================")

        val videoRepo = VideoRepository()
        val resolver = PlaybackResolver()
        val comicRepo = ComicRepository()

        // 1. Concurrently probe all video sources
        println("\n>>> [1/2] PROBING ALL ${ContentSourceCatalog.sources.size} VIDEO SOURCES CONCURRENTLY...")
        val videoResults = coroutineScope {
            ContentSourceCatalog.sources.map { source ->
                async {
                    val startMs = System.currentTimeMillis()
                    val categories = FeedCategory.availableFor(source.baseUrl)
                    val firstCat = categories.firstOrNull() ?: FeedCategory.RECENT
                    try {
                        val items = withTimeoutOrNull(15000L) {
                            videoRepo.loadFeed(source.baseUrl, firstCat, 1)
                        } ?: return@async VideoProbeResult(source.name, source.baseUrl, false, 0, false, "Feed Timeout (>15s)", System.currentTimeMillis() - startMs)

                        var playbackOk = false
                        var playbackMsg = ""
                        val first = items.firstOrNull()
                        if (first != null) {
                            try {
                                val pb = withTimeoutOrNull(25000L) {
                                    resolver.resolve(first.pageUrl)
                                }
                                if (pb != null && pb.url.isNotBlank()) {
                                    playbackOk = true
                                    playbackMsg = "OK (${pb.url.take(30)}...)"
                                } else {
                                    playbackMsg = "Playback Timeout / Empty URL"
                                }
                            } catch (e: Exception) {
                                playbackMsg = "${e::class.java.simpleName}: ${e.message}"
                            }
                        } else {
                            playbackMsg = "No items to resolve playback"
                        }

                        VideoProbeResult(
                            name = source.name,
                            baseUrl = source.baseUrl,
                            feedOk = items.isNotEmpty(),
                            itemCount = items.size,
                            playbackOk = playbackOk,
                            detail = playbackMsg,
                            durationMs = System.currentTimeMillis() - startMs,
                        )
                    } catch (e: Exception) {
                        VideoProbeResult(
                            name = source.name,
                            baseUrl = source.baseUrl,
                            feedOk = false,
                            itemCount = 0,
                            playbackOk = false,
                            detail = "Feed Error: ${e::class.java.simpleName}: ${e.message}",
                            durationMs = System.currentTimeMillis() - startMs,
                        )
                    }
                }
            }.awaitAll()
        }

        // 2. Concurrently probe all comic sources
        println("\n>>> [2/2] PROBING ALL ${comicRepo.availableSources.size} COMIC SOURCES CONCURRENTLY...")
        val comicResults = coroutineScope {
            comicRepo.availableSources.map { info ->
                async {
                    val startMs = System.currentTimeMillis()
                    if (info.requiresAuthentication) {
                        return@async ComicProbeResult(
                            id = info.id,
                            name = info.name,
                            browseOk = true,
                            itemCount = 0,
                            chapterCount = 0,
                            pageCount = 0,
                            detail = "Requires Authentication (Picacg API Key / Login flow)",
                            durationMs = System.currentTimeMillis() - startMs,
                        )
                    }
                    val firstCat = info.categories.firstOrNull()?.id ?: ""
                    try {
                        val comics = withTimeoutOrNull(15000L) {
                            comicRepo.browse(info.id, firstCat, 1)
                        } ?: return@async ComicProbeResult(info.id, info.name, false, 0, 0, 0, "Browse Timeout (>15s)", System.currentTimeMillis() - startMs)

                        var chapterCount = 0
                        var pageCount = 0
                        var detailMsg = "OK"

                        val first = comics.firstOrNull()
                        if (first != null) {
                            try {
                                val details = withTimeoutOrNull(15000L) {
                                    comicRepo.details(first)
                                }
                                if (details != null) {
                                    chapterCount = details.chapters.size
                                    val firstChapter = details.chapters.firstOrNull()
                                    if (firstChapter != null) {
                                        val pages = withTimeoutOrNull(15000L) {
                                            comicRepo.chapter(first, firstChapter.id)
                                        }
                                        if (pages != null) {
                                            pageCount = pages.size
                                        } else {
                                            detailMsg = "Chapter pages timeout (>15s)"
                                        }
                                    }
                                } else {
                                    detailMsg = "Details timeout (>15s)"
                                }
                            } catch (e: Exception) {
                                detailMsg = "Details/Chapter: ${e::class.java.simpleName}: ${e.message}"
                            }
                        } else {
                            detailMsg = "Empty comic list returned"
                        }

                        ComicProbeResult(
                            id = info.id,
                            name = info.name,
                            browseOk = comics.isNotEmpty(),
                            itemCount = comics.size,
                            chapterCount = chapterCount,
                            pageCount = pageCount,
                            detail = detailMsg,
                            durationMs = System.currentTimeMillis() - startMs,
                        )
                    } catch (e: Exception) {
                        ComicProbeResult(
                            id = info.id,
                            name = info.name,
                            browseOk = false,
                            itemCount = 0,
                            chapterCount = 0,
                            pageCount = 0,
                            detail = "Browse Error: ${e::class.java.simpleName}: ${e.message}",
                            durationMs = System.currentTimeMillis() - startMs,
                        )
                    }
                }
            }.awaitAll()
        }

        println("\n=================================================================")
        println("                      VIDEO SOURCES REPORT                       ")
        println("=================================================================")
        videoResults.forEachIndexed { i, res ->
            val status = if (res.feedOk && res.playbackOk) "[PASS]" else if (res.feedOk) "[FEED ONLY]" else "[FAIL]"
            println("%-3d %-12s %-10s Items: %-3d Playback: %-5s (%d ms) | %s".format(
                i + 1, res.name, status, res.itemCount, if (res.playbackOk) "OK" else "FAIL", res.durationMs, res.detail
            ))
        }

        println("\n=================================================================")
        println("                      COMIC SOURCES REPORT                       ")
        println("=================================================================")
        comicResults.forEachIndexed { i, res ->
            val status = if (res.browseOk && res.pageCount > 0) "[PASS]" else if (res.browseOk) "[PARTIAL]" else "[FAIL]"
            println("%-3d %-12s %-10s Comics: %-3d Chapters: %-3d Pages: %-3d (%d ms) | %s".format(
                i + 1, res.name, status, res.itemCount, res.chapterCount, res.pageCount, res.durationMs, res.detail
            ))
        }
        println("=================================================================\n")
    }

    data class VideoProbeResult(
        val name: String,
        val baseUrl: String,
        val feedOk: Boolean,
        val itemCount: Int,
        val playbackOk: Boolean,
        val detail: String,
        val durationMs: Long,
    )

    data class ComicProbeResult(
        val id: String,
        val name: String,
        val browseOk: Boolean,
        val itemCount: Int,
        val chapterCount: Int,
        val pageCount: Int,
        val detail: String,
        val durationMs: Long,
    )
}
