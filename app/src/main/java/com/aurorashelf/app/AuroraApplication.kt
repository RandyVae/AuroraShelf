package com.aurorashelf.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.aurorashelf.app.data.OfflineCacheManager
import com.aurorashelf.app.data.comic.EhentaiDns
import com.aurorashelf.app.ui.CoverImageLoader
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Path.Companion.toOkioPath

private const val IMAGE_CACHE_DIRECTORY = "image_cache"
private const val MEMORY_CACHE_MAX_BYTES = 64L * 1024L * 1024L
private const val DISK_CACHE_MAX_BYTES = 128L * 1024L * 1024L
private const val MEMORY_CACHE_HEAP_DIVISOR = 4L

class AuroraApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // Install the process-wide cover memory/disk cache before any cover request can start.
        CoverImageLoader.initialize(this)
        // Recreate Media3's persistent download manager as soon as the app process starts, but
        // off the main thread: constructing it opens the download database and the disk cache.
        // Media3 resolves its application handler with createHandlerForCurrentOrMainLooper, so a
        // Looper-less warm-up thread keeps listener callbacks on the main looper exactly as before.
        Thread(
            { runCatching { OfflineCacheManager.get(this) } },
            "offline-cache-warmup",
        ).apply {
            isDaemon = true
            start()
        }
    }

    override fun newImageLoader(context: Context): ImageLoader {
        val dispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 12
        }
        val client = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .dns(EhentaiDns)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .addInterceptor(comicPageInterceptor())
            .build()
        // Coil 3.3.0 defaults (verified against the shipped jar): memory cache = 15% of the app
        // heap on low-RAM devices / 20% otherwise, disk cache = a process-wide singleton under the
        // system temp dir ("coil3_disk_cache", 2% free space clamped to 10–250 MB). We declare
        // both explicitly so the budget/directory are stable and not shared by accident.
        val memoryCache = MemoryCache.Builder()
            .maxSizeBytes {
                (Runtime.getRuntime().maxMemory() / MEMORY_CACHE_HEAP_DIVISOR)
                    .coerceAtMost(MEMORY_CACHE_MAX_BYTES)
            }
            .build()
        val diskCache = DiskCache.Builder()
            .directory(File(context.applicationContext.cacheDir, IMAGE_CACHE_DIRECTORY).toOkioPath())
            .maxSizeBytes(DISK_CACHE_MAX_BYTES)
            .build()
        return ImageLoader.Builder(context)
            .memoryCache(memoryCache)
            .diskCache(diskCache)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
    }

    private fun comicPageInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val urlString = request.url.toString()
        val referer = request.header("Referer").orEmpty()
        var response: Response = chain.proceed(request)

        val host = request.url.host.lowercase()
        if (!response.isSuccessful && host.endsWith("bzcdn.net")) {
            val fallbackHosts = listOf("static-tw.baozimh.com", "s1.bzcdn.net", "s2.bzcdn.net")
                .filterNot { it.equals(host, ignoreCase = true) }
            for (candidateHost in fallbackHosts) {
                val fallbackUrl = request.url.newBuilder().host(candidateHost).build()
                val fallbackReferer = if (candidateHost.contains("baozimh")) {
                    "https://cn.twbzmg.com/"
                } else {
                    referer.ifBlank { "https://cn.webmota.com/" }
                }
                val fallbackRequest = request.newBuilder()
                    .url(fallbackUrl)
                    .header("Referer", fallbackReferer)
                    .build()
                val candidateResponse = runCatching { chain.proceed(fallbackRequest) }.getOrNull()
                if (candidateResponse != null) {
                    if (candidateResponse.isSuccessful) {
                        response.close()
                        response = candidateResponse
                        break
                    } else {
                        candidateResponse.close()
                    }
                }
            }
        }

        if (response.isSuccessful && CoverImageLoader.isEncryptedCover(urlString, referer)) {
            val mediaType = response.body?.contentType() ?: "image/jpeg".toMediaType()
            val rawBytes = response.body?.bytes()
            if (rawBytes != null) {
                val decoded = CoverImageLoader.decryptPayloadIfNeeded(rawBytes, urlString, referer)
                return@Interceptor response.newBuilder()
                    .body(decoded.toResponseBody(mediaType))
                    .build()
            }
        }

        response
    }
}
