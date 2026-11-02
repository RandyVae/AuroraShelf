package com.aurorashelf.app.data.comic

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

class ComicSourceProbeTest {

    @Test
    fun probeAllComicSources() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE_SOURCE_TESTS") == "1")
        val repo = ComicRepository()
        println("===== PROBING ALL ${repo.availableSources.size} COMIC SOURCES =====")
        for (info in repo.availableSources) {
            println("--- [${info.name}] (id=${info.id}) requiresAuth=${info.requiresAuthentication} ---")
            if (info.requiresAuthentication) {
                println("  [${info.id}] Skipped live browse: Authentication required")
                continue
            }
            for (cat in info.categories.take(3)) {
                try {
                    val items = repo.browse(info.id, cat.id, 1)
                    print("  Cat [${cat.label} (id=${cat.id})]: ${items.size} items")
                    val first = items.firstOrNull()
                    if (first != null) {
                        try {
                            val details = repo.details(first)
                            print(" -> Details OK (${details.chapters.size} chapters)")
                            val firstChapter = details.chapters.firstOrNull()
                            if (firstChapter != null) {
                                try {
                                    val pages = repo.chapter(first, firstChapter.id)
                                    println(" -> Chapter OK (${pages.size} pages)")
                                } catch (e: Exception) {
                                    println(" -> Chapter FAILED: ${e::class.java.simpleName}: ${e.message}")
                                }
                            } else {
                                println(" -> No chapters in details")
                            }
                        } catch (e: Exception) {
                            println(" -> Details FAILED: ${e::class.java.simpleName}: ${e.message}")
                        }
                    } else {
                        println(" -> Empty list")
                    }
                } catch (e: Exception) {
                    println("  Cat [${cat.label} (id=${cat.id})]: FAILED: ${e::class.java.simpleName}: ${e.message}")
                }
            }
        }
        println("===== COMIC PROBE FINISHED =====")
    }
}
