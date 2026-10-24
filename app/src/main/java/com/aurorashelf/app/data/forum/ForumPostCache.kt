package com.aurorashelf.app.data.forum

import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CachedPostHtml(val html: String, val fetchedAtMs: Long)

/**
 * Prefetches and caches rendered post HTML so opening a thread can paint from local data instead
 * of waiting for the full page round-trip. Entries are small (usually tens of KB) and expire so a
 * stale thread does not hide edits.
 */
internal object ForumPostCache {
    private const val TAG = "ForumPostCache"
    private const val MAX_ENTRIES = 8
    private const val TTL_MS = 30 * 60_000L
    private const val MIN_HTML_SIZE = 1_024
    private const val MAX_HTML_SIZE = 4_000_000

    private val cache = object : LruCache<String, CachedPostHtml>(MAX_ENTRIES) {}
    private val inFlight = mutableSetOf<String>()

    fun get(url: String): String? {
        val entry = cache.get(url) ?: return null
        if (System.currentTimeMillis() - entry.fetchedAtMs > TTL_MS) {
            cache.remove(url)
            return null
        }
        return entry.html
    }

    fun put(url: String, html: String) {
        if (html.length !in MIN_HTML_SIZE..MAX_HTML_SIZE) return
        cache.put(url, CachedPostHtml(html, System.currentTimeMillis()))
    }

    suspend fun prefetch(url: String) = withContext(Dispatchers.IO) {
        if (get(url) != null) return@withContext
        synchronized(inFlight) {
            if (!inFlight.add(url)) return@withContext
        }
        try {
            val started = System.currentTimeMillis()
            val document = ForumHttp.fetchDocument(url, mobileLayout = true)
            val html = document.outerHtml()
            put(url, html)
            Log.i(TAG, "prefetch ms=${System.currentTimeMillis() - started} bytes=${html.length}")
        } catch (failure: Exception) {
            Log.i(TAG, "prefetch failed: ${failure.message}")
        } finally {
            synchronized(inFlight) { inFlight.remove(url) }
        }
    }
}
