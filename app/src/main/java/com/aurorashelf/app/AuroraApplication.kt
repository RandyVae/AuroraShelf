package com.aurorashelf.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.aurorashelf.app.data.OfflineCacheManager
import com.aurorashelf.app.data.comic.EhentaiDns
import com.aurorashelf.app.ui.CoverImageLoader
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

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
            .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
    }
}
