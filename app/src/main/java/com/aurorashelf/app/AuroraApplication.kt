package com.aurorashelf.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.aurorashelf.app.data.OfflineCacheManager
import com.aurorashelf.app.data.comic.EhentaiDns
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

class AuroraApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // Recreate Media3's persistent download manager as soon as the app process starts.
        OfflineCacheManager.get(this)
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
