package com.aurorashelf.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.aurorashelf.app.data.OfflineCacheManager
import com.aurorashelf.app.data.resilience.ResilientDns
import com.aurorashelf.app.data.resilience.SourceHealthRegistry
import com.aurorashelf.app.data.resilience.SourceResilienceBootstrap
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
        // 初始化全链路端侧自愈与网络预热基础设施（SWR 多槽位快照 / TUN-Safe DNS / 镜像健康度注册中心）
        SourceResilienceBootstrap.initialize(this)
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
            .dns(ResilientDns)
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
        val rawHost = request.url.host.lowercase()
        val cdnPoolKey = when {
            rawHost.endsWith("bzcdn.net") || rawHost.endsWith("baozicdn.com") || rawHost.contains("baozimh") -> "baozi-cdn"
            rawHost.contains("jmapiproxy") || rawHost.contains("jmapinode") -> "jm-cdn"
            else -> null
        }

        // 若初始图片 CDN 已处于熔断状态，直接提前切换至健康度最高的候选 CDN，避免逐页等待超时
        var activeRequest = request
        var activeHost = rawHost
        if (cdnPoolKey != null && SourceHealthRegistry.isCircuitOpen(rawHost)) {
            val bestOrigin = SourceHealthRegistry.resolveOrigins(cdnPoolKey).firstOrNull()
            val bestHost = bestOrigin?.let { runCatching { java.net.URI(it).host?.lowercase() }.getOrNull() }
            if (!bestHost.isNullOrBlank() && bestHost != rawHost) {
                val reroutedUrl = request.url.newBuilder().host(bestHost).build()
                val reroutedReferer = when {
                    bestHost.contains("baozimh") -> "https://cn.twbzmg.com/"
                    cdnPoolKey == "baozi-cdn" -> referer.ifBlank { "https://cn.webmota.com/" }
                    else -> referer
                }
                activeRequest = request.newBuilder()
                    .url(reroutedUrl)
                    .apply { if (reroutedReferer.isNotBlank()) header("Referer", reroutedReferer) }
                    .build()
                activeHost = bestHost
            }
        }

        val startedAt = System.currentTimeMillis()
        var response: Response? = runCatching { chain.proceed(activeRequest) }.getOrNull()
        if (response != null && response.isSuccessful) {
            if (cdnPoolKey != null) {
                SourceHealthRegistry.recordSuccess(cdnPoolKey, activeHost, System.currentTimeMillis() - startedAt)
            }
        } else {
            val statusCode = response?.code ?: 0
            if (cdnPoolKey != null) {
                SourceHealthRegistry.recordFailure(cdnPoolKey, activeHost, statusCode = statusCode, isHardBlock = statusCode == 0 || statusCode >= 500)
                val backupHosts = SourceHealthRegistry.resolveOrigins(cdnPoolKey)
                    .mapNotNull { runCatching { java.net.URI(it).host?.lowercase() }.getOrNull() }
                    .filterNot { it.equals(activeHost, ignoreCase = true) || it.equals(rawHost, ignoreCase = true) }
                for (candidateHost in backupHosts) {
                    val fallbackUrl = request.url.newBuilder().host(candidateHost).build()
                    val fallbackReferer = when {
                        candidateHost.contains("baozimh") -> "https://cn.twbzmg.com/"
                        cdnPoolKey == "baozi-cdn" -> referer.ifBlank { "https://cn.webmota.com/" }
                        else -> referer
                    }
                    val fallbackRequest = request.newBuilder()
                        .url(fallbackUrl)
                        .apply { if (fallbackReferer.isNotBlank()) header("Referer", fallbackReferer) }
                        .build()
                    val hopStarted = System.currentTimeMillis()
                    val candidateResponse = runCatching { chain.proceed(fallbackRequest) }.getOrNull()
                    if (candidateResponse != null) {
                        if (candidateResponse.isSuccessful) {
                            SourceHealthRegistry.recordSuccess(cdnPoolKey, candidateHost, System.currentTimeMillis() - hopStarted)
                            response?.close()
                            response = candidateResponse
                            break
                        } else {
                            SourceHealthRegistry.recordFailure(cdnPoolKey, candidateHost, statusCode = candidateResponse.code)
                            candidateResponse.close()
                        }
                    } else {
                        SourceHealthRegistry.recordFailure(cdnPoolKey, candidateHost, statusCode = 0, isHardBlock = true)
                    }
                }
            }
        }

        val finalResponse = response ?: chain.proceed(request)

        if (finalResponse.isSuccessful && CoverImageLoader.isEncryptedCover(urlString, referer)) {
            val mediaType = finalResponse.body?.contentType() ?: "image/jpeg".toMediaType()
            val rawBytes = finalResponse.body?.bytes()
            if (rawBytes != null) {
                val decoded = CoverImageLoader.decryptPayloadIfNeeded(rawBytes, urlString, referer)
                return@Interceptor finalResponse.newBuilder()
                    .body(decoded.toResponseBody(mediaType))
                    .build()
            }
        }

        finalResponse
    }
}
